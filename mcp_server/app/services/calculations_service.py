from __future__ import annotations

from typing import Any

from app.clients.backend_client import BackendClient
from app.services.time_series import normalize_time_series_map


class CalculationsService:
    def __init__(self, backend: BackendClient) -> None:
        self._backend = backend

    def list(self, uid: str, search: str | None = None) -> dict[str, Any]:
        params = {}
        if search:
            params["name"] = search
        response = self._backend.get("/api/saved-calculations", params=params, json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load saved calculations.")

        items = []
        for row in response.data or []:
            items.append(_summary_item(row))
        return {"items": items, "count": len(items)}

    def detail(self, uid: str, calculation_id: int) -> dict[str, Any]:
        response = self._backend.get("/api/saved-calculations", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load saved calculations.")
        for row in response.data or []:
            if int(row.get("id")) == calculation_id:
                return _detail_item(row)
        return _error_response(404, "Calculation not found.")

    def time_series(self, uid: str, calculation_id: int) -> dict[str, Any]:
        response = self._backend.get("/api/saved-calculations", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load saved calculations.")
        for row in response.data or []:
            if int(row.get("id")) == calculation_id:
                series = normalize_time_series_map(row.get("timeSeries") or row.get("time_series"))
                return {"calculationId": str(calculation_id), "series": series}
        return _error_response(404, "Calculation not found.")

    def compare(self, uid: str, calculation_ids: list[int]) -> dict[str, Any]:
        response = self._backend.get("/api/saved-calculations", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load saved calculations.")
        comparison_items = []
        for row in response.data or []:
            row_id = int(row.get("id"))
            if row_id in calculation_ids:
                comparison_items.append({
                    "id": str(row_id),
                    "futureValue": row.get("futureValue"),
                    "series": normalize_time_series_map(row.get("timeSeries") or row.get("time_series"))
                })
        return {"comparisons": comparison_items}

    def project(self, ticker: str, initial_investment: float, years: float) -> dict[str, Any]:
        response = self._backend.post("/api/calculator/project", json_body={
            "ticker": ticker,
            "initialInvestment": initial_investment,
            "years": years
        })
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to project calculation.")
        data = response.data or {}
        return {
            "ticker": data.get("ticker"),
            "futureValue": data.get("futureValue"),
            "beta": data.get("beta"),
            "expectedReturn": data.get("expectedReturn"),
            "series": normalize_time_series_map(data.get("timeSeries") or {})
        }


def _summary_item(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "id": str(row.get("id")),
        "name": row.get("name"),
        "ticker": row.get("ticker"),
        "initialInvestment": row.get("initialInvestment"),
        "years": row.get("years"),
        "beta": row.get("beta"),
        "expectedReturn": row.get("expectedReturn"),
        "futureValue": row.get("futureValue")
    }


def _detail_item(row: dict[str, Any]) -> dict[str, Any]:
    return {
        **_summary_item(row),
        "timeSeries": normalize_time_series_map(row.get("timeSeries") or row.get("time_series"))
    }


def _error_response(status_code: int, message: str) -> dict[str, Any]:
    return {
        "error": True,
        "code": f"HTTP_{status_code}",
        "message": message
    }
