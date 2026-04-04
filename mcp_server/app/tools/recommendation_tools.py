from __future__ import annotations

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.funds_service import FundsService
from app.services.recommendation_service import RecommendationService
from app.services.calculations_service import CalculationsService
from app.tools.errors import error_response


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    funds_service = FundsService(backend)
    recommendation_service = RecommendationService(funds_service)
    calculations_service = CalculationsService(backend)

    @mcp.tool()
    def compare_funds(tickers: list[str], uid: str = "") -> dict:
        if not tickers:
            return error_response("MISSING_INPUT", "tickers are required", ["tickers"])
        return recommendation_service.compare_funds([t.strip().upper() for t in tickers if t])

    @mcp.tool()
    def funds_suggest_by_goal(goal: str,
                              years: float = 0,
                              risk: str = "",
                              current_ticker: str = "",
                              uid: str = "") -> dict:
        if not goal or not goal.strip():
            return error_response("MISSING_INPUT", "goal is required", ["goal"])
        return recommendation_service.suggest_by_goal(
            goal=goal.strip(),
            years=years if years and years > 0 else None,
            risk=risk.strip() if risk else None,
            current_ticker=current_ticker.strip() if current_ticker else None
        )

    @mcp.tool()
    def suggest_alternative_funds(goal: str,
                                  current_ticker: str = "",
                                  uid: str = "") -> dict:
        if not goal or not goal.strip():
            return error_response("MISSING_INPUT", "goal is required", ["goal"])
        return recommendation_service.suggest_alternatives(goal=goal.strip(),
                                                           current_ticker=current_ticker.strip() if current_ticker else None,
                                                           uid=uid.strip() if uid else None)

    @mcp.tool()
    def funds_projection_recommendations(principal: float,
                                         years: float,
                                         risk: str = "aggressive",
                                         top_n: int = 4,
                                         goal_amount: float = 0,
                                         uid: str = "") -> dict:
        missing = [name for name, value in {
            "principal": principal,
            "years": years
        }.items() if value in (None, "", 0)]
        if missing:
            return error_response("MISSING_INPUT", "principal and years are required", missing)
        try:
            principal_value = float(principal)
            years_value = float(years)
        except (TypeError, ValueError):
            return error_response("INVALID_INPUT", "principal and years must be numbers", ["principal", "years"])
        if principal_value <= 0 or years_value <= 0:
            return error_response("INVALID_INPUT", "principal and years must be greater than zero", ["principal", "years"])

        risk_value = (risk or "").strip().lower()
        top_value = int(top_n) if isinstance(top_n, int) and top_n > 0 else 4
        goal_value = float(goal_amount) if goal_amount and goal_amount > 0 else None

        funds = funds_service.list()
        if funds.get("error"):
            return funds

        scored: list[dict] = []
        for fund in funds.get("items", []):
            ticker = fund.get("ticker")
            if not ticker:
                continue
            projection = calculations_service.project(ticker, principal_value, years_value)
            if projection.get("error"):
                continue
            scored.append({
                "ticker": ticker,
                "name": fund.get("name"),
                "category": fund.get("category"),
                "beta": projection.get("beta"),
                "expectedReturn": projection.get("expectedReturn"),
                "futureValue": projection.get("futureValue")
            })

        scored = _rank_by_risk(scored, risk_value)
        top_candidates = scored[:top_value]

        results: list[dict] = []
        for candidate in top_candidates:
            monte = calculations_service.monte_carlo(
                candidate["ticker"],
                principal_value,
                years_value,
                goal_value,
                None
            )
            if monte.get("error"):
                monte_summary = {"error": True, "message": monte.get("message")}
            else:
                monte_summary = {
                    "percentile10": monte.get("percentile10"),
                    "percentile50": monte.get("percentile50"),
                    "percentile90": monte.get("percentile90"),
                    "probabilityOfGoal": monte.get("probabilityOfGoal"),
                    "deterministicFV": monte.get("deterministicFV"),
                    "sharpeRatio": monte.get("sharpeRatio"),
                    "riskAdjustedReturn": monte.get("riskAdjustedReturn"),
                    "valueAtRisk": monte.get("valueAtRisk")
                }
            results.append({
                **candidate,
                "monteCarlo": monte_summary
            })

        return {
            "principal": principal_value,
            "years": years_value,
            "risk": risk_value or "aggressive",
            "candidateCount": len(scored),
            "items": results
        }


def _rank_by_risk(items: list[dict], risk: str) -> list[dict]:
    def safe_float(value: object) -> float:
        return float(value) if isinstance(value, (int, float)) else 0.0

    if risk in {"low", "lower_risk", "conservative"}:
        return sorted(items, key=lambda row: (safe_float(row.get("expectedReturn")),
                                             safe_float(row.get("beta"))))
    if risk in {"balanced", "moderate", "medium"}:
        return sorted(items, key=lambda row: (abs(safe_float(row.get("beta")) - 1.0),
                                             -safe_float(row.get("expectedReturn"))))
    return sorted(items, key=lambda row: (-safe_float(row.get("expectedReturn")),
                                         -safe_float(row.get("beta"))))
