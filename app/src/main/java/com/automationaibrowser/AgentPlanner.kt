package com.automationaibrowser

import org.json.JSONObject
import java.io.BufferedReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Calls a user-configured HTTPS planner proxy. Keep model credentials on that server,
 * never in the Android app. The returned plan is validated before it reaches the UI.
 */
internal object AgentPlanner {
    private const val MAX_RESPONSE_CHARS = 100_000
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
                val endpointUrl = URL(endpoint)
                require(endpointUrl.protocol.equals("https", ignoreCase = true) &&
                    endpointUrl.host.isNotBlank() && endpointUrl.userInfo == null) {
                    "Planner endpoint must be a valid HTTPS URL without embedded credentials"
                }
                val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    useCaches = false
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val payload = JSONObject()
                        .put("task", task.take(4_000))
                        .put("currentUrl", currentUrl.take(2_000))
                        .put("pageTitle", pageTitle.take(500))
                    connection.outputStream.use {
                        it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use {
                        readLimited(it, MAX_RESPONSE_CHARS)
                    }.orEmpty()
                    check(code in 200..299) {
                        if (code in 300..399) "Planner redirects are not allowed (HTTP $code)"
                        else "Planner server returned HTTP $code"
                    }
                    val response = JSONObject(body)
                    val plan = response.optJSONObject("plan") ?: response
                    val validationError = AgentPlanValidator.validate(plan)
                    require(validationError == null) {
                        validationError ?: "Planner returned an invalid action plan"
                    }
                    plan
                } finally {
                    connection.disconnect()
                }
            }
            callback(result)
        }
    }

    private fun readLimited(reader: Reader, maxChars: Int): String {
        val buffered = if (reader is BufferedReader) reader else BufferedReader(reader)
        val output = StringBuilder()
        val chunk = CharArray(4_096)
        while (true) {
            val count = buffered.read(chunk)
            if (count < 0) break
            require(output.length + count <= maxChars) { "Planner response is too large" }
            output.append(chunk, 0, count)
        }
        return output.toString()
    }

    /**
     * Offline fallback: accepts a plain URL or a simple open/visit/go-to URL command.
     * Anything else becomes a web search; full multi-step planning requires the proxy.
     */
    fun localPlan(task: String): JSONObject {
        val trimmed = task.trim()
        require(trimmed.isNotEmpty()) { "Task is empty" }
        val target = when {
            trimmed.startsWith("https://", true) || trimmed.startsWith("http://", true) -> trimmed
            trimmed.matches(Regex("(?i)^(open|visit|go to)\\s+https?://.*")) ->
                trimmed.replaceFirst(Regex("(?i)^(open|visit|go to)\\s+"), "")
            trimmed.matches(Regex("(?i)^(open|visit|go to)\\s+[a-z0-9.-]+\\.[a-z]{2,}(/.*)?$")) ->
                "https://" + trimmed.replaceFirst(Regex("(?i)^(open|visit|go to)\\s+"), "")
            else -> ""
        }
        val step = if (target.isNotBlank()) {
            JSONObject().put("action", "navigate").put("url", target)
        } else {
            val query = trimmed.replaceFirst(Regex("(?i)^(search for|search|find)\\s+"), "").trim()
            JSONObject().put("action", "search").put("query", query)
        }
        return JSONObject()
            .put("steps", org.json.JSONArray().put(step))
            .put("verification", JSONObject().put("selector", "body"))
    }
}
