from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Optional

import httpx

from config.settings import get_backend_base_url, get_backend_timeout_seconds


@dataclass
class BackendResponse:
    status_code: int
    data: Any


class BackendClient:
    def __init__(self,
                 base_url: Optional[str] = None,
                 timeout_seconds: Optional[float] = None) -> None:
        self._base_url = (base_url or get_backend_base_url()).rstrip("/")
        self._timeout = timeout_seconds if timeout_seconds is not None else get_backend_timeout_seconds()

    def _url(self, path: str) -> str:
        if not path.startswith("/"):
            path = f"/{path}"
        return f"{self._base_url}{path}"

    def _request(self,
                 method: str,
                 path: str,
                 params: Optional[dict[str, Any]] = None,
                 json_body: Optional[dict[str, Any]] = None,
                 headers: Optional[dict[str, str]] = None) -> BackendResponse:
        try:
            # trust_env=False avoids inheriting potentially unsafe proxy settings.
            with httpx.Client(timeout=self._timeout, trust_env=False, follow_redirects=False) as client:
                response = client.request(
                    method,
                    self._url(path),
                    params=params,
                    json=json_body,
                    headers=headers,
                )
                return BackendResponse(response.status_code, self._parse_json(response))
        except httpx.HTTPError as exc:
            return BackendResponse(503, {"error": True, "message": str(exc)})

    def get(self, path: str,
            params: Optional[dict[str, Any]] = None,
            json_body: Optional[dict[str, Any]] = None,
            headers: Optional[dict[str, str]] = None) -> BackendResponse:
        return self._request("GET", path, params=params, json_body=json_body, headers=headers)

    def post(self, path: str,
             json_body: Optional[dict[str, Any]] = None,
             headers: Optional[dict[str, str]] = None) -> BackendResponse:
        return self._request("POST", path, json_body=json_body, headers=headers)

    def patch(self, path: str,
              json_body: Optional[dict[str, Any]] = None,
              headers: Optional[dict[str, str]] = None) -> BackendResponse:
        return self._request("PATCH", path, json_body=json_body, headers=headers)

    def put(self, path: str,
            json_body: Optional[dict[str, Any]] = None,
            headers: Optional[dict[str, str]] = None) -> BackendResponse:
        return self._request("PUT", path, json_body=json_body, headers=headers)

    def delete(self, path: str,
               json_body: Optional[dict[str, Any]] = None,
               headers: Optional[dict[str, str]] = None) -> BackendResponse:
        return self._request("DELETE", path, json_body=json_body, headers=headers)

    @staticmethod
    def _parse_json(response: httpx.Response) -> Any:
        if not response.content:
            return None
        try:
            return response.json()
        except ValueError:
            return {"raw": response.text}
