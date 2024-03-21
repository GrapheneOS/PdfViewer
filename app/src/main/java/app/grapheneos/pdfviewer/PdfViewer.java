package app.grapheneos.pdfviewer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.google.android.material.snackbar.Snackbar;

import app.grapheneos.pdfviewer.databinding.PdfviewerBinding;
import app.grapheneos.pdfviewer.fragment.DocumentPropertiesFragment;
import app.grapheneos.pdfviewer.fragment.PasswordPromptFragment;
import app.grapheneos.pdfviewer.fragment.JumpToPageFragment;
import app.grapheneos.pdfviewer.ktx.ViewKt;
import app.grapheneos.pdfviewer.loader.DocumentPropertiesAsyncTaskLoader;
import app.grapheneos.pdfviewer.viewModel.PasswordStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PdfViewer extends AppCompatActivity implements LoaderManager.LoaderCallbacks<List<CharSequence>> {
    public static final String TAG = "PdfViewer";

    private static final String STATE_URI = "uri";
    private static final String STATE_PAGE = "page";
    private static final String STATE_ZOOM_RATIO = "zoomRatio";
    private static final String STATE_DOCUMENT_ORIENTATION_DEGREES = "documentOrientationDegrees";
    private static final String STATE_ENCRYPTED_DOCUMENT_PASSWORD = "encrypted_document_password";
    private static final String KEY_PROPERTIES = "properties";
    private static final int MIN_WEBVIEW_RELEASE = 92;

    private static final String CONTENT_SECURITY_POLICY =
        "default-src 'none'; " +
        "form-action 'none'; " +
        "connect-src https://localhost/placeholder.pdf; " +
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
    private static final float MAX_ZOOM_RATIO = 1.5f;
    private static final int ALPHA_LOW = 130;
    private static final int ALPHA_HIGH = 255;
    private static final int STATE_LOADED = 1;
    private static final int STATE_END = 2;
    private static final int PADDING = 10;

    private Uri mUri;
    public int mPage;
    public int mNumPages;
    private float mZoomRatio = 1f;
    private int mDocumentOrientationDegrees;
    private int mDocumentState;
    private String mEncryptedDocumentPassword;
    private List<CharSequence> mDocumentProperties;
    private InputStream mInputStream;

    private PdfviewerBinding binding;
    private TextView mTextView;
    private Toast mToast;
    private Snackbar snackbar;
    private PasswordPromptFragment mPasswordPromptFragment;
    public PasswordStatus passwordValidationViewModel;

    private final ActivityResultLauncher<Intent> openDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result == null) return;
                if (result.getResultCode() != RESULT_OK) return;
                Intent resultData = result.getData();
                if (resultData != null) {
                    mUri = result.getData().getData();
                    mPage = 1;
                    mDocumentProperties = null;
                    mEncryptedDocumentPassword = "";
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
        public int getPage() {
            return mPage;
        }

        @JavascriptInterface
        public float getZoomRatio() {
            return mZoomRatio;
        }

        @JavascriptInterface
        public void setZoomRatio(final float ratio) {
            mZoomRatio = Math.max(Math.min(ratio, MAX_ZOOM_RATIO), MIN_ZOOM_RATIO);
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
            return mDocumentOrientationDegrees;
        }

        @JavascriptInterface
        public void setNumPages(int numPages) {
            mNumPages = numPages;
            runOnUiThread(PdfViewer.this::invalidateOptionsMenu);
        }

        @JavascriptInterface
        public void setDocumentProperties(final String properties) {
            if (mDocumentProperties != null) {
                throw new SecurityException("mDocumentProperties not null");
            }

            final Bundle args = new Bundle();
            args.putString(KEY_PROPERTIES, properties);
            runOnUiThread(() -> LoaderManager.getInstance(PdfViewer.this).restartLoader(DocumentPropertiesAsyncTaskLoader.ID, args, PdfViewer.this));
        }

        @JavascriptInterface
        public void showPasswordPrompt() {
            if (!getPasswordPromptFragment().isAdded()) {
                getPasswordPromptFragment().show(getSupportFragmentManager(), PasswordPromptFragment.class.getName());
            }
            passwordValidationViewModel.passwordMissing();
        }

        @JavascriptInterface
        public void invalidPassword() {
            runOnUiThread(() -> passwordValidationViewModel.invalid());
        }

        @JavascriptInterface
        public void onLoaded() {
            passwordValidationViewModel.validated();
            if (getPasswordPromptFragment().isAdded()) {
                getPasswordPromptFragment().dismiss();
            }
        }

        @JavascriptInterface
        public String getPassword() {
            return mEncryptedDocumentPassword != null ? mEncryptedDocumentPassword : "";
        }
    }

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = PdfviewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        passwordValidationViewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(PasswordStatus.class);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Margins for the toolbar are needed, so that content of the toolbar
        // is not covered by a system button navigation bar when in landscape.
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.leftMargin = insets.left;
            mlp.rightMargin = insets.right;
            v.setLayoutParams(mlp);
            return windowInsets;
        });

        binding.webview.setBackgroundColor(Color.TRANSPARENT);

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        final WebSettings settings = binding.webview.getSettings();
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
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
                    maybeCloseInputStream();
                    try {
                        mInputStream = getContentResolver().openInputStream(mUri);
                    } catch (FileNotFoundException ignored) {
                        snackbar.setText(R.string.error_while_opening).show();
                    }
                    return new WebResourceResponse("application/pdf", null, mInputStream);
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

                if ("/viewer/js/index.js".equals(path) || "/viewer/js/worker.js".equals(path)) {
                    return fromAsset("application/javascript", path);
                }

                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mDocumentState = STATE_LOADED;
                invalidateOptionsMenu();
                loadPdfWithPassword(mEncryptedDocumentPassword);
            }
        });

        GestureHelper.attach(PdfViewer.this, binding.webview,
                new GestureHelper.GestureListener() {
                    @Override
                    public boolean onTapUp() {
                        if (mUri != null) {
                            binding.webview.evaluateJavascript("isTextSelected()", selection -> {
                                if (!Boolean.parseBoolean(selection)) {
                                    if (getSupportActionBar().isShowing()) {
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
                    public void onSwipeLeft() {
                        onJumpToPageInDocument(mPage + 1);
                    }

                    @Override
                    public void onSwipeRight() {
                        onJumpToPageInDocument(mPage - 1);
                    }

                    @Override
                    public void onZoomIn(float value) {
                        zoomIn(value, false);
                    }

                    @Override
                    public void onZoomOut(float value) {
                        zoomOut(value, false);
                    }

                    @Override
                    public void onZoomEnd() {
                        zoomEnd();
                    }
                });

        mTextView = new TextView(this);
        mTextView.setBackgroundColor(Color.DKGRAY);
        mTextView.setTextColor(ColorStateList.valueOf(Color.WHITE));
        mTextView.setTextSize(18);
        mTextView.setPadding(PADDING, 0, PADDING, 0);

        // If loaders are not being initialized in onCreate(), the result will not be delivered
        // after orientation change (See FragmentHostCallback), thus initialize the
        // loader manager impl so that the result will be delivered.
        LoaderManager.getInstance(this);

        snackbar = Snackbar.make(binding.getRoot(), "", Snackbar.LENGTH_LONG);

        final Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (!"application/pdf".equals(intent.getType())) {
                snackbar.setText(R.string.invalid_mime_type).show();
                return;
            }
            mUri = intent.getData();
            mPage = 1;
        }

        if (savedInstanceState != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @SuppressWarnings("deprecation")
                final Uri uri = savedInstanceState.getParcelable(STATE_URI);
                mUri = uri;
            } else {
                mUri = savedInstanceState.getParcelable(STATE_URI, Uri.class);
            }
            mPage = savedInstanceState.getInt(STATE_PAGE);
            mZoomRatio = savedInstanceState.getFloat(STATE_ZOOM_RATIO);
            mDocumentOrientationDegrees = savedInstanceState.getInt(STATE_DOCUMENT_ORIENTATION_DEGREES);
            mEncryptedDocumentPassword = savedInstanceState.getString(STATE_ENCRYPTED_DOCUMENT_PASSWORD);
        }

        if (mUri != null) {
            if ("file".equals(mUri.getScheme())) {
                snackbar.setText(R.string.legacy_file_uri).show();
                return;
            }

            loadPdf();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.webview.removeJavascriptInterface("channel");
        binding.getRoot().removeView(binding.webview);
        binding.webview.destroy();
        maybeCloseInputStream();
    }

    void maybeCloseInputStream() {
        InputStream stream = mInputStream;
        if (stream == null) {
            return;
        }
        mInputStream = null;
        try {
            stream.close();
        } catch (IOException ignored) {}
    }

    private PasswordPromptFragment getPasswordPromptFragment() {
        if (mPasswordPromptFragment == null) {
            final Fragment fragment = getSupportFragmentManager().findFragmentByTag(PasswordPromptFragment.class.getName());
            if (fragment != null) {
                mPasswordPromptFragment = (PasswordPromptFragment) fragment;
            } else {
                mPasswordPromptFragment = new PasswordPromptFragment();
            }
        }
        return mPasswordPromptFragment;
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

        // The user could have left the activity to update the WebView
        invalidateOptionsMenu();
        if (getWebViewRelease() >= MIN_WEBVIEW_RELEASE) {
            binding.webviewOutOfDateLayout.setVisibility(View.GONE);
            binding.webview.setVisibility(View.VISIBLE);
        } else {
            binding.webview.setVisibility(View.GONE);
            binding.webviewOutOfDateMessage.setText(getString(R.string.webview_out_of_date_message, getWebViewRelease(), MIN_WEBVIEW_RELEASE));
            binding.webviewOutOfDateLayout.setVisibility(View.VISIBLE);
        }
    }

    private int getWebViewRelease() {
        PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
        String webViewVersionName = webViewPackage.versionName;
        return Integer.parseInt(webViewVersionName.substring(0, webViewVersionName.indexOf(".")));
    }

    @NonNull
    @Override
    public Loader<List<CharSequence>> onCreateLoader(int id, Bundle args) {
        return new DocumentPropertiesAsyncTaskLoader(this, args.getString(KEY_PROPERTIES), mNumPages, mUri);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<CharSequence>> loader, List<CharSequence> data) {
        mDocumentProperties = data;
        setToolbarTitleWithDocumentName();
        LoaderManager.getInstance(this).destroyLoader(DocumentPropertiesAsyncTaskLoader.ID);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<CharSequence>> loader) {
        mDocumentProperties = null;
    }

    private void loadPdf() {
        try {
            if (mInputStream != null) {
                mInputStream.close();
            }
            mInputStream = getContentResolver().openInputStream(mUri);
        } catch (IOException e) {
            snackbar.setText(R.string.error_while_opening).show();
            return;
        }

        mDocumentState = 0;
        showSystemUi();
        invalidateOptionsMenu();
        binding.webview.loadUrl("https://localhost/viewer/index.html");
    }

    public void loadPdfWithPassword(final String password) {
        mEncryptedDocumentPassword = password;
        binding.webview.evaluateJavascript("loadDocument()", null);
    }

    private void renderPage(final int zoom) {
        binding.webview.evaluateJavascript("onRenderPage(" + zoom + ")", null);
    }

    private void documentOrientationChanged(final int orientationDegreesOffset) {
        mDocumentOrientationDegrees = (mDocumentOrientationDegrees + orientationDegreesOffset) % 360;
        if (mDocumentOrientationDegrees < 0) {
            mDocumentOrientationDegrees += 360;
        }
        renderPage(0);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        openDocumentLauncher.launch(intent);
    }

    private void shareDocument() {
        if (mUri != null) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setDataAndTypeAndNormalize(mUri, "application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, mUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
        } else {
            Log.w(TAG, "Cannot share unexpected null URI");
        }
    }

    private void zoomIn(float value, boolean end) {
        if (mZoomRatio < MAX_ZOOM_RATIO) {
            mZoomRatio = Math.min(mZoomRatio + value, MAX_ZOOM_RATIO);
            renderPage(end ? 1 : 2);
            invalidateOptionsMenu();
        }
    }

    private void zoomOut(float value, boolean end) {
        if (mZoomRatio > MIN_ZOOM_RATIO) {
            mZoomRatio = Math.max(mZoomRatio - value, MIN_ZOOM_RATIO);
            renderPage(end ? 1 : 2);
            invalidateOptionsMenu();
        }
    }

    private void zoomEnd() {
        renderPage(1);
    }

    private static void enableDisableMenuItem(MenuItem item, boolean enable) {
        if (enable) {
            item.setEnabled(true);
            item.getIcon().setAlpha(ALPHA_HIGH);
        } else {
            item.setEnabled(false);
            item.getIcon().setAlpha(ALPHA_LOW);
        }
    }

    public void onJumpToPageInDocument(final int selected_page) {
        if (selected_page >= 1 && selected_page <= mNumPages && mPage != selected_page) {
            mPage = selected_page;
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
        savedInstanceState.putInt(STATE_PAGE, mPage);
        savedInstanceState.putFloat(STATE_ZOOM_RATIO, mZoomRatio);
        savedInstanceState.putInt(STATE_DOCUMENT_ORIENTATION_DEGREES, mDocumentOrientationDegrees);
        savedInstanceState.putString(STATE_ENCRYPTED_DOCUMENT_PASSWORD, mEncryptedDocumentPassword);
    }

    private void showPageNumber() {
        if (mToast != null) {
            mToast.cancel();
        }
        mTextView.setText(String.format("%s/%s", mPage, mNumPages));
        mToast = new Toast(getApplicationContext());
        mToast.setGravity(Gravity.BOTTOM | Gravity.END, PADDING, PADDING);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.setView(mTextView);
        mToast.show();
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
                R.id.action_view_document_properties, R.id.action_share, R.id.action_save_as));
        if (BuildConfig.DEBUG) {
            ids.add(R.id.debug_action_toggle_text_layer_visibility);
        }
        if (mDocumentState < STATE_LOADED) {
            for (final int id : ids) {
                final MenuItem item = menu.findItem(id);
                if (item.isVisible()) {
                    item.setVisible(false);
                }
            }
        } else if (mDocumentState == STATE_LOADED) {
            for (final int id : ids) {
                final MenuItem item = menu.findItem(id);
                if (!item.isVisible()) {
                    item.setVisible(true);
                }
            }
            mDocumentState = STATE_END;
        }

        enableDisableMenuItem(menu.findItem(R.id.action_open), getWebViewRelease() >= MIN_WEBVIEW_RELEASE);
        enableDisableMenuItem(menu.findItem(R.id.action_share), mUri != null);
        enableDisableMenuItem(menu.findItem(R.id.action_next), mPage < mNumPages);
        enableDisableMenuItem(menu.findItem(R.id.action_previous), mPage > 1);
        enableDisableMenuItem(menu.findItem(R.id.action_save_as), mUri != null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_previous) {
            onJumpToPageInDocument(mPage - 1);
            return true;
        } else if (itemId == R.id.action_next) {
            onJumpToPageInDocument(mPage + 1);
            return true;
        } else if (itemId == R.id.action_first) {
            onJumpToPageInDocument(1);
            return true;
        } else if (itemId == R.id.action_last) {
            onJumpToPageInDocument(mNumPages);
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
        } else if (itemId == R.id.action_view_document_properties) {
            DocumentPropertiesFragment
                .newInstance(mDocumentProperties)
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
        if (mDocumentProperties == null || mDocumentProperties.isEmpty()) return "";
        String fileName = "";
        String title = "";
        for (CharSequence property : mDocumentProperties) {
            if (property.toString().startsWith("File name:")) {
                fileName = property.toString().replace("File name:", "");
            }
            if (property.toString().startsWith("Title:-")) {
                title = property.toString().replace("Title:-", "");
            }
        }
        return fileName.length() > 2 ? fileName : title;
    }

    private void saveDocumentAs(Uri uri) {
        try {
            KtUtilsKt.saveAs(this, mUri, uri);
        } catch (IOException | OutOfMemoryError | IllegalArgumentException e) {
            snackbar.setText(R.string.error_while_saving).show();
        }
    }
}
