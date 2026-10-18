from fastapi.testclient import TestClient
from main import app, validate_public_url

client = TestClient(app)

def test_health_reports_real_browser_mode():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["mode"] == "live-playwright"

def test_rejects_localhost():
    try:
        validate_public_url("http://localhost:8000/")
    except ValueError as exc:
        assert "blocked" in str(exc).lower() or "local" in str(exc).lower()
    else:
        raise AssertionError("localhost should be rejected")

def test_rejects_embedded_credentials():
    try:
        validate_public_url("https://user:pass@example.com/")
    except ValueError as exc:
        assert "credentials" in str(exc).lower()
    else:
        raise AssertionError("embedded credentials should be rejected")
