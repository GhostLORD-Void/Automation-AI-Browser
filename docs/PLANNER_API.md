# AI Planner Proxy Contract

Configure a trusted HTTPS endpoint in Agent Mode to enable server-generated multi-step plans. Keep model API keys on the proxy/server, never in the Android APK. The app sends the task, current page URL, and page title to this endpoint.

## Request

The app sends a JSON POST:

```json
{
  "task": "Find current Android WebView security guidance",
  "currentUrl": "https://example.com",
  "pageTitle": "Example"
}
```

The endpoint must respond with JSON. Return either the plan directly or wrap it in a top-level `plan` property.

## Response format

```json
{
  "steps": [
    { "action": "search", "query": "Android WebView security guidance" },
    { "action": "wait_for", "selector": "main", "timeoutMs": 10000 },
    { "action": "extract", "selector": "main" }
  ],
  "verification": {
    "selector": "main",
    "textContains": "security",
    "urlContains": "example",
    "titleContains": "Guidance"
  }
}
```

## Supported actions

| Action | Required fields | Behavior |
|---|---|---|
| `navigate` | `url` | Open an HTTP(S) URL; embedded URL credentials are rejected |
| `search` | `query` | Search DuckDuckGo |
| `click` | `selector` | Click the first matching DOM element |
| `type` | `selector`, `text` | Set an editable field and dispatch input/change events |
| `press_enter` | `selector` | Focus an element and dispatch Enter key events |
| `submit` | `selector` | Submit the selected form/field's form using `requestSubmit` and browser validation |
| `scroll` | `selector` | Scroll a matching element into view |
| `extract` | `selector` | Read text from a matching element (capped in the app) |
| `wait` | `milliseconds` | Wait 100–30000 ms |
| `wait_for` | `selector` | Poll for an element; optional `timeoutMs` is 100–30000 ms |
| `back` | none | Go back one browser history entry |
| `forward` | none | Go forward one browser history entry |
| `reload` | none | Reload the current page |

Selectors must be non-empty CSS selectors no longer than 300 characters. Text input is capped at 4,000 characters and search queries at 1,000 characters. Plans must contain 1–20 valid steps. Unknown actions, malformed URLs, missing fields, and out-of-range waits are rejected before execution.

## Completion verification

`verification.selector` is required. Optional fields add stronger checks:

- `textContains`: the page body must contain this text.
- `urlContains`: the final URL must contain this substring.
- `titleContains`: the page title must contain this substring.

The agent reports success only if all configured checks pass. A selector-only check proves only that an element exists; it does not prove a purchase, message, booking, or other real-world side effect succeeded. For critical tasks, verify the resulting state explicitly and keep the user in the loop.

## Safety and operational limits

- The Android app rejects non-HTTPS planner endpoints and redirects from the configured proxy. Use an endpoint you control and trust.
- A plan is shown for review before execution; the preview includes target selectors, URLs/queries, typed text, and final checks.
- Action execution is bounded, selector and payload lengths are capped, and DOM/page waits have timeouts.
- The planner receives the user's task and current URL/title. Do not send private browsing data to a proxy unless you trust its data handling.
- Local mode works offline only for simple URL navigation and web search. Full natural-language multi-step planning requires a reachable compatible HTTPS proxy.
