from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient


def register(mcp: FastMCP) -> None:
    backend = BackendClient()

    @mcp.tool()
    def health_check() -> dict:
        response = backend.get("/api/funds")
        backend_status = "reachable" if response.status_code < 500 else "error"
        return {
            "status": "ok",
            "backend": backend_status
        }
