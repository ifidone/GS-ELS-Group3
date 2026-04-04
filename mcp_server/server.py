from fastmcp import FastMCP
from config.settings import get_host_port, get_mcp_path
from app.routes.health import register as register_health
from app.tools.chat_tools import register as register_chat
from app.tools.auth_tools import register as register_auth
from app.tools.calculation_tools import register as register_calculations
from app.tools.fund_tools import register as register_funds
from app.tools.health_tools import register as register_health_tools
from app.tools.portfolio_tools import register as register_portfolios
from app.tools.recommendation_tools import register as register_recommendations

mcp = FastMCP("mcp-server")

register_health(mcp)
register_chat(mcp)
register_auth(mcp)
register_health_tools(mcp)
register_calculations(mcp)
register_funds(mcp)
register_portfolios(mcp)
register_recommendations(mcp)


if __name__ == "__main__":
    host, port = get_host_port()
    path = get_mcp_path()
    mcp.run(transport="http", host=host, port=port, path=path)
