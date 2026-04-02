from decimal import Decimal
from typing import Any

import psycopg2
from psycopg2.extras import RealDictCursor

from config.settings import get_db_connection_params


def get_connection():
    params = get_db_connection_params()
    if "dsn" in params:
        return psycopg2.connect(params["dsn"])
    return psycopg2.connect(**params)


def get_dict_cursor(connection):
    return connection.cursor(cursor_factory=RealDictCursor)


def normalize_row(row: dict[str, Any]) -> dict[str, Any]:
    normalized = {}
    for key, value in row.items():
        if isinstance(value, Decimal):
            normalized[key] = float(value)
        else:
            normalized[key] = value
    return normalized
