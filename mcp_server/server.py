from fastmcp import FastMCP
from config.settings import get_host_port, get_mcp_path
from routes.health import register as register_health
from tools.chat_echo import register as register_chat
from tools.saved_calculations_list import register as register_saved_calculations

mcp = FastMCP("mcp-server")

register_health(mcp)
register_chat(mcp)
register_saved_calculations(mcp)


if __name__ == "__main__":
    host, port = get_host_port()
    path = get_mcp_path()
    mcp.run(transport="http", host=host, port=port, path=path)
