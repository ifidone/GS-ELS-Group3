import pytest

from config import settings


def test_parse_jdbc_url_valid():
    result = settings.parse_jdbc_url("jdbc:postgresql://db.example.com:5432/users")
    assert result["host"] == "db.example.com"
    assert result["port"] == 5432
    assert result["dbname"] == "users"


def test_get_db_connection_params_from_jdbc(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("DATABASE_URL", raising=False)
    monkeypatch.setenv("DB_URL", "jdbc:postgresql://localhost:5432/users")
    monkeypatch.setenv("DB_USERNAME", "postgres")
    monkeypatch.setenv("DB_PASSWORD", "secret")

    params = settings.get_db_connection_params()
    assert params["host"] == "localhost"
    assert params["port"] == 5432
    assert params["dbname"] == "users"
    assert params["user"] == "postgres"
    assert params["password"] == "secret"


def test_get_db_connection_params_from_database_url(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("DATABASE_URL", "postgresql://user:pass@localhost:5432/users")
    params = settings.get_db_connection_params()
    assert params == {"dsn": "postgresql://user:pass@localhost:5432/users"}


def test_get_host_port_uses_port_fallback(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("MCP_PORT", raising=False)
    monkeypatch.setenv("PORT", "8080")
    monkeypatch.setenv("MCP_HOST", "0.0.0.0")

    host, port = settings.get_host_port()
    assert host == "0.0.0.0"
    assert port == 8080


def test_get_host_port_rejects_invalid_range(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("MCP_PORT", "99999")
    with pytest.raises(ValueError):
        settings.get_host_port()


def test_get_backend_base_url_rejects_invalid_scheme(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("BACKEND_BASE_URL", "ftp://localhost:8080")
    with pytest.raises(ValueError):
        settings.get_backend_base_url()


def test_get_mcp_path_normalizes_trailing_slash(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("MCP_PATH", "/mcp/")
    assert settings.get_mcp_path() == "/mcp"


def test_validate_runtime_security_requires_token_on_public_bind(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ALLOW_INSECURE_PUBLIC_BIND", "false")
    with pytest.raises(RuntimeError):
        settings.validate_runtime_security("0.0.0.0", None)


def test_validate_runtime_security_allows_with_token():
    settings.validate_runtime_security("0.0.0.0", "token-123")


def test_validate_runtime_security_allows_override(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ALLOW_INSECURE_PUBLIC_BIND", "true")
    settings.validate_runtime_security("0.0.0.0", None)
