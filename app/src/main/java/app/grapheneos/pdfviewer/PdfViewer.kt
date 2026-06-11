package app.grapheneos.pdfviewer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentTransaction
import app.grapheneos.pdfviewer.databinding.PdfviewerBinding
import app.grapheneos.pdfviewer.fragment.DocumentPropertiesFragment
import app.grapheneos.pdfviewer.fragment.JumpToPageFragment
import app.grapheneos.pdfviewer.fragment.PasswordPromptFragment
import app.grapheneos.pdfviewer.ktx.hideSystemUi
import app.grapheneos.pdfviewer.ktx.showSystemUi
import app.grapheneos.pdfviewer.outline.OutlineFragment
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.snackbar.Snackbar
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class PdfViewer : AppCompatActivity() {

    companion object {
        private const val TAG = "PdfViewer"
        private const val MIN_WEBVIEW_RELEASE = 133
        private const val PDF_MIME = "application/pdf"

        private const val CONTENT_SECURITY_POLICY =
            "default-src 'none'; " +
                    "form-action 'none'; " +
                    "connect-src 'self'; " +
                    "img-src blob: 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self'; " +
                    "worker-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'none'"

        // Workers need a separate set of CSP.
        // MDN reference: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy#csp_in_workers
        private const val WORKER_CONTENT_SECURITY_POLICY =
            "default-src 'none'; " +
                    "script-src 'self' 'wasm-unsafe-eval'; " +
                    "connect-src 'self'"

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

        private const val MIN_ZOOM_RATIO = 0.2f
        private const val MAX_ZOOM_RATIO = 10f
        private const val MAX_RENDER_PIXELS = 8_388_608 // 8 mega-pixels
        private const val ALPHA_LOW = 130
        private const val ALPHA_HIGH = 255

        private val ICC_PATH_RE = Regex("^/viewer/iccs/.*\\.icc$")
        private val BCMAP_PATH_RE = Regex("^/viewer/cmaps/.*\\.bcmap$")
        private val PFB_PATH_RE = Regex("^/viewer/standard_fonts/.*\\.pfb$")
        private val TTF_PATH_RE = Regex("^/viewer/standard_fonts/.*\\.ttf$")

        private val JS_PATHS = setOf(
            "/viewer/js/index.js",
            "/viewer/wasm/openjpeg_nowasm_fallback.js",
            "/viewer/wasm/jbig2_nowasm_fallback.js",
            "/viewer/wasm/quickjs-eval.js"
        )

        private val WASM_PATHS = setOf(
            "/viewer/wasm/openjpeg.wasm",
            "/viewer/wasm/qcms_bg.wasm",
            "/viewer/wasm/jbig2.wasm",
            "/viewer/wasm/quickjs-eval.wasm"
        )
    }

    private val streamLock = Any()

    @Volatile
    private var zoomFocusX = 0f
    @Volatile
    private var zoomFocusY = 0f
    private var swipeThreshold = 0
    private var swipeVelocityThreshold = 0
    @Volatile
    private var insetLeft = 0f
    @Volatile
    private var insetTop = 0f
    @Volatile
    private var insetRight = 0f
    @Volatile
    private var insetBottom = 0f
    private var documentLoaded = false
    @Volatile
    private var inputStream: InputStream? = null
    private val documentPropertiesLoaded = AtomicBoolean(false)

    private lateinit var binding: PdfviewerBinding
    private lateinit var snackbar: Snackbar
    private var passwordPromptFragment: PasswordPromptFragment? = null
    val viewModel: PdfViewModel by viewModels()

    private val appBarOnLayoutChangeListener =
        View.OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            if (binding.toolbar.isVisible) {
                insetTop = (bottom - top).toFloat()
            }
        }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        result.data?.data?.let { uri ->
            viewModel.uri = uri
            resetDocumentState()
            loadPdf()
            invalidateOptionsMenu()
        }
    }

    private val saveAsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        result.data?.data?.let { saveDocumentAs(it) }
    }

    private inner class Channel {
        @JavascriptInterface
        fun setHasDocumentOutline(hasOutline: Boolean) {
            runOnUiThread { viewModel.setHasOutline(hasOutline) }
        }

        @JavascriptInterface
        fun setDocumentOutline(outline: String) {
            runOnUiThread { viewModel.parseOutlineString(outline) }
        }

        @JavascriptInterface
        fun getPage(): Int = viewModel.page

        @JavascriptInterface
        fun getZoomRatio(): Float = viewModel.zoomRatio

        @JavascriptInterface
        fun setZoomRatio(ratio: Float) {
            viewModel.zoomRatio = ratio.coerceIn(MIN_ZOOM_RATIO, MAX_ZOOM_RATIO)
        }

        @JavascriptInterface
        fun getMaxRenderPixels(): Int = MAX_RENDER_PIXELS

        @JavascriptInterface
        fun getZoomFocusX(): Float = zoomFocusX

        @JavascriptInterface
        fun getZoomFocusY(): Float = zoomFocusY

        @JavascriptInterface
        fun getMinZoomRatio(): Float = MIN_ZOOM_RATIO

        @JavascriptInterface
        fun getMaxZoomRatio(): Float = MAX_ZOOM_RATIO

        @JavascriptInterface
        fun getInsetLeft(): Float = insetLeft

        @JavascriptInterface
        fun getInsetTop(): Float = insetTop

        @JavascriptInterface
        fun getInsetRight(): Float = insetRight

        @JavascriptInterface
        fun getInsetBottom(): Float = insetBottom

        @JavascriptInterface
        fun getDocumentOrientationDegrees(): Int = viewModel.documentOrientationDegrees

        @JavascriptInterface
        fun setNumPages(numPages: Int) {
            viewModel.numPages = numPages
            runOnUiThread { this@PdfViewer.invalidateOptionsMenu() }
        }

        @JavascriptInterface
        fun setDocumentProperties(properties: String) {
            if (!documentPropertiesLoaded.compareAndSet(false, true)) {
                throw SecurityException("setDocumentProperties already called")
            }
            val numPages = viewModel.numPages
            val uri = viewModel.uri ?: return
            runOnUiThread { viewModel.retrieveDocumentProperties(properties, numPages, uri) }
        }

        @JavascriptInterface
        fun showPasswordPrompt() {
            runOnUiThread {
                val fragment = getPasswordPromptFragment()
                if (!fragment.isAdded) {
                    fragment.show(supportFragmentManager, PasswordPromptFragment::class.java.name)
                }
            }
            viewModel.passwordMissing()
        }

        @JavascriptInterface
        fun invalidPassword() {
            viewModel.invalid()
        }

        @JavascriptInterface
        fun onLoaded() {
            viewModel.validated()
            runOnUiThread {
                val fragment = getPasswordPromptFragment()
                if (fragment.isAdded) {
                    fragment.dismiss()
                }
            }
        }

        @JavascriptInterface
        fun onLoadError() {
            runOnUiThread {
                maybeCloseInputStream()
                resetDocumentState()
                invalidateOptionsMenu()
                snackbar.setText(R.string.error_while_opening).show()
            }
        }

        @JavascriptInterface
        fun getPassword(): String = viewModel.encryptedDocumentPassword
    }

    private fun showWebViewCrashed() {
        binding.webviewAlertTitle.text = getString(R.string.webview_crash_title)
        binding.webviewAlertMessage.text = getString(R.string.webview_crash_message)
        binding.webviewAlertLayout.visibility = View.VISIBLE
        binding.webviewAlertReload.visibility = View.VISIBLE
        binding.webview.visibility = View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        binding = PdfviewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel.outline.observe(this) { status ->
            if (status is PdfViewModel.OutlineStatus.Requested) {
                viewModel.setLoadingOutline()
                binding.webview.evaluateJavascript("getDocumentOutline()", null)
            }
        }

        viewModel.saveError.observe(this) { error ->
            if (error) {
                snackbar.setText(R.string.error_while_saving).show()
                viewModel.clearSaveError()
            }
        }

        viewModel.documentProperties.observe(this) { invalidateOptionsMenu() }

        viewModel.documentName.observe(this) { setToolbarTitleWithDocumentName() }

        supportFragmentManager.setFragmentResultListener(
            OutlineFragment.RESULT_KEY, this
        ) { _, result ->
            val newPage = result.getInt(OutlineFragment.PAGE_KEY, -1)
            if (viewModel.shouldAbortOutline()) {
                Log.d(TAG, "aborting outline operations")
                binding.webview.evaluateJavascript("abortDocumentOutline()", null)
                viewModel.clearOutline()
            } else {
                onJumpToPageInDocument(newPage)
            }
        }

        // Margins for the toolbar are needed, so that content of the toolbar
        // is not covered by a system button navigation bar when in landscape.
        applySystemBarMargins(binding.toolbar, applyBottom = false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.webview) { _, insets ->
            val allInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            insetLeft = allInsets.left.toFloat()
            insetRight = allInsets.right.toFloat()
            // Only set the bottom inset. The top will use the height of the app bar layout
            // which includes the status bar/display cutout.
            insetBottom = allInsets.bottom.toFloat()
            insets
        }

        binding.webview.setBackgroundColor(Color.TRANSPARENT)

        binding.webview.settings.apply {
            allowContentAccess = false
            allowFileAccess = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            javaScriptEnabled = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            minimumFontSize = 1
        }

        CookieManager.getInstance().setAcceptCookie(false)

        binding.webview.addJavascriptInterface(Channel(), "channel")

        binding.webview.webViewClient = object : WebViewClient() {
            private fun fromAsset(
                mime: String,
                path: String,
                vararg extraHeaders: Pair<String, String>
            ): WebResourceResponse? {
                return try {
                    val stream = assets.open(path.substring(1))
                    WebResourceResponse(mime, null, stream).apply {
                        responseHeaders = mutableMapOf(
                            "X-Content-Type-Options" to "nosniff",
                            *extraHeaders
                        )
                    }
                } catch (_: IOException) {
                    null
                }
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (viewModel.uri == null) return null
                if (request.method != "GET") return null

                val url = request.url
                if (url.host != "localhost") return null

                val path = url.path
                Log.d(TAG, "path $path")

                if (path == null) return null

                if (path == "/placeholder.pdf") {
                    synchronized(streamLock) {
                        maybeCloseInputStream()
                        val stream: InputStream = try {
                            val uri = viewModel.uri ?: return null
                            contentResolver.openInputStream(uri)
                                ?: throw FileNotFoundException()
                        } catch (e: Exception) {
                            when (e) {
                                is FileNotFoundException, is IllegalArgumentException,
                                is IllegalStateException, is SecurityException -> {
                                    runOnUiThread {
                                        snackbar.setText(R.string.error_while_opening).show()
                                    }
                                    return null
                                }
                                else -> throw e
                            }
                        }
                        inputStream = stream
                        return WebResourceResponse(PDF_MIME, null, stream)
                    }
                }

                return when (path) {
                    "/viewer/index.html" -> fromAsset(
                        "text/html", path,
                        "Content-Security-Policy" to CONTENT_SECURITY_POLICY,
                        "Permissions-Policy" to PERMISSIONS_POLICY
                    )
                    "/viewer/main.css" -> fromAsset("text/css", path)
                    "/viewer/js/worker.js" -> fromAsset(
                        "application/javascript", path,
                        "Content-Security-Policy" to WORKER_CONTENT_SECURITY_POLICY
                        // Permissions-Policy does not apply to workers.
                        // See: https://github.com/w3c/webappsec-permissions-policy/issues/207
                    )
                    in JS_PATHS -> fromAsset("application/javascript", path)
                    in WASM_PATHS -> fromAsset("application/wasm", path)
                    else -> when {
                        ICC_PATH_RE.matches(path) ->
                            fromAsset("application/vnd.iccprofile", path)
                        BCMAP_PATH_RE.matches(path) ->
                            fromAsset("application/octet-stream", path)
                        PFB_PATH_RE.matches(path) ->
                            fromAsset("application/octet-stream", path)
                        TTF_PATH_RE.matches(path) ->
                            fromAsset("font/sfnt", path)
                        else -> null
                    }
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = true

            override fun onPageFinished(view: WebView, url: String) {
                documentLoaded = true
                invalidateOptionsMenu()
                loadPdfWithPassword(viewModel.encryptedDocumentPassword)
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                if (detail.didCrash()) {
                    viewModel.webViewCrashed = true
                    showWebViewCrashed()
                    invalidateOptionsMenu()
                    purgeWebView()
                    return true
                }
                return false
            }
        }

        initializeGestures()

        GestureHelper.attach(
            this@PdfViewer, binding.webview,
            object : GestureHelper.GestureListener {
                override fun onTapUp(): Boolean {
                    if (viewModel.uri != null) {
                        binding.webview.evaluateJavascript("isTextSelected()") { selection ->
                            if (!selection.toBoolean()) {
                                val actionBar = supportActionBar ?: return@evaluateJavascript
                                if (actionBar.isShowing) {
                                    hideSystemUi()
                                } else {
                                    showSystemUi()
                                }
                            }
                        }
                        return true
                    }
                    return false
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y

                    if (abs(deltaX) > abs(deltaY) &&
                        abs(deltaX) > swipeThreshold &&
                        abs(velocityX) > swipeVelocityThreshold
                    ) {
                        val swipeLeft = deltaX < 0
                        val swipeRight = deltaX > 0

                        val atLeftEdge = !binding.webview.canScrollHorizontally(-1)
                        val atRightEdge = !binding.webview.canScrollHorizontally(1)

                        if (swipeLeft && atRightEdge) {
                            onJumpToPageInDocument(viewModel.page + 1)
                            return true
                        } else if (swipeRight && atLeftEdge) {
                            onJumpToPageInDocument(viewModel.page - 1)
                            return true
                        }
                    }

                    return false
                }

                override fun onZoom(scaleFactor: Float, focusX: Float, focusY: Float) {
                    zoom(scaleFactor, focusX, focusY, false)
                }

                override fun onZoomEnd() {
                    zoomEnd()
                }
            })

        snackbar = Snackbar.make(binding.root, "", Snackbar.LENGTH_LONG)

        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) {
            val type = intent.type
            if (type != null && type != PDF_MIME) {
                snackbar.setText(R.string.invalid_mime_type).show()
                return
            }
            if (type == null) {
                Log.w(TAG, "MIME type is null, but we'll try to load it anyway")
            }
            viewModel.uri = intent.data
            resetDocumentState()
        }

        binding.appBarLayout.addOnLayoutChangeListener(appBarOnLayoutChangeListener)

        binding.webviewAlertReload.setOnClickListener {
            viewModel.webViewCrashed = false
            recreate()
        }

        if (viewModel.webViewCrashed) {
            showWebViewCrashed()
        } else if (viewModel.uri != null) {
            if (viewModel.uri?.scheme == "file") {
                snackbar.setText(R.string.legacy_file_uri).show()
                return
            }
            loadPdf()
        }
    }

    private fun initializeGestures() {
        val vc = ViewConfiguration.get(this)
        swipeThreshold = vc.scaledTouchSlop * 6
        swipeVelocityThreshold = vc.scaledMinimumFlingVelocity
    }

    private fun purgeWebView() {
        binding.webview.removeJavascriptInterface("channel")
        binding.root.removeView(binding.webview)
        binding.webview.destroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.appBarLayout.removeOnLayoutChangeListener(appBarOnLayoutChangeListener)
        purgeWebView()
        maybeCloseInputStream()
    }

    private fun maybeCloseInputStream() {
        synchronized(streamLock) {
            val stream = inputStream ?: return
            inputStream = null
            try {
                stream.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun getPasswordPromptFragment(): PasswordPromptFragment {
        passwordPromptFragment?.let { return it }
        val existing = supportFragmentManager.findFragmentByTag(
            PasswordPromptFragment::class.java.name
        ) as? PasswordPromptFragment
        return (existing ?: PasswordPromptFragment()).also { passwordPromptFragment = it }
    }

    @VisibleForTesting
    fun setToolbarTitleWithDocumentName() {
        supportActionBar?.title = currentDocumentName.ifEmpty { getString(R.string.app_name) }
    }

    override fun onResume() {
        super.onResume()

        if (!viewModel.webViewCrashed) {
            // The user could have left the activity to update the WebView
            invalidateOptionsMenu()
            val webViewRelease = getWebViewRelease()
            if (webViewRelease >= MIN_WEBVIEW_RELEASE) {
                binding.webviewAlertLayout.visibility = View.GONE
                binding.webview.visibility = View.VISIBLE
            } else {
                binding.webview.visibility = View.GONE
                binding.webviewAlertTitle.text =
                    getString(R.string.webview_out_of_date_title)
                binding.webviewAlertMessage.text =
                    getString(
                        R.string.webview_out_of_date_message,
                        webViewRelease,
                        MIN_WEBVIEW_RELEASE
                    )
                binding.webviewAlertLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun getWebViewRelease(): Int {
        val versionName = WebView.getCurrentWebViewPackage()?.versionName ?: return 0
        return versionName.substringBefore(".").toIntOrNull() ?: 0
    }

    private fun loadPdf() {
        documentPropertiesLoaded.set(false)
        documentLoaded = false
        viewModel.zoomRatio = 0f
        showSystemUi()
        invalidateOptionsMenu()
        binding.webview.loadUrl("https://localhost/viewer/index.html")
    }

    fun loadPdfWithPassword(password: String) {
        viewModel.encryptedDocumentPassword = password
        binding.webview.evaluateJavascript("loadDocument()", null)
    }

    private fun renderPage(zoom: Int) {
        binding.webview.evaluateJavascript("onRenderPage($zoom)", null)
    }

    private fun documentOrientationChanged(orientationDegreesOffset: Int) {
        var degrees = (viewModel.documentOrientationDegrees + orientationDegreesOffset) % 360
        if (degrees < 0) {
            degrees += 360
        }
        viewModel.documentOrientationDegrees = degrees
        renderPage(0)
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = PDF_MIME
        }
        openDocumentLauncher.launch(intent)
    }

    private fun shareDocument() {
        val uri = viewModel.uri
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                setDataAndTypeAndNormalize(uri, PDF_MIME)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
        } else {
            Log.w(TAG, "Cannot share unexpected null URI")
        }
    }

    private fun zoom(scaleFactor: Float, focusX: Float, focusY: Float, end: Boolean) {
        viewModel.zoomRatio =
            (viewModel.zoomRatio * scaleFactor).coerceIn(MIN_ZOOM_RATIO, MAX_ZOOM_RATIO)
        zoomFocusX = focusX
        zoomFocusY = focusY
        renderPage(if (end) 1 else 2)
        invalidateOptionsMenu()
    }

    private fun zoomEnd() {
        renderPage(1)
    }

    private fun MenuItem.setState(visible: Boolean, enabled: Boolean) {
        isVisible = visible
        isEnabled = enabled
        icon?.alpha = if (enabled) ALPHA_HIGH else ALPHA_LOW
    }

    fun onJumpToPageInDocument(selectedPage: Int) {
        if (selectedPage in 1..viewModel.numPages && viewModel.page != selectedPage) {
            viewModel.page = selectedPage
            renderPage(0)
            showPageNumber()
            invalidateOptionsMenu()
        }
    }

    private fun showSystemUi() {
        binding.root.showSystemUi(window)
        supportActionBar?.show()
    }

    private fun hideSystemUi() {
        binding.root.hideSystemUi(window)
        supportActionBar?.hide()
    }

    private fun showPageNumber() {
        Snackbar.make(
            binding.webview,
            "${viewModel.page}/${viewModel.numPages}",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.pdf_viewer, menu)
        if (BuildConfig.DEBUG) {
            menuInflater.inflate(R.menu.pdf_viewer_debug, menu)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val loaded = documentLoaded
        val crashed = viewModel.webViewCrashed
        val enabled = loaded && !crashed
        val hasPages = viewModel.numPages > 0

        menu.findItem(R.id.action_open).setState(
            visible = true,
            enabled = !crashed && getWebViewRelease() >= MIN_WEBVIEW_RELEASE
        )
        menu.findItem(R.id.action_jump_to_page).setState(
            visible = loaded && hasPages,
            enabled = enabled && hasPages
        )
        menu.findItem(R.id.action_next).setState(
            visible = loaded && hasPages,
            enabled = enabled && hasPages && viewModel.page < viewModel.numPages
        )
        menu.findItem(R.id.action_previous).setState(
            visible = loaded && hasPages,
            enabled = enabled && hasPages && viewModel.page > 1
        )
        menu.findItem(R.id.action_first).setState(
            visible = loaded && hasPages,
            enabled = enabled && hasPages
        )
        menu.findItem(R.id.action_last).setState(
            visible = loaded && hasPages,
            enabled = enabled && hasPages
        )
        menu.findItem(R.id.action_rotate_clockwise).setState(
            visible = loaded,
            enabled = enabled
        )
        menu.findItem(R.id.action_rotate_counterclockwise).setState(
            visible = loaded,
            enabled = enabled
        )
        menu.findItem(R.id.action_view_document_properties).setState(
            visible = loaded,
            enabled = enabled && viewModel.documentProperties.value != null
        )
        menu.findItem(R.id.action_share).setState(
            visible = loaded,
            enabled = enabled && viewModel.uri != null
        )
        menu.findItem(R.id.action_save_as).setState(
            visible = loaded,
            enabled = enabled && viewModel.uri != null
        )
        menu.findItem(R.id.action_outline).setState(
            visible = loaded && viewModel.hasOutline(),
            enabled = enabled
        )

        if (BuildConfig.DEBUG) {
            menu.findItem(R.id.debug_action_toggle_text_layer_visibility).setState(
                visible = loaded,
                enabled = enabled
            )
            menu.findItem(R.id.debug_action_crash_webview).setState(
                visible = loaded,
                enabled = enabled
            )
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_previous -> onJumpToPageInDocument(viewModel.page - 1)
            R.id.action_next -> onJumpToPageInDocument(viewModel.page + 1)
            R.id.action_first -> onJumpToPageInDocument(1)
            R.id.action_last -> onJumpToPageInDocument(viewModel.numPages)
            R.id.action_open -> openDocument()
            R.id.action_rotate_clockwise -> documentOrientationChanged(90)
            R.id.action_rotate_counterclockwise -> documentOrientationChanged(-90)
            R.id.action_outline -> {
                val outlineFragment =
                    OutlineFragment.newInstance(viewModel.page, currentDocumentName)
                supportFragmentManager.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .add(android.R.id.content, outlineFragment)
                    .addToBackStack(null)
                    .commit()
            }

            R.id.action_view_document_properties -> {
                DocumentPropertiesFragment.newInstance()
                    .show(supportFragmentManager, DocumentPropertiesFragment.TAG)
            }

            R.id.action_jump_to_page -> {
                JumpToPageFragment()
                    .show(supportFragmentManager, JumpToPageFragment.TAG)
            }

            R.id.action_share -> shareDocument()
            R.id.action_save_as -> saveDocument()
            R.id.debug_action_toggle_text_layer_visibility -> {
                binding.webview.evaluateJavascript("toggleTextLayerVisibility()", null)
            }

            R.id.debug_action_crash_webview -> binding.webview.loadUrl("chrome://crash")
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun saveDocument() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = PDF_MIME
            putExtra(Intent.EXTRA_TITLE, currentDocumentName)
        }
        saveAsLauncher.launch(intent)
    }

    private val currentDocumentName: String
        get() = viewModel.documentName.value ?: ""

    private fun saveDocumentAs(saveUri: Uri) {
        val sourceUri = viewModel.uri ?: return
        viewModel.saveDocumentAs(contentResolver, sourceUri, saveUri)
    }

    private fun resetDocumentState() {
        viewModel.page = 1
        viewModel.numPages = 0
        viewModel.zoomRatio = 0f
        viewModel.documentOrientationDegrees = 0
        viewModel.encryptedDocumentPassword = ""
        viewModel.clearOutline()
        viewModel.clearDocumentProperties()
    }
}
