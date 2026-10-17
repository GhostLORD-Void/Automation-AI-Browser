package com.automationaibrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class MainActivity : Activity() {
    private data class BrowserTab(
        val webView: WebView,
        var title: String = "New tab",
        var url: String = ""
    )

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0

    private lateinit var addressBar: EditText
    private lateinit var addressBookmarkButton: TextView
    private lateinit var webContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tabCountButton: TextView
    private lateinit var backButton: LinearLayout
    private lateinit var forwardButton: LinearLayout
    private lateinit var reloadButton: LinearLayout
    private lateinit var homeButton: LinearLayout
    private lateinit var bookmarkButton: LinearLayout
    private lateinit var agentButton: TextView
    private var agentEngine: AgentActionEngine? = null

    private val pageBg = Color.rgb(11, 15, 24)
    private val panelBg = Color.rgb(20, 26, 39)
    private val inputBg = Color.rgb(25, 33, 48)
    private val accent = Color.rgb(129, 115, 255)
    private val primaryText = Color.rgb(244, 246, 255)
    private val mutedText = Color.rgb(150, 160, 181)
    private val green = Color.rgb(98, 220, 174)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBg)
            setPadding(dp(14), dp(8), dp(14), dp(6))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        val logo = TextView(this).apply {
            text = "N"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            background = rounded(accent, 16)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        brand.addView(TextView(this).apply {
            text = "NOVA"
            textSize = 18f
            letterSpacing = 0.12f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setTextColor(primaryText)
        })
        brand.addView(TextView(this).apply {
            text = "AUTOMATION BROWSER"
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(mutedText)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, dp(44), 1f))

        val historyButton = TextView(this).apply {
            text = "◷"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            background = rounded(panelBg, 14)
            contentDescription = "Browsing history"
            setOnClickListener { showSavedItems("history") }
        }
        header.addView(historyButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginEnd = dp(8)
        })

        agentButton = TextView(this).apply {
            text = "AI"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accent, 14)
            contentDescription = "Run browser task"
            setOnClickListener { showAgentDialog() }
        }
        header.addView(agentButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            marginEnd = dp(8)
        })

        tabCountButton = TextView(this).apply {
            text = "▣ 1"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            background = rounded(panelBg, 14)
            contentDescription = "Open tabs"
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { showTabsDialog() }
        }
        header.addView(tabCountButton, LinearLayout.LayoutParams(dp(62), dp(42)))
        root.addView(header)

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(inputBg, 17, Color.rgb(42, 52, 72))
            setPadding(dp(10), 0, dp(5), 0)
        }

        val securityIcon = TextView(this).apply {
            text = "⌕"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(green)
        }
        addressRow.addView(securityIcon, LinearLayout.LayoutParams(dp(30), dp(48)))

        addressBar = EditText(this).apply {
            hint = "Search or enter website"
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(primaryText)
            setHintTextColor(mutedText)
            setPadding(dp(4), 0, dp(4), 0)
            setSelectAllOnFocus(false)
            background = null
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        addressRow.addView(addressBar, LinearLayout.LayoutParams(0, dp(48), 1f))

        val goButton = TextView(this).apply {
            text = "↵"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accent, 13)
            contentDescription = "Go"
            setOnClickListener { navigate(addressBar.text.toString()) }
        }
        addressRow.addView(goButton, LinearLayout.LayoutParams(dp(42), dp(40)))

        addressBookmarkButton = TextView(this).apply {
            text = "☆"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(mutedText)
            background = rounded(panelBg, 13)
            contentDescription = "Save or remove bookmark"
            setOnClickListener { toggleBookmark() }
        }
        addressRow.addView(addressBookmarkButton, LinearLayout.LayoutParams(dp(38), dp(40)).apply {
            marginStart = dp(4)
        })
        root.addView(addressRow)

        addressBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(addressBar.text.toString())
                true
            } else {
                false
            }
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(accent)
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        ).apply {
            topMargin = dp(8)
            bottomMargin = dp(4)
        })

        webContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(pageBg, 20)
            clipToOutline = true
        }
        root.addView(webContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply {
            topMargin = dp(5)
            bottomMargin = dp(8)
        })

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(panelBg, 18)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        backButton = toolbarItem("‹", "Back") {
            currentTab()?.webView?.let { if (it.canGoBack()) it.goBack() }
        }
        forwardButton = toolbarItem("›", "Forward") {
            currentTab()?.webView?.let { if (it.canGoForward()) it.goForward() }
        }
        reloadButton = toolbarItem("↻", "Reload") {
            currentTab()?.webView?.reload()
        }
        homeButton = toolbarItem("⌂", "Home") {
            currentTab()?.let { loadHome(it.webView) }
        }
        bookmarkButton = toolbarItem("☆", "Bookmarks") {
            showSavedItems("bookmarks")
        }
        listOf(backButton, forwardButton, reloadButton, homeButton, bookmarkButton).forEach {
            toolbar.addView(it, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(60)
        ))

        setContentView(root)
        agentEngine = AgentActionEngine(
            webView = { currentTab()?.webView },
            onStatus = { _ ->
                runOnUiThread {
                    if (::agentButton.isInitialized) agentButton.text =
                        if (agentEngine?.isRunning() == true) "••" else "AI"
                }
            },
            onFinished = { success, summary ->
                runOnUiThread {
                    if (::agentButton.isInitialized) agentButton.text = "AI"
                    AlertDialog.Builder(this)
                        .setTitle(if (success) "Task verified" else "Task stopped")
                        .setMessage(summary)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        )
        createTab(null, true)
        refreshToolbar()
    }

    private fun showAgentDialog() {
        val prefs = getSharedPreferences("nova_browser", MODE_PRIVATE)
        val taskInput = EditText(this).apply {
            hint = "Example: search for Android WebView security"
            setTextColor(primaryText)
            setHintTextColor(mutedText)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(inputBg, 12)
            minLines = 2
        }
        val endpointInput = EditText(this).apply {
            hint = "Optional HTTPS AI planner endpoint"
            setText(prefs.getString("agent_planner_endpoint", "").orEmpty())
            setTextColor(primaryText)
            setHintTextColor(mutedText)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(inputBg, 12)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(taskInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(TextView(this@MainActivity).apply {
                text = "Task instructions"
                textSize = 11f
                setTextColor(mutedText)
                setPadding(dp(2), dp(8), dp(2), dp(4))
            })
            addView(endpointInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(TextView(this@MainActivity).apply {
                text = "Leave endpoint blank for local URL/search mode. Add an HTTPS planner-proxy URL for AI-generated multi-step plans. Keep model API keys on the server, not in this app."
                textSize = 12f
                setTextColor(mutedText)
                setPadding(dp(2), dp(10), dp(2), dp(4))
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Run browser task")
            .setView(panel)
            .setPositiveButton("Run", null)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Stop", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val task = taskInput.text.toString().trim()
                        val endpoint = endpointInput.text.toString().trim()
                        if (task.isBlank()) {
                            taskInput.error = "Enter a task"
                            return@setOnClickListener
                        }
                        if (endpoint.isNotBlank() && !endpoint.startsWith("https://", true)) {
                            endpointInput.error = "Planner endpoint must use HTTPS"
                            return@setOnClickListener
                        }
                        prefs.edit().putString("agent_planner_endpoint", endpoint).apply()
                        dialog.dismiss()
                        if (endpoint.isBlank()) {
                            val plan = runCatching { AgentPlanner.localPlan(task) }.getOrElse {
                                Toast.makeText(this, it.message ?: "Could not plan task", Toast.LENGTH_LONG).show()
                                return@setOnClickListener
                            }
                            Toast.makeText(this, "Running local task plan", Toast.LENGTH_SHORT).show()
                            agentEngine?.execute(plan)
                        } else {
                            agentButton.text = "…"
                            Toast.makeText(this, "Asking planner to create a task plan…", Toast.LENGTH_SHORT).show()
                            val tab = currentTab()
                            AgentPlanner.requestPlan(
                                endpoint = endpoint,
                                task = task,
                                currentUrl = tab?.url.orEmpty(),
                                pageTitle = tab?.title.orEmpty()
                            ) { result ->
                                runOnUiThread {
                                    agentButton.text = "AI"
                                    result.onSuccess { plan ->
                                        AlertDialog.Builder(this)
                                            .setTitle("Review AI plan")
                                            .setMessage(buildString {
                                                val steps = plan.optJSONArray("steps")
                                                if (steps == null || steps.length() == 0) {
                                                    append("No steps returned")
                                                } else {
                                                    for (i in 0 until steps.length()) {
                                                        val step = steps.optJSONObject(i) ?: continue
                                                        val action = step.optString("action", "unknown action")
                                                        append("${i + 1}. $action")
                                                        when (action) {
                                                            "navigate" -> append("\\n   URL: ${step.optString("url").take(180)}")
                                                            "search" -> append("\\n   Query: ${step.optString("query").take(180)}")
                                                            "click", "scroll", "extract", "wait_for", "press_enter", "submit" ->
                                                                append("\\n   Target: ${step.optString("selector").take(140)}")
                                                            "type" -> {
                                                                append("\\n   Target: ${step.optString("selector").take(140)}")
                                                                append("\\n   Text: ${step.optString("text").take(140)}")
                                                            }
                                                            "wait" -> append("\\n   Delay: ${step.optLong("milliseconds", 500L)} ms")
                                                            else -> Unit
                                                        }
                                                        if (action == "wait_for") {
                                                            append("\\n   Timeout: ${step.optLong("timeoutMs", 10_000L)} ms")
                                                        }
                                                        append("\\n\\n")
                                                    }
                                                }
                                                val verification = plan.optJSONObject("verification")
                                                if (verification != null) {
                                                    append("Final verification")
                                                    append("\\n   Element: ${verification.optString("selector").take(140)}")
                                                    listOf("textContains", "urlContains", "titleContains").forEach { field ->
                                                        val expected = verification.optString(field)
                                                        if (expected.isNotBlank()) append("\\n   $field: ${expected.take(140)}")
                                                    }
                                                }
                                            })
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton("Execute") { _, _ -> agentEngine?.execute(plan) }
                                            .show()
                                    }.onFailure { error ->
                                        AlertDialog.Builder(this)
                                            .setTitle("Planner error")
                                            .setMessage(error.message ?: "Could not create a plan")
                                            .setPositiveButton("OK", null)
                                            .show()
                                    }
                                }
                            }
                        }
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        agentEngine?.cancel()
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(this).apply {
            setBackgroundColor(pageBg)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return true
                    return when {
                        url.startsWith("https://", true) ||
                            url.startsWith("http://", true) -> false
                        url.startsWith("mailto:", true) || url.startsWith("tel:", true) -> {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (_: Exception) {
                                Toast.makeText(this@MainActivity, "No app can open this link", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        else -> true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val tab = tabs.firstOrNull { it.webView === view } ?: return
                    if (!url.isNullOrBlank() && !url.startsWith("https://nova.local/")) {
                        tab.url = url
                    } else {
                        tab.url = ""
                    }
                    if (tab === currentTab()) {
                        if (tab.url.isNotBlank()) addressBar.setText(tab.url) else addressBar.setText("")
                    }
                    refreshToolbar()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val tab = tabs.firstOrNull { it.webView === view } ?: return
                    if (url?.startsWith("https://nova.local/") == true) {
                        tab.url = ""
                        tab.title = "New tab"
                        if (tab === currentTab()) addressBar.setText("")
                    } else if (!url.isNullOrBlank()) {
                        tab.url = url
                        if (tab.title.isBlank() || tab.title == "New tab") tab.title = url
                        saveHistory(tab.title, url)
                        if (tab === currentTab()) addressBar.setText(url)
                    }
                    refreshToolbar()
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    handler?.cancel()
                    Toast.makeText(this@MainActivity, "Secure connection failed. Page blocked.", Toast.LENGTH_LONG).show()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (view === currentTab()?.webView) {
                        progressBar.progress = newProgress
                        progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    val tab = tabs.firstOrNull { it.webView === view } ?: return
                    if (!title.isNullOrBlank() && !title.contains("nova.local")) tab.title = title
                    refreshToolbar()
                }
            }
        }
    }

    private fun createTab(url: String?, activate: Boolean) {
        val tab = BrowserTab(createWebView())
        tabs.add(tab)
        if (activate || tabs.size == 1) {
            activeTabIndex = tabs.lastIndex
            showActiveTab()
        }
        if (url.isNullOrBlank()) loadHome(tab.webView) else tab.webView.loadUrl(url)
        updateTabCount()
    }

    private fun showActiveTab() {
        val tab = currentTab() ?: return
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        webContainer.removeAllViews()
        webContainer.addView(tab.webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        addressBar.setText(tab.url)
        updateTabCount()
        refreshToolbar()
    }

    private fun currentTab(): BrowserTab? = tabs.getOrNull(activeTabIndex)

    private fun loadHome(webView: WebView) {
        val tab = tabs.firstOrNull { it.webView === webView }
        if (tab != null) {
            tab.url = ""
            tab.title = "New tab"
        }
        webView.loadDataWithBaseURL(
            "https://nova.local/",
            HOME_HTML,
            "text/html",
            "UTF-8",
            null
        )
        if (tab === currentTab()) addressBar.setText("")
        refreshToolbar()
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) {
            addressBar.requestFocus()
            return
        }
        val destination = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.contains("://") -> {
                Toast.makeText(this, "Only web links (HTTP/HTTPS) are supported", Toast.LENGTH_SHORT).show()
                return
            }
            value.startsWith("localhost") || value.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")) ->
                "http://" + value
            value.contains(" ") || !value.contains(".") ->
                "https://duckduckgo.com/?q=" + URLEncoder.encode(value, "UTF-8")
            else -> "https://" + value
        }
        currentTab()?.webView?.loadUrl(destination)
        addressBar.clearFocus()
    }

    private fun toolbarItem(symbol: String, label: String, action: () -> Unit): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(panelBg, 13)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            contentDescription = label
        }
        val symbolView = TextView(this).apply {
            text = symbol
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(if (label == "Save") accent else primaryText)
            if (label == "Save") tag = "bookmark-symbol"
        }
        item.addView(symbolView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(29)
        ))
        item.addView(TextView(this).apply {
            text = label
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(mutedText)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(15)
        ))
        return item
    }

    private fun refreshToolbar() {
        val tab = currentTab() ?: return
        backButton.alpha = if (tab.webView.canGoBack()) 1f else 0.4f
        forwardButton.alpha = if (tab.webView.canGoForward()) 1f else 0.4f
        val isSaved = records("bookmarks").any { it.second == tab.url && tab.url.isNotBlank() }
        addressBookmarkButton.text = if (isSaved) "★" else "☆"
        addressBookmarkButton.setTextColor(if (isSaved) accent else mutedText)
        updateTabCount()
    }

    private fun updateTabCount() {
        if (::tabCountButton.isInitialized) tabCountButton.text = "▣ " + tabs.size.toString()
    }

    private fun toggleBookmark() {
        val tab = currentTab() ?: return
        if (tab.url.isBlank()) {
            Toast.makeText(this, "Open a website before saving a bookmark", Toast.LENGTH_SHORT).show()
            return
        }
        val items = records("bookmarks")
        val existing = items.indexOfFirst { it.second == tab.url }
        if (existing >= 0) {
            items.removeAt(existing)
            writeRecords("bookmarks", items)
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
        } else {
            items.add(0, (tab.title.ifBlank { tab.url }) to tab.url)
            writeRecords("bookmarks", items)
            Toast.makeText(this, "Bookmark saved on this device", Toast.LENGTH_SHORT).show()
        }
        refreshToolbar()
    }

    private fun saveHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("https://nova.local/")) return
        val items = records("history")
        items.removeAll { it.second == url }
        items.add(0, (title.ifBlank { url }) to url)
        writeRecords("history", items.take(100))
    }

    private fun records(key: String): MutableList<Pair<String, String>> {
        val raw = getSharedPreferences("nova_browser", MODE_PRIVATE).getString(key, "[]") ?: "[]"
        val result = mutableListOf<Pair<String, String>>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val url = item.optString("url")
                if (url.isNotBlank()) result.add(title to url)
            }
        } catch (_: Exception) {
            // Ignore malformed local history data and start with an empty list.
        }
        return result
    }

    private fun writeRecords(key: String, items: List<Pair<String, String>>) {
        val array = JSONArray()
        items.forEach { (title, url) ->
            array.put(JSONObject().put("title", title).put("url", url))
        }
        getSharedPreferences("nova_browser", MODE_PRIVATE).edit().putString(key, array.toString()).apply()
    }

    private fun showSavedItems(key: String) {
        val items = records(key)
        val label = if (key == "history") "Recent pages" else "Bookmarks"
        if (items.isEmpty()) {
            Toast.makeText(this, if (key == "history") "No browsing history yet" else "No bookmarks saved yet", Toast.LENGTH_SHORT).show()
            return
        }
        val rows = items.map { pair ->
            val title = pair.first.ifBlank { pair.second }
            title + "\n" + pair.second
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(rows) { _, which ->
                currentTab()?.webView?.loadUrl(items[which].second)
            }
            .setNeutralButton("Clear") { _, _ ->
                writeRecords(key, emptyList())
                Toast.makeText(this, "$label cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showTabsDialog() {
        if (tabs.isEmpty()) {
            createTab(null, true)
            return
        }
        val labels = tabs.mapIndexed { index, tab ->
            (if (index == activeTabIndex) "✓ " else "") +
                (index + 1).toString() + ". " + tab.title.ifBlank { "New tab" }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Open tabs · " + tabs.size)
            .setItems(labels) { _, which ->
                activeTabIndex = which
                showActiveTab()
            }
            .setPositiveButton("New tab") { _, _ -> createTab(null, true) }
            .setNegativeButton("Close current") { _, _ -> closeCurrentTab() }
            .show()
    }

    private fun closeCurrentTab() {
        val tab = currentTab() ?: return
        if (tabs.size == 1) {
            loadHome(tab.webView)
            return
        }
        webContainer.removeView(tab.webView)
        tabs.removeAt(activeTabIndex)
        tab.webView.stopLoading()
        tab.webView.loadUrl("about:blank")
        tab.webView.clearHistory()
        tab.webView.removeAllViews()
        tab.webView.destroy()
        if (activeTabIndex >= tabs.size) activeTabIndex = tabs.lastIndex
        showActiveTab()
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val webView = currentTab()?.webView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else if (tabs.size > 1) {
            closeCurrentTab()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
            tab.webView.stopLoading()
            tab.webView.removeAllViews()
            tab.webView.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }

    private companion object {
        val HOME_HTML = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <meta name="color-scheme" content="dark">
              <title>Nova Browser</title>
              <style>
                :root { color-scheme: dark; }
                * { box-sizing: border-box; }
                body { margin: 0; background: #0b0f18; color: #f4f6ff; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                .wrap { width: min(100%, 880px); margin: 0 auto; padding: 30px 22px 38px; }
                .eyebrow { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid #293348; border-radius: 999px; color: #b5bcce; font-size: 11px; letter-spacing: 1.1px; }
                .dot { width: 7px; height: 7px; background: #62dcae; display: inline-block; border-radius: 50%; box-shadow: 0 0 12px #62dcae66; }
                h1 { font-size: clamp(33px, 8vw, 57px); line-height: 1.04; letter-spacing: -2px; margin: 24px 0 13px; font-weight: 750; }
                h1 span { color: #968aff; }
                .intro { max-width: 490px; color: #9aa5bc; font-size: 15px; line-height: 1.7; margin: 0 0 32px; }
                .section-label { font-size: 11px; text-transform: uppercase; letter-spacing: 1.5px; color: #7e8aa5; margin: 0 0 14px; }
                .grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; }
                .card { text-decoration: none; color: inherit; display: flex; align-items: center; gap: 13px; padding: 15px; min-height: 78px; background: #141a27; border: 1px solid #242e42; border-radius: 18px; transition: border-color .15s, transform .15s; }
                .card:active { border-color: #8173ff; transform: scale(.985); }
                .symbol { width: 39px; height: 39px; flex: 0 0 39px; display: grid; place-items: center; border-radius: 13px; font-size: 19px; font-weight: 700; background: #262445; color: #b5aaff; }
                .info { min-width: 0; flex: 1; }
                .card:nth-child(2) .symbol { background: #3a2028; color: #ff9aab; }
                .card:nth-child(3) .symbol { background: #18352f; color: #86e9c6; }
                .card:nth-child(4) .symbol { background: #233047; color: #9bc4ff; }
                .card:nth-child(5) .symbol { background: #362d1d; color: #f1cb87; }
                .name { font-weight: 650; font-size: 14px; }
                .desc { color: #8591aa; font-size: 11px; margin-top: 4px; }
                .arrow { color: #6f7c96; margin-left: auto; font-size: 18px; }
                .foot { border-top: 1px solid #20283a; margin-top: 30px; padding-top: 18px; color: #7f8aa2; font-size: 11px; line-height: 1.6; }
                @media(min-width:600px) { .grid { grid-template-columns: repeat(3, minmax(0,1fr)); } .wrap { padding-top: 45px; } }
              </style>
            </head>
            <body>
              <main class="wrap">
                <div class="eyebrow"><span class="dot"></span> YOUR SPACE ON THE WEB</div>
                <h1>Browse smarter.<br><span>Stay in control.</span></h1>
                <p class="intro">A focused browser built for your everyday web. Search, explore, and keep the pages you need close at hand.</p>
                <p class="section-label">Quick launch</p>
                <div class="grid">
                  <a class="card" href="https://duckduckgo.com/"><span class="symbol">⌕</span><div class="info"><div class="name">Search</div><div class="desc">Find it on the web</div></div><span class="arrow">↗</span></a>
                  <a class="card" href="https://www.youtube.com/"><span class="symbol">▶</span><div class="info"><div class="name">YouTube</div><div class="desc">Videos and creators</div></div><span class="arrow">↗</span></a>
                  <a class="card" href="https://www.wikipedia.org/"><span class="symbol">W</span><div class="info"><div class="name">Wikipedia</div><div class="desc">Knowledge library</div></div><span class="arrow">↗</span></a>
                  <a class="card" href="https://github.com/"><span class="symbol">⌘</span><div class="info"><div class="name">GitHub</div><div class="desc">Build something great</div></div><span class="arrow">↗</span></a>
                  <a class="card" href="https://news.ycombinator.com/"><span class="symbol">Y</span><div class="info"><div class="name">Tech news</div><div class="desc">Ideas from the community</div></div><span class="arrow">↗</span></a>
                  <a class="card" href="https://www.google.com/"><span class="symbol">G</span><div class="info"><div class="name">Google</div><div class="desc">Classic web search</div></div><span class="arrow">↗</span></a>
                </div>
                <div class="foot">Your bookmarks and recent pages are saved locally on this device. AI-powered features will be added when a secure AI service is configured.</div>
              </main>
            </body>
            </html>
        """.trimIndent()
    }
}
