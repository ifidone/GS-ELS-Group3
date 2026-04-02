from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.calculations_service import CalculationsService
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    service = CalculationsService(backend)

    @mcp.tool()
    def saved_calculations_list(uid: str, search: str | None = None, limit: int | None = None) -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        result = service.list(uid.strip(), search=search)
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
    def project_calculation(ticker: str, initial_investment: float, years: float) -> dict:
        missing = [name for name, value in {
            "ticker": ticker,
            "initial_investment": initial_investment,
            "years": years
        }.items() if value in (None, "", 0)]
        if missing:
            return error_response("MISSING_INPUT", "ticker, initial_investment, and years are required", missing)
        return service.project(ticker, float(initial_investment), float(years))
