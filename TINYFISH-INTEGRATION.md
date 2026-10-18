# TinyFish Browser Core: website + Android integration

## What's real vs simulated
- `tinyfish-browser-core/index.html` is the original mock/simulation demo. It uses synthetic fixture data and does not control a remote browser.
- `tinyfish-browser-core/live-agent.html` is a separate static client that calls the real backend.
- `app/src/main/java/com/automationaibrowser/LiveAgentActivity.kt` is a native Android client. Open the Android browser and tap **LIVE** to call the same backend.
- `tinyfish-agent-api/` is the real Python FastAPI + Playwright Chromium service. It navigates to public HTTP(S) pages and extracts page metadata, headings, links and visible text.

## Deploy the backend (free-first)
1. Create/sign in to a Render account and connect this repository.
2. Use the blueprint file `tinyfish-agent-api/render.yaml` to create the `tinyfish-browser-core-api` web service.
3. Wait for the service deployment. Open `https://YOUR-SERVICE.onrender.com/health`; expected JSON includes `"ok": true` and `"mode": "live-playwright"`.
4. Open `https://YOUR-SERVICE.onrender.com/docs` to inspect the API.
5. On the static Live Agent page, enter the HTTPS backend URL. In Android, tap **LIVE**, enter the same URL and tap **Check backend health**.

Free hosted services can sleep, have cold starts, and have usage limits. Availability and free tiers may change.

## Test a real page
In either client use `https://example.com` and press **Extract live page**. A successful response should include `"synthetic": false`, the actual final URL and extracted document content.

## Current scope and safety
- Read-only website navigation and extraction. No clicking, form submissions, login, purchases, or account actions.
- The API rejects localhost/private/reserved network targets and URLs containing embedded credentials. Do not deploy an unauthenticated browser service to a sensitive/private network.
- Public websites can block automation or require consent/cookies.
- Do not send private URLs or credentials.
- GitHub Pages cannot run Python/Chromium; the backend must be deployed separately.

## Verification
GitHub Actions workflow `TinyFish Integrated CI` assembles the Android debug APK and runs API tests. A green CI run is necessary but does not replace deployment and end-to-end testing against the deployed backend.
