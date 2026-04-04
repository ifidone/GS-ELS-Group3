from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.portfolio_service import PortfolioService
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    service = PortfolioService(backend)

    @mcp.tool()
    def portfolio_list(uid: str, page: int = -1, size: int = -1, sort: str = "") -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        return service.list(
            uid.strip(),
            page if isinstance(page, int) and page >= 0 else None,
            size if isinstance(size, int) and size > 0 else None,
            sort.strip() if sort else None
        )

    @mcp.tool()
    def portfolio_detail(uid: str, name: str) -> dict:
        missing = [key for key, value in {"uid": uid, "name": name}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and name are required", missing)
        return service.detail(uid.strip(), name.strip())

    @mcp.tool()
    def portfolio_items(uid: str, name: str) -> dict:
        missing = [key for key, value in {"uid": uid, "name": name}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and name are required", missing)
        return service.items(uid.strip(), name.strip())

    @mcp.tool()
    def portfolio_create(uid: str, name: str, description: str = "") -> dict:
        missing = [key for key, value in {"uid": uid, "name": name}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and name are required", missing)
        return service.create(uid.strip(), name.strip(), description.strip() if description else None)

    @mcp.tool()
    def portfolio_update(uid: str, name: str, new_name: str = "", description: str = "") -> dict:
        missing = [key for key, value in {"uid": uid, "name": name}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and name are required", missing)
        return service.update(
            uid.strip(),
            name.strip(),
            new_name.strip() if new_name else None,
            description.strip() if description else None
        )

    @mcp.tool()
    def portfolio_delete(uid: str, name: str) -> dict:
        missing = [key for key, value in {"uid": uid, "name": name}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and name are required", missing)
        return service.delete(uid.strip(), name.strip())

    @mcp.tool()
    def portfolio_add_item(uid: str, name: str, calculation_id: int) -> dict:
        missing = [key for key, value in {
            "uid": uid,
            "name": name,
            "calculation_id": calculation_id
        }.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid, name, and calculation_id are required", missing)
        return service.add_item(uid.strip(), name.strip(), int(calculation_id))

    @mcp.tool()
    def portfolio_remove_item(uid: str, name: str, calculation_id: int) -> dict:
        missing = [key for key, value in {
            "uid": uid,
            "name": name,
            "calculation_id": calculation_id
        }.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid, name, and calculation_id are required", missing)
        return service.remove_item(uid.strip(), name.strip(), int(calculation_id))

    @mcp.tool()
    def portfolio_available_calculations(uid: str, name: str) -> dict:
        missing = [key for key, value in {"uid": uid, "name": name}.items() if not value]
        if missing:
            return error_response("MISSING_INPUT", "uid and name are required", missing)
        return service.available_calculations(uid.strip(), name.strip())

    @mcp.tool()
    def portfolio_total_investment(uid: str) -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        return service.totals(uid.strip())
