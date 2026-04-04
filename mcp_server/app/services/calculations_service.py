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

    def list_calculations_endpoint(self, uid: str) -> dict[str, Any]:
        response = self._backend.get("/api/calculations", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to load calculations.")
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

    def create(self,
               uid: str,
               name: str | None,
               ticker: str,
               initial_investment: float,
               years: float,
               beta: float,
               expected_return: float,
               future_value: float) -> dict[str, Any]:
        response = self._backend.post("/api/calculations", json_body={
            "uid": uid,
            "name": name,
            "ticker": ticker,
            "initialInvestment": initial_investment,
            "years": years,
            "beta": beta,
            "expectedReturn": expected_return,
            "futureValue": future_value
        })
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to create saved calculation.")
        return _detail_item(response.data or {})

    def patch(self,
              uid: str,
              calculation_id: int,
              name: str | None = None,
              ticker: str | None = None,
              initial_investment: float | None = None,
              years: float | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {"uid": uid}
        if name is not None:
            payload["name"] = name
        if ticker is not None:
            payload["ticker"] = ticker
        if initial_investment is not None:
            payload["initialInvestment"] = initial_investment
        if years is not None:
            payload["years"] = years
        response = self._backend.patch(f"/api/saved-calculations/{calculation_id}", json_body=payload)
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to update saved calculation.")
        return _detail_item(response.data or {})

    def delete(self, uid: str, calculation_id: int) -> dict[str, Any]:
        response = self._backend.delete(f"/api/saved-calculations/{calculation_id}", json_body={"uid": uid})
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to delete saved calculation.")
        return {"deleted": True}

    def update_with_auth(self,
                         id_token: str,
                         calculation_id: int,
                         name: str,
                         ticker: str,
                         initial_investment: float,
                         years: float,
                         beta: float,
                         expected_return: float,
                         future_value: float) -> dict[str, Any]:
        headers = {"Authorization": f"Bearer {id_token}"}
        response = self._backend.put(f"/api/calculations/{calculation_id}", json_body={
            "name": name,
            "ticker": ticker,
            "initialInvestment": initial_investment,
            "years": years,
            "beta": beta,
            "expectedReturn": expected_return,
            "futureValue": future_value
        }, headers=headers)
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to update calculation.")
        return _detail_item(response.data or {})

    def delete_with_auth(self, id_token: str, calculation_id: int) -> dict[str, Any]:
        headers = {"Authorization": f"Bearer {id_token}"}
        response = self._backend.delete(f"/api/calculations/{calculation_id}", headers=headers)
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to delete calculation.")
        return {"deleted": True}

    def monte_carlo(self,
                    ticker: str,
                    principal: float,
                    time_years: float,
                    goal_amount: float | None = None,
                    n_simulations: int | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "ticker": ticker,
            "principal": principal,
            "timeYears": time_years
        }
        if goal_amount is not None:
            payload["goalAmount"] = goal_amount
        if n_simulations is not None:
            payload["nSimulations"] = n_simulations
        response = self._backend.post("/api/monte-carlo", json_body=payload)
        if response.status_code >= 400:
            return _error_response(response.status_code, "Failed to run Monte Carlo simulation.")
        return response.data or {}


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
