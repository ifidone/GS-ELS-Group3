# MCP Server

## Purpose
This MCP server is the tool layer for the Mutual Fund Calculator chatbot.
It is implemented in Python (FastMCP) and runs as a separate service from Spring Boot.

Core responsibilities:
- expose structured tools for the chatbot
- call Spring Boot REST APIs (source of truth)
- normalize responses for LLM usage

Non-goals:
- do not duplicate backend business logic
- do not become the primary source of truth

## Architecture
Frontend (Angular) -> Spring Boot Backend
Chat UI -> /api/chat (Spring Boot) -> MCP client -> FastMCP server -> Spring Boot REST APIs

Key rule:
- Spring Boot = business logic
- MCP server = tool layer

## Core Principles
1. Backend owns logic: do not recompute CAPM, projections, or time series.
2. Structured responses: return clean JSON.
3. Lightweight lists: list endpoints do not include time series.
4. User scoping: user tools require `uid`.
5. Recommendations: use category-based heuristics unless backend provides beta.

## Environment
The MCP server reads configuration from `.env`.

Required:
- `MCP_HOST` (default `0.0.0.0`)
- `MCP_PORT` (default `8000` locally so it matches Spring’s `MCP_SERVER_ORIGIN`; Docker image sets `8080`)
- `MCP_PATH` (default `/mcp`)
- `BACKEND_BASE_URL` (default `http://localhost:8080`)
- `BACKEND_TIMEOUT_SECONDS` (default `10`)
- `LOG_LEVEL` (default `INFO`)

Optional DB (diagnostics only):
- `DATABASE_URL`
- or `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`

Example `.env`:
```bash
MCP_HOST=127.0.0.1
MCP_PORT=8000
MCP_PATH=/mcp
LOG_LEVEL=INFO
BACKEND_BASE_URL=http://localhost:8080
BACKEND_TIMEOUT_SECONDS=10
```

## Project Structure
```
mcp_server/
  app/
    clients/   # Spring Boot HTTP client
    services/  # backend-facing service logic
    tools/     # MCP tool definitions
    routes/    # HTTP routes (health)
    db/        # optional DB helpers (diagnostics only)
  server.py    # FastMCP entrypoint (tool registration)
  config/
```

Notes:
- `app/` is the active code path for tools/routes.
- Top-level `tools/`, `routes/`, `db/` were removed and are no longer used.

## Tools
Health:
- `health_check(uid?: str) -> {status, backend}`

Chat:
- `chat_echo(message: str, uid?: str) -> {message}`

Saved calculations (uid required):
- `saved_calculations_list(uid: str, search?: str, limit?: int) -> {items, count}`
- `calculations_list(uid: str, limit?: int) -> {items, count}`
- `saved_calculation_detail(uid: str, calculation_id: int) -> {.., timeSeries}`
- `saved_calculation_time_series(uid: str, calculation_id: int) -> {calculationId, series}`
- `saved_calculations_compare(uid: str, calculation_ids: list[int]) -> {comparisons}`
- `saved_calculation_create(uid: str, ticker: str, initial_investment: float, years: float, beta: float, expected_return: float, future_value: float, name?: str)`
- `saved_calculation_update(uid: str, calculation_id: int, name?: str, ticker?: str, initial_investment?: float, years?: float)`
- `saved_calculation_delete(uid: str, calculation_id: int)`
- `saved_calculation_update_with_auth(id_token: str, calculation_id: int, name: str, ticker: str, initial_investment: float, years: float, beta: float, expected_return: float, future_value: float, uid?: str)`
- `saved_calculation_delete_with_auth(id_token: str, calculation_id: int, uid?: str)`

Projection:
- `project_calculation(ticker: str, initial_investment: float, years: float, uid?: str)`
- `monte_carlo_simulation(ticker: str, principal: float, time_years: float, goal_amount?: float, n_simulations?: int, uid?: str)`

Portfolios (uid required):
- `portfolio_list(uid: str, page?: int, size?: int, sort?: str) -> {items, count}`
- `portfolio_detail(uid: str, name: str)`
- `portfolio_items(uid: str, name: str) -> {items, count}`
- `portfolio_create(uid: str, name: str, description?: str)`
- `portfolio_update(uid: str, name: str, new_name?: str, description?: str)`
- `portfolio_delete(uid: str, name: str)`
- `portfolio_add_item(uid: str, name: str, calculation_id: int)`
- `portfolio_remove_item(uid: str, name: str, calculation_id: int)`
- `portfolio_available_calculations(uid: str, name: str)`
- `portfolio_total_investment(uid: str)`

Funds:
- `funds_list(uid?: str) -> {items, count}`
- `fund_detail(ticker: str, uid?: str)`

Recommendations:
- `funds_suggest_by_goal(goal: str, years?: float, risk?: str, current_ticker?: str, uid?: str)`
- `compare_funds(tickers: list[str], uid?: str)`
- `suggest_alternative_funds(goal: str, current_ticker?: str, uid?: str)`
- `funds_projection_recommendations(principal: float, years: float, risk?: str, top_n?: int, goal_amount?: float, uid?: str)`

Auth:
- `auth_sync(id_token: str, uid?: str)`

## Response Normalization
- List tools return summary data only (no time series).
- Time series maps from the backend are normalized to:
  `[{ "year": 0, "value": 10000 }, ...]`

## Example Tool Flow
1. `saved_calculations_list(uid)` calls Spring Boot `GET /api/saved-calculations`
2. MCP server returns:
```json
{
  "items": [
    {
      "id": "42",
      "name": "Retirement Plan",
      "ticker": "VFIAX",
      "initialInvestment": 10000,
      "years": 20,
      "beta": 1.08,
      "expectedReturn": 0.082,
      "futureValue": 48210.22
    }
  ],
  "count": 1
}
```

## Chatbot Modes
- Learn: uses `fund_detail`, `compare_funds`, and model explanations.
- Portfolio Ideas: uses `funds_suggest_by_goal` and `suggest_alternative_funds`.
- Take Action: uses calculation and portfolio tools (`project_calculation`, `saved_*`, `portfolio_*`).

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

## Postman Testing
Health (HTTP):
```
GET http://localhost:8000/health
```

MCP tool call format:
```
POST http://localhost:8000/mcp
Content-Type: application/json
{"tool":"health_check","args":{}}
```

Sample tool calls:
```json
{"tool":"funds_list","args":{}}
```

```json
{"tool":"saved_calculations_list","args":{"uid":"YOUR_UID","limit":5}}
```

```json
{"tool":"project_calculation","args":{"ticker":"VFIAX","initial_investment":10000,"years":20}}
```

## Deployment
- Cloud Run: set `MCP_HOST=0.0.0.0`, `MCP_PORT=8080`, `MCP_PATH=/mcp`
- Configure backend connectivity via `BACKEND_BASE_URL`
- Optional DB access via `DATABASE_URL` or `DB_URL/DB_USERNAME/DB_PASSWORD`

## Run with Docker
```bash
cd mcp_server
docker build -t mcp-server .
docker run --rm -p 8080:8080 mcp-server
```
