# TinyFish Browser Core — Live Agent API

This API is the first real-browser milestone. It uses Chromium via Playwright to navigate to a public HTTP(S) page and extract its title, description, headings, links and visible text. Returned data is from the visited page, not mock fixtures.

## Safety and current limits
- Read-only navigation/extraction only. It does not click buttons, submit forms, log in, purchase, or bypass site access controls.
- Rejects local/private/reserved IP targets and embedded URL credentials; outbound browser requests are checked too.
- Do not expose this API publicly without rate limiting and abuse monitoring. A public endpoint can consume browser resources.
- Some sites block automated browsers or require consent/cookies.
- GitHub Pages remains a static frontend; it cannot run this Python API.

## Run locally
Requires Python 3.11+ and Docker is recommended.

```bash
cd tinyfish-agent-api
pip install -r requirements.txt
python -m playwright install --with-deps chromium
uvicorn main:app --reload --port 8000
```

Open http://127.0.0.1:8000/docs and POST `/api/extract` with:
```json
{"url":"https://example.com","max_chars":12000,"timeout_ms":20000}
```

Successful responses include `"mode":"live-playwright"` and `"synthetic":false`.

## Free-first deployment
A Render blueprint is provided in `render.yaml`. Import the repository into Render and select the blueprint. Availability and free-plan limits may change; free instances may sleep and cold-start. After deployment, copy the service URL and test `/health` and `/docs`. The static frontend is not connected to this API yet; that integration is the next milestone once a backend URL exists.

## Verify
```bash
python -m compileall -q main.py
```
