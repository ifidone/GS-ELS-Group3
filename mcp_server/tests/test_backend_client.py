import httpx

from app.clients.backend_client import BackendClient, BackendResponse


def test_url_normalization():
    client = BackendClient(base_url="http://localhost:8080/")
    assert client._url("api/funds") == "http://localhost:8080/api/funds"
    assert client._url("/api/funds") == "http://localhost:8080/api/funds"


def test_parse_json_fallback_for_non_json():
    response = httpx.Response(status_code=200, content=b"plain text")
    parsed = BackendClient._parse_json(response)
    assert parsed == {"raw": "plain text"}


def test_parse_json_empty_body():
    response = httpx.Response(status_code=204, content=b"")
    parsed = BackendClient._parse_json(response)
    assert parsed is None


def test_request_error_returns_503(monkeypatch):
    class FailingClient:
        def __init__(self, *args, **kwargs):
            pass

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def request(self, *args, **kwargs):
            raise httpx.ConnectError("network failed")

    monkeypatch.setattr(httpx, "Client", FailingClient)
    client = BackendClient(base_url="http://localhost:8080")
    response = client.get("/api/funds")
    assert isinstance(response, BackendResponse)
    assert response.status_code == 503
    assert response.data["error"] is True
