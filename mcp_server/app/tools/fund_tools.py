from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.funds_service import FundsService
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    service = FundsService(backend)

    @mcp.tool()
    def funds_list() -> dict:
        return service.list()

    @mcp.tool()
    def fund_detail(ticker: str) -> dict:
        if not ticker or not ticker.strip():
            return error_response("MISSING_INPUT", "ticker is required", ["ticker"])
        return service.detail(ticker.strip().upper())
