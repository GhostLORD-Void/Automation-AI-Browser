# AI Planner Proxy Contract

Configure an HTTPS endpoint in Agent Mode to enable server-generated multi-step plans. Keep model API keys on the server; do not embed them in the APK.

## Request

The app sends a JSON POST with task, currentUrl, and pageTitle fields.

## Response

Return either the plan object directly or an object with a top-level plan property:

```json
{
  "steps": [
    { "action": "search", "query": "Android WebView security" },
    { "action": "wait", "milliseconds": 1500 }
  ],
  "verification": { "selector": "body" }
}
```

Supported actions are navigate (url), search (query), click (selector), type (selector and text), scroll (selector), extract (selector), and wait (milliseconds). Plans must contain 1–20 steps and a verification.selector. Non-HTTPS endpoints and invalid responses are rejected.

If no endpoint is configured, local fallback supports basic URL navigation and web search only. The final selector check confirms an element exists; it does not prove a transaction or other real-world side effect succeeded. Only configure a planner server you trust, because the app sends it the task and current page URL/title.