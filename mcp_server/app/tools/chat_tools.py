from __future__ import annotations

from fastmcp import FastMCP


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def chat_echo(message: str, uid: str = "") -> dict:
        if message is None:
            message = ""
        return {"message": message}
