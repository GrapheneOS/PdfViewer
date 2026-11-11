package app.grapheneos.pdfviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.icu.text.DecimalFormatSymbols
import android.icu.text.NumberFormat
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.ConfigurationCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.grapheneos.pdfviewer.PdfJsChannel.Companion.MAX_ZOOM_RATIO
import app.grapheneos.pdfviewer.PdfJsChannel.Companion.MIN_ZOOM_RATIO
import app.grapheneos.pdfviewer.PdfViewer.Companion.PDF_MIME
import app.grapheneos.pdfviewer.outline.OutlineScreen
import app.grapheneos.pdfviewer.properties.DocumentProperty
import app.grapheneos.pdfviewer.ui.darkSearchFieldColors
import app.grapheneos.pdfviewer.ui.darkTopAppBarColors
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "PdfViewerScreen"
private const val MIN_WEBVIEW_RELEASE = 133
private val ZOOM_PRESETS = intArrayOf(25, 50, 75, 100, 125, 150, 200, 300, 500, 750, 1000)

private fun nextZoomPreset(ratio: Float): Float? {
    val currentPercent = (ratio * 100).roundToInt()
    return ZOOM_PRESETS.firstOrNull { it > currentPercent }?.let { it / 100f }
}

private fun previousZoomPreset(ratio: Float): Float? {
    val currentPercent = (ratio * 100).roundToInt()
    return ZOOM_PRESETS.lastOrNull { it < currentPercent }?.let { it / 100f }
}

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

private fun getWebViewRelease(): Int {
    val versionName = WebView.getCurrentWebViewPackage()?.versionName ?: return 0
    return versionName.substringBefore(".").toIntOrNull() ?: 0
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewModel = viewModel(),
    initialMimeError: Boolean = false,
    onRequestRecreate: () -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {},
    onWebViewDestroyed: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as Activity
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val snackbarHostState = remember { SnackbarHostState() }

    val uri by viewModel.uri.collectAsStateWithLifecycle()
    val documentLoaded by viewModel.documentLoaded.collectAsStateWithLifecycle()
    val webViewCrashed by viewModel.webViewCrashed.collectAsStateWithLifecycle()
    val numPages by viewModel.numPages.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val documentName by viewModel.documentName.collectAsStateWithLifecycle()
    val documentProperties by viewModel.documentProperties.collectAsStateWithLifecycle()
    val outlineStatus by viewModel.outline.collectAsStateWithLifecycle()
    val showPasswordDialog by viewModel.showPasswordDialog.collectAsStateWithLifecycle()
    val pageIndicator by viewModel.pageIndicator.collectAsStateWithLifecycle()
    var showPageIndicator by remember { mutableStateOf(false) }

    val isToolbarVisible by viewModel.toolbarVisible.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchResult.collectAsStateWithLifecycle()
    var showOutline by rememberSaveable { mutableStateOf(false) }
    var showJumpToPage by rememberSaveable { mutableStateOf(false) }
    var showDocProperties by rememberSaveable { mutableStateOf(false) }
    var showCustomZoom by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var webViewRelease by remember { mutableIntStateOf(getWebViewRelease()) }
    val webViewOk = webViewRelease >= MIN_WEBVIEW_RELEASE
    var toolbarHeightPx by remember { mutableFloatStateOf(0f) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LifecycleResumeEffect(Unit) {
        webViewRelease = getWebViewRelease()
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        var snackbarJob: Job? = null
        viewModel.snackbarEvent.collect { event ->
            snackbarJob?.cancel()
            snackbarJob = launch {
                snackbarHostState.showSnackbar(
                    message = event.message,
                    duration = if (event.long) SnackbarDuration.Long else SnackbarDuration.Short
                )
            }
        }
    }

    val mimeErrorMessage = stringResource(R.string.invalid_mime_type)
    LaunchedEffect(initialMimeError) {
        if (initialMimeError) {
            snackbarHostState.showSnackbar(
                message = mimeErrorMessage,
                duration = SnackbarDuration.Long
            )
        }
    }

    val channel = remember { PdfJsChannel(viewModel) }

    SideEffect {
        WindowCompat.getInsetsController(activity.window, view)
            .isAppearanceLightStatusBars = false
    }

    LaunchedEffect(isToolbarVisible) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (isToolbarVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    val systemInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insetLeftPx = systemInsets.getLeft(density, layoutDirection).toFloat()
    val insetRightPx = systemInsets.getRight(density, layoutDirection).toFloat()
    val insetBottomPx = systemInsets.getBottom(density).toFloat()
    val insetImePx = WindowInsets.ime.getBottom(density).toFloat()
    SideEffect {
        viewModel.insetLeft = insetLeftPx
        viewModel.insetRight = insetRightPx
        viewModel.insetBottom = insetBottomPx
        viewModel.insetIme = insetImePx
        if (isToolbarVisible) {
            viewModel.insetTop = toolbarHeightPx
        }
    }

    LaunchedEffect(outlineStatus) {
        if (outlineStatus is PdfViewModel.OutlineStatus.Requested) {
            viewModel.setLoadingOutline()
            webView?.evaluateJavascript("getDocumentOutline()", null)
        }
    }

    // Extraction runs once per document. webView is a key so a crash and Activity recreate
    // re-issues it; the corpus lives in the ViewModel, so a finished sweep is never repeated.
    LaunchedEffect(searchActive, documentLoaded, webView, numPages) {
        if (!searchActive || !documentLoaded || numPages == 0) return@LaunchedEffect
        if (viewModel.extractionComplete()) return@LaunchedEffect
        webView?.evaluateJavascript(
            "extractText(${page.coerceIn(1, numPages)},${viewModel.currentSearchGeneration})", null
        )
    }

    LaunchedEffect(searchQuery, numPages) {
        if (numPages > 0) viewModel.runSearch(searchQuery, page.coerceIn(1, numPages))
    }

    // Keyed on the index too: stepping between two matches on the same page must still bring the
    // viewer back to that page if the user has since swiped away from it.
    LaunchedEffect(searchResult.activePage, searchResult.activeIndex) {
        if (searchResult.activePage > 0) jumpToPage(viewModel, webView, searchResult.activePage)
    }

    // Deliberately not keyed on the whole searchResult: that would fire one evaluateJavascript
    // per page during a scan. The active match plus the displayed page covers every visible
    // transition, and the scan starts at the current page so its highlights land immediately.
    // searchResult.version is in the key list because two different queries can select the same
    // (page, index); without it the highlights would keep the previous query's offsets.
    LaunchedEffect(
        page, searchResult.activePage, searchResult.activeIndex, searchResult.version,
        searchActive, webView
    ) {
        val wv = webView ?: return@LaunchedEffect
        if (!searchActive || searchResult.total == 0) {
            wv.evaluateJavascript("setSearchHighlights(0,[],-1)", null)
            return@LaunchedEffect
        }
        val active = if (searchResult.activePage == page) searchResult.activeIndex else -1
        // Off the main thread: tuplesFor takes the index lock, which a binder thread can be
        // holding for the length of one page's match pass.
        val tuples = withContext(Dispatchers.Default) { viewModel.tuplesFor(page) }
        wv.evaluateJavascript("setSearchHighlights($page,$tuples,$active)", null)
    }

    LaunchedEffect(pageIndicator) {
        if (pageIndicator > 0) {
            showPageIndicator = true
            delay(1500L)
            showPageIndicator = false
        }
    }

    val viewConfiguration = remember { ViewConfiguration.get(context) }
    val swipeThreshold = remember { viewConfiguration.scaledTouchSlop * 6 }
    val swipeVelocityThreshold = remember { viewConfiguration.scaledMinimumFlingVelocity }

    DisposableEffect(webView) {
        val wv = webView ?: return@DisposableEffect onDispose {}
        GestureHelper.attach(context, wv, object : GestureHelper.GestureListener {
            override fun onTapUp(): Boolean {
                if (viewModel.uri.value == null) return false
                // Hiding the toolbar while searching would take the find bar with it, and also
                // hide the status bar and freeze insetTop, so guard the whole gesture.
                if (viewModel.searchActive.value) return false
                wv.evaluateJavascript("isTextSelected()") { selection ->
                    if (!selection.toBoolean()) {
                        viewModel.setToolbarVisible(!viewModel.toolbarVisible.value)
                    }
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (abs(deltaX) > abs(deltaY) &&
                    abs(deltaX) > swipeThreshold &&
                    abs(velocityX) > swipeVelocityThreshold
                ) {
                    if (deltaX < 0 && !wv.canScrollHorizontally(1)) {
                        jumpToPage(viewModel, wv, viewModel.page.value + 1)
                        return true
                    } else if (deltaX > 0 && !wv.canScrollHorizontally(-1)) {
                        jumpToPage(viewModel, wv, viewModel.page.value - 1)
                        return true
                    }
                }
                return false
            }

            override fun onZoom(scaleFactor: Float, focusX: Float, focusY: Float) {
                viewModel.setZoomRatio(
                    (viewModel.zoomRatio.value * scaleFactor)
                        .coerceIn(MIN_ZOOM_RATIO, MAX_ZOOM_RATIO)
                )
                viewModel.zoomFocusX = focusX
                viewModel.zoomFocusY = focusY
                wv.evaluateJavascript("onRenderPage(2)", null)
            }

            override fun onZoomEnd() {
                wv.evaluateJavascript("onRenderPage(1)", null)
            }
        })
        onDispose {
            wv.setOnTouchListener(null)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.data?.let { newUri ->
            viewModel.setUri(newUri)
            viewModel.resetDocumentState()
            viewModel.setToolbarVisible(true)
            loadPdf(viewModel, webView)
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.data?.let { saveUri ->
            viewModel.uri.value?.let { sourceUri ->
                viewModel.saveDocumentAs(context.contentResolver, sourceUri, saveUri)
            }
        }
    }

    val legacyFileUriMessage = stringResource(R.string.legacy_file_uri)
    LaunchedEffect(webView) {
        val wv = webView ?: return@LaunchedEffect
        val currentUri = viewModel.uri.value ?: return@LaunchedEffect
        if (viewModel.webViewCrashed.value) return@LaunchedEffect
        if (currentUri.scheme == "file") {
            snackbarHostState.showSnackbar(
                message = legacyFileUriMessage,
                duration = SnackbarDuration.Long
            )
            return@LaunchedEffect
        }
        viewModel.setToolbarVisible(true)
        loadPdf(viewModel, wv)
    }

    val hasPages = numPages > 0
    val enabled = documentLoaded && !webViewCrashed
    val displayName = documentName.ifEmpty { stringResource(R.string.app_name) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            webViewCrashed -> WebViewAlertScreen(
                title = stringResource(R.string.webview_crash_title),
                message = stringResource(R.string.webview_crash_message),
                showReload = true,
                onReload = {
                    viewModel.setWebViewCrashed(false)
                    onRequestRecreate()
                },
                modifier = Modifier.testTag(TestTags.WEBVIEW_ALERT)
            )
            !webViewOk -> WebViewAlertScreen(
                title = stringResource(R.string.webview_out_of_date_title),
                message = stringResource(
                    R.string.webview_out_of_date_message,
                    webViewRelease, MIN_WEBVIEW_RELEASE
                ),
                showReload = false,
                onReload = {},
                modifier = Modifier.testTag(TestTags.WEBVIEW_ALERT)
            )
            else -> {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.apply {
                                allowContentAccess = false
                                allowFileAccess = false
                                blockNetworkLoads = true
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                javaScriptEnabled = true
                                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                                minimumFontSize = 1
                            }
                            CookieManager.getInstance().setAcceptCookie(false)
                            addJavascriptInterface(channel, "channel")
                            webViewClient = createWebViewClient(ctx, viewModel)
                        }.also {
                            webView = it
                            onWebViewCreated(it)
                        }
                    },
                    onRelease = { releasingWebView ->
                        webView = null
                        onWebViewDestroyed()
                        purgeWebView(releasingWebView)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TestTags.WEBVIEW)
                )
            }
        }

        if (isToolbarVisible && searchActive && !webViewCrashed && webViewOk) {
            SearchAppBar(
                query = searchQuery,
                result = searchResult,
                numPages = numPages,
                onQueryChange = viewModel::setSearchQuery,
                onStep = viewModel::stepMatch,
                onClose = viewModel::closeSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        toolbarHeightPx = coordinates.size.height.toFloat()
                    }
            )
        } else if (isToolbarVisible) {
            PdfTopAppBar(
                title = displayName,
                documentLoaded = documentLoaded,
                webViewCrashed = webViewCrashed,
                webViewOk = webViewOk,
                hasPages = hasPages,
                enabled = enabled,
                page = page,
                numPages = numPages,
                hasOutline = viewModel.hasOutline(),
                hasDocumentProperties = documentProperties != null,
                hasUri = uri != null,
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onPrevious = { jumpToPage(viewModel, webView, page - 1) },
                onNext = { jumpToPage(viewModel, webView, page + 1) },
                onOpen = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = PDF_MIME
                    }
                    openDocumentLauncher.launch(intent)
                },
                onFirst = { jumpToPage(viewModel, webView, 1) },
                onLast = { jumpToPage(viewModel, webView, numPages) },
                onJumpToPage = { showJumpToPage = true },
                onRotateClockwise = { rotateDocument(viewModel, webView, 90) },
                onRotateCounterClockwise = { rotateDocument(viewModel, webView, -90) },
                zoomRatioFlow = viewModel.zoomRatio,
                onZoomIn = {
                    nextZoomPreset(viewModel.zoomRatio.value)?.let { preset ->
                        zoomDocument(viewModel, webView, preset)
                    }
                },
                onZoomOut = {
                    previousZoomPreset(viewModel.zoomRatio.value)?.let { preset ->
                        zoomDocument(viewModel, webView, preset)
                    }
                },
                onCustomZoom = { showCustomZoom = true },
                onOutline = { showOutline = true },
                onShare = { shareDocument(context, viewModel) },
                onSaveAs = {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = PDF_MIME
                        putExtra(Intent.EXTRA_TITLE, documentName)
                    }
                    saveAsLauncher.launch(intent)
                },
                onSearch = { viewModel.openSearch() },
                onDocumentProperties = { showDocProperties = true },
                onToggleTextLayer = {
                    webView?.evaluateJavascript("toggleTextLayerVisibility()", null)
                },
                onCrashWebView = { webView?.loadUrl("chrome://crash") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        toolbarHeightPx = coordinates.size.height.toFloat()
                    }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
        )

        AnimatedVisibility(
            visible = showPageIndicator && hasPages,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 500)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 24.dp)
        ) {
            PageIndicator(page = page, numPages = numPages)
        }

        AnimatedVisibility(
            visible = showOutline,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            OutlineScreen(
                pdfViewModel = viewModel,
                docTitle = documentName,
                onPageSelected = { selectedPage ->
                    showOutline = false
                    if (viewModel.shouldAbortOutline()) {
                        webView?.evaluateJavascript("abortDocumentOutline()", null)
                        viewModel.clearOutline()
                    } else {
                        jumpToPage(viewModel, webView, selectedPage)
                    }
                },
                onDismiss = {
                    showOutline = false
                    if (viewModel.shouldAbortOutline()) {
                        webView?.evaluateJavascript("abortDocumentOutline()", null)
                        viewModel.clearOutline()
                    }
                }
            )
        }
    }

    if (showCustomZoom) {
        val currentZoom = remember { viewModel.zoomRatio.value }
        CustomZoomDialog(
            currentZoomRatio = currentZoom,
            onZoom = { newRatio ->
                showCustomZoom = false
                zoomDocument(viewModel, webView, newRatio)
            },
            onDismiss = { showCustomZoom = false }
        )
    }

    if (showJumpToPage && hasPages) {
        JumpToPageDialog(
            currentPage = page,
            numPages = numPages,
            onJump = { selectedPage ->
                showJumpToPage = false
                jumpToPage(viewModel, webView, selectedPage)
            },
            onDismiss = { showJumpToPage = false }
        )
    }

    if (showDocProperties) {
        DocumentPropertiesDialog(
            properties = documentProperties,
            onDismiss = { showDocProperties = false }
        )
    }

    if (showPasswordDialog) {
        PasswordPromptDialog(
            invalidPasswordEvent = viewModel.invalidPasswordEvent,
            onSubmit = { password ->
                loadPdfWithPassword(viewModel, webView, password)
            },
            onCancel = { viewModel.dismissPasswordPrompt() }
        )
    }
}

private fun createWebViewClient(context: Context, viewModel: PdfViewModel): WebViewClient {
    val assets = context.assets

    fun fromAsset(
        mime: String,
        path: String,
        vararg extraHeaders: Pair<String, String>
    ): WebResourceResponse? = try {
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

    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (viewModel.uri.value == null) return null
            if (request.method != "GET") return null

            val url = request.url
            if (url.host != "localhost") return null
            val path = url.path ?: return null
            Log.d(TAG, "path $path")

            if (path == "/placeholder.pdf") {
                synchronized(viewModel.streamLock) {
                    viewModel.maybeCloseInputStream()
                    val stream: InputStream = try {
                        val pdfUri = viewModel.uri.value ?: return null
                        context.contentResolver.openInputStream(pdfUri)
                            ?: throw FileNotFoundException()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open input stream", e)
                        viewModel.postSnackbar(R.string.error_while_opening)
                        return null
                    }
                    viewModel.inputStream = stream
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
                    ICC_PATH_RE.matches(path) -> fromAsset("application/vnd.iccprofile", path)
                    BCMAP_PATH_RE.matches(path) -> fromAsset("application/octet-stream", path)
                    PFB_PATH_RE.matches(path) -> fromAsset("application/octet-stream", path)
                    TTF_PATH_RE.matches(path) -> fromAsset("font/sfnt", path)
                    else -> null
                }
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = true

        override fun onPageFinished(view: WebView, url: String) {
            viewModel.setDocumentLoaded(true)
            loadPdfWithPassword(viewModel, view, viewModel.encryptedDocumentPassword)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail
        ): Boolean {
            if (detail.didCrash()) {
                viewModel.setWebViewCrashed(true)
                return true
            }
            return false
        }
    }
}

private fun loadPdf(viewModel: PdfViewModel, webView: WebView?) {
    webView ?: return
    viewModel.prepareForLoad()
    webView.loadUrl("https://localhost/viewer/index.html")
}

private fun loadPdfWithPassword(viewModel: PdfViewModel, webView: WebView?, password: String) {
    webView ?: return
    viewModel.encryptedDocumentPassword = password
    webView.evaluateJavascript("loadDocument()", null)
}

internal fun jumpToPage(viewModel: PdfViewModel, webView: WebView?, selectedPage: Int) {
    webView ?: return
    val num = viewModel.numPages.value
    if (selectedPage in 1..num && viewModel.page.value != selectedPage) {
        viewModel.setPage(selectedPage)
        webView.evaluateJavascript("onRenderPage(0)", null)
        viewModel.showPageIndicator()
    }
}

private fun rotateDocument(viewModel: PdfViewModel, webView: WebView?, offset: Int) {
    webView ?: return
    var degrees = (viewModel.documentOrientationDegrees.value + offset) % 360
    if (degrees < 0) degrees += 360
    viewModel.setDocumentOrientationDegrees(degrees)
    webView.evaluateJavascript("onRenderPage(0)", null)
}

private fun zoomDocument(viewModel: PdfViewModel, webView: WebView?, ratio: Float) {
    webView ?: return
    viewModel.setZoomRatio(ratio.coerceIn(MIN_ZOOM_RATIO, MAX_ZOOM_RATIO))
    webView.evaluateJavascript("onRenderPage(1)", null)
}

private fun shareDocument(context: Context, viewModel: PdfViewModel) {
    val uri = viewModel.uri.value ?: return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        setDataAndTypeAndNormalize(uri, PDF_MIME)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, context.getString(R.string.action_share))
    )
}

private fun purgeWebView(webView: WebView) {
    webView.removeJavascriptInterface("channel")
    (webView.parent as? ViewGroup)?.removeView(webView)
    webView.destroy()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfTopAppBar(
    title: String,
    documentLoaded: Boolean,
    webViewCrashed: Boolean,
    webViewOk: Boolean,
    hasPages: Boolean,
    enabled: Boolean,
    page: Int,
    numPages: Int,
    hasOutline: Boolean,
    hasDocumentProperties: Boolean,
    hasUri: Boolean,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    onJumpToPage: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateCounterClockwise: () -> Unit,
    zoomRatioFlow: StateFlow<Float>,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCustomZoom: () -> Unit,
    onOutline: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onDocumentProperties: () -> Unit,
    onToggleTextLayer: () -> Unit,
    onCrashWebView: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        modifier = modifier,
        colors = darkTopAppBarColors(),
        actions = {
            if (documentLoaded && hasPages) {
                IconButton(onClick = onPrevious, enabled = enabled && page > 1) {
                    Icon(
                        painterResource(R.drawable.ic_navigate_before_24dp),
                        contentDescription = stringResource(R.string.action_previous)
                    )
                }
                IconButton(onClick = onNext, enabled = enabled && page < numPages) {
                    Icon(
                        painterResource(R.drawable.ic_navigate_next_24dp),
                        contentDescription = stringResource(R.string.action_next)
                    )
                }
                IconButton(onClick = onSearch, enabled = enabled) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.action_search)
                    )
                }
            }

            IconButton(onClick = onOpen, enabled = !webViewCrashed && webViewOk) {
                Icon(
                    painterResource(R.drawable.ic_open_file_24dp),
                    contentDescription = stringResource(R.string.action_open)
                )
            }

            if (documentLoaded) {
                IconButton(onClick = { onMenuToggle(true) }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = TestTags.OVERFLOW_MENU
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onMenuToggle(false) }
                ) {
                    if (hasPages) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_first)) },
                            onClick = { onMenuToggle(false); onFirst() },
                            enabled = enabled,
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_first_page_24dp),
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_last)) },
                            onClick = { onMenuToggle(false); onLast() },
                            enabled = enabled,
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_last_page_24dp),
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_jump_to_page)) },
                            onClick = { onMenuToggle(false); onJumpToPage() },
                            enabled = enabled,
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_pageview_24dp),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rotate_clockwise)) },
                        onClick = { onMenuToggle(false); onRotateClockwise() },
                        enabled = enabled,
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_rotate_right_24dp),
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rotate_counterclockwise)) },
                        onClick = { onMenuToggle(false); onRotateCounterClockwise() },
                        enabled = enabled,
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_rotate_left_24dp),
                                contentDescription = null
                            )
                        }
                    )
                    ZoomRow(
                        zoomRatioFlow = zoomRatioFlow,
                        onZoomIn = onZoomIn,
                        onZoomOut = onZoomOut,
                        onCustomZoom = { onMenuToggle(false); onCustomZoom() },
                        enabled = enabled
                    )
                    if (hasOutline) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_outline)) },
                            onClick = { onMenuToggle(false); onOutline() },
                            enabled = enabled,
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_outline_bulletlist_24dp),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    if (hasUri) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            onClick = { onMenuToggle(false); onShare() },
                            enabled = enabled,
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_share_24dp),
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save_as)) },
                            onClick = { onMenuToggle(false); onSaveAs() },
                            enabled = enabled,
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_save_24dp),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_view_document_properties)) },
                        onClick = { onMenuToggle(false); onDocumentProperties() },
                        enabled = enabled && hasDocumentProperties,
                        leadingIcon = {
                            Icon(Icons.Outlined.Info, contentDescription = null)
                        }
                    )
                    if (BuildConfig.DEBUG) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.debug_action_toggle_text_layer_visibility)) },
                            onClick = { onMenuToggle(false); onToggleTextLayer() },
                            enabled = enabled
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.debug_action_crash_webview)) },
                            onClick = { onMenuToggle(false); onCrashWebView() },
                            enabled = enabled
                        )
                    }
                }
            }
        }
    )
}

/**
 * Find-in-document bar. Replaces the top app bar while a search is active, the way Chrome,
 * Firefox and Acrobat all present find on Android.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAppBar(
    query: String,
    result: PdfViewModel.SearchResult,
    numPages: Int,
    onQueryChange: (String) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Held as TextFieldValue, and saved, so the caret does not jump to the start on a
    // configuration change — the same pattern the JumpToPage and CustomZoom dialogs use.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    }
    // Re-synced from an effect rather than during composition: typing updates `field` first and
    // `query` only after the ViewModel round trip, so a composition-time write could put the
    // pre-keystroke text back.
    LaunchedEffect(query) {
        if (field.text != query) {
            field = TextFieldValue(query, TextRange(query.length))
        }
    }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            focusRequester.requestFocus()
        }
    }

    val hasMatches = result.total > 0
    val countText = when {
        query.isEmpty() -> ""
        result.scanning && result.total == 0 -> ""
        // While the document is still being indexed the total is a lower bound, so it is shown
        // with a "+". Without that, "1/3" two seconds in reads as final when it will become
        // "1/247" once the scan finishes.
        result.truncated || result.scanning -> stringResource(
            R.string.search_match_count_partial, result.ordinal, result.total
        )
        else -> stringResource(R.string.search_match_count, result.ordinal, result.total)
    }

    Column {
        TopAppBar(
            // The modifier carries onGloballyPositioned, so it must sit on the bar itself and
            // not the Column: insetTop has to stay put when the progress bar comes and goes.
            modifier = modifier,
            colors = darkTopAppBarColors(),
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_close)
                    )
                }
            },
            title = {
                TextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onQueryChange(it.text)
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    trailingIcon = if (query.isEmpty()) null else ({
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    }),
                    colors = darkSearchFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            // Dismissing the IME is the only way to stop a match being scrolled
                            // behind the keyboard: edge to edge means the WebView is not resized.
                            focusManager.clearFocus()
                            if (hasMatches) onStep(true)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(TestTags.SEARCH_FIELD)
                )
            },
            actions = {
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        // Bounded so the text field keeps a usable width at large font scales and
                        // in narrow split-screen windows, where "128/1024+" would otherwise eat
                        // most of the bar.
                        .widthIn(max = 88.dp)
                        .testTag(TestTags.SEARCH_COUNT)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                IconButton(onClick = { onStep(false) }, enabled = hasMatches) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.search_previous)
                    )
                }
                IconButton(onClick = { onStep(true) }, enabled = hasMatches) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.search_next)
                    )
                }
            }
        )
        if (result.scanning && numPages > 0) {
            LinearProgressIndicator(
                progress = { result.scannedPages / numPages.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WebViewAlertScreen(
    title: String,
    message: String,
    showReload: Boolean,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_error_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (showReload) {
                    Button(
                        onClick = onReload,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag(TestTags.RELOAD_BUTTON)
                    ) {
                        Text(stringResource(R.string.reload))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomRow(
    zoomRatioFlow: StateFlow<Float>,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onCustomZoom: () -> Unit,
    enabled: Boolean
) {
    val zoomRatio by zoomRatioFlow.collectAsStateWithLifecycle()
    if (zoomRatio <= 0f) return

    val locale = currentLocale()
    val zoomFormat = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onZoomOut,
            enabled = enabled && previousZoomPreset(zoomRatio) != null
        ) {
            Icon(
                painterResource(R.drawable.ic_remove_24dp),
                contentDescription = stringResource(R.string.zoom_out)
            )
        }

        TextButton(
            onClick = onCustomZoom,
            enabled = enabled,
            modifier = Modifier.testTag(TestTags.ZOOM_PERCENTAGE)
            .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(zoomFormat.format(zoomRatio))
        }

        IconButton(
            onClick = onZoomIn,
            enabled = enabled && nextZoomPreset(zoomRatio) != null
        ) {
            Icon(
                painterResource(R.drawable.ic_add_24dp),
                contentDescription = stringResource(R.string.zoom_in)
            )
        }
    }
}

@Composable
private fun CustomZoomDialog(
    currentZoomRatio: Float,
    onZoom: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val minPercent = (MIN_ZOOM_RATIO * 100).roundToInt()
    val maxPercent = (MAX_ZOOM_RATIO * 100).roundToInt()
    val maxLength = maxPercent.toString().length

    val locale = currentLocale()
    val percentSymbol = remember(locale) {
        DecimalFormatSymbols.getInstance(locale).percent.toString()
    }
    val percentIsPrefix = remember(locale) {
        val formatted = NumberFormat.getPercentInstance(locale).format(1)
        val digitIndex = formatted.indexOfFirst { it.isDigit() }
        val symbolIndex = formatted.indexOfFirst { !it.isDigit() && !it.isWhitespace() }
        symbolIndex in 0 until digitIndex
    }

    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val initial = (currentZoomRatio * 100).roundToInt().toString()
        mutableStateOf(
            TextFieldValue(
                text = initial,
                selection = TextRange(0, initial.length)
            )
        )
    }
    val parsed = textFieldValue.text.toIntOrNull()
    val isValid = parsed != null && parsed in minPercent..maxPercent
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onZoom(it / 100f) } },
                enabled = isValid
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.zoom)) },
        text = {
            Column {
                Text(stringResource(R.string.zoom_range, minPercent, maxPercent))
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.text.filter { it.isDigit() }.take(maxLength)
                        textFieldValue = if (filtered == newValue.text) {
                            newValue
                        } else {
                            newValue.copy(
                                text = filtered,
                                selection = TextRange(
                                    minOf(newValue.selection.end, filtered.length)
                                )
                            )
                        }
                    },
                    singleLine = true,
                    isError = textFieldValue.text.isNotEmpty() && !isValid,
                    prefix = if (percentIsPrefix) ({ Text(percentSymbol) }) else null,
                    suffix = if (percentIsPrefix) null else ({ Text(percentSymbol) }),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValid) onZoom(parsed / 100f) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester)
                        .testTag(TestTags.CUSTOM_ZOOM_FIELD)
                )
            }
        }
    )
}

@Composable
private fun JumpToPageDialog(
    currentPage: Int,
    numPages: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val maxLength = numPages.toString().length
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val initial = currentPage.toString()
        mutableStateOf(
            TextFieldValue(
                text = initial,
                selection = TextRange(0, initial.length)
            )
        )
    }
    val parsed = textFieldValue.text.toIntOrNull()
    val isValid = parsed != null && parsed in 1..numPages
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onJump) },
                enabled = isValid
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.action_jump_to_page)) },
        text = {
            Column {
                Text("Page (1-$numPages)")
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        val filtered = newValue.text.filter { it.isDigit() }.take(maxLength)
                        textFieldValue = if (filtered == newValue.text) {
                            newValue
                        } else {
                            newValue.copy(
                                text = filtered,
                                selection = TextRange(
                                    minOf(newValue.selection.end, filtered.length)
                                )
                            )
                        }
                    },
                    singleLine = true,
                    isError = textFieldValue.text.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValid) parsed?.let(onJump) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .focusRequester(focusRequester)
                        .testTag(TestTags.JUMP_TO_PAGE_FIELD)
                )
            }
        }
    )
}

@Composable
private fun PageIndicator(page: Int, numPages: Int) {
    Box(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$page/$numPages",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DocumentPropertiesDialog(
    properties: Map<DocumentProperty, String>?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        title = {
            Text(
                if (!properties.isNullOrEmpty())
                    stringResource(R.string.action_view_document_properties)
                else
                    stringResource(R.string.document_properties_retrieval_failed)
            )
        },
        text = {
            if (!properties.isNullOrEmpty()) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    for ((property, value) in properties) {
                        val name = stringResource(property.nameResource)
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(name)
                                }
                                append(":\n")
                                append(value)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun PasswordPromptDialog(
    invalidPasswordEvent: Flow<Unit>,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        invalidPasswordEvent.collect {
            password = ""
            showError = true
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(stringResource(R.string.password_prompt_description)) },
        confirmButton = {
            TextButton(
                onClick = { if (password.isNotEmpty()) onSubmit(password) },
                enabled = password.isNotEmpty()
            ) {
                Text(stringResource(R.string.open))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { newValue ->
                    if (newValue != password) {
                        password = newValue
                        showError = false
                    }
                },
                label = { Text(stringResource(R.string.password_prompt_hint)) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.invalid_password)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (password.isNotEmpty()) onSubmit(password) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(TestTags.PASSWORD_FIELD)
            )
        }
    )
}

@Composable
@ReadOnlyComposable
@SuppressLint("NonObservableLocale")
private fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
        ?: Locale.getDefault()
