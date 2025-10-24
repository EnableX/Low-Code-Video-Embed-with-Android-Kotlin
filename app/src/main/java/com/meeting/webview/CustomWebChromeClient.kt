package com.meeting.webview

import android.app.Activity
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient

class CustomWebChromeClient(private val activity: Activity) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest) {
        activity.runOnUiThread { request.grant(request.resources) }
    }
}
