from __future__ import annotations

import asyncio
import ipaddress
import os
import socket
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, HttpUrl
from playwright.async_api import async_playwright, TimeoutError as PlaywrightTimeoutError

APP_VERSION = "0.1.0"
app = FastAPI(
    title="TinyFish Browser Core API",
    version=APP_VERSION,
    description="Real read-only website navigation and extraction using Playwright. No synthetic data.",
)

origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "*").split(",") if x.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins if origins != ["*"] else ["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type"],
)

class ExtractRequest(BaseModel):
    url: HttpUrl
    max_chars: int = Field(default=12000, ge=500, le=30000)
    timeout_ms: int = Field(default=20000, ge=3000, le=45000)

def validate_public_url(raw: str) -> str:
    parsed = urlparse(raw)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise ValueError("Only http/https URLs are allowed.")
    if parsed.username or parsed.password:
        raise ValueError("URLs containing embedded credentials are not allowed.")
    host = parsed.hostname.rstrip(".").lower()
    if host == "localhost" or host.endswith((".localhost", ".local", ".internal")):
        raise ValueError("Local and internal hostnames are blocked.")
    try:
        addresses = {ipaddress.ip_address(item[4][0]) for item in socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80), type=socket.SOCK_STREAM)}
    except (socket.gaierror, ValueError, OSError):
        raise ValueError("Hostname could not be resolved.")
    if not addresses or any(not addr.is_global for addr in addresses):
        raise ValueError("Private, loopback, link-local and reserved IP addresses are blocked.")
    return raw

async def guard_request(route):
    try:
        validate_public_url(route.request.url)
        await route.continue_()
    except Exception:
        await route.abort("blockedbyclient")

@app.get("/")
async def root():
    return {"service": "TinyFish Browser Core API", "version": APP_VERSION, "mode": "live-playwright", "docs": "/docs"}

@app.get("/health")
async def health():
    return {"ok": True, "mode": "live-playwright", "browser": "chromium"}

@app.post("/api/extract")
async def extract(request: ExtractRequest):
    try:
        url = validate_public_url(str(request.url))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True, args=["--no-sandbox", "--disable-dev-shm-usage"])
        try:
            context = await browser.new_context(
                viewport={"width": 1365, "height": 900},
                user_agent="TinyFishBrowserCore/0.1 (+read-only website extraction)",
            )
            await context.route("**/*", guard_request)
            page = await context.new_page()
            try:
                response = await page.goto(url, wait_until="domcontentloaded", timeout=request.timeout_ms)
                # Redirects are checked as well; the route guard blocks non-public requests.
                final_url = page.url
                try:
                    validate_public_url(final_url)
                except ValueError:
                    raise HTTPException(status_code=400, detail="Navigation ended at a blocked URL.")
                await page.wait_for_timeout(700)
                data = await page.evaluate("""(maxChars) => {
                  const clean = (s) => (s || '').replace(/\\s+/g, ' ').trim();
                  const title = clean(document.title);
                  const description = clean(document.querySelector('meta[name="description"]')?.content || document.querySelector('meta[property="og:description"]')?.content || '');
                  const headings = Array.from(document.querySelectorAll('h1,h2,h3')).slice(0, 40).map(e => ({level: Number(e.tagName.slice(1)), text: clean(e.innerText)})).filter(x => x.text);
                  const links = Array.from(document.querySelectorAll('a[href]')).slice(0, 60).map(a => ({text: clean(a.innerText), url: a.href})).filter(a => a.text && /^https?:/.test(a.url));
                  const text = clean(document.body?.innerText || '').slice(0, maxChars);
                  return {title, description, headings, links, text};
                }""", request.max_chars)
                return {
                    "status": "success",
                    "mode": "live-playwright",
                    "synthetic": False,
                    "url": final_url,
                    "http_status": response.status if response else None,
                    "content_type": response.headers.get("content-type") if response else None,
                    "extracted": data,
                    "limits": ["Read-only extraction; no clicking, form submission, login or purchase actions.", "Some sites may block automated browsers or require JavaScript/cookies."],
                }
            except PlaywrightTimeoutError:
                raise HTTPException(status_code=504, detail="Website navigation timed out.")
            finally:
                await context.close()
        finally:
            await browser.close()
