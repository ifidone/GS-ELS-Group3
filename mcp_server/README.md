# MCP Server

## Purpose
This service is the MCP tool layer for the chatbot. It runs in Python (FastMCP) and calls the Spring Boot backend APIs.

Design contract:
- Spring Boot backend is the source of truth for business logic.
- MCP server exposes tool-friendly interfaces and response normalization.
- MCP server can be run locally over HTTP and deployed online over HTTP behind a platform TLS edge.

## Security Standard
Baseline controls implemented:
- Local-safe default bind: `MCP_HOST=127.0.0.1`.
- Public bind guard: if `MCP_HOST` is public (`0.0.0.0` or `::`), `MCP_AUTH_TOKEN` is required unless `ALLOW_INSECURE_PUBLIC_BIND=true`.
- Optional token auth on `/mcp`:
  - `Authorization: Bearer <MCP_AUTH_TOKEN>`
  - or `X-MCP-Token: <MCP_AUTH_TOKEN>`
  - or query fallback `/mcp?mcp_token=<MCP_AUTH_TOKEN>` for clients without custom header support
- Startup config validation for host/port/path, backend URL scheme, timeout, and log level.
- Outbound backend HTTP client uses `trust_env=False` to avoid untrusted proxy env behavior.

## Environment Configuration
Create `mcp_server/.env` (start from `.env.example`).

Required runtime:
- `MCP_HOST` (local default `127.0.0.1`)
- `MCP_PORT` (default `8000`, falls back to `PORT` when `MCP_PORT` is absent)
- `MCP_PATH` (default `/mcp`)
- `LOG_LEVEL` (`CRITICAL|ERROR|WARNING|INFO|DEBUG`)
- `BACKEND_BASE_URL` (`http://...` or `https://...`)
- `BACKEND_TIMEOUT_SECONDS` (> 0)

Security:
- `MCP_AUTH_TOKEN` (recommended; required for public bind unless explicitly overridden)
- `ALLOW_INSECURE_PUBLIC_BIND` (`false` by default; set to `true` only for temporary non-prod use)

Database credentials in `.env`:
- `DB_URL=jdbc:postgresql://host:5432/dbname`
- `DB_USERNAME=...`
- `DB_PASSWORD=...`
- Optional alternative: `DATABASE_URL=postgresql://user:password@host:5432/dbname`

## Local Run (HTTP)
```bash
cd mcp_server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python3 server.py
```

Local endpoints:
- Health: `http://127.0.0.1:8000/health`
- MCP: `http://127.0.0.1:8000/mcp`

Example MCP request (without token, if `MCP_AUTH_TOKEN` is empty):
```bash
curl -X POST http://127.0.0.1:8000/mcp \
  -H "Content-Type: application/json" \
  -d '{"tool":"health_check","args":{}}'
```

If token is enabled:
```bash
curl -X POST http://127.0.0.1:8000/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <MCP_AUTH_TOKEN>" \
  -d '{"tool":"health_check","args":{}}'
```

Query-token fallback:
```bash
curl -X POST "http://127.0.0.1:8000/mcp?mcp_token=<MCP_AUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"tool":"health_check","args":{}}'
```

## Online Deployment (HTTP Service Behind TLS Edge)
This server runs HTTP internally. For internet access, place it behind a managed TLS edge (Cloud Run, ingress, reverse proxy, load balancer).

Recommended production env:
```bash
MCP_HOST=0.0.0.0
MCP_PORT=8080
MCP_PATH=/mcp
MCP_AUTH_TOKEN=<strong-random-secret>
ALLOW_INSECURE_PUBLIC_BIND=false
BACKEND_BASE_URL=http://<private-backend-address>
BACKEND_TIMEOUT_SECONDS=10
LOG_LEVEL=INFO
```

Deployment checklist:
1. Keep backend connectivity private (VPC/private network where possible).
2. Keep only required ingress open.
3. Store `MCP_AUTH_TOKEN` and DB credentials in a secret manager, not in image layers.
4. Rotate secrets periodically.
5. Keep `ALLOW_INSECURE_PUBLIC_BIND=false`.
6. Use query-token fallback only when header-based auth is unavailable.

## Docker Run
```bash
cd mcp_server
docker build -t mcp-server .
docker run --rm -p 8080:8080 --env-file .env mcp-server
```

## Unit Tests
Test dependencies are in `requirements-dev.txt`.

Install and run:
```bash
cd mcp_server
source .venv/bin/activate
pip install -r requirements-dev.txt
pytest -q
```

What is tested:
- `tests/test_settings.py`
  - JDBC parsing
  - DB env parameter loading
  - host/port/path validation
  - backend URL validation
  - public-bind security enforcement
- `tests/test_security.py`
  - MCP token middleware authorization behavior
  - non-MCP route bypass behavior
- `tests/test_backend_client.py`
  - URL normalization
  - JSON parsing fallback behavior
  - network error handling path (`503`)

## Notes for Backend Integration
- Backend MCP client uses `MCP_SERVER_ORIGIN` and `MCP_SERVER_ENDPOINT`.
- If MCP token auth is enabled and client headers are unavailable, set:
  - `MCP_SERVER_ENDPOINT=/mcp?mcp_token=<MCP_AUTH_TOKEN>`
- Keep transport as HTTP between private services; use TLS at the platform edge for public traffic.
