from fastmcp import FastMCP


def register(mcp: FastMCP) -> None:
    @mcp.tool()
    def chat_echo(message: str) -> str:
        """Echo placeholder for chatbot integration."""
        return f"Echo: {message}"
