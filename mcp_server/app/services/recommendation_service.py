from __future__ import annotations

from typing import Any

from app.services.funds_service import FundsService


class RecommendationService:
    def __init__(self, funds_service: FundsService) -> None:
        self._funds_service = funds_service

    def compare_funds(self, tickers: list[str]) -> dict[str, Any]:
        items = []
        for ticker in tickers:
            detail = self._funds_service.detail(ticker)
            if detail.get("error"):
                continue
            items.append(detail)
        return {"items": items, "count": len(items)}

    def suggest_by_goal(self,
                        goal: str,
                        years: float | None = None,
                        risk: str | None = None,
                        current_ticker: str | None = None) -> dict[str, Any]:
        funds = self._funds_service.list()
        if funds.get("error"):
            return funds

        candidates = _filter_by_goal(funds.get("items", []), goal, current_ticker)
        return {
            "goal": goal,
            "candidates": candidates
        }

    def suggest_alternatives(self,
                             goal: str,
                             current_ticker: str | None = None,
                             uid: str | None = None) -> dict[str, Any]:
        return self.suggest_by_goal(goal=goal, current_ticker=current_ticker)


def _filter_by_goal(items: list[dict[str, Any]], goal: str, current_ticker: str | None) -> list[dict[str, Any]]:
    normalized_goal = (goal or "").lower()
    result: list[dict[str, Any]] = []

    for fund in items:
        ticker = fund.get("ticker")
        if current_ticker and ticker and ticker.upper() == current_ticker.upper():
            continue
        category = (fund.get("category") or "").lower()

        if normalized_goal in {"aggressive_growth", "more_profit"}:
            if "growth" in category or "small" in category or "mid" in category:
                result.append(_candidate(fund, "growth-oriented category", "higher volatility expected"))
        elif normalized_goal == "lower_risk":
            if "bond" in category:
                result.append(_candidate(fund, "bond exposure", "typically lower volatility"))
        elif normalized_goal == "diversification":
            if "international" in category or "bond" in category or "blend" in category:
                result.append(_candidate(fund, "diversification benefit", "mix across categories"))

    if not result:
        for fund in items[:5]:
            result.append(_candidate(fund, "broad market exposure", "review fit with your risk level"))

    return result[:5]


def _candidate(fund: dict[str, Any], reason: str, risk_note: str) -> dict[str, Any]:
    return {
        "ticker": fund.get("ticker"),
        "reason": reason,
        "riskNote": risk_note
    }
