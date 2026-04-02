from __future__ import annotations

from typing import Any


def normalize_time_series_map(series_map: dict[str, Any] | None) -> list[dict[str, float]]:
    if not series_map:
        return []

    items: list[dict[str, float]] = []
    for key, value in series_map.items():
        try:
            year = int(key)
            numeric_value = float(value)
        except (ValueError, TypeError):
            continue
        items.append({"year": year, "value": numeric_value})
    items.sort(key=lambda item: item["year"])
    return items
