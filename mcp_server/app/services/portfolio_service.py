from __future__ import annotations

from typing import Any

from app.clients.backend_client import BackendClient


class PortfolioService:
    def __init__(self, backend: BackendClient) -> None:
        self._backend = backend

    def list(self, uid: str, page: int | None = None, size: int | None = None,
             sort: str | None = None) -> dict[str, Any]:
        params: dict[str, Any] = {}
        if page is not None:
            params["page"] = page
        if size is not None:
            params["size"] = size
        if sort:
            params["sort"] = sort
        response = self._backend.get("/api/portfolios", params=params or None, json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load portfolios.")
        items = []
        for row in response.data or []:
            metadata = row.get("metadata") or {}
            summary = row.get("summary") or {}
            items.append({
                "name": metadata.get("name"),
                "description": metadata.get("description"),
                "calculationCount": summary.get("calculationCount"),
                "totalPrincipal": summary.get("totalPrincipal"),
                "totalFutureValue": summary.get("totalFutureValue"),
                "avgBeta": summary.get("avgBeta")
            })
        return {"items": items, "count": len(items)}

    def totals(self, uid: str) -> dict[str, Any]:
        response = self._backend.get("/api/portfolios", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load portfolios.")
        total_principal = 0.0
        total_future_value = 0.0
        for row in response.data or []:
            summary = row.get("summary") or {}
            principal = summary.get("totalPrincipal")
            future_value = summary.get("totalFutureValue")
            if isinstance(principal, (int, float)):
                total_principal += float(principal)
            if isinstance(future_value, (int, float)):
                total_future_value += float(future_value)
        return {
            "totalPrincipal": total_principal,
            "totalFutureValue": total_future_value,
            "portfolioCount": len(response.data or [])
        }

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

    def create(self, uid: str, name: str, description: str | None = None) -> dict[str, Any]:
        response = self._backend.post("/api/portfolios", json_body={
            "uid": uid,
            "name": name,
            "description": description
        })
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to create portfolio.")
        return response.data or {}

    def update(self, uid: str, name: str, new_name: str | None = None,
               description: str | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {"uid": uid}
        if new_name is not None:
            payload["name"] = new_name
        if description is not None:
            payload["description"] = description
        response = self._backend.patch(f"/api/portfolios/{name}", json_body=payload)
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to update portfolio.")
        return response.data or {}

    def delete(self, uid: str, name: str) -> dict[str, Any]:
        response = self._backend.delete(f"/api/portfolios/{name}", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to delete portfolio.")
        return {"deleted": True}

    def add_item(self, uid: str, name: str, calculation_id: int) -> dict[str, Any]:
        response = self._backend.put(
            f"/api/portfolios/{name}/items/{calculation_id}",
            json_body={"uid": uid}
        )
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to add calculation to portfolio.")
        return {"added": True}

    def remove_item(self, uid: str, name: str, calculation_id: int) -> dict[str, Any]:
        response = self._backend.delete(
            f"/api/portfolios/{name}/items/{calculation_id}",
            json_body={"uid": uid}
        )
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to remove calculation from portfolio.")
        return {"removed": True}

    def available_calculations(self, uid: str, name: str) -> dict[str, Any]:
        response = self._backend.get(
            f"/api/portfolios/{name}/available-calculations",
            json_body={"uid": uid}
        )
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load available calculations.")
        return {"items": response.data or [], "count": len(response.data or [])}


def _error_response(status_code: int, message: str) -> dict[str, Any]:
    return {
        "error": True,
        "code": f"HTTP_{status_code}",
        "message": message
    }
