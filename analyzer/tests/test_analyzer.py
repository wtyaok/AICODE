from app.diff_parser import parse_added_lines
from app.models import AnalyzeRequest, Rule
from app.rules import run_rules


def test_only_added_lines_are_analyzed():
    request = AnalyzeRequest(
        diff=(
            "diff --git a/src/a.py b/src/a.py\n"
            "--- a/src/a.py\n"
            "+++ b/src/a.py\n"
            "@@ -1,2 +1,2 @@\n"
            "-TODO: old line\n"
            " context\n"
            "+TODO: new line\n"
        )
    )
    lines = parse_added_lines(request)
    assert [(line.file, line.line, line.text) for line in lines] == [
        ("src/a.py", 2, "TODO: new line")
    ]


def test_rule_finding_has_file_and_line():
    request = AnalyzeRequest(
        diff=(
            "diff --git a/src/a.py b/src/a.py\n"
            "--- a/src/a.py\n"
            "+++ b/src/a.py\n"
            "@@ -0,0 +1 @@\n"
            "+console.log(value)\n"
        ),
        rules=[
            Rule(
                id="console",
                name="调试日志",
                severity="low",
                pattern=r"console\.log",
                suggestion="使用结构化日志",
            )
        ],
    )
    findings = run_rules(parse_added_lines(request), request.rules)
    assert len(findings) == 1
    assert findings[0].file == "src/a.py"
    assert findings[0].line == 1


def test_duplicate_rule_reports_second_contiguous_block_only():
    request = AnalyzeRequest(
        diff=(
            "diff --git a/src/a.js b/src/a.js\n"
            "--- a/src/a.js\n"
            "+++ b/src/a.js\n"
            "@@ -0,0 +1,8 @@\n"
            "+const result = fetchItem();\n"
            "+const normalized = result.trim();\n"
            "+return normalized.toLowerCase();\n"
            "+\n"
            "+const result = fetchItem();\n"
            "+const normalized = result.trim();\n"
            "+return normalized.toLowerCase();\n"
            "+\n"
        ),
        rules=[
            Rule(
                id="duplicate-block",
                name="重复新增代码片段",
                severity="low",
                pattern=r"\S",
                suggestion="抽取公共逻辑",
            )
        ],
    )
    findings = run_rules(parse_added_lines(request), request.rules)
    assert [(item.file, item.line) for item in findings] == [("src/a.js", 5)]
