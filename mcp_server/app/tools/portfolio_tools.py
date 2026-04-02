from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.portfolio_service import PortfolioService
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    service = PortfolioService(backend)

    @mcp.tool()
    def portfolio_list(uid: str) -> dict:
        if not uid or not uid.strip():
            return error_response("MISSING_INPUT", "uid is required", ["uid"])
        return service.list(uid.strip())

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
