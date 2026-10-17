package com.automationaibrowser

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Calls a user-configured HTTPS planner proxy. Keep model credentials on that server,
 * never in the Android app. The proxy must return a validated action plan JSON object.
 */
internal object AgentPlanner {
    private val executor = Executors.newSingleThreadExecutor()

    fun requestPlan(
        endpoint: String,
        task: String,
        currentUrl: String,
        pageTitle: String,
        callback: (Result<JSONObject>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                require(endpoint.startsWith("https://", ignoreCase = true)) {
                    "Planner endpoint must use HTTPS"
                }
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val payload = JSONObject()
                        .put("task", task.take(4000))
                        .put("currentUrl", currentUrl.take(2000))
                        .put("pageTitle", pageTitle.take(500))
                    connection.outputStream.use {
                        it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                    check(code in 200..299) { "Planner server returned HTTP $code" }
                    check(body.length <= 100_000) { "Planner response is too large" }
                    val response = JSONObject(body)
                    val plan = response.optJSONObject("plan") ?: response
                    val steps = plan.optJSONArray("steps")
                    require(steps != null && steps.length() in 1..20) {
                        "Planner response must include 1–20 steps"
                    }
                    require(plan.optJSONObject("verification")?.optString("selector").orEmpty().isNotBlank()) {
                        "Planner response must include verification.selector"
                    }
                    plan
                } finally {
                    connection.disconnect()
                }
            }
            callback(result)
        }
    }

    fun localPlan(task: String): JSONObject {
        val trimmed = task.trim()
        require(trimmed.isNotEmpty()) { "Task is empty" }
        val target = when {
            trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true) -> trimmed
            trimmed.matches(Regex("(?i)^(open|visit|go to)\\\\s+https?://.*")) ->
                trimmed.replaceFirst(Regex("(?i)^(open|visit|go to)\\\\s+"), "")
            trimmed.matches(Regex("(?i)^(open|visit|go to)\\\\s+[a-z0-9.-]+\\\\.[a-z]{2,}(/.*)?$")) ->
                "https://" + trimmed.replaceFirst(Regex("(?i)^(open|visit|go to)\\\\s+"), "")
            else -> ""
        }
        val step = if (target.isNotBlank()) {
            JSONObject().put("action", "navigate").put("url", target)
        } else {
            val query = trimmed.replaceFirst(Regex("(?i)^(search for|search|find)\\\\s+"), "").trim()
            JSONObject().put("action", "search").put("query", query)
        }
        return JSONObject()
            .put("steps", org.json.JSONArray().put(step))
            .put("verification", JSONObject().put("selector", "body"))
    }
}
