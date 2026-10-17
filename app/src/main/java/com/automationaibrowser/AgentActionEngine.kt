package com.automationaibrowser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Runs a bounded, user-reviewed browser plan. Planner output is validated before
 * execution; navigation and DOM waits are bounded; final checks can assert page text,
 * URL, and title in addition to the presence of a selector.
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
    private val maxWaitMs = 30_000L
    private val navigationTimeoutMs = 15_000L

    fun isRunning(): Boolean = running

    fun cancel() {
        generation++
        val wasRunning = running
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        if (wasRunning) {
            onStatus("Task cancelled")
            onFinished(false, "Cancelled by user")
        }
    }

    fun execute(plan: JSONObject) {
        if (running) {
            onStatus("An agent task is already running")
            return
        }
        val validationError = AgentPlanValidator.validate(plan)
        if (validationError != null) {
            onFinished(false, validationError)
            return
        }

        steps = plan.getJSONArray("steps")
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
                val url = step.optString("url").trim()
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
                val url = "https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8")
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
            "back", "forward", "reload" -> {
                val browser = webView()
                if (browser == null) {
                    finish(runId, false, "Browser tab is unavailable")
                    return
                }
                browser.post {
                    if (!running || runId != generation) return@post
                    when (action) {
                        "back" -> {
                            if (!browser.canGoBack()) {
                                finish(runId, false, "No previous page is available")
                                return@post
                            }
                            browser.goBack()
                        }
                        "forward" -> {
                            if (!browser.canGoForward()) {
                                finish(runId, false, "No forward page is available")
                                return@post
                            }
                            browser.goForward()
                        }
                        "reload" -> browser.reload()
                    }
                    waitForPageAndContinue(browser, runId)
                }
            }
            "click", "type", "scroll", "extract", "press_enter", "submit" ->
                executeDomAction(step, action, runId)
            "wait" -> {
                val delay = step.optLong("milliseconds", 500L).coerceIn(100L, maxWaitMs)
                mainHandler.postDelayed({
                    if (running && runId == generation) {
                        index++
                        runStep(runId)
                    }
                }, delay)
            }
            "wait_for" -> waitForSelector(
                selector = step.optString("selector").trim(),
                timeoutMs = step.optLong("timeoutMs", 10_000L).coerceIn(100L, maxWaitMs),
                runId = runId
            )
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
                  try {
                    const e = document.querySelector($selectorJson);
                    if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                    if (e.disabled || e.getAttribute('aria-disabled') === 'true')
                      return JSON.stringify({ok:false,error:'Element is disabled'});
                    e.click();
                    return JSON.stringify({ok:true});
                  } catch (err) {
                    return JSON.stringify({ok:false,error:'Invalid selector or click failed'});
                  }
                })()
            """.trimIndent()
            "type" -> {
                val text = JSONObject.quote(step.optString("text").take(4000))
                """
                (() => {
                  try {
                    const e = document.querySelector($selectorJson);
                    if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                    if (!('value' in e) || e.disabled || e.readOnly)
                      return JSON.stringify({ok:false,error:'Element is not an editable field'});
                    e.focus();
                    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e), 'value')?.set;
                    if (setter) setter.call(e, $text); else e.value = $text;
                    e.dispatchEvent(new Event('input',{bubbles:true}));
                    e.dispatchEvent(new Event('change',{bubbles:true}));
                    return JSON.stringify({ok:true});
                  } catch (err) {
                    return JSON.stringify({ok:false,error:'Could not type into the field'});
                  }
                })()
                """.trimIndent()
            }
            "scroll" -> """
                (() => {
                  try {
                    const e = document.querySelector($selectorJson);
                    if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                    e.scrollIntoView({behavior:'instant',block:'center'});
                    return JSON.stringify({ok:true});
                  } catch (err) {
                    return JSON.stringify({ok:false,error:'Invalid selector or scroll failed'});
                  }
                })()
            """.trimIndent()
            "press_enter" -> """
                (() => {
                  try {
                    const e = document.querySelector($selectorJson);
                    if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                    e.focus();
                    const options = {key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true};
                    e.dispatchEvent(new KeyboardEvent('keydown',options));
                    e.dispatchEvent(new KeyboardEvent('keypress',options));
                    e.dispatchEvent(new KeyboardEvent('keyup',options));
                    return JSON.stringify({ok:true});
                  } catch (err) {
                    return JSON.stringify({ok:false,error:'Could not press Enter'});
                  }
                })()
            """.trimIndent()
            "submit" -> """
                (() => {
                  try {
                    const e = document.querySelector($selectorJson);
                    if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                    const form = e.matches('form') ? e : e.form;
                    if (!form) return JSON.stringify({ok:false,error:'No form associated with the selected element'});
                    if (typeof form.requestSubmit !== 'function')
                      return JSON.stringify({ok:false,error:'Safe form submission is not supported on this page'});
                    form.requestSubmit();
                    return JSON.stringify({ok:true});
                  } catch (err) {
                    return JSON.stringify({ok:false,error:'Form submission failed'});
                  }
                })()
            """.trimIndent()
            else -> """
                (() => {
                  try {
                    const e = document.querySelector($selectorJson);
                    if (!e) return JSON.stringify({ok:false,error:'Element not found'});
                    return JSON.stringify({ok:true,text:(e.innerText || e.textContent || '').slice(0,12000)});
                  } catch (err) {
                    return JSON.stringify({ok:false,error:'Invalid CSS selector'});
                  }
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
                val result = parseResultObject(raw)
                if (!result.optBoolean("ok", false)) {
                    finish(runId, false, result.optString("error", "Browser action failed"))
                    return@evaluateJavascript
                }
                if (action == "extract") {
                    onStatus("Extracted: " + result.optString("text").take(800))
                }
                if (action == "click" || action == "press_enter" || action == "submit") {
                    // Give the page a bounded chance to react before the next action.
                    mainHandler.postDelayed({
                        if (running && runId == generation) {
                            index++
                            runStep(runId)
                        }
                    }, 300L)
                } else {
                    index++
                    runStep(runId)
                }
            }
        }
    }

    private fun waitForSelector(selector: String, timeoutMs: Long, runId: Int) {
        val deadline = System.currentTimeMillis() + timeoutMs
        fun poll() {
            if (!running || runId != generation) return
            val browser = webView()
            if (browser == null) {
                finish(runId, false, "Browser tab is unavailable while waiting for element")
                return
            }
            val selectorJson = JSONObject.quote(selector)
            browser.evaluateJavascript(
                "(() => { try { return !!document.querySelector($selectorJson); } catch(e) { return false; } })()"
            ) { raw ->
                if (!running || runId != generation) return@evaluateJavascript
                if (raw?.trim() == "true") {
                    index++
                    runStep(runId)
                } else if (System.currentTimeMillis() >= deadline) {
                    finish(runId, false, "Timed out waiting for element: $selector")
                } else {
                    mainHandler.postDelayed({ poll() }, 250L)
                }
            }
        }
        poll()
    }

    private fun waitForPageAndContinue(browser: WebView, runId: Int) {
        val deadline = System.currentTimeMillis() + navigationTimeoutMs
        fun poll() {
            if (!running || runId != generation) return
            if (webView() !== browser) {
                finish(runId, false, "Browser tab changed while navigation was running")
                return
            }
            browser.evaluateJavascript("document.readyState") { raw ->
                if (!running || runId != generation) return@evaluateJavascript
                val readyState = try {
                    org.json.JSONTokener(raw ?: "null").nextValue() as? String
                } catch (_: Exception) {
                    null
                }
                if (readyState == "complete") {
                    index++
                    runStep(runId)
                } else if (System.currentTimeMillis() >= deadline) {
                    finish(runId, false, "Timed out waiting for page navigation to finish")
                } else {
                    mainHandler.postDelayed({ poll() }, 250L)
                }
            }
        }
        mainHandler.postDelayed({ poll() }, 250L)
    }

    private fun verify(runId: Int) {
        val plan = currentPlan ?: run {
            finish(runId, false, "No plan available for verification")
            return
        }
        val verification = plan.optJSONObject("verification")
        val selector = verification?.optString("selector").orEmpty().trim()
        if (selector.isEmpty() || selector.length > 300) {
            finish(runId, false, "Cannot verify completion: plan must provide a valid verification selector")
            return
        }
        val selectorJson = JSONObject.quote(selector)
        val textContains = JSONObject.quote(verification?.optString("textContains").orEmpty())
        val urlContains = JSONObject.quote(verification?.optString("urlContains").orEmpty())
        val titleContains = JSONObject.quote(verification?.optString("titleContains").orEmpty())
        val script = """
            (() => {
              try {
                const e = document.querySelector($selectorJson);
                const bodyText = document.body ? (document.body.innerText || document.body.textContent || '') : '';
                const url = location.href || '';
                const title = document.title || '';
                const expectedText = $textContains;
                const expectedUrl = $urlContains;
                const expectedTitle = $titleContains;
                const checks = [!!e];
                if (expectedText) checks.push(bodyText.includes(expectedText));
                if (expectedUrl) checks.push(url.includes(expectedUrl));
                if (expectedTitle) checks.push(title.includes(expectedTitle));
                return JSON.stringify({
                  ok: checks.every(Boolean),
                  selectorFound: !!e,
                  text: e ? (e.innerText || e.textContent || '').slice(0,1200) : '',
                  url: url.slice(0,1000),
                  title: title.slice(0,300),
                  textMatched: !expectedText || bodyText.includes(expectedText),
                  urlMatched: !expectedUrl || url.includes(expectedUrl),
                  titleMatched: !expectedTitle || title.includes(expectedTitle)
                });
              } catch (err) {
                return JSON.stringify({ok:false,selectorFound:false});
              }
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
                val result = parseResultObject(raw)
                if (!result.optBoolean("ok", false)) {
                    val failures = buildList {
                        if (!result.optBoolean("selectorFound", false)) add("expected element missing")
                        if (!result.optBoolean("textMatched", true)) add("expected page text missing")
                        if (!result.optBoolean("urlMatched", true)) add("URL condition not met")
                        if (!result.optBoolean("titleMatched", true)) add("title condition not met")
                    }.joinToString(", ")
                    finish(runId, false, "Final verification failed: $failures")
                } else {
                    val details = listOf(
                        result.optString("title").takeIf { it.isNotBlank() },
                        result.optString("url").takeIf { it.isNotBlank() },
                        result.optString("text").takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString("\n")
                    finish(runId, true, "Verified page conditions:\n" + details.take(1_800))
                }
            }
        }
    }

    private fun parseResultObject(raw: String?): JSONObject {
        return try {
            val decoded = raw?.let { org.json.JSONTokener(it).nextValue() as? String ?: it } ?: "{}"
            JSONObject(decoded)
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("error", "Could not parse browser action result")
        }
    }

    private fun finish(runId: Int, success: Boolean, summary: String) {
        if (runId != generation || !running) return
        running = false
        mainHandler.removeCallbacksAndMessages(null)
        onStatus(if (success) "Task verified" else "Task stopped")
        onFinished(success, summary)
    }

    private fun isAllowedWebUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        return (uri.scheme == "https" || uri.scheme == "http") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && value.length <= 2_048
    }
}
