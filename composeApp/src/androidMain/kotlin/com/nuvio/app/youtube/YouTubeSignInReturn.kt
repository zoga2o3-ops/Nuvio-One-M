package com.nuvio.app.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens youtube.com/activate in a browser, and brings the app back to the front once YouTube
 * reports that the code was approved, so Google's "Device connected" page isn't left on screen.
 */
internal object YouTubeSignInReturn {
    private const val TAG = "YouTubeSignInReturn"

    /** Browsers to use when the system can't name one that supports Custom Tabs. */
    private val BROWSERS = listOf(
        "com.android.chrome",
        "com.google.android.browser",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
    )

    /** True while an activate page opened from here may still be on screen. */
    @Volatile
    private var tabOpen = false

    @Volatile
    private var appContext: Context? = null

    /** Opens [link] in a browser, never in the YouTube app, so it is the web sign-in page. */
    fun openActivatePage(context: Context, link: String) {
        appContext = context.applicationContext
        YouTubeSignInKeepAliveService.start(context.applicationContext)
        val uri = Uri.parse(link)
        val browser = browserPackage(context)
        Log.i(TAG, "opening the activate page in ${browser ?: "a browser"}")
        val opened = runCatching {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (browser != null) tab.intent.setPackage(browser)
            tab.launchUrl(context, uri)
        }.onFailure { Log.w(TAG, "custom tab unavailable", it) }.isSuccess
        tabOpen = opened
        if (!opened) openInBrowser(context, uri, browser)
    }

    private fun openInBrowser(context: Context, uri: Uri, browser: String?) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (browser != null) intent.setPackage(browser)
            context.startActivity(intent)
        }.onFailure { Log.w(TAG, "couldn't open the activate page", it) }
    }

    /**
     * A browser that supports Custom Tabs. The YouTube app also accepts youtube.com links, so it
     * must be left out or "sign in on the web" would open the app instead.
     */
    private fun browserPackage(context: Context): String? {
        val candidates = runCatching {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE),
                0,
            ).map { it.activityInfo.packageName }
                .distinct()
                .filter { it != "com.google.android.youtube" }
        }.getOrDefault(emptyList())
        return runCatching { CustomTabsClient.getPackageName(context, candidates) }.getOrNull()
            ?: BROWSERS.firstOrNull { packageName ->
                runCatching {
                    context.packageManager.getPackageInfo(packageName, 0)
                    true
                }.getOrDefault(false)
            }
    }

    /** Brings the app's own screen back over the browser tab, but only when this sign-in opened one. */
    fun backToApp(context: Context) {
        YouTubeSignInKeepAliveService.stop(context)
        if (!tabOpen) return
        tabOpen = false
        runCatching {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(launch)
        }.onFailure { Log.w(TAG, "couldn't return to the app", it) }
    }

    /** The sign-in was cancelled, so there is nothing to return from. */
    fun cancelled() {
        tabOpen = false
        appContext?.let(YouTubeSignInKeepAliveService::stop)
    }
}
