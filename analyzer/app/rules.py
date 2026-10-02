import re

from .diff_parser import AddedLine
from .models import Finding, Rule


def run_rules(lines: list[AddedLine], rules: list[Rule]) -> list[Finding]:
    results: list[Finding] = []

    for rule in rules:
        try:
            pattern = re.compile(rule.pattern, re.IGNORECASE)
        except re.error:
            continue

        if rule.id == "duplicate-block":
            results.extend(find_duplicate_blocks(lines, rule, pattern))
            continue

        for line in lines:
            if not pattern.search(line.text):
                continue
            results.append(
                Finding(
                    file=line.file,
                    line=line.line,
                    ruleId=rule.id,
                    category=rule.name,
                    severity=rule.severity,
                    message=f"{rule.name}：新增代码命中规则，请结合上下文确认。",
                    suggestion=rule.suggestion,
                    source="rule",
                )
            )

    return results


def find_duplicate_blocks(
    lines: list[AddedLine], rule: Rule, pattern: re.Pattern[str]
) -> list[Finding]:
    seen: dict[tuple[str, str, str], tuple[str, int]] = {}
    last_reported: dict[str, int] = {}
    findings = []

    for index in range(len(lines) - 2):
        block = lines[index : index + 3]
        if any(
            item.file != block[0].file or item.line != block[0].line + offset
            for offset, item in enumerate(block)
        ):
            continue
        if not all(pattern.search(item.text) for item in block):
            continue

        normalized = tuple(" ".join(item.text.split()) for item in block)
        if sum(len(item) for item in normalized) < 30:
            continue

        first = seen.setdefault(normalized, (block[0].file, block[0].line))
        if first == (block[0].file, block[0].line):
            continue
        if first[0] == block[0].file and block[0].line - first[1] < 3:
            continue
        if block[0].line - last_reported.get(block[0].file, -100) < 3:
            continue

        findings.append(
            Finding(
                file=block[0].file,
                line=block[0].line,
                ruleId=rule.id,
                category=rule.name,
                severity=rule.severity,
                message="发现与本次其他新增代码相同的连续三行，请确认是否重复实现。",
                suggestion=rule.suggestion,
                source="rule",
            )
        )
        last_reported[block[0].file] = block[0].line

    return findings
