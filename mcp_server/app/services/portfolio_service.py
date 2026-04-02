from __future__ import annotations

from typing import Any

from app.clients.backend_client import BackendClient


class PortfolioService:
    def __init__(self, backend: BackendClient) -> None:
        self._backend = backend

    def list(self, uid: str) -> dict[str, Any]:
        response = self._backend.get("/api/portfolios", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load portfolios.")
        items = []
        for row in response.data or []:
            metadata = row.get("metadata") or {}
            items.append({
                "name": metadata.get("name"),
                "description": metadata.get("description")
            })
        return {"items": items, "count": len(items)}

    def detail(self, uid: str, name: str) -> dict[str, Any]:
        response = self._backend.get(f"/api/portfolios/{name}", json_body={"uid": uid, "name": name})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Portfolio not found.")
        data = response.data or {}
        metadata = data.get("metadata") or {}
        return {
            "name": metadata.get("name"),
            "description": metadata.get("description"),
            "summary": data.get("summary"),
            "allocations": data.get("allocationBreakdown"),
            "projections": data.get("projectionPoints"),
            "linkedCalculations": data.get("linkedCalculations")
        }

    def items(self, uid: str, name: str) -> dict[str, Any]:
        response = self._backend.get(f"/api/portfolios/{name}/items", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load portfolio items.")
        items = []
        for row in response.data or []:
            items.append({
                "id": row.get("calculationId"),
                "ticker": row.get("ticker")
            })
        return {"items": items, "count": len(items)}


def _error_response(status_code: int, message: str) -> dict[str, Any]:
    return {
        "error": True,
        "code": f"HTTP_{status_code}",
        "message": message
    }
