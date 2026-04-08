from starlette.applications import Starlette
from starlette.responses import JSONResponse
from starlette.routing import Route
from starlette.testclient import TestClient

from app.security import McpTokenAuthMiddleware


async def mcp_handler(_request):
    return JSONResponse({"ok": True})


async def health_handler(_request):
    return JSONResponse({"status": "ok"})


def build_client() -> TestClient:
    app = Starlette(routes=[
        Route("/mcp", mcp_handler, methods=["POST"]),
        Route("/health", health_handler, methods=["GET"]),
    ])
    app.add_middleware(McpTokenAuthMiddleware, token="test-token", mcp_path="/mcp")
    return TestClient(app)


def test_mcp_request_requires_auth_token():
    client = build_client()
    response = client.post("/mcp", json={"tool": "health_check", "args": {}})
    assert response.status_code == 401


def test_mcp_request_accepts_bearer_auth():
    client = build_client()
    response = client.post(
        "/mcp",
        json={"tool": "health_check", "args": {}},
        headers={"Authorization": "Bearer test-token"},
    )
    assert response.status_code == 200


def test_mcp_request_accepts_x_mcp_token():
    client = build_client()
    response = client.post(
        "/mcp",
        json={"tool": "health_check", "args": {}},
        headers={"X-MCP-Token": "test-token"},
    )
    assert response.status_code == 200


def test_non_mcp_route_bypasses_middleware():
    client = build_client()
    response = client.get("/health")
    assert response.status_code == 200


def test_mcp_request_accepts_query_token_fallback():
    client = build_client()
    response = client.post("/mcp?mcp_token=test-token", json={"tool": "health_check", "args": {}})
    assert response.status_code == 200
