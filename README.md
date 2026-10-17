# Automation AI Browser

An Android-first AI browser prototype, with Windows support planned.

## Current MVP

- Native Android app shell built with Kotlin and Android WebView
- Address bar with URL navigation and web search
- Back navigation
- Safe defaults for local file access
- AI actions reserved for a future backend integration

## Android development

1. Open this repository in Android Studio.
2. Use JDK 17 and a compatible Android SDK (API 35 recommended).
3. Sync the Gradle project and run the `app` configuration on an emulator or device.

> This repository currently contains the Android starter source. A Gradle wrapper and automated build workflow can be added next.

## Planned roadmap

- [ ] Android browser shell and navigation
- [ ] AI page summarization through a backend API
- [ ] Permission-gated browser task automation
- [ ] Bookmarks, history, downloads, and tab management
- [ ] Windows desktop client
- [ ] Tests and CI build workflow

## Security principles

- Keep AI provider keys on a backend; never embed secrets in the app.
- Require user confirmation before sensitive actions such as submitting forms or making purchases.
- Treat webpage content as untrusted input and do not follow instructions embedded in pages without user intent.
