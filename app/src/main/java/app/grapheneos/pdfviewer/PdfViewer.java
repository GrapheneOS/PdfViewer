package app.grapheneos.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import app.grapheneos.pdfviewer.databinding.PdfviewerBinding;
import app.grapheneos.pdfviewer.fragment.DocumentPropertiesFragment;
import app.grapheneos.pdfviewer.fragment.JumpToPageFragment;
import app.grapheneos.pdfviewer.fragment.PasswordPromptFragment;
import app.grapheneos.pdfviewer.ktx.ViewKt;
import app.grapheneos.pdfviewer.loader.DocumentProperty;
import app.grapheneos.pdfviewer.outline.OutlineFragment;
import app.grapheneos.pdfviewer.viewModel.PdfViewModel;

public class PdfViewer extends AppCompatActivity {
    private static final String TAG = "PdfViewer";

    private static final int MIN_WEBVIEW_RELEASE = 133;

    private static final String CONTENT_SECURITY_POLICY =
        "default-src 'none'; " +
        "form-action 'none'; " +
        "connect-src 'self'; " +
        "img-src blob: 'self'; " +
        "script-src 'self'; " +
        "style-src 'self'; " +
        "frame-ancestors 'none'; " +
        "base-uri 'none'";

    private static final String PERMISSIONS_POLICY =
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
        "xr-spatial-tracking=()";

    private static final float MIN_ZOOM_RATIO = 0.2f;
    private static final float MAX_ZOOM_RATIO = 10f;
    private static final int MAX_RENDER_PIXELS = 1 << 23; // 8 mega-pixels
    private static final int ALPHA_LOW = 130;
    private static final int ALPHA_HIGH = 255;
    private static final int STATE_LOADED = 1;
    private static final int STATE_END = 2;
    private static final int PADDING = 10;

    private final Object streamLock = new Object();

    private volatile float zoomFocusX = 0f;
    private volatile float zoomFocusY = 0f;
    private int documentState;
    private volatile InputStream inputStream;
    private boolean documentPropertiesLoaded;

    private PdfviewerBinding binding;
    private TextView textView;
    private Toast toast;
    private Snackbar snackbar;
    private PasswordPromptFragment passwordPromptFragment;
    public PdfViewModel viewModel;

    private final ActivityResultLauncher<Intent> openDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result == null) return;
                if (result.getResultCode() != RESULT_OK) return;
                Intent resultData = result.getData();
                if (resultData != null) {
                    handleNewUri(resultData.getData());
                    viewModel.setPage(1);
                    documentPropertiesLoaded = false;
                    viewModel.clearDocumentProperties();
                    viewModel.setEncryptedDocumentPassword("");
                    viewModel.clearOutline();
                    loadPdf();
                    invalidateOptionsMenu();
                }
            });

    private final ActivityResultLauncher<Intent> saveAsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result == null) return;
                if (result.getResultCode() != RESULT_OK) return;
                Intent resultData = result.getData();
                if (resultData != null) {
                    Uri path = resultData.getData();
                    if (path != null) {
                        saveDocumentAs(path);
                    }
                }
            });

    private class Channel {
        @JavascriptInterface
        public void setHasDocumentOutline(final boolean hasOutline) {
            runOnUiThread(() -> viewModel.setHasOutline(hasOutline));
        }

        @JavascriptInterface
        public void setDocumentOutline(final String outline) {
            runOnUiThread(() -> viewModel.parseOutlineString(outline));
        }

        @JavascriptInterface
        public int getPage() {
            return viewModel.getPage();
        }

        @JavascriptInterface
        public float getZoomRatio() {
            return viewModel.getZoomRatio();
        }

        @JavascriptInterface
        public void setZoomRatio(final float ratio) {
            runOnUiThread(() ->
                    viewModel.setZoomRatio(Math.max(Math.min(ratio, MAX_ZOOM_RATIO), MIN_ZOOM_RATIO))
            );
        }

        @JavascriptInterface
        public int getMaxRenderPixels() {
            return MAX_RENDER_PIXELS;
        }

        @JavascriptInterface
        public float getZoomFocusX() {
            return zoomFocusX;
        }

        @JavascriptInterface
        public float getZoomFocusY() {
            return zoomFocusY;
        }

        @JavascriptInterface
        public float getMinZoomRatio() {
            return MIN_ZOOM_RATIO;
        }

        @JavascriptInterface
        public float getMaxZoomRatio() {
            return MAX_ZOOM_RATIO;
        }

        @JavascriptInterface
        public int getDocumentOrientationDegrees() {
            return viewModel.getDocumentOrientationDegrees();
        }

        @JavascriptInterface
        public void setNumPages(int numPages) {
            viewModel.setNumPages(numPages);
            runOnUiThread(PdfViewer.this::invalidateOptionsMenu);
        }

        @JavascriptInterface
        public void setDocumentProperties(final String properties) {
            if (documentPropertiesLoaded) {
                throw new SecurityException("setDocumentProperties already called");
            }
            documentPropertiesLoaded = true;
            final int numPages = viewModel.getNumPages();
            final Uri uri = viewModel.getUri();
            runOnUiThread(() -> viewModel.loadDocumentProperties(properties, numPages, uri));
        }

        @JavascriptInterface
        public void showPasswordPrompt() {
            runOnUiThread(() -> {
                if (!getPasswordPromptFragment().isAdded()) {
                    getPasswordPromptFragment().show(getSupportFragmentManager(), PasswordPromptFragment.class.getName());
                }
            });
            viewModel.passwordMissing();
        }

        @JavascriptInterface
        public void invalidPassword() {
            viewModel.invalid();
        }

        @JavascriptInterface
        public void onLoaded() {
            viewModel.validated();
            runOnUiThread(() -> {
                if (getPasswordPromptFragment().isAdded()) {
                    getPasswordPromptFragment().dismiss();
                }
            });
        }

        @JavascriptInterface
        public String getPassword() {
            return viewModel.getEncryptedDocumentPassword();
        }
    }

    private void showWebViewCrashed() {
        binding.webviewAlertTitle.setText(getString(R.string.webview_crash_title));
        binding.webviewAlertMessage.setText(getString(R.string.webview_crash_message));
        binding.webviewAlertLayout.setVisibility(View.VISIBLE);
        binding.webviewAlertReload.setVisibility(View.VISIBLE);
        binding.webview.setVisibility(View.GONE);
    }

    @Override
    @SuppressLint({"SetJavaScriptEnabled"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = PdfviewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        viewModel = new ViewModelProvider(this).get(PdfViewModel.class);

        viewModel.getOutline().observe(this, requested -> {
            if (requested instanceof PdfViewModel.OutlineStatus.Requested) {
                viewModel.setLoadingOutline();
                binding.webview.evaluateJavascript("getDocumentOutline()", null);
            }
        });

        viewModel.getSaveError().observe(this, error -> {
            if (error) {
                snackbar.setText(R.string.error_while_saving).show();
                viewModel.clearSaveError();
            }
        });

        viewModel.getDocumentProperties().observe(this, properties -> {
            setToolbarTitleWithDocumentName();
            invalidateOptionsMenu();
        });

        getSupportFragmentManager().setFragmentResultListener(OutlineFragment.RESULT_KEY, this,
                (requestKey, result) -> {
            final int newPage = result.getInt(OutlineFragment.PAGE_KEY, -1);
            if (viewModel.shouldAbortOutline()) {
                Log.d(TAG, "aborting outline operations");
                binding.webview.evaluateJavascript("abortDocumentOutline()", null);
                viewModel.clearOutline();
            } else {
                onJumpToPageInDocument(newPage);
            }
        });

        // Margins for the toolbar are needed, so that content of the toolbar
        // is not covered by a system button navigation bar when in landscape.
        KtUtilsKt.applySystemBarMargins(binding.toolbar, false);

        binding.webview.setBackgroundColor(Color.TRANSPARENT);

        final WebSettings settings = binding.webview.getSettings();
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setMinimumFontSize(1);

        CookieManager.getInstance().setAcceptCookie(false);

        binding.webview.addJavascriptInterface(new Channel(), "channel");

        binding.webview.setWebViewClient(new WebViewClient() {
            private WebResourceResponse fromAsset(final String mime, final String path) {
                try {
                    InputStream inputStream = getAssets().open(path.substring(1));
                    return new WebResourceResponse(mime, null, inputStream);
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (viewModel.getUri() == null) return null;

                if (!"GET".equals(request.getMethod())) {
                    return null;
                }

                final Uri url = request.getUrl();
                if (!"localhost".equals(url.getHost())) {
                    return null;
                }

                final String path = url.getPath();
                Log.d(TAG, "path " + path);

                if ("/placeholder.pdf".equals(path)) {
                    synchronized (streamLock) {
                        maybeCloseInputStream();
                        try {
                            inputStream = getContentResolver().openInputStream(viewModel.getUri());
                            if (inputStream == null) {
                                throw new FileNotFoundException();
                            }
                        } catch (final FileNotFoundException | IllegalArgumentException |
                                       IllegalStateException | SecurityException ignored) {
                            runOnUiThread(() -> snackbar.setText(R.string.error_while_opening).show());
                            return null;
                        }
                        return new WebResourceResponse("application/pdf", null, inputStream);
                    }
                }

                if ("/viewer/index.html".equals(path)) {
                    final WebResourceResponse response = fromAsset("text/html", path);
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Content-Security-Policy", CONTENT_SECURITY_POLICY);
                    headers.put("Permissions-Policy", PERMISSIONS_POLICY);
                    headers.put("X-Content-Type-Options", "nosniff");
                    response.setResponseHeaders(headers);
                    return response;
                }

                if ("/viewer/main.css".equals(path)) {
                    return fromAsset("text/css", path);
                }

                if ("/viewer/js/index.js".equals(path) || "/viewer/js/worker.js".equals(path) ||
                        "/viewer/wasm/openjpeg_nowasm_fallback.js".equals(path)) {
                    return fromAsset("application/javascript", path);
                }

                if ("/viewer/wasm/openjpeg.wasm".equals(path) ||
                        "/viewer/wasm/qcms_bg.wasm".equals(path)) {
                    return fromAsset("application/wasm", path);
                }

                if (path != null && path.matches("^/viewer/iccs/.*\\.icc$")) {
                    return fromAsset("application/vnd.iccprofile", path);
                }

                if (path != null && path.matches("^/viewer/cmaps/.*\\.bcmap$")) {
                    return fromAsset("application/octet-stream", path);
                }

                if (path != null && path.matches("^/viewer/standard_fonts/.*\\.pfb$")) {
                    return fromAsset("application/octet-stream", path);
                }

                if (path != null && path.matches("^/viewer/standard_fonts/.*\\.ttf$")) {
                    return fromAsset("font/sfnt", path);
                }

                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                documentState = STATE_LOADED;
                invalidateOptionsMenu();
                loadPdfWithPassword(viewModel.getEncryptedDocumentPassword());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (detail.didCrash()) {
                    viewModel.setWebViewCrashed(true);
                    showWebViewCrashed();
                    invalidateOptionsMenu();
                    purgeWebView();
                    return true;
                }
                return false;
            }
        });

        GestureHelper.attach(PdfViewer.this, binding.webview,
                new GestureHelper.GestureListener() {
                    @Override
                    public boolean onTapUp() {
                        if (viewModel.getUri() != null) {
                            binding.webview.evaluateJavascript("isTextSelected()", selection -> {
                                if (!Boolean.parseBoolean(selection)) {
                                    if (getSupportActionBar() != null && getSupportActionBar().isShowing()) {
                                        hideSystemUi();
                                    } else {
                                        showSystemUi();
                                    }
                                }
                            });
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onZoom(float scaleFactor, float focusX, float focusY) {
                        zoom(scaleFactor, focusX, focusY, false);
                    }

                    @Override
                    public void onZoomEnd() {
                        zoomEnd();
                    }
                });

        textView = new TextView(this);
        textView.setBackgroundColor(Color.DKGRAY);
        textView.setTextColor(ColorStateList.valueOf(Color.WHITE));
        textView.setTextSize(18);
        textView.setPadding(PADDING, 0, PADDING, 0);

        snackbar = Snackbar.make(binding.getRoot(), "", Snackbar.LENGTH_LONG);

        final Intent intent = getIntent();
        if (savedInstanceState == null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            final String type = intent.getType();
            if (!"application/pdf".equals(type) && type != null) {
                snackbar.setText(R.string.invalid_mime_type).show();
                return;
            }
            if (type == null) {
                Log.w(TAG, "MIME type is null, but we'll try to load it anyway");
            }
            handleNewUri(intent.getData());
            viewModel.setPage(1);
        }

        binding.webviewAlertReload.setOnClickListener(v -> {
            viewModel.setWebViewCrashed(false);
            recreate();
        });

        if (viewModel.getWebViewCrashed()) {
            showWebViewCrashed();
        } else if (viewModel.getUri() != null) {
            if ("file".equals(viewModel.getUri().getScheme())) {
                snackbar.setText(R.string.legacy_file_uri).show();
                return;
            }
            loadPdf();
        }
    }

    private void purgeWebView() {
        binding.webview.removeJavascriptInterface("channel");
        binding.getRoot().removeView(binding.webview);
        binding.webview.destroy();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        purgeWebView();
        maybeCloseInputStream();
    }

    void maybeCloseInputStream() {
        synchronized (streamLock) {
            InputStream stream = inputStream;
            if (stream == null) {
                return;
            }
            inputStream = null;
            try {
                stream.close();
            } catch (IOException ignored) {}
        }
    }

    private PasswordPromptFragment getPasswordPromptFragment() {
        if (passwordPromptFragment == null) {
            final Fragment fragment = getSupportFragmentManager().findFragmentByTag(PasswordPromptFragment.class.getName());
            if (fragment != null) {
                passwordPromptFragment = (PasswordPromptFragment) fragment;
            } else {
                passwordPromptFragment = new PasswordPromptFragment();
            }
        }
        return passwordPromptFragment;
    }

    private void setToolbarTitleWithDocumentName() {
        String documentName = getCurrentDocumentName();
        if (documentName != null && !documentName.isEmpty()) {
            getSupportActionBar().setTitle(documentName);
        } else {
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!viewModel.getWebViewCrashed()) {
            // The user could have left the activity to update the WebView
            invalidateOptionsMenu();
            if (getWebViewRelease() >= MIN_WEBVIEW_RELEASE) {
                binding.webviewAlertLayout.setVisibility(View.GONE);
                binding.webview.setVisibility(View.VISIBLE);
            } else {
                binding.webview.setVisibility(View.GONE);
                binding.webviewAlertTitle.setText(getString(R.string.webview_out_of_date_title));
                binding.webviewAlertMessage.setText(getString(R.string.webview_out_of_date_message, getWebViewRelease(), MIN_WEBVIEW_RELEASE));
                binding.webviewAlertLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    private int getWebViewRelease() {
        PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
        String webViewVersionName = webViewPackage.versionName;
        return Integer.parseInt(webViewVersionName.substring(0, webViewVersionName.indexOf(".")));
    }

    private void loadPdf() {
        documentPropertiesLoaded = false;
        documentState = 0;
        showSystemUi();
        invalidateOptionsMenu();
        binding.webview.loadUrl("https://localhost/viewer/index.html");
    }

    public void loadPdfWithPassword(final String password) {
        viewModel.setEncryptedDocumentPassword(password);
        binding.webview.evaluateJavascript("loadDocument()", null);
    }

    private void renderPage(final int zoom) {
        binding.webview.evaluateJavascript("onRenderPage(" + zoom + ")", null);
    }

    private void documentOrientationChanged(final int orientationDegreesOffset) {
        int degrees = (viewModel.getDocumentOrientationDegrees() + orientationDegreesOffset) % 360;
        if (degrees < 0) {
            degrees += 360;
        }
        viewModel.setDocumentOrientationDegrees(degrees);
        renderPage(0);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        openDocumentLauncher.launch(intent);
    }

    private void shareDocument() {
        if (viewModel.getUri() != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setDataAndTypeAndNormalize(viewModel.getUri(), "application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, viewModel.getUri());
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
        } else {
            Log.w(TAG, "Cannot share unexpected null URI");
        }
    }

    private void zoom(float scaleFactor, float focusX, float focusY, boolean end) {
        viewModel.setZoomRatio(Math.min(Math.max(viewModel.getZoomRatio() * scaleFactor, MIN_ZOOM_RATIO), MAX_ZOOM_RATIO));
        zoomFocusX = focusX;
        zoomFocusY = focusY;
        renderPage(end ? 1 : 2);
        invalidateOptionsMenu();
    }

    private void zoomEnd() {
        renderPage(1);
    }

    private static void enableDisableMenuItem(MenuItem item, boolean enable) {
        item.setEnabled(enable);
        if (item.getIcon() != null) {
            item.getIcon().setAlpha(enable ? ALPHA_HIGH : ALPHA_LOW);
        }
    }

    public void onJumpToPageInDocument(final int selected_page) {
        if (selected_page >= 1 && selected_page <= viewModel.getNumPages() && viewModel.getPage() != selected_page) {
            viewModel.setPage(selected_page);
            renderPage(0);
            showPageNumber();
            invalidateOptionsMenu();
        }
    }

    private void showSystemUi() {
        ViewKt.showSystemUi(binding.getRoot(), getWindow());
        getSupportActionBar().show();
    }

    private void hideSystemUi() {
        ViewKt.hideSystemUi(binding.getRoot(), getWindow());
        getSupportActionBar().hide();
    }

    private void showPageNumber() {
        if (toast != null) {
            toast.cancel();
        }
        textView.setText(String.format("%s/%s", viewModel.getPage(), viewModel.getNumPages()));
        toast = new Toast(this);
        toast.setGravity(Gravity.BOTTOM | Gravity.END, PADDING, PADDING);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(textView);
        toast.show();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pdf_viewer, menu);
        if (BuildConfig.DEBUG) {
            inflater.inflate(R.menu.pdf_viewer_debug, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        final ArrayList<Integer> ids = new ArrayList<>(Arrays.asList(R.id.action_jump_to_page,
                R.id.action_next, R.id.action_previous, R.id.action_first, R.id.action_last,
                R.id.action_rotate_clockwise, R.id.action_rotate_counterclockwise,
                R.id.action_view_document_properties, R.id.action_share, R.id.action_save_as,
                R.id.action_outline));
        if (BuildConfig.DEBUG) {
            ids.add(R.id.debug_action_toggle_text_layer_visibility);
            ids.add(R.id.debug_action_crash_webview);
        }
        if (documentState < STATE_LOADED) {
            for (final int id : ids) {
                final MenuItem item = menu.findItem(id);
                if (item.isVisible()) {
                    item.setVisible(false);
                }
            }
        } else if (documentState == STATE_LOADED) {
            for (final int id : ids) {
                final MenuItem item = menu.findItem(id);
                if (!item.isVisible()) {
                    item.setVisible(true);
                }
            }
            documentState = STATE_END;
        }


        enableDisableMenuItem(menu.findItem(R.id.action_open),
                !viewModel.getWebViewCrashed() && getWebViewRelease() >= MIN_WEBVIEW_RELEASE);
        enableDisableMenuItem(menu.findItem(R.id.action_share), viewModel.getUri() != null);
        enableDisableMenuItem(menu.findItem(R.id.action_next), viewModel.getPage() < viewModel.getNumPages());
        enableDisableMenuItem(menu.findItem(R.id.action_previous), viewModel.getPage() > 1);
        enableDisableMenuItem(menu.findItem(R.id.action_save_as), viewModel.getUri() != null);
        enableDisableMenuItem(menu.findItem(R.id.action_view_document_properties),
                viewModel.getDocumentProperties().getValue() != null);

        menu.findItem(R.id.action_outline).setVisible(viewModel.hasOutline());

        if (viewModel.getWebViewCrashed()) {
            for (final int id : ids) {
                enableDisableMenuItem(menu.findItem(id), false);
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_previous) {
            onJumpToPageInDocument(viewModel.getPage() - 1);
            return true;
        } else if (itemId == R.id.action_next) {
            onJumpToPageInDocument(viewModel.getPage() + 1);
            return true;
        } else if (itemId == R.id.action_first) {
            onJumpToPageInDocument(1);
            return true;
        } else if (itemId == R.id.action_last) {
            onJumpToPageInDocument(viewModel.getNumPages());
            return true;
        } else if (itemId == R.id.action_open) {
            openDocument();
            return true;
        } else if (itemId == R.id.action_rotate_clockwise) {
            documentOrientationChanged(90);
            return true;
        } else if (itemId == R.id.action_rotate_counterclockwise) {
            documentOrientationChanged(-90);
            return true;
        } else if (itemId == R.id.action_outline) {
            OutlineFragment outlineFragment =
                    OutlineFragment.newInstance(viewModel.getPage(), getCurrentDocumentName());
            getSupportFragmentManager().beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    // fullscreen fragment, since content root view == activity's root view
                    .add(android.R.id.content, outlineFragment)
                    .addToBackStack(null)
                    .commit();
            return true;
        } else if (itemId == R.id.action_view_document_properties) {
            DocumentPropertiesFragment
                    .newInstance()
                    .show(getSupportFragmentManager(), DocumentPropertiesFragment.TAG);
            return true;
        } else if (itemId == R.id.action_jump_to_page) {
            new JumpToPageFragment()
                .show(getSupportFragmentManager(), JumpToPageFragment.TAG);
            return true;
        } else if (itemId == R.id.action_share) {
            shareDocument();
            return true;
        } else if (itemId == R.id.action_save_as) {
            saveDocument();
        } else if (itemId == R.id.debug_action_toggle_text_layer_visibility) {
            binding.webview.evaluateJavascript("toggleTextLayerVisibility()", null);
            return true;
        } else if (itemId == R.id.debug_action_crash_webview) {
            binding.webview.loadUrl("chrome://crash");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void saveDocument() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, getCurrentDocumentName());
        saveAsLauncher.launch(intent);
    }

    private String getCurrentDocumentName() {
        Map<DocumentProperty, String> properties = viewModel.getDocumentProperties().getValue();
        if (properties == null || properties.isEmpty()) return "";
        String fileName = properties.getOrDefault(DocumentProperty.FileName, "");
        String title = properties.getOrDefault(DocumentProperty.Title, "");
        return !(fileName != null && fileName.isEmpty()) ? fileName : title;
    }

    private void saveDocumentAs(final Uri saveUri) {
        if (viewModel.getUri() == null) return;
        viewModel.saveDocumentAs(getContentResolver(), viewModel.getUri(), saveUri);
    }

    private void handleNewUri(Uri newUri) {
        Uri oldUri = viewModel.getUri();
        if (oldUri != null) {
            try {
                getContentResolver().releasePersistableUriPermission(oldUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}
        }
        try {
            getContentResolver().takePersistableUriPermission(newUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}
        viewModel.setUri(newUri);
    }
}
