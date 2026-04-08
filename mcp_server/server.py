from fastmcp import FastMCP
from starlette.middleware import Middleware
import uvicorn

from app.security import McpTokenAuthMiddleware
from config.settings import (
    get_host_port,
    get_log_level,
    get_mcp_auth_token,
    get_mcp_path,
    validate_runtime_security,
)
from app.routes.health import register as register_health
from app.tools.chat_tools import register as register_chat
from app.tools.auth_tools import register as register_auth
from app.tools.calculation_tools import register as register_calculations
from app.tools.fund_tools import register as register_funds
from app.tools.health_tools import register as register_health_tools
from app.tools.portfolio_tools import register as register_portfolios
from app.tools.recommendation_tools import register as register_recommendations
from app.tools.ai_portfolio_tools import register as register_ai_portfolio

mcp = FastMCP("mcp-server")

register_health(mcp)
register_chat(mcp)
register_auth(mcp)
register_health_tools(mcp)
register_calculations(mcp)
register_funds(mcp)
register_portfolios(mcp)
register_recommendations(mcp)
register_ai_portfolio(mcp)


def create_app():
    path = get_mcp_path()
    token = get_mcp_auth_token()
    host, _ = get_host_port()
    # Block accidental internet exposure without auth.
    validate_runtime_security(host, token)
    middleware = []
    if token:
        # Protect only MCP tool calls; keep /health accessible for probes.
        middleware.append(Middleware(McpTokenAuthMiddleware, token=token, mcp_path=path))

    return mcp.http_app(path=path, transport="http", middleware=middleware or None)


if __name__ == "__main__":
    host, port = get_host_port()
    token = get_mcp_auth_token()
    validate_runtime_security(host, token)
    log_level = get_log_level().lower()
    # HTTP runtime; TLS is expected at ingress/load balancer in hosted environments.
    uvicorn.run(create_app(), host=host, port=port, log_level=log_level)
