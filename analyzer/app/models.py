from typing import Literal

from pydantic import BaseModel, Field


Severity = Literal["critical", "high", "medium", "low"]


class DiffFile(BaseModel):
    path: str
    oldPath: str = ""
    diff: str


class Rule(BaseModel):
    id: str
    name: str
    severity: Severity
    pattern: str
    suggestion: str


class AnalyzeRequest(BaseModel):
    diff: str = ""
    files: list[DiffFile] = Field(default_factory=list)
    rules: list[Rule] = Field(default_factory=list)


class Finding(BaseModel):
    file: str
    line: int
    ruleId: str
    category: str
    severity: Severity
    message: str
    suggestion: str
    source: Literal["rule", "llm"]


class AnalyzeResponse(BaseModel):
    findings: list[Finding]
    llmError: str | None = None
