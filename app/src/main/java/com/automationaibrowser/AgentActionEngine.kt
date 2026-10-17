package com.automationaibrowser

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Executes a bounded sequence of browser actions returned by a trusted AI backend.
 *
 * This class deliberately does not invent plans or claim a task is complete without
 * a backend plan and a final verification step. Backend integration should validate
 * every proposed action before passing it here.
 */
internal class AgentActionEngine(
    private val webView: () -> WebView?,
    private val onStatus: (String) -> Unit,
    private val onFinished: (success: Boolean, summary: String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var generation = 0
    private var steps = JSONArray()
    private var currentPlan: JSONObject? = null
    private var index = 0
    private val maxSteps = 20
    private val maxWaitMs = 8_000L

    fun isRunning(): Boolean = running

    fun cancel() {
        generation++
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        onStatus("Task cancelled")
        onFinished(false, "Cancelled by user")
    }

    /**
     * Expected plan format:
     * {"steps":[{"action":"navigate","url":"https://example.com"},
     * {"action":"click","selector":"button.search"},
     * {"action":"type","selector":"input[name=q]","text":"query"},
     * {"action":"extract","selector":"main"}],
     * "verification":{"selector":"main"}}
     *
     * A production backend must generate this JSON and independently verify results.
     */
    fun execute(plan: JSONObject) {
        if (running) {
            onStatus("An agent task is already running")
            return
        }
        val proposed = plan.optJSONArray("steps")
        if (proposed == null || proposed.length() == 0 || proposed.length() > maxSteps) {
            onFinished(false, "Invalid plan: provide between 1 and $maxSteps steps")
            return
        }
        if (!plan.has("verification")) {
            onFinished(false, "Rejected plan: a final verification step is required")
            return
        }

        steps = proposed
        currentPlan = plan
        index = 0
        running = true
        generation++
        runStep(generation)
    }

    private fun runStep(runId: Int) {
        if (!running || runId != generation) return
        if (index >= steps.length()) {
            verify(runId)
            return
        }

        val step = steps.optJSONObject(index)
        if (step == null) {
            finish(runId, false, "Invalid action at step ${index + 1}")
            return
        }

        val action = step.optString("action")
        onStatus("Step ${index + 1}/${steps.length()}: $action")

        when (action) {
            "navigate" -> {
                val url = step.optString("url")
                if (!isAllowedWebUrl(url)) {
                    finish(runId, false, "Blocked unsafe or invalid navigation URL")
                    return
                }
                val browser = webView()
                if (browser == null) {
                    finish(runId, false, "Browser tab is unavailable")
                    return
                }
                browser.post {
                    if (!running || runId != generation) return@post
                    browser.loadUrl(url)
                    waitForPageAndContinue(browser, runId)
                }
            }
            "search" -> {
                val query = step.optString("query").trim()
                if (query.isEmpty()) {
                    finish(runId, false, "Search query is empty")
                    return
                }
                val url = "https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8")
                webView()?.post {
                    val browser = webView()
                    if (!running || runId != generation || browser == null) return@post
                    browser.loadUrl(url)
                    waitForPageAndContinue(browser, runId)
                } ?: finish(runId, false, "Browser tab is unavailable")
            }
            "click", "type", "scroll", "extract" -> executeDomAction(step, action, runId)
            "wait" -> {
                val delay = step.optLong("milliseconds", 500L).coerceIn(100L, maxWaitMs)
                mainHandler.postDelayed({
                    if (running && runId == generation) {
                        index++
                        runStep(runId)
                    }
                }, delay)
            }
            else -> finish(runId, false, "Unsupported action: $action")
        }
    }

    private fun executeDomAction(step: JSONObject, action: String, runId: Int) {
        val selector = step.optString("selector").trim()
        if (selector.isEmpty() || selector.length > 300) {
            finish(runId, false, "Missing or invalid CSS selector")
            return
        }
        val selectorJson = JSONObject.quote(selector)
        val script = when (action) {
            "click" -> """
                (() => {
                  const e = document.querySelector($selectorJson);
                  if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                  if (e.disabled || e.getAttribute('aria-disabled') === 'true')
                    return JSON.stringify({ok:false,error:'Element is disabled'});
                  e.click();
                  return JSON.stringify({ok:true});
                })()
            """.trimIndent()
            "type" -> {
                val text = JSONObject.quote(step.optString("text").take(4000))
                """
                (() => {
                  const e = document.querySelector($selectorJson);
                  if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                  if (!('value' in e) || e.disabled || e.readOnly)
                    return JSON.stringify({ok:false,error:'Element is not an editable field'});
                  e.focus(); e.value = $text;
                  e.dispatchEvent(new Event('input',{bubbles:true}));
                  e.dispatchEvent(new Event('change',{bubbles:true}));
                  return JSON.stringify({ok:true});
                })()
                """.trimIndent()
            }
            "scroll" -> """
                (() => {
                  const e = document.querySelector($selectorJson);
                  if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                  e.scrollIntoView({behavior:'instant',block:'center'});
                  return JSON.stringify({ok:true});
                })()
            """.trimIndent()
            else -> """
                (() => {
                  const e = document.querySelector($selectorJson);
                  if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                  return JSON.stringify({ok:true,text:(e.innerText || e.textContent || '').slice(0,12000)});
                })()
            """.trimIndent()
        }

        val browser = webView()
        if (browser == null) {
            finish(runId, false, "Browser tab is unavailable")
            return
        }
        browser.post {
            if (!running || runId != generation) return@post
            browser.evaluateJavascript(script) { raw ->
                if (!running || runId != generation) return@evaluateJavascript
                val result = try {
                    JSONObject(raw?.let { org.json.JSONTokener(it).nextValue() as? String ?: it } ?: "{}")
                } catch (_: Exception) {
                    JSONObject().put("ok", false).put("error", "Could not parse browser action result")
                }
                if (!result.optBoolean("ok", false)) {
                    finish(runId, false, result.optString("error", "Browser action failed"))
                    return@evaluateJavascript
                }
                if (action == "extract") onStatus("Extracted: " + result.optString("text").take(500))
                index++
                runStep(runId)
            }
        }
    }

    private fun waitForPageAndContinue(browser: WebView, runId: Int) {
        // WebView navigation is asynchronous. The backend's final verification remains
        // authoritative; this bounded delay only gives the initial document time to load.
        mainHandler.postDelayed({
            if (running && runId == generation) {
                index++
                runStep(runId)
            }
        }, 1_200L)
    }

    private fun verify(runId: Int) {
        val plan = currentPlan
        val verification = plan?.optJSONObject("verification")
        val selector = verification?.optString("selector").orEmpty().trim()
        if (selector.isEmpty() || selector.length > 300) {
            finish(runId, false, "Cannot verify completion: plan must provide a valid verification selector")
            return
        }
        val selectorJson = JSONObject.quote(selector)
        val script = """
            (() => {
              const e = document.querySelector($selectorJson);
              return JSON.stringify({ok: !!e, text: e ? (e.innerText || e.textContent || '').slice(0,2000) : ''});
            })()
        """.trimIndent()
        val browser = webView()
        if (browser == null) {
            finish(runId, false, "Cannot verify completion: browser tab is unavailable")
            return
        }
        browser.post {
            if (!running || runId != generation) return@post
            browser.evaluateJavascript(script) { raw ->
                if (!running || runId != generation) return@evaluateJavascript
                val result = try {
                    JSONObject(raw?.let { org.json.JSONTokener(it).nextValue() as? String ?: it } ?: "{}")
                } catch (_: Exception) {
                    JSONObject().put("ok", false)
                }
                if (!result.optBoolean("ok", false)) {
                    finish(runId, false, "Final verification failed: expected page element was not found")
                } else {
                    finish(runId, true, "Final page condition verified: " + result.optString("text").take(500))
                }
            }
        }
    }

    private fun finish(runId: Int, success: Boolean, summary: String) {
        if (runId != generation || !running) return
        running = false
        onStatus(if (success) "Task steps finished; verification required" else "Task stopped")
        onFinished(success, summary)
    }

    private fun isAllowedWebUrl(value: String): Boolean {
        val uri = android.net.Uri.parse(value)
        return (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }
}
