# MCP Server

## Features
- HTTP/SSE MCP server using FastMCP.
- Plain HTTP health check at `/health`.
- Read-only access to saved calculations in PostgreSQL.
- `chat_echo` tool that echoes input for integration testing.
- `saved_calculations_list` tool that checks the `users` table before returning data.

## Requirements
- Python 3.11+
- PostgreSQL connection details in `.env`

## Environment
Ensure `.env` includes database settings plus `MCP_HOST`, `MCP_PORT`, and optional `MCP_PATH`.
Local dev uses `MCP_PORT=8000` in `.env`.

## Run Locally
```bash
cd mcp_server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python3 server.py
```

## Health Check
```bash
curl -s http://localhost:8000/health
```

## MCP Tools
- `chat_echo(message: str) -> str`
- `saved_calculations_list(uid: str) -> list[dict]`

## Deployment
- Deploy to Cloud Run with `MCP_HOST=0.0.0.0`, `MCP_PORT=8080`, and `MCP_PATH=/mcp`.
- Configure database access via `DATABASE_URL` or `DB_URL/DB_USERNAME/DB_PASSWORD`.

## Run with Docker
```bash
cd mcp_server
docker build -t mcp-server .
docker run --rm -p 8080:8080 mcp-server
```
