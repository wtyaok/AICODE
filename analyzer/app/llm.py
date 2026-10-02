import json
import os

from openai import OpenAI
from pydantic import ValidationError

from .diff_parser import AddedLine
from .models import Finding


SYSTEM_PROMPT = (
    "你是代码审查器。仅审查新增代码中可证实的风险。"
    "重点核对空值处理、SQL 拼接、异常处理、重复逻辑、接口返回兼容性、"
    "权限校验、敏感信息和日志风险。缺少上下文时不要推断确定性缺陷。"
    "只输出 JSON 对象，字段 findings 为数组。每条含 file、line、"
    "category、severity、message、suggestion。severity 只能是 "
    "critical/high/medium/low。无法确认时返回空数组。"
)


def run_llm(
    diff_text: str,
    added_lines: list[AddedLine],
) -> tuple[list[Finding], str | None]:
    api_key = os.getenv("LLM_API_KEY", "")
    model = os.getenv("LLM_MODEL", "")
    if not api_key or not model:
        return [], None

    allowed = {(line.file, line.line) for line in added_lines}
    if not allowed:
        return [], None

    try:
        client = OpenAI(
            api_key=api_key,
            base_url=os.getenv("LLM_BASE_URL") or None,
            timeout=45,
            max_retries=1,
        )
        completion = client.chat.completions.create(
            model=model,
            temperature=0.1,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": diff_text[:60000]},
            ],
        )
        content = completion.choices[0].message.content or ""
        parsed = json.loads(content)
        if not isinstance(parsed, dict) or not isinstance(
            parsed.get("findings"), list
        ):
            raise ValueError("LLM 未返回 findings 数组")

        findings = []
        for item in parsed["findings"][:50]:
            try:
                candidate = Finding(
                    file=item["file"],
                    line=item["line"],
                    ruleId="llm",
                    category=item["category"],
                    severity=item["severity"],
                    message=item["message"],
                    suggestion=item["suggestion"],
                    source="llm",
                )
            except (KeyError, TypeError, ValidationError):
                continue
            if (candidate.file, candidate.line) in allowed:
                findings.append(candidate)
        return findings, None
    except Exception as error:
        return [], f"{type(error).__name__}: {str(error)[:300]}"
