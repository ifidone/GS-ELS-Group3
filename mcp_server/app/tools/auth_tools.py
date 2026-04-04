from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()

    @mcp.tool()
    def auth_sync(id_token: str, uid: str = "") -> dict:
        if not id_token or not id_token.strip():
            return error_response("MISSING_INPUT", "id_token is required", ["id_token"])
        headers = {"Authorization": f"Bearer {id_token.strip()}"}
        payload = {"uid": uid.strip()} if uid and uid.strip() else None
        response = backend.post("/api/auth/sync", json_body=payload, headers=headers)
        if response.status_code >= 400:
            return error_response(f"HTTP_{response.status_code}", "Auth sync failed.")
        return response.data or {}
