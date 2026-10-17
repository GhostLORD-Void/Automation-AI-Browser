package com.automationaibrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 18, 28))
            setPadding(dp(10), dp(10), dp(10), dp(6))
        }

        val title = TextView(this).apply {
            text = "Automation AI Browser"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        root.addView(title)

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        addressBar = EditText(this).apply {
            hint = "Enter URL or search"
            singleLine = true
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), 0, dp(8), 0)
            imeOptions = EditorInfo.IME_ACTION_GO
            setBackgroundColor(Color.rgb(36, 41, 56))
        }
        addressRow.addView(
            addressBar,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        val goButton = Button(this).apply {
            text = "Go"
            setOnClickListener { navigate(addressBar.text.toString()) }
        }
        addressRow.addView(goButton, LinearLayout.LayoutParams(dp(72), dp(48)))
        root.addView(addressRow)

        val aiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val summarizeButton = Button(this).apply {
            text = "AI Summary"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "AI summaries will be added after the secure AI backend is connected.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val automateButton = Button(this).apply {
            text = "Automate"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "Automation will ask for your approval before taking actions.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        aiRow.addView(summarizeButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        aiRow.addView(automateButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(aiRow)

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!url.isNullOrBlank()) addressBar.setText(url)
                }
            }
            webChromeClient = WebChromeClient()
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        addressBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                navigate(addressBar.text.toString())
                true
            } else {
                false
            }
        }

        setContentView(root)
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            navigate("https://www.google.com")
        }
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return

        val destination = when {
            value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true) -> value
            value.contains(" ") || !value.contains(".") ->
                "https://www.google.com/search?q=" +
                    java.net.URLEncoder.encode(value, "UTF-8")
            else -> "https://$value"
        }
        webView.loadUrl(destination)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
