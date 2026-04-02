from __future__ import annotations

from typing import Iterable


def error_response(code: str, message: str, missing: Iterable[str] | None = None) -> dict:
    payload = {"error": True, "code": code, "message": message}
    if missing:
        payload["missing"] = list(missing)
    return payload
