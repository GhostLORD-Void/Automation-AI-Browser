package com.automationaibrowser

import org.json.JSONObject
import java.net.URI

/**
 * Validates planner output before any browser action is executed.
 * Keep this allowlist in sync with AgentActionEngine and docs/PLANNER_API.md.
 */
internal object AgentPlanValidator {
    private const val MAX_STEPS = 20
    private const val MAX_SELECTOR_LENGTH = 300
    private const val MAX_TEXT_LENGTH = 4_000
    private const val MAX_QUERY_LENGTH = 1_000
    private const val MAX_CONDITION_LENGTH = 2_000
    private const val MAX_WAIT_MS = 30_000L

    /** Returns null for a valid plan, otherwise a user-readable rejection reason. */
    fun validate(plan: JSONObject): String? {
        val steps = plan.optJSONArray("steps")
            ?: return "Invalid plan: steps array is required"
        if (steps.length() !in 1..MAX_STEPS) {
            return "Invalid plan: provide between 1 and $MAX_STEPS steps"
        }

        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i)
                ?: return "Invalid plan: step ${i + 1} is not an object"
            val action = step.optString("action").trim()
            when (action) {
                "navigate" -> {
                    val url = step.optString("url").trim()
                    if (!isAllowedWebUrl(url)) return "Step ${i + 1}: only valid HTTP(S) URLs without user info are allowed"
                }
                "search" -> {
                    val query = step.optString("query").trim()
                    if (query.isEmpty() || query.length > MAX_QUERY_LENGTH) {
                        return "Step ${i + 1}: search query is empty or too long"
                    }
                }
                "click", "type", "scroll", "extract", "wait_for", "press_enter", "submit" -> {
                    val selector = step.optString("selector").trim()
                    if (selector.isEmpty() || selector.length > MAX_SELECTOR_LENGTH) {
                        return "Step ${i + 1}: missing or invalid CSS selector"
                    }
                    if (action == "type" && step.optString("text").length > MAX_TEXT_LENGTH) {
                        return "Step ${i + 1}: input text exceeds $MAX_TEXT_LENGTH characters"
                    }
                    if (action == "wait_for") {
                        val timeout = step.optLong("timeoutMs", 10_000L)
                        if (timeout !in 100L..MAX_WAIT_MS) {
                            return "Step ${i + 1}: wait_for timeout must be 100–30000 ms"
                        }
                    }
                }
                "wait" -> {
                    val delay = step.optLong("milliseconds", 500L)
                    if (delay !in 100L..MAX_WAIT_MS) {
                        return "Step ${i + 1}: wait must be 100–30000 ms"
                    }
                }
                "back", "forward", "reload" -> Unit
                else -> return "Step ${i + 1}: unsupported action '$action'"
            }
        }

        val verification = plan.optJSONObject("verification")
            ?: return "Invalid plan: verification object is required"
        val selector = verification.optString("selector").trim()
        if (selector.isEmpty() || selector.length > MAX_SELECTOR_LENGTH) {
            return "Invalid plan: verification.selector is missing or too long"
        }
        for (field in listOf("textContains", "urlContains", "titleContains")) {
            val value = verification.optString(field)
            if (value.length > MAX_CONDITION_LENGTH) {
                return "Invalid plan: verification.$field is too long"
            }
        }
        return null
    }

    private fun isAllowedWebUrl(value: String): Boolean {
        if (value.isBlank() || value.length > 2_048) return false
        return try {
            val uri = URI(value)
            (uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("http", ignoreCase = true)) &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        } catch (_: Exception) {
            false
        }
    }
}
