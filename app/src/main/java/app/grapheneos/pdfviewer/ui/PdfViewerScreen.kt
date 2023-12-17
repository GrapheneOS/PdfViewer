package app.grapheneos.pdfviewer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.grapheneos.pdfviewer.BuildConfig
import app.grapheneos.pdfviewer.GestureHelper
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import java.io.IOException

private const val TAG = "PdfViewer"

private const val CONTENT_SECURITY_POLICY =
    "default-src 'none'; " +
            "form-action 'none'; " +
            "connect-src https://localhost/placeholder.pdf; " +
            "img-src blob: 'self'; " +
            "script-src 'self'; " +
            "style-src 'self'; " +
            "frame-ancestors 'none'; " +
            "base-uri 'none'"

private const val PERMISSIONS_POLICY =
    "accelerometer=(), " +
            "ambient-light-sensor=(), " +
            "autoplay=(), " +
            "battery=(), " +
            "camera=(), " +
            "clipboard-read=(), " +
            "clipboard-write=(), " +
            "display-capture=(), " +
            "document-domain=(), " +
            "encrypted-media=(), " +
            "fullscreen=(), " +
            "gamepad=(), " +
            "geolocation=(), " +
            "gyroscope=(), " +
            "hid=(), " +
            "idle-detection=(), " +
            "interest-cohort=(), " +
            "magnetometer=(), " +
            "microphone=(), " +
            "midi=(), " +
            "payment=(), " +
            "picture-in-picture=(), " +
            "publickey-credentials-get=(), " +
            "screen-wake-lock=(), " +
            "serial=(), " +
            "speaker-selection=(), " +
            "sync-xhr=(), " +
            "usb=(), " +
            "xr-spatial-tracking=()"

const val MIN_ZOOM_RATIO = 0.2f
const val MAX_ZOOM_RATIO = 1.5f
private const val STATE_LOADED = 1

const val mimeTypePdf = "application/pdf"

/**
 * Composable for viewing a PDF file.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PdfViewerScreen(
    pdfViewModel: PdfViewModel,
    pdfUiState: PdfViewModel.PdfUiState,
    snackbarHostState: SnackbarHostState,
    navigateUp: () -> Unit,
    onGestureHelperTapUp: () -> Unit,
    onShowPasswordPrompt: () -> Unit,
    onInvalidPassword: () -> Unit,
    onOnLoaded: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    /** This is needed in the event that the FileViewModel or FileUiState is destroyed or cleared so that it
     * automatically goes to the last screen or start screen instead of viewing a blank "read only" non-existent "file"
     */
    LaunchedEffect(pdfUiState.uri) {
        if (pdfUiState.uri == Uri.EMPTY) {
            navigateUp()
        }
    }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        pdfViewModel.loadPdf(context, snackbarHostState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                pdfViewModel.setWebView(WebView(context))
                pdfUiState.webView.value?.apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    setBackgroundColor(colorScheme.background.toArgb()) // set WebView background color to current colorScheme's background color.

                    if (BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    settings.allowContentAccess = false
                    settings.allowFileAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.javaScriptEnabled = true
                    settings.minimumFontSize = 1

                    CookieManager.getInstance().setAcceptCookie(false)

                    addJavascriptInterface(
                        Channel(
                            context,
                            context.resources,
                            pdfViewModel,
                            pdfUiState,
                            onShowPasswordPrompt,
                            onInvalidPassword,
                            onOnLoaded,
                        ),
                        "channel"
                    )

                    webViewClient = object : WebViewClient() {
                        private fun fromAsset(mime: String, path: String): WebResourceResponse? {
                            try {
                                val inputStream = context.assets.open(path.substring(1))
                                return WebResourceResponse(mime, null, inputStream)
                            } catch (e: IOException) {
                                return null
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            if (request != null) {
                                if ("GET" != request.method) {
                                    return null
                                }

                                val url = request.url
                                if ("localhost" != url.host) {
                                    return null
                                }

                                val path = url.path
                                Log.d(TAG, "path $path")

                                if ("/placeholder.pdf" == path) {
                                    pdfViewModel.maybeCloseInputStream()
                                    pdfViewModel.openInputStream(context.contentResolver)

                                    return WebResourceResponse("application/pdf", null, pdfUiState.inputStream.value)
                                }

                                if ("/viewer/index.html" == path) {
                                    val response = fromAsset("text/html", path)
                                    val headers = HashMap<String, String>()
                                    headers["Content-Security-Policy"] = CONTENT_SECURITY_POLICY
                                    headers["Permissions-Policy"] = PERMISSIONS_POLICY
                                    headers["X-Content-Type-Options"] = "nosniff"
                                    if (response != null) {
                                        response.responseHeaders = headers
                                    }
                                    return response
                                }

                                if ("/viewer/main.css" == path) {
                                    return fromAsset("text/css", path)
                                }

                                if (("/viewer/js/index.js" == path) || ("/viewer/js/worker.js" == path)) {
                                    return fromAsset("application/javascript", path)
                                }

                                return null
                            } else {
                                return null
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            pdfViewModel.setDocumentState(STATE_LOADED)
                            pdfViewModel.setEncryptedDocumentPassword(pdfUiState.encryptedDocumentPassword)
                            pdfViewModel.loadPdfWithPassword()
                        }
                    }

                    GestureHelper.attach(context, pdfUiState.webView.value!!, object : GestureHelper.GestureListener {
                        override fun onTapUp(): Boolean {
                            onGestureHelperTapUp()
                            return true
                        }

                        override fun onZoomIn(value: Float) {
                            pdfViewModel.zoomIn(value, false)
                        }

                        override fun onZoomOut(value: Float) {
                            pdfViewModel.zoomOut(value, false)
                        }

                        override fun onZoomEnd() {
                            pdfViewModel.zoomEnd()
                        }
                    })
                }!!
            },
        )
    }
}

class Channel(
    private val context: Context,
    private val resources: Resources,
    private val pdfViewModel: PdfViewModel,
    private val pdfUiState: PdfViewModel.PdfUiState,
    private val onShowPasswordPrompt: () -> Unit,
    private val onInvalidPassword: () -> Unit,
    private val onOnLoaded: () -> Unit,
) {
    @JavascriptInterface
    fun getPage(): Int {
        return pdfUiState.page
    }

    @JavascriptInterface
    fun getZoomRatio(): Float {
        return pdfUiState.zoomRatio
    }

    @JavascriptInterface
    fun setZoomRatio(ratio: Float) {
        println(ratio)
        pdfViewModel.setZoomRatio(ratio)
    }

    @JavascriptInterface
    fun getMinZoomRatio(): Float {
        return MIN_ZOOM_RATIO
    }

    @JavascriptInterface
    fun getMaxZoomRatio(): Float {
        return MAX_ZOOM_RATIO
    }

    @JavascriptInterface
    fun getDocumentOrientationDegrees(): Int {
        return pdfUiState.documentOrientationDegrees
    }

    @JavascriptInterface
    fun setNumPages(numPages: Int) {
        pdfViewModel.setNumPages(numPages)
    }

    @JavascriptInterface
    fun setDocumentProperties(properties: String) {
        pdfViewModel.setDocumentProperties(
            properties,
            resources.getString(R.string.document_properties_invalid_date),
            context,
        )
        pdfViewModel.setDocumentName()
    }

    @JavascriptInterface
    fun showPasswordPrompt() {
        onShowPasswordPrompt()
    }

    @JavascriptInterface
    fun invalidPassword() {
        onInvalidPassword()
    }

    @JavascriptInterface
    fun onLoaded() {
        onOnLoaded()
    }

    @JavascriptInterface
    fun getPassword(): String {
        return pdfUiState.encryptedDocumentPassword
    }
}
