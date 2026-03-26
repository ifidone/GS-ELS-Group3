from fastmcp import FastMCP
from starlette.requests import Request
from starlette.responses import PlainTextResponse


def register(mcp: FastMCP) -> None:
    @mcp.custom_route("/health", methods=["GET"])
    async def health_check(request: Request) -> PlainTextResponse:
        """Simple HTTP health check."""
        return PlainTextResponse("OK")
