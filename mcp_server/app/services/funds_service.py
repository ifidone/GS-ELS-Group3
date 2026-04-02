from __future__ import annotations

from typing import Any

from app.clients.backend_client import BackendClient


class FundsService:
    def __init__(self, backend: BackendClient) -> None:
        self._backend = backend

    def list(self) -> dict[str, Any]:
        response = self._backend.get("/api/funds")
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load funds.")
        items = []
        for row in response.data or []:
            items.append({
                "ticker": row.get("ticker"),
                "name": row.get("name"),
                "category": row.get("category")
            })
        return {"items": items, "count": len(items)}

    def detail(self, ticker: str) -> dict[str, Any]:
        response = self._backend.get(f"/api/funds/{ticker}")
        if response.status_code >= 400:
            return _error_response(response.status_code, "Fund not found.")
        row = response.data or {}
        return {
            "ticker": row.get("ticker"),
            "name": row.get("name"),
            "category": row.get("category")
        }


def _error_response(status_code: int, message: str) -> dict[str, Any]:
    return {
        "error": True,
        "code": f"HTTP_{status_code}",
        "message": message
    }
