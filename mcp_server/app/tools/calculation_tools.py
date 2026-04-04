from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.calculations_service import CalculationsService
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    service = CalculationsService(backend)

    @mcp.tool()
    def saved_calculations_list(uid: str, search: str = "", limit: int = 0) -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        search_value = search.strip() if isinstance(search, str) else ""
        result = service.list(uid.strip(), search=search_value or None)
        if isinstance(limit, int) and limit > 0 and isinstance(result.get("items"), list):
            result["items"] = result["items"][:limit]
            result["count"] = len(result["items"])
        return result

    @mcp.tool()
    def calculations_list(uid: str, limit: int = 0) -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        result = service.list_calculations_endpoint(uid.strip())
        if isinstance(limit, int) and limit > 0 and isinstance(result.get("items"), list):
            result["items"] = result["items"][:limit]
            result["count"] = len(result["items"])
        return result

    @mcp.tool()
    def saved_calculation_detail(uid: str, calculation_id: int) -> dict:
        missing = [name for name, value in {"uid": uid, "calculation_id": calculation_id}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and calculation_id are required", missing)
        return service.detail(uid.strip(), int(calculation_id))

    @mcp.tool()
    def saved_calculation_time_series(uid: str, calculation_id: int) -> dict:
        missing = [name for name, value in {"uid": uid, "calculation_id": calculation_id}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and calculation_id are required", missing)
        return service.time_series(uid.strip(), int(calculation_id))

    @mcp.tool()
    def saved_calculations_compare(uid: str, calculation_ids: list[int]) -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        if not calculation_ids:
            return error_response("MISSING_INPUT", "calculation_ids are required", ["calculation_ids"])
        ids = [int(value) for value in calculation_ids]
        return service.compare(uid.strip(), ids)

    @mcp.tool()
    def project_calculation(ticker: str, initial_investment: float, years: float, uid: str = "") -> dict:
        missing = [name for name, value in {
            "ticker": ticker,
            "initial_investment": initial_investment,
            "years": years
        }.items() if value in (None, "", 0)]
        if missing:
            return error_response("MISSING_INPUT", "ticker, initial_investment, and years are required", missing)
        return service.project(ticker, float(initial_investment), float(years))

    @mcp.tool()
    def saved_calculation_create(uid: str,
                                 ticker: str,
                                 initial_investment: float,
                                 years: float,
                                 beta: float,
                                 expected_return: float,
                                 future_value: float,
                                 name: str = "") -> dict:
        missing = [name for name, value in {
            "uid": uid,
            "ticker": ticker,
            "initial_investment": initial_investment,
            "years": years,
            "beta": beta,
            "expected_return": expected_return,
            "future_value": future_value
        }.items() if value in (None, "", 0)]
        if missing:
            return error_response("MISSING_INPUT",
                                  "uid, ticker, initial_investment, years, beta, expected_return, future_value are required",
                                  missing)
        return service.create(uid.strip(),
                              name.strip() if name else None,
                              ticker.strip().upper(),
                              float(initial_investment),
                              float(years),
                              float(beta),
                              float(expected_return),
                              float(future_value))

    @mcp.tool()
    def saved_calculation_update(uid: str,
                                 calculation_id: int,
                                 name: str = "",
                                 ticker: str = "",
                                 initial_investment: float = 0,
                                 years: float = 0) -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        if not calculation_id:
            return error_response("MISSING_INPUT", "calculation_id is required", ["calculation_id"])
        return service.patch(
            uid.strip(),
            int(calculation_id),
            name.strip() if name else None,
            ticker.strip().upper() if ticker else None,
            float(initial_investment) if initial_investment else None,
            float(years) if years else None
        )

    @mcp.tool()
    def saved_calculation_delete(uid: str, calculation_id: int) -> dict:
        missing = [name for name, value in {"uid": uid, "calculation_id": calculation_id}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and calculation_id are required", missing)
        return service.delete(uid.strip(), int(calculation_id))

    @mcp.tool()
    def saved_calculation_update_with_auth(id_token: str,
                                           calculation_id: int,
                                           name: str,
                                           ticker: str,
                                           initial_investment: float,
                                           years: float,
                                           beta: float,
                                           expected_return: float,
                                           future_value: float,
                                           uid: str = "") -> dict:
        missing = [name for name, value in {
            "id_token": id_token,
            "calculation_id": calculation_id,
            "name": name,
            "ticker": ticker,
            "initial_investment": initial_investment,
            "years": years,
            "beta": beta,
            "expected_return": expected_return,
            "future_value": future_value
        }.items() if value in (None, "", 0)]
        if missing:
            return error_response("MISSING_INPUT", "all inputs are required", missing)
        return service.update_with_auth(
            id_token.strip(),
            int(calculation_id),
            name.strip(),
            ticker.strip().upper(),
            float(initial_investment),
            float(years),
            float(beta),
            float(expected_return),
            float(future_value)
        )

    @mcp.tool()
    def saved_calculation_delete_with_auth(id_token: str, calculation_id: int, uid: str = "") -> dict:
        missing = [name for name, value in {"id_token": id_token, "calculation_id": calculation_id}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "id_token and calculation_id are required", missing)
        return service.delete_with_auth(id_token.strip(), int(calculation_id))

    @mcp.tool()
    def monte_carlo_simulation(ticker: str,
                               principal: float,
                               time_years: float,
                               goal_amount: float = 0,
                               n_simulations: int = 0,
                               uid: str = "") -> dict:
        missing = [name for name, value in {
            "ticker": ticker,
            "principal": principal,
            "time_years": time_years
        }.items() if value in (None, "", 0)]
        if missing:
            return error_response("MISSING_INPUT", "ticker, principal, and time_years are required", missing)
        return service.monte_carlo(
            ticker.strip().upper(),
            float(principal),
            float(time_years),
            float(goal_amount) if goal_amount else None,
            int(n_simulations) if n_simulations else None
        )
