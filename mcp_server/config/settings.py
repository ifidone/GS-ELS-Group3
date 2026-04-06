import os
from typing import Any
from urllib.parse import urlparse

from dotenv import load_dotenv

load_dotenv()
load_dotenv(".env.example")


def parse_jdbc_url(jdbc_url: str) -> dict[str, Any]:
    if not jdbc_url.startswith("jdbc:"):
        raise ValueError("DB_URL must start with jdbc:")
    url = jdbc_url.replace("jdbc:", "", 1)
    parsed = urlparse(url)
    if parsed.scheme != "postgresql":
        raise ValueError("Only postgresql JDBC URLs are supported")
    if not parsed.hostname or not parsed.path:
        raise ValueError("DB_URL is missing host or database name")
    dbname = parsed.path.lstrip("/")
    return {
        "host": parsed.hostname,
        "port": parsed.port or 5432,
        "dbname": dbname,
    }


def get_db_connection_params() -> dict[str, Any]:
    url = os.getenv("DATABASE_URL")
    if url:
        return {"dsn": url}

    jdbc_url = os.getenv("DB_URL")
    username = os.getenv("DB_USERNAME")
    password = os.getenv("DB_PASSWORD")
    if not jdbc_url or not username or not password:
        raise RuntimeError("Set DATABASE_URL or DB_URL/DB_USERNAME/DB_PASSWORD")

    params = parse_jdbc_url(jdbc_url)
    params["user"] = username
    params["password"] = password
    return params


def get_host_port() -> tuple[str, int]:
    host = os.getenv("MCP_HOST", "0.0.0.0")
    # Default 8000 matches Spring Boot spring.ai.mcp.client...url (localhost:8000).
    # Java backend uses 8080, so MCP must not default to the same port for local dev.
    port_raw = os.getenv("MCP_PORT", "8000")
    try:
        port = int(port_raw)
    except ValueError as exc:
        raise ValueError("MCP_PORT must be an integer") from exc
    return host, port


def get_mcp_path() -> str:
    path = os.getenv("MCP_PATH", "/mcp")
    if not path.startswith("/"):
        raise ValueError("MCP_PATH must start with '/'")
    return path


def get_backend_base_url() -> str:
    return os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")


def get_backend_timeout_seconds() -> float:
    raw = os.getenv("BACKEND_TIMEOUT_SECONDS", "10")
    try:
        return float(raw)
    except ValueError as exc:
        raise ValueError("BACKEND_TIMEOUT_SECONDS must be a number") from exc
