package com.automationaibrowser

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Native Android client for the real, read-only TinyFish Playwright API. */
class LiveAgentActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var apiInput: EditText
    private lateinit var targetInput: EditText
    private lateinit var output: TextView
    private lateinit var runButton: Button
    private val bg = Color.rgb(9, 13, 22)
    private val panel = Color.rgb(20, 27, 40)
    private val fg = Color.rgb(239, 244, 255)
    private val muted = Color.rgb(159, 173, 195)
    private val accent = Color.rgb(98, 220, 174)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("nova_browser", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "TINYFISH · LIVE AGENT"
            textSize = 12f
            letterSpacing = .12f
            setTextColor(accent)
        })
        root.addView(TextView(this).apply {
            text = "Real website extraction"
            textSize = 27f
            setTextColor(fg)
            setPadding(0, dp(10), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Uses a deployed Playwright backend to visit a public website. Read-only: no login, clicks, form submissions, or purchases. This is separate from the mock simulation."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(12))
        })
        apiInput = field("HTTPS backend URL (Render service URL)").apply {
            setText(prefs.getString("tinyfish_api_base", "").orEmpty())
        }
        root.addView(apiInput, params())
        targetInput = field("Public website URL").apply { setText("https://example.com") }
        root.addView(targetInput, params())
        val health = Button(this).apply {
            text = "Check backend health"
            setOnClickListener { request("/health", null) }
        }
        root.addView(health, params())
        runButton = Button(this).apply {
            text = "Extract live page"
            setOnClickListener {
                val target = targetInput.text.toString().trim()
                val parsed = runCatching { URL(target) }.getOrNull()
                if (parsed == null || (parsed.protocol != "https" && parsed.protocol != "http")) {
                    targetInput.error = "Enter a valid HTTP/HTTPS URL"
                } else {
                    prefs.edit().putString("tinyfish_api_base", apiInput.text.toString().trim().trimEnd('/')).apply()
                    request("/api/extract", JSONObject().put("url", target).put("max_chars", 12000).put("timeout_ms", 20000))
                }
            }
        }
        root.addView(runButton, params())
        root.addView(TextView(this).apply {
            text = "Result"
            textSize = 16f
            setTextColor(fg)
            setPadding(0, dp(16), 0, dp(8))
        })
        output = TextView(this).apply {
            text = "Not connected. Enter your backend URL to begin."
            textSize = 12f
            setTextColor(fg)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(panel)
            textIsSelectable = true
        }
        val scroll = ScrollView(this).apply { addView(output) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun request(path: String, body: JSONObject?) {
        val base = apiInput.text.toString().trim().trimEnd('/')
        if (!base.startsWith("https://")) {
            apiInput.error = "Backend URL must start with https://"
            return
        }
        runButton.isEnabled = false
        output.text = "Connecting to live backend…"
        executor.execute {
            val result = runCatching {
                val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = if (body == null) "GET" else "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    }
                }
                try {
                    val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: $text")
                    JSONObject(text).toString(2)
                } finally { connection.disconnect() }
            }
            runOnUiThread {
                runButton.isEnabled = true
                output.text = result.getOrElse { "Request failed: ${it.message}\n\nCheck that the backend is deployed and its URL is correct." }
            }
        }
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        setTextColor(fg)
        setHintTextColor(muted)
        textSize = 14f
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(panel)
        singleLine = true
    }

    private fun params() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(10)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
