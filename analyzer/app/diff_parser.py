from dataclasses import dataclass
from io import StringIO

from unidiff import PatchSet

from .models import AnalyzeRequest


@dataclass(frozen=True)
class AddedLine:
    file: str
    line: int
    text: str


def parse_added_lines(request: AnalyzeRequest) -> list[AddedLine]:
    if request.files:
        patches = []
        for item in request.files:
            old_path = item.oldPath or item.path
            header = (
                f"diff --git a/{old_path} b/{item.path}\n"
                f"--- a/{old_path}\n"
                f"+++ b/{item.path}\n"
            )
            patches.append(header + item.diff.rstrip("\n") + "\n")
        raw = "".join(patches)
    else:
        raw = request.diff

    if not raw.strip():
        return []

    patch_set = PatchSet(StringIO(raw))
    result = []

    for patched_file in patch_set:
        path = patched_file.path
        if path.startswith("b/"):
            path = path[2:]
        for hunk in patched_file:
            for line in hunk:
                if line.is_added and line.target_line_no is not None:
                    result.append(
                        AddedLine(
                            file=path,
                            line=line.target_line_no,
                            text=line.value.rstrip("\r\n"),
                        )
                    )

    return result
