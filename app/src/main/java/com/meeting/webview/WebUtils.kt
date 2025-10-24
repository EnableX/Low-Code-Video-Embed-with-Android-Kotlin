package com.meeting.webview

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebUtils {

    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebView(webView: WebView?) {
        val webSettings = webView?.settings
        webSettings?.javaScriptEnabled = true
        webSettings?.domStorageEnabled = true
        webSettings?.databaseEnabled = true
        webSettings?.setSupportMultipleWindows(true)
        webSettings?.allowFileAccess = true
        webSettings?.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webSettings?.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings?.javaScriptCanOpenWindowsAutomatically = true
        webSettings?.mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            webView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }
}
