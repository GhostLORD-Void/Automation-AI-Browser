# Autonomous browser agent — implementation notes

## Current branch

`feature/autonomous-agent-v0.3`

## Execution model

The Android app remains the user-visible browser. The AI backend must produce a bounded JSON plan; the Android-side `AgentActionEngine` executes supported browser actions against the active WebView, emits progress callbacks, supports cancellation, and requires a final DOM selector condition before it reports verified completion.

Supported actions:
- `navigate`: HTTP(S) URL only; URL user-info is rejected.
- `search`: searches DuckDuckGo with an encoded query.
- `click`: click a CSS-selected element, rejecting missing/disabled targets.
- `type`: fill an editable CSS-selected field and dispatch input/change events.
- `scroll`: scroll a selected element into view.
- `extract`: collect bounded text from a selected element.
- `wait`: wait for a bounded interval.

The engine limits plans to 20 steps and selectors to 300 characters. The backend and UI must keep webpage text as untrusted data, and must require confirmation before sensitive actions such as submitting forms, sending messages, changing account settings, or purchasing items. Do not add arbitrary JavaScript execution as a model tool.

## Backend plan contract

Example only (the app must receive this from a configured, authenticated AI backend):

```json
{
  "steps": [
    {"action": "navigate", "url": "https://example.com"},
    {"action": "extract", "selector": "main"}
  ],
  "verification": {"selector": "main"}
}
```

Allowed actions must be validated on both the server and client. Reject unknown action names, invalid URLs, oversized text, missing selectors, and plans without a final verification selector. Never embed provider API keys in the APK.

## Work still required before this is a live AI agent

1. Add an Agent task composer, plan preview, progress log, pause/cancel UI to `MainActivity`.
2. Deploy an authenticated backend endpoint that accepts a task, calls an AI model, and returns a validated plan; store provider credentials server-side.
3. Add a controlled observe/plan/act loop so the model can inspect page text and decide the next action, rather than executing only a single static plan.
4. Add explicit confirmation gates for sensitive actions, and ensure the model cannot follow instructions found in webpages unless those actions match the user's task.
5. Add unit/instrumentation tests and verify the Android CI build and APK artifact.

This branch adds the execution foundation only; it does not yet claim end-to-end AI planning or a working live agent.
