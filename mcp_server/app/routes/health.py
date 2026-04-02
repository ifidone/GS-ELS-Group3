import httpx
from fastmcp import FastMCP
from starlette.requests import Request
from starlette.responses import JSONResponse

from config.settings import get_backend_base_url, get_backend_timeout_seconds


def register(mcp: FastMCP) -> None:
    @mcp.custom_route("/health", methods=["GET"])
    async def health_check(request: Request) -> JSONResponse:
        """HTTP health check with backend reachability."""
        base_url = get_backend_base_url()
        timeout = get_backend_timeout_seconds()
        backend_status = "unreachable"
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                response = await client.get(f"{base_url}/api/funds")
                backend_status = "reachable" if response.status_code < 500 else "error"
        except httpx.HTTPError:
            backend_status = "unreachable"

        return JSONResponse({"status": "ok", "backend": backend_status})
