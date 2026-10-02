from fastapi import FastAPI, HTTPException
from unidiff.errors import UnidiffParseError

from .diff_parser import parse_added_lines
from .llm import run_llm
from .models import AnalyzeRequest, AnalyzeResponse
from .rules import run_rules

app = FastAPI(title="Review Analyzer", version="1.0.0")


@app.get("/health")
def health() -> dict[str, bool]:
    return {"ok": True}


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    try:
        lines = parse_added_lines(request)
    except (UnidiffParseError, ValueError) as error:
        raise HTTPException(status_code=422, detail=f"diff 格式无效：{error}") from error

    diff_text = request.diff or "\n".join(
        f"diff --git a/{item.oldPath or item.path} b/{item.path}\n{item.diff}"
        for item in request.files
    )
    rule_findings = run_rules(lines, request.rules)
    llm_findings, llm_error = run_llm(diff_text, lines)

    unique = {}
    for finding in rule_findings + llm_findings:
        key = (finding.file, finding.line, finding.category)
        unique.setdefault(key, finding)

    priority = {"critical": 0, "high": 1, "medium": 2, "low": 3}
    findings = sorted(
        unique.values(),
        key=lambda item: (priority[item.severity], item.file, item.line),
    )
    return AnalyzeResponse(findings=findings, llmError=llm_error)
