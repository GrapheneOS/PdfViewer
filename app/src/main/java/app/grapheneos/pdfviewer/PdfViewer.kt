package app.grapheneos.pdfviewer

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import app.grapheneos.pdfviewer.GestureHelper.GestureListener
import app.grapheneos.pdfviewer.databinding.PdfviewerBinding
import app.grapheneos.pdfviewer.fragment.DocumentPropertiesFragment
import app.grapheneos.pdfviewer.fragment.DocumentPropertiesFragment.Companion.newInstance
import app.grapheneos.pdfviewer.fragment.JumpToPageFragment
import app.grapheneos.pdfviewer.fragment.PasswordPromptFragment
import app.grapheneos.pdfviewer.ktx.hideSystemUi
import app.grapheneos.pdfviewer.ktx.showSystemUi
import app.grapheneos.pdfviewer.loader.DocumentPropertiesAsyncTaskLoader
import app.grapheneos.pdfviewer.viewModel.PasswordStatus
import com.google.android.material.snackbar.Snackbar
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

class PdfViewer : AppCompatActivity(), LoaderManager.LoaderCallbacks<List<CharSequence>> {
    private lateinit var mUri: Uri

    var mPage = 0
    var mNumPages = 0
    var zoomRatio = 1f
        private set
    var documentOrientationDegrees = 0
        private set
    private var mDocumentState = 0
    private var mEncryptedDocumentPassword: String? = null
    private var mDocumentProperties: List<CharSequence>? = null
    private var mInputStream: InputStream? = null
    private lateinit var binding: PdfviewerBinding
    private val pageNumberTextView: TextView by lazy {
        TextView(this).apply {
            setBackgroundColor(Color.DKGRAY)
            setTextColor(ColorStateList.valueOf(Color.WHITE))
            textSize = 18f
            setPadding(PADDING, 0, PADDING, 0)
        }
    }
    private lateinit var pageNumberToast: Toast
    private val snackbar: Snackbar by lazy {
        Snackbar.make(binding.root, "", Snackbar.LENGTH_LONG)
    }
    private var mPasswordPromptFragment: PasswordPromptFragment? = null
    val passwordValidationViewModel: PasswordStatus by viewModels()
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? ->
        if (result == null || result.resultCode != RESULT_OK) return@registerForActivityResult
        mUri = result.data?.data ?: return@registerForActivityResult
        mPage = 1
        mDocumentProperties = null
        mEncryptedDocumentPassword = ""
        loadPdf()
        invalidateOptionsMenu()
    }

    private val saveAsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult? ->
        if (result == null || result.resultCode != RESULT_OK) return@registerForActivityResult
        val resultData = result.data?.data ?: return@registerForActivityResult
        saveDocumentAs(resultData)
    }


    private inner class Channel {
        @JavascriptInterface
        fun getPage(): Int {
            return mPage
        }

        @JavascriptInterface
        fun getZoomRatio(): Float {
            return zoomRatio
        }

        @JavascriptInterface
        fun getDocumentOrientationDegrees(): Int {
            return documentOrientationDegrees
        }

        @JavascriptInterface
        fun setNumPages(numPages: Int) {
            mNumPages = numPages
            runOnUiThread { invalidateOptionsMenu() }
        }

        @JavascriptInterface
        fun setDocumentProperties(properties: String?) {
            if (mDocumentProperties != null) {
                throw SecurityException("mDocumentProperties not null")
            }
            val args = Bundle()
            args.putString(KEY_PROPERTIES, properties)
            runOnUiThread {
                LoaderManager.getInstance(this@PdfViewer)
                    .restartLoader(DocumentPropertiesAsyncTaskLoader.ID, args, this@PdfViewer)
            }
        }

        @JavascriptInterface
        fun showPasswordPrompt() {
            if (!passwordPromptFragment.isAdded) {
                passwordPromptFragment.show(
                    supportFragmentManager,
                    PasswordPromptFragment::class.java.name
                )
            }
            passwordValidationViewModel.passwordMissing()
        }

        @JavascriptInterface
        fun invalidPassword() {
            runOnUiThread { passwordValidationViewModel.invalid() }
        }

        @JavascriptInterface
        fun onLoaded() {
            passwordValidationViewModel.validated()
            if (passwordPromptFragment.isAdded) {
                passwordPromptFragment.dismiss()
            }
        }

        @get:JavascriptInterface
        val password: String
            get() = if (mEncryptedDocumentPassword != null) mEncryptedDocumentPassword!! else ""
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PdfviewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Margins for the toolbar are needed, so that content of the toolbar
        // is not covered by a system button navigation bar when in landscape.
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v: View, windowInsets: WindowInsetsCompat ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val mlp = v.layoutParams as MarginLayoutParams
            mlp.leftMargin = insets.left
            mlp.rightMargin = insets.right
            v.layoutParams = mlp
            windowInsets
        }
        binding.webview.setBackgroundColor(Color.TRANSPARENT)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val settings = binding.webview.settings
        settings.allowContentAccess = false
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.javaScriptEnabled = true
        settings.minimumFontSize = 1
        CookieManager.getInstance().setAcceptCookie(false)
        binding.webview.addJavascriptInterface(Channel(), JAVASCRIPT_CHANNEL_NAME)
        binding.webview.webViewClient = object : WebViewClient() {
            private fun fromAsset(mime: String, path: String): WebResourceResponse? {
                return try {
                    val inputStream = assets.open(path.substring(1))
                    WebResourceResponse(mime, null, inputStream)
                } catch (e: IOException) {
                    null
                }
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
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
                    maybeCloseInputStream()
                    try {
                        mInputStream = contentResolver.openInputStream(mUri)
                    } catch (ignored: FileNotFoundException) {
                        snackbar.setText(R.string.error_while_opening).show()
                    }
                    return WebResourceResponse(DOCUMENT_TYPE_PDF, null, mInputStream)
                }
                if ("/viewer.html" == path) {
                    val response = fromAsset("text/html", path)
                    val headers = HashMap<String, String>()
                    headers["Content-Security-Policy"] = CONTENT_SECURITY_POLICY
                    headers["Permissions-Policy"] = PERMISSIONS_POLICY
                    headers["X-Content-Type-Options"] = "nosniff"
                    return response?.apply { responseHeaders = headers }
                }
                if ("/viewer.css" == path) {
                    return fromAsset("text/css", path)
                }
                return if ("/viewer.js" == path || "/pdf.js" == path || "/pdf.worker.js" == path) {
                    fromAsset("application/javascript", path)
                } else null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                mDocumentState = STATE_LOADED
                invalidateOptionsMenu()
                loadPdfWithPassword(mEncryptedDocumentPassword)
            }
        }
        GestureHelper.attach(this@PdfViewer, binding.webview,
            object : GestureListener {
                override fun onTapUp(): Boolean {
                    if (!this@PdfViewer::mUri.isInitialized) return false
                    binding.webview.evaluateJavascript("isTextSelected()") { selection: String? ->
                        if (!java.lang.Boolean.parseBoolean(selection)) {
                            if (supportActionBar!!.isShowing) {
                                hideSystemUi()
                            } else {
                                showSystemUi()
                            }
                        }
                    }
                    return true
                }

                override fun onZoomIn(value: Float) {
                    zoomIn(value, false)
                }

                override fun onZoomOut(value: Float) {
                    zoomOut(value, false)
                }

                override fun onZoomEnd() {
                    zoomEnd()
                }
            })

        // If loaders are not being initialized in onCreate(), the result will not be delivered
        // after orientation change (See FragmentHostCallback), thus initialize the
        // loader manager impl so that the result will be delivered.
        LoaderManager.getInstance(this)
        val intent = intent
        if (Intent.ACTION_VIEW == intent.action) {
            if (DOCUMENT_TYPE_PDF != intent.type) {
                snackbar.setText(R.string.invalid_mime_type).show()
                return
            }
            intent.data?.let { mUri = it }
            mPage = 1
        }
        if (savedInstanceState != null) {
            getParcelableExtra<Uri>(savedInstanceState, STATE_URI)?.let { mUri = it }
            mPage = savedInstanceState.getInt(STATE_PAGE)
            zoomRatio = savedInstanceState.getFloat(STATE_ZOOM_RATIO)
            documentOrientationDegrees = savedInstanceState.getInt(
                STATE_DOCUMENT_ORIENTATION_DEGREES
            )
            mEncryptedDocumentPassword = savedInstanceState.getString(
                STATE_ENCRYPTED_DOCUMENT_PASSWORD
            )
        }
        if (!this@PdfViewer::mUri.isInitialized) {
            return
        }
        if (LEGACY_URI_SCHEME == mUri.scheme) {
            snackbar.setText(R.string.legacy_file_uri).show()
            return
        }
        loadPdf()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webview.removeJavascriptInterface(JAVASCRIPT_CHANNEL_NAME)
        binding.root.removeView(binding.webview)
        binding.webview.destroy()
        maybeCloseInputStream()
    }

    private inline fun <reified T : Parcelable> getParcelableExtra(
        bundle: Bundle,
        name: String
    ): T? {
        return if (Build.VERSION.SDK_INT >= 33) {
            bundle.getParcelable(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable<T>(name)
        }
    }

    fun maybeCloseInputStream() {
        val stream = mInputStream ?: return
        mInputStream = null
        try {
            stream.close()
        } catch (ignored: IOException) {
        }
    }

    private val passwordPromptFragment: PasswordPromptFragment
        get() {
            var mPasswordPromptFragment = mPasswordPromptFragment
            if (mPasswordPromptFragment == null) {
                val fragment = supportFragmentManager.findFragmentByTag(
                    PasswordPromptFragment::class.java.name
                )
                mPasswordPromptFragment = if (fragment != null) {
                    fragment as PasswordPromptFragment
                } else {
                    PasswordPromptFragment()
                }
            }
            return mPasswordPromptFragment
        }

    private fun setToolbarTitleWithDocumentName() {
        binding.toolbar.title = currentDocumentName.ifEmpty { getString(R.string.app_name) }
    }

    override fun onResume() {
        super.onResume()

        // The user could have left the activity to update the WebView
        invalidateOptionsMenu()
        if (webViewRelease >= MIN_WEBVIEW_RELEASE) {
            binding.webviewOutOfDateLayout.visibility = View.GONE
            binding.webview.visibility = View.VISIBLE
        } else {
            binding.webview.visibility = View.GONE
            binding.webviewOutOfDateMessage.text = getString(
                R.string.webview_out_of_date_message,
                webViewRelease,
                MIN_WEBVIEW_RELEASE
            )
            binding.webviewOutOfDateLayout.visibility = View.VISIBLE
        }
    }

    private val webViewRelease: Int
        get() {
            val webViewPackage = WebView.getCurrentWebViewPackage()
            val webViewVersionName = webViewPackage!!.versionName
            return webViewVersionName.substring(0, webViewVersionName.indexOf(".")).toInt()
        }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<CharSequence>> {
        return DocumentPropertiesAsyncTaskLoader(
            this,
            args!!.getString(KEY_PROPERTIES),
            mNumPages,
            mUri
        )
    }

    override fun onLoadFinished(loader: Loader<List<CharSequence>>, data: List<CharSequence>) {
        mDocumentProperties = data
        setToolbarTitleWithDocumentName()
        LoaderManager.getInstance(this).destroyLoader(DocumentPropertiesAsyncTaskLoader.ID)
    }

    override fun onLoaderReset(loader: Loader<List<CharSequence>>) {
        mDocumentProperties = null
    }

    private fun loadPdf() {
        mInputStream = try {
            mInputStream?.close()
            contentResolver.openInputStream(mUri)
        } catch (e: IOException) {
            snackbar.setText(R.string.error_while_opening).show()
            return
        }
        mDocumentState = 0
        showSystemUi()
        invalidateOptionsMenu()
        binding.webview.loadUrl("https://localhost/viewer.html")
    }

    fun loadPdfWithPassword(password: String?) {
        mEncryptedDocumentPassword = password
        binding.webview.evaluateJavascript("loadDocument()", null)
    }

    private fun renderPage(zoom: Int) {
        binding.webview.evaluateJavascript("onRenderPage($zoom)", null)
    }

    private fun documentOrientationChanged(orientationDegreesOffset: Int) {
        documentOrientationDegrees = (documentOrientationDegrees + orientationDegreesOffset) % 360
        if (documentOrientationDegrees < 0) {
            documentOrientationDegrees += 360
        }
        renderPage(0)
    }

    private fun openDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = DOCUMENT_TYPE_PDF
        openDocumentLauncher.launch(intent)
    }

    private fun shareDocument() {
        if (!this@PdfViewer::mUri.isInitialized) {
            Log.w(TAG, "Cannot share unexpected null URI")
            return
        }
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.setDataAndTypeAndNormalize(mUri, DOCUMENT_TYPE_PDF)
        shareIntent.putExtra(Intent.EXTRA_STREAM, mUri)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    private fun zoomIn(value: Float, end: Boolean) {
        val maxZoomRatio = 1.5f
        if (zoomRatio < maxZoomRatio) {
            zoomRatio = Math.min(zoomRatio + value, maxZoomRatio)
            renderPage(if (end) 1 else 2)
            invalidateOptionsMenu()
        }
    }

    private fun zoomOut(value: Float, end: Boolean) {
        val minZoomRatio = 0.5f
        if (zoomRatio > minZoomRatio) {
            zoomRatio = Math.max(zoomRatio - value, minZoomRatio)
            renderPage(if (end) 1 else 2)
            invalidateOptionsMenu()
        }
    }

    private fun zoomEnd() {
        renderPage(1)
    }

    fun onJumpToPageInDocument(selected_page: Int) {
        if (selected_page in 1..mNumPages && mPage != selected_page) {
            mPage = selected_page
            renderPage(0)
            showPageNumber()
            invalidateOptionsMenu()
        }
    }

    private fun showSystemUi() {
        binding.root.showSystemUi(window)
        supportActionBar!!.show()
    }

    private fun hideSystemUi() {
        binding.root.hideSystemUi(window)
        supportActionBar!!.hide()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putParcelable(STATE_URI, if(this::mUri.isInitialized) mUri else null)
        savedInstanceState.putInt(STATE_PAGE, mPage)
        savedInstanceState.putFloat(STATE_ZOOM_RATIO, zoomRatio)
        savedInstanceState.putInt(STATE_DOCUMENT_ORIENTATION_DEGREES, documentOrientationDegrees)
        savedInstanceState.putString(STATE_ENCRYPTED_DOCUMENT_PASSWORD, mEncryptedDocumentPassword)
    }

    private fun createPageNumberToast(): Toast {
        return Toast(applicationContext).apply {
            setGravity(Gravity.BOTTOM or Gravity.END, PADDING, PADDING)
            duration = Toast.LENGTH_SHORT
        }
    }

    private fun showPageNumber() {
        if (!this::pageNumberToast.isInitialized) {
            pageNumberToast = createPageNumberToast()
        }
        if (pageNumberToast.view?.isShown == true) {
            pageNumberToast.cancel()
            pageNumberToast = createPageNumberToast()
        }
        pageNumberToast.view = pageNumberTextView.apply {
            text = String.format("%s/%s", mPage, mNumPages)
        }
        pageNumberToast.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.pdf_viewer, menu)
        if (BuildConfig.DEBUG) {
            inflater.inflate(R.menu.pdf_viewer_debug, menu)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val ids = arrayListOf(
            R.id.action_jump_to_page,
            R.id.action_next, R.id.action_previous, R.id.action_first, R.id.action_last,
            R.id.action_rotate_clockwise, R.id.action_rotate_counterclockwise,
            R.id.action_view_document_properties, R.id.action_share, R.id.action_save_as
        )
        if (BuildConfig.DEBUG) {
            ids.add(R.id.debug_action_toggle_text_layer_visibility)
        }
        if (mDocumentState < STATE_LOADED) {
            for (id in ids) {
                val item = menu.findItem(id)
                if (item.isVisible) {
                    item.isVisible = false
                }
            }
        } else if (mDocumentState == STATE_LOADED) {
            for (id in ids) {
                val item = menu.findItem(id)
                if (!item.isVisible) {
                    item.isVisible = true
                }
            }
            mDocumentState = STATE_END
        }
        enableDisableMenuItem(
            menu.findItem(R.id.action_open),
            webViewRelease >= MIN_WEBVIEW_RELEASE
        )
        enableDisableMenuItem(menu.findItem(R.id.action_share), this@PdfViewer::mUri.isInitialized)
        enableDisableMenuItem(menu.findItem(R.id.action_next), mPage < mNumPages)
        enableDisableMenuItem(menu.findItem(R.id.action_previous), mPage > 1)
        enableDisableMenuItem(
            menu.findItem(R.id.action_save_as),
            this@PdfViewer::mUri.isInitialized
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_previous -> {
                onJumpToPageInDocument(mPage - 1)
                true
            }

            R.id.action_next -> {
                onJumpToPageInDocument(mPage + 1)
                true
            }

            R.id.action_first -> {
                onJumpToPageInDocument(1)
                true
            }

            R.id.action_last -> {
                onJumpToPageInDocument(mNumPages)
                true
            }

            R.id.action_open -> {
                openDocument()
                true
            }

            R.id.action_rotate_clockwise -> {
                documentOrientationChanged(90)
                true
            }

            R.id.action_rotate_counterclockwise -> {
                documentOrientationChanged(-90)
                true
            }

            R.id.action_view_document_properties -> {
                newInstance(mDocumentProperties!!)
                    .show(supportFragmentManager, DocumentPropertiesFragment.TAG)
                true
            }

            R.id.action_jump_to_page -> {
                JumpToPageFragment()
                    .show(supportFragmentManager, JumpToPageFragment.TAG)
                true
            }

            R.id.action_share -> {
                shareDocument()
                true
            }

            R.id.action_save_as -> {
                saveDocument()
                true
            }

            R.id.debug_action_toggle_text_layer_visibility -> {
                binding.webview.evaluateJavascript("toggleTextLayerVisibility()", null)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveDocument() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = DOCUMENT_TYPE_PDF
        intent.putExtra(Intent.EXTRA_TITLE, currentDocumentName)
        saveAsLauncher.launch(intent)
    }

    private val currentDocumentName: String
        get() {
            if (mDocumentProperties == null || mDocumentProperties!!.isEmpty()) return ""
            var fileName = ""
            var title = ""
            for (property in mDocumentProperties!!) {
                if (property.toString().startsWith("File name:")) {
                    fileName = property.toString().replace("File name:", "")
                }
                if (property.toString().startsWith("Title:-")) {
                    title = property.toString().replace("Title:-", "")
                }
            }
            return if (fileName.length > 2) fileName else title
        }

    private fun saveDocumentAs(uri: Uri) {
        try {
            saveAs(this, mUri, uri)
        } catch (e: IOException) {
            snackbar.setText(R.string.error_while_saving).show()
        } catch (e: OutOfMemoryError) {
            snackbar.setText(R.string.error_while_saving).show()
        } catch (e: IllegalArgumentException) {
            snackbar.setText(R.string.error_while_saving).show()
        }
    }

    private fun enableDisableMenuItem(item: MenuItem, enable: Boolean) {
        val alphaLow = 130
        val alphaHigh = 255

        item.isEnabled = enable
        item.icon?.alpha = if (enable) alphaHigh else alphaLow
    }

    companion object {
        private const val DOCUMENT_TYPE_PDF = "application/pdf"
        private const val LEGACY_URI_SCHEME = "file"
        const val TAG = "PdfViewer"
        private const val STATE_URI = "uri"
        private const val STATE_PAGE = "page"
        private const val STATE_ZOOM_RATIO = "zoomRatio"
        private const val STATE_DOCUMENT_ORIENTATION_DEGREES = "documentOrientationDegrees"
        private const val STATE_ENCRYPTED_DOCUMENT_PASSWORD = "encrypted_document_password"
        private const val JAVASCRIPT_CHANNEL_NAME = "channel"
        private const val KEY_PROPERTIES = "properties"
        private const val MIN_WEBVIEW_RELEASE = 89
        private const val STATE_LOADED = 1
        private const val STATE_END = 2
        private const val PADDING = 10
    }
}