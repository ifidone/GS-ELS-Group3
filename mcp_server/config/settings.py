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
    # Default localhost to avoid exposing MCP tools outside the machine by default.
    host = os.getenv("MCP_HOST", "127.0.0.1")
    # Default 8000 matches Spring Boot spring.ai.mcp.client...url (localhost:8000).
    # If MCP_PORT is not set (for hosted platforms), fallback to PORT.
    # Java backend uses 8080, so MCP must not default to the same port for local dev.
    port_raw = os.getenv("MCP_PORT", os.getenv("PORT", "8000"))
    try:
        port = int(port_raw)
    except ValueError as exc:
        raise ValueError("MCP_PORT must be an integer") from exc
    if port <= 0 or port > 65535:
        raise ValueError("MCP_PORT must be in range 1..65535")
    return host, port


def get_mcp_path() -> str:
    path = os.getenv("MCP_PATH", "/mcp")
    if not path.startswith("/"):
        raise ValueError("MCP_PATH must start with '/'")
    return path.rstrip("/") or "/mcp"


def get_backend_base_url() -> str:
    url = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        raise ValueError("BACKEND_BASE_URL must use http or https")
    if not parsed.netloc:
        raise ValueError("BACKEND_BASE_URL must include host")
    return url


def get_backend_timeout_seconds() -> float:
    raw = os.getenv("BACKEND_TIMEOUT_SECONDS", "10")
    try:
        timeout = float(raw)
    except ValueError as exc:
        raise ValueError("BACKEND_TIMEOUT_SECONDS must be a number") from exc
    if timeout <= 0:
        raise ValueError("BACKEND_TIMEOUT_SECONDS must be > 0")
    return timeout


def get_log_level() -> str:
    level = os.getenv("LOG_LEVEL", "INFO").strip().upper()
    allowed = {"CRITICAL", "ERROR", "WARNING", "INFO", "DEBUG"}
    if level not in allowed:
        raise ValueError("LOG_LEVEL must be one of CRITICAL, ERROR, WARNING, INFO, DEBUG")
    return level


def get_mcp_auth_token() -> str | None:
    token = os.getenv("MCP_AUTH_TOKEN", "").strip()
    return token or None


def allow_insecure_public_bind() -> bool:
    return os.getenv("ALLOW_INSECURE_PUBLIC_BIND", "false").strip().lower() == "true"


def is_public_bind_host(host: str) -> bool:
    normalized = host.strip().lower()
    return normalized in {"0.0.0.0", "::"}


def validate_runtime_security(host: str, token: str | None) -> None:
    # Public bind with no auth token is denied by default.
    if is_public_bind_host(host) and not token and not allow_insecure_public_bind():
        raise RuntimeError(
            "Refusing insecure MCP exposure: set MCP_AUTH_TOKEN when MCP_HOST is public "
            "(0.0.0.0/::), or set ALLOW_INSECURE_PUBLIC_BIND=true explicitly."
        )
