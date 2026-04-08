from __future__ import annotations

import hmac

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response


class McpTokenAuthMiddleware(BaseHTTPMiddleware):
    """Protect MCP endpoint with a shared bearer token when enabled."""

    def __init__(self, app, token: str, mcp_path: str) -> None:
        super().__init__(app)
        self._token = token
        # Match /mcp and nested MCP routes regardless of trailing slash in config.
        self._mcp_path = mcp_path.rstrip("/") or "/mcp"

    async def dispatch(self, request: Request, call_next) -> Response:
        if not request.url.path.startswith(self._mcp_path):
            return await call_next(request)

        auth_header = request.headers.get("authorization", "")
        expected = f"Bearer {self._token}"
        if hmac.compare_digest(auth_header, expected):
            return await call_next(request)

        # Optional fallback header for local tooling that cannot set Authorization.
        x_token = request.headers.get("x-mcp-token", "")
        if x_token and hmac.compare_digest(x_token, self._token):
            return await call_next(request)

        # Compatibility fallback for MCP clients that cannot set custom headers.
        # Example endpoint: /mcp?mcp_token=<token>
        qp_token = request.query_params.get("mcp_token", "")
        if qp_token and hmac.compare_digest(qp_token, self._token):
            return await call_next(request)

        return JSONResponse(
            status_code=401,
            content={"error": True, "message": "Unauthorized MCP request."},
        )
