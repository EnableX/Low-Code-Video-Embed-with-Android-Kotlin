package com.meeting.webview

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date

class MeetingActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var webSettings: WebSettings? = null
    private var mUploadMessage: ValueCallback<Uri>? = null
    private var mCapturedImageURI: Uri? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String? = null
    var roomUrl = "" // Replace by your own
    private val roomParameters = "?skipMediaPermissionPrompt"

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.RECORD_AUDIO,

        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private fun loadLowCodeEmbedRoomUrl() {
        this.webView?.loadUrl(roomUrl + roomParameters)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            var results: Array<Uri>? = null
            if (resultCode == Activity.RESULT_OK) {
                if (data == null) {
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                } else {
                    val dataString = data.dataString
                    if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }
            mFilePathCallback?.onReceiveValue(results)
            mFilePathCallback = null
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (requestCode != FILECHOOSER_RESULTCODE || mUploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data)
                return
            }
            if (requestCode == FILECHOOSER_RESULTCODE) {
                if (null == this.mUploadMessage) {
                    return
                }
                var result: Uri? = null
                try {
                    if (resultCode != RESULT_OK) {
                        result = null
                    } else {
                        result = data?.data ?: mCapturedImageURI
                    }
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "activity :$e",
                        Toast.LENGTH_LONG).show()
                }
                mUploadMessage?.onReceiveValue(result)
                mUploadMessage = null
            }
        }
        return
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.meeting_webview)
        webView = findViewById(R.id.webView)
        WebUtils.configureWebView(this.webView)
        webView?.setWebViewClient(Client())
        webView?.setWebChromeClient(ChromeClient())
        webView?.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (url.startsWith("data:")) {  
                val path = createAndSaveFileFromBase64Url(url)
                StrictMode.VmPolicy.Builder().build().also { StrictMode.setVmPolicy(it) }
                return@setDownloadListener
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    fun createAndSaveFileFromBase64Url(url: String): String {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val filetype = url.substring(url.indexOf("/") + 1, url.indexOf(";"))
        val filename = System.currentTimeMillis().toString() + "." + filetype
        val file = File(path, filename)

        try {
            if (!path.exists())
                path.mkdirs()
            if (!file.exists())
                file.createNewFile()

            val base64EncodedString = url.substring(url.indexOf(",") + 1)
            val decodedBytes = Base64.decode(base64EncodedString, Base64.DEFAULT)
            val os: OutputStream = FileOutputStream(file)
            os.write(decodedBytes)
            os.close()

        } catch (e: IOException) {
            Log.w("ExternalStorage", "Error writing $file", e)
            Toast.makeText(applicationContext, "", Toast.LENGTH_LONG).show()
        }

        return file.toString()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,  
            ".jpg",  
            storageDir  
        )
    }

    inner class ChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread { request.grant(request.resources) }
        }

        override fun onShowFileChooser(view: WebView, filePath: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams): Boolean {
            if (mFilePathCallback != null) {
                mFilePathCallback?.onReceiveValue(null)
            }
            mFilePathCallback = filePath
            var takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e(TAG, "Unable to create Image File", ex)
                    null
                }
                if (photoFile != null) {
                    mCameraPhotoPath = "file:" + photoFile.absolutePath
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
                }
            }
            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
            contentSelectionIntent.type = "image/*"
            val intentArray: Array<Intent?> = if (takePictureIntent != null) arrayOf(takePictureIntent) else arrayOfNulls(0)
            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE)
            return true
        }
    }

    inner class Client : WebViewClient() {
        var progressDialog: ProgressDialog? = null

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return if (url.contains("mailto:")) {
                view.context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            } else {
                view.loadUrl(url)
                true
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (this@MeetingActivity.isFinishing || this@MeetingActivity.isDestroyed) return

            if (progressDialog == null) {
                progressDialog = ProgressDialog(this@MeetingActivity).apply {
                    setMessage("Loading...")
                    show()
                }
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (progressDialog?.isShowing == true) {
                progressDialog?.dismiss()
                progressDialog = null
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if ((keyCode == KeyEvent.KEYCODE_BACK) && webView!!.canGoBack()) {
            webView!!.goBack()
            true
        } else super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        if (this.webView?.url == null) {
            loadPage()
        }
    }

    private fun loadPage() {
        if (isPendingPermissions()) {
            requestCameraAndAudioPermissions()
        } else {
            loadLowCodeEmbedRoomUrl()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (!grantResultsContainsDenials(grantResults)) {
                    loadLowCodeEmbedRoomUrl()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun requestCameraAndAudioPermissions() {
        val pendingPermissions = getPendingPermissions()
        if (pendingPermissions.isNotEmpty()) {
            requestPermissions(pendingPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun getPendingPermissions(): Array<String> {
        val pendingPermissions = mutableListOf<String>()
        for (permission in requiredPermissions) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                pendingPermissions.add(permission)
            }
        }
        return pendingPermissions.toTypedArray()
    }

    private fun grantResultsContainsDenials(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                return true
            }
        }
        return false
    }

    private fun isPendingPermissions(): Boolean {
        for (permission in requiredPermissions) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val INPUT_FILE_REQUEST_CODE = 1
        private const val FILECHOOSER_RESULTCODE = 1
        private val TAG = MeetingActivity::class.java.simpleName
        private const val PERMISSION_REQUEST_CODE = 1
    }
}
