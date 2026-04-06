from __future__ import annotations

import math
from typing import Any

from fastmcp import FastMCP

from app.clients.backend_client import BackendClient
from app.services.calculations_service import CalculationsService
from app.services.funds_service import FundsService
from app.tools.errors import error_response


RISK_FREE_RATE = 0.04
MIN_FUNDS = 4
MAX_FUNDS_LOW = 6
MAX_FUNDS_MED = 7
MAX_FUNDS_HIGH = 8


def register(mcp: FastMCP) -> None:
    backend = BackendClient()
    funds_service = FundsService(backend)
    calculations_service = CalculationsService(backend)

    @mcp.tool()
    def ai_portfolio_generator(
        investmentAmount: float,
        investmentYears: float,
        riskTolerance: str = "medium",
        uid: str = "",
    ) -> dict[str, Any]:
        try:
            principal = float(investmentAmount)
            years = float(investmentYears)
        except (TypeError, ValueError):
            return error_response("INVALID_INPUT", "investmentAmount and investmentYears must be numbers.")

        if principal <= 0 or years <= 0:
            return error_response("INVALID_INPUT", "investmentAmount and investmentYears must be greater than zero.")

        risk_key, risk_label = _normalize_risk(riskTolerance)

        funds = funds_service.list()
        if funds.get("error"):
            return funds

        enriched = []
        for fund in funds.get("items", []):
            ticker = (fund.get("ticker") or "").strip().upper()
            if not ticker:
                continue
            projection = calculations_service.project(ticker, principal, years)
            if projection.get("error"):
                continue
            enriched.append({
                "ticker": ticker,
                "name": fund.get("name"),
                "category": fund.get("category"),
                "beta": projection.get("beta"),
                "expectedReturn": projection.get("expectedReturn"),
                "score": _category_risk_score(fund.get("category"))
            })

        if len(enriched) < MIN_FUNDS:
            return error_response("NOT_ENOUGH_FUNDS", "Not enough funds to build a portfolio.")

        selected = _select_funds(enriched, risk_key)
        if len(selected) < MIN_FUNDS:
            return error_response("NOT_ENOUGH_FUNDS", "Could not select enough funds for this risk profile.")

        for item in selected:
            monte = calculations_service.monte_carlo(item["ticker"], principal, years)
            if monte.get("error"):
                item["riskAdjustedReturn"] = None
                item["sharpeRatio"] = None
                item["valueAtRisk"] = None
            else:
                item["riskAdjustedReturn"] = _safe_float(monte.get("riskAdjustedReturn"))
                item["sharpeRatio"] = _safe_float(monte.get("sharpeRatio"))
                item["valueAtRisk"] = _safe_float(monte.get("valueAtRisk"))

        weights = _assign_weights(selected, risk_key)
        for idx, item in enumerate(selected):
            rar = item.get("riskAdjustedReturn")
            if rar is not None:
                adjustment = 1 + _clamp(rar, -0.2, 0.2)
                weights[idx] *= adjustment

        _normalize_weights(weights)

        weighted_er = 0.0
        blended_capm = 0.0
        allocations = []
        for item, weight in zip(selected, weights):
            expected_return = _safe_float(item.get("expectedReturn"))
            beta = _safe_float(item.get("beta"))
            weighted_er += weight * expected_return
            blended_capm += weight * _capm_rate(beta, expected_return)
            allocations.append({
                "ticker": item.get("ticker"),
                "name": item.get("name"),
                "category": item.get("category"),
                "weightPercent": _round2(weight * 100.0),
                "beta": _round2(beta),
                "expectedReturn": _round2(expected_return)
            })

        deterministic_fv = principal * math.exp(blended_capm * years)

        explanation = _build_explanation(
            risk_key=risk_key,
            years=years,
            allocations=allocations,
            blended_capm=blended_capm,
            weighted_er=weighted_er
        )

        return {
            "riskLabel": risk_label,
            "allocations": allocations,
            "portfolioWeightedExpectedReturn": _round2(weighted_er),
            "portfolioBlendedAnnualRate": _round2(blended_capm),
            "deterministicFutureValue": _round2(deterministic_fv),
            "explanation": explanation
        }


def _normalize_risk(risk: str | None) -> tuple[str, str]:
    value = (risk or "").strip().lower()
    if value in {"low", "conservative", "lower_risk"}:
        return "low", "Low"
    if value in {"high", "aggressive"}:
        return "high", "High"
    return "medium", "Medium"


def _select_funds(items: list[dict[str, Any]], risk_key: str) -> list[dict[str, Any]]:
    sorted_items = sorted(items, key=lambda row: (row.get("score", 2), row.get("ticker", "")))
    if risk_key == "low":
        return _pick_low_risk(sorted_items)
    if risk_key == "high":
        return _pick_high_risk(sorted_items)
    return _pick_medium_risk(sorted_items)


def _pick_low_risk(sorted_items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    pool = [row for row in sorted_items if row.get("score", 2) <= 2]
    if len(pool) < MIN_FUNDS:
        pool = list(sorted_items)
    return pool[:MAX_FUNDS_LOW]


def _pick_medium_risk(sorted_items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    n = len(sorted_items)
    start = max(0, n // 2 - 2)
    end = min(n, start + MAX_FUNDS_MED)
    slice_items = list(sorted_items[start:end])
    if len(slice_items) < MIN_FUNDS:
        slice_items = list(sorted_items[:MAX_FUNDS_MED])
    return slice_items


def _pick_high_risk(sorted_items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    pool = [row for row in sorted_items if row.get("score", 2) >= 2]
    if len(pool) < MIN_FUNDS:
        pool = list(sorted_items)
    pool.sort(key=lambda row: (row.get("score", 2), row.get("ticker", "")), reverse=True)
    return pool[:MAX_FUNDS_HIGH]


def _assign_weights(items: list[dict[str, Any]], risk_key: str) -> list[float]:
    weights = []
    for item in items:
        score = _safe_float(item.get("score"), fallback=2.0)
        if risk_key == "low":
            weight = 5.0 - score + 0.05
        elif risk_key == "high":
            weight = score + 0.05
        else:
            weight = 1.0
        weights.append(max(weight, 0.01))
    return weights


def _normalize_weights(weights: list[float]) -> None:
    total = sum(weights)
    if total <= 0:
        equal = 1.0 / len(weights)
        for idx in range(len(weights)):
            weights[idx] = equal
        return
    for idx in range(len(weights)):
        weights[idx] = weights[idx] / total


def _category_risk_score(category: str | None) -> int:
    if not category:
        return 2
    text = category.lower()
    if "bond" in text:
        return 0
    if "large" in text and "blend" in text:
        return 1
    if "large" in text and "growth" in text:
        return 2
    if "international" in text or "global" in text or "emerging" in text:
        return 3
    if "mid" in text:
        return 3
    if "small" in text:
        return 4
    return 2


def _capm_rate(beta: float, expected_return: float) -> float:
    raw = RISK_FREE_RATE + beta * (expected_return - RISK_FREE_RATE)
    return max(RISK_FREE_RATE, raw)


def _build_explanation(
    risk_key: str,
    years: float,
    allocations: list[dict[str, Any]],
    blended_capm: float,
    weighted_er: float,
) -> str:
    names = ", ".join([item.get("ticker", "") for item in allocations[:3]])
    if len(allocations) > 3:
        names += ", …"

    if years >= 10:
        horizon = ("A longer horizon like yours can usually absorb more short-term swings, "
                   "so we leaned into diversified equity sleeves while still matching your stated risk.")
    elif years >= 5:
        horizon = ("For a medium-term horizon we balanced growth potential with diversification "
                   "across market caps and regions.")
    else:
        horizon = ("For a shorter horizon we kept the mix relatively defensive while still using only "
                   "funds available in this platform.")

    if risk_key == "low":
        risk_text = ("Low risk prioritizes steadier categories (for example bonds and broad large-cap "
                     "index funds) when those names exist in the fund list.")
    elif risk_key == "high":
        risk_text = ("Higher risk tilts toward growth-oriented and smaller-cap funds where available, "
                     "which historically carry more volatility but more upside potential.")
    else:
        risk_text = ("Medium risk spreads exposure across large, mid, and international names so "
                     "growth and stability are both represented.")

    return (
        f"{risk_text} {horizon} "
        "We used only funds returned by the live fund catalog and each fund's historical return and beta "
        "from the same data sources as the calculator, with Monte Carlo simulations used to sanity-check "
        "risk-adjusted positioning. "
        f"The blended annual rate used for the headline projection is about {blended_capm * 100.0:.2f}% "
        f"(weighted CAPM-style mix); the simple weighted average of raw historical returns is about "
        f"{weighted_er * 100.0:.2f}%. "
        "This is educational projection—not personal advice. "
        f"Holdings preview: {names}."
    )


def _round2(value: float) -> float:
    return round(value, 2)


def _safe_float(value: Any, fallback: float = 0.0) -> float:
    if isinstance(value, (int, float)) and math.isfinite(value):
        return float(value)
    return fallback


def _clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))
