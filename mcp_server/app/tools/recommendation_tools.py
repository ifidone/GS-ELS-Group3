from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.funds_service import FundsService
from app.services.recommendation_service import RecommendationService
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    funds_service = FundsService(backend)
    recommendation_service = RecommendationService(funds_service)

    @mcp.tool()
    def compare_funds(tickers: list[str]) -> dict:
        if not tickers:
            return error_response("MISSING_INPUT", "tickers are required", ["tickers"])
        return recommendation_service.compare_funds([t.strip().upper() for t in tickers if t])

    @mcp.tool()
    def funds_suggest_by_goal(goal: str,
                              years: float | None = None,
                              risk: str | None = None,
                              current_ticker: str | None = None) -> dict:
        if not goal or not goal.strip():
            return error_response("MISSING_INPUT", "goal is required", ["goal"])
        return recommendation_service.suggest_by_goal(
            goal=goal.strip(),
            years=years,
            risk=risk,
            current_ticker=current_ticker
        )

    @mcp.tool()
    def suggest_alternative_funds(goal: str,
                                  current_ticker: str | None = None,
                                  uid: str | None = None) -> dict:
        if not goal or not goal.strip():
            return error_response("MISSING_INPUT", "goal is required", ["goal"])
        return recommendation_service.suggest_alternatives(goal=goal.strip(),
                                                           current_ticker=current_ticker,
                                                           uid=uid)
