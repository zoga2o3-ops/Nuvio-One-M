package com.nuvio.app.music

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nuvio.app.music.spotifyweb.SpotifyWebClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spotify's real sign-in page, so the same `sp_dc` session the desktop player uses can be read
 * without typing a password into Nuvio. The page cannot read its own HttpOnly cookie; the app's
 * CookieManager can, and the cookie is checked against Spotify before it is saved.
 */
class SpotifyLoginActivity : Activity() {
    companion object {
        private const val LOGIN_URL =
            "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
        private const val POLL_MS = 1_500L
        private const val TIMEOUT_MS = 5 * 60 * 1000L
    }

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastTriedCookie = ""
    private var validating = false
    private var finished = false

    private val poll = object : Runnable {
        override fun run() {
            if (finished) return
            checkCookie()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val timeout = Runnable {
        if (finished) return@Runnable
        status.text = "Spotify sign-in timed out. Close this window and try again."
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicSpotifyWebSession.init(this)
        CookieManager.getInstance().setAcceptCookie(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        status = TextView(this).apply {
            text = "Sign in to Spotify. This window closes by itself when you're signed in."
            setPadding(32, 32, 32, 24)
            textSize = 15f
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = MOBILE_USER_AGENT
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    handler.removeCallbacks(poll)
                    handler.post(poll)
                }
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        val cancel = Button(this).apply {
            text = "Cancel"
            setOnClickListener { cancelAndFinish() }
        }
        root.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(
            cancel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        setContentView(FrameLayout(this).also { it.addView(root) })

        webView.loadUrl(LOGIN_URL)
        handler.postDelayed(timeout, TIMEOUT_MS)
        handler.postDelayed(poll, POLL_MS)
    }

    private fun checkCookie() {
        if (validating || finished) return
        val header = CookieManager.getInstance().getCookie("https://open.spotify.com").orEmpty()
        val cookie = header.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("sp_dc=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
            ?: return
        if (cookie == lastTriedCookie) return
        lastTriedCookie = cookie
        validating = true
        status.text = "Checking the Spotify session."
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    SpotifyWebClient.connect(cookie, requireSignedIn = true).getOrThrow()
                }
            }
            val client = result.getOrNull()
            if (client?.isSignedIn == true) {
                val account = runCatching {
                    withContext(Dispatchers.IO) { client.account().getOrNull() }
                }.getOrNull()
                MusicSpotifyWebSession.save(this@SpotifyLoginActivity, cookie, account?.name)
                finished = true
                handler.removeCallbacksAndMessages(null)
                setResult(RESULT_OK, Intent().putExtra("spotify_account", account?.name))
                finish()
            } else {
                val error = result.exceptionOrNull()?.message
                status.text = if (error.isNullOrBlank()) {
                    "Waiting for Spotify sign-in."
                } else {
                    "Spotify check failed: $error"
                }
                validating = false
            }
        }
    }

    private fun cancelAndFinish() {
        finished = true
        handler.removeCallbacksAndMessages(null)
        setResult(RESULT_CANCELED)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else cancelAndFinish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
