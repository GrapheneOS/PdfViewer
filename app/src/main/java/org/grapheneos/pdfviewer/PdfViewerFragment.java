package org.grapheneos.pdfviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import org.grapheneos.pdfviewer.fragment.DocumentPropertiesFragment;
import org.grapheneos.pdfviewer.fragment.JumpToPageFragment;
import org.grapheneos.pdfviewer.viewmodel.PdfViewerViewModel;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

public class PdfViewerFragment extends Fragment {
    public static final String TAG = PdfViewerFragment.class.getSimpleName();

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; " +
                    "form-action 'none'; " +
                    "connect-src https://localhost/placeholder.pdf; " +
                    "img-src blob: 'self'; " +
                    "script-src 'self' 'resource://pdf.js'; " +
                    "style-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "base-uri 'none'";

    private static final String FEATURE_POLICY =
            "accelerometer 'none'; " +
                    "ambient-light-sensor 'none'; " +
                    "autoplay 'none'; " +
                    "camera 'none'; " +
                    "encrypted-media 'none'; " +
                    "fullscreen 'none'; " +
                    "geolocation 'none'; " +
                    "gyroscope 'none'; " +
                    "magnetometer 'none'; " +
                    "microphone 'none'; " +
                    "midi 'none'; " +
                    "payment 'none'; " +
                    "picture-in-picture 'none'; " +
                    "speaker 'none'; " +
                    "sync-xhr 'none'; " +
                    "usb 'none'; " +
                    "vr 'none'";

    private static final float MIN_ZOOM_RATIO = 0.5f;
    private static final float MAX_ZOOM_RATIO = 1.5f;
    private static final int ALPHA_LOW = 130;
    private static final int ALPHA_HIGH = 255;
    private static final int STATE_LOADED = 1;
    private static final int STATE_END = 2;
    private static final int PADDING = 10;

    private int mDocumentState;
    private WebView mWebView;
    private int mWindowInsetsTop;
    private InputStream mInputStream;

    private Toast mToast;
    private TextView mTextView;
    private Snackbar mSnackbar;
    private FrameLayout mWebViewContainer;

    private PdfViewerViewModel mViewModel;

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        if (mWebView != null && mWebViewContainer != null) {
            //mWebViewContainer.removeView(mWebView);
            //mWebView.removeAllViews();
            //mWebView.destroy();
        }

    }

    private class Channel {
        @JavascriptInterface
        public int getWindowInsetTop() {
            return mWindowInsetsTop;
        }

        @JavascriptInterface
        public int getPage() {
            return mViewModel.getPage();
        }

        @JavascriptInterface
        public float getZoomRatio() {
            return mViewModel.getZoomRatio();
        }

        @JavascriptInterface
        public int getDocumentOrientationDegrees() {
            return mViewModel.getDocumentOrientationDegrees();
        }

        @JavascriptInterface
        public void setNumPages(int numPages) {
            mViewModel.setNumPages(numPages);
            if (getActivity() != null) {
                requireActivity().runOnUiThread(requireActivity()::invalidateOptionsMenu);
            }
        }

        @JavascriptInterface
        public void setDocumentProperties(final String properties) {
            final List<CharSequence> list = mViewModel.getDocumentProperties().getValue();
            if (list != null && list.isEmpty() && getActivity() != null) {
                mViewModel.loadProperties(properties, requireActivity().getApplicationContext());
            }
        }
    }

    public PdfViewerFragment() {
        // Required empty public constructor
    }

    public static PdfViewerFragment newInstance() {
        return new PdfViewerFragment();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        getParentFragmentManager().setFragmentResultListener(JumpToPageFragment.REQUEST_KEY,
                this, (requestKey, result) -> {
                    final int newPage = result.getInt(JumpToPageFragment.BUNDLE_KEY);
                    onJumpToPageInDocument(newPage);
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_webview, container, false);
    }

    // Can be removed once minSdkVersion >= 26
    @SuppressWarnings("deprecation")
    private void disableSaveFormData(final WebSettings settings) {
        settings.setSaveFormData(false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mViewModel.saveState();
    }

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(PdfViewerViewModel.class);

        mWebViewContainer = view.findViewById(R.id.webview_container);
        mWebView = view.findViewById(R.id.webview);

        mWebView.setOnApplyWindowInsetsListener((v, insets) -> {
            mWindowInsetsTop = insets.getSystemWindowInsetTop();
            mWebView.evaluateJavascript("updateInset()", null);
            return insets;
        });

        final WebSettings settings = mWebView.getSettings();
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        disableSaveFormData(settings);

        CookieManager.getInstance().setAcceptCookie(false);

        mWebView.addJavascriptInterface(new Channel(), "channel");

        mWebView.setWebViewClient(new WebViewClient() {
            private WebResourceResponse fromAsset(final String mime, final String path) {
                if (getActivity() == null) {
                    return null;
                }

                try {
                    InputStream inputStream = requireActivity().getAssets().open(path.substring(1));
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
                    return new WebResourceResponse("application/pdf", null, mInputStream);
                }

                if ("/viewer.html".equals(path)) {
                    final WebResourceResponse response = fromAsset("text/html", path);
                    if (response == null) {
                        return null;
                    }

                    HashMap<String, String> headers = new HashMap<String, String>();
                    headers.put("Content-Security-Policy", CONTENT_SECURITY_POLICY);
                    headers.put("Feature-Policy", FEATURE_POLICY);
                    headers.put("X-Content-Type-Options", "nosniff");
                    response.setResponseHeaders(headers);
                    return response;
                }

                if ("/viewer.css".equals(path)) {
                    return fromAsset("text/css", path);
                }

                if ("/viewer.js".equals(path) || "/pdf.js".equals(path) || "/pdf.worker.js".equals(path)) {
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

                if (getActivity() != null) {
                    requireActivity().invalidateOptionsMenu();
                }
            }
        });

        GestureHelper.attach(getContext(), mWebView,
                new GestureHelper.GestureListener() {
                    @Override
                    public boolean onTapUp() {
                        if (mViewModel.getUri() != null) {
                            mWebView.evaluateJavascript("isTextSelected()", selection -> {
                                if (!Boolean.valueOf(selection) && getActivity() != null) {
                                    if ((requireActivity().getWindow().getDecorView().getSystemUiVisibility() &
                                            View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
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

        mTextView = new TextView(getContext());
        mTextView.setBackgroundColor(Color.DKGRAY);
        mTextView.setTextColor(ColorStateList.valueOf(Color.WHITE));
        mTextView.setTextSize(18);
        mTextView.setPadding(PADDING, 0, PADDING, 0);

        mSnackbar = Snackbar.make(mWebView, "", Snackbar.LENGTH_LONG);

        final Intent intent = requireActivity().getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (!"application/pdf".equals(intent.getType())) {
                mSnackbar.setText(R.string.invalid_mime_type).show();
                return;
            }

            mViewModel.setUri(intent.getData());
            mViewModel.setPage(1);
        }

        if (savedInstanceState != null) {
            mViewModel.restoreState();
        }

        final Uri uri = mViewModel.getUri();
        if (uri != null) {
            if ("file".equals(uri.getScheme())) {
                mSnackbar.setText(R.string.legacy_file_uri).show();
                return;
            }

            loadPdf(savedInstanceState == null);
        }
    }

    private void loadPdf(boolean isLoadingNewPdf) {
        final Uri uri = mViewModel.getUri();
        if (uri == null) {
            return;
        }

        try {
            if (mInputStream != null) {
                mInputStream.close();
            }
            mInputStream = requireActivity().getContentResolver().openInputStream(uri);
        } catch (IOException e) {
            mSnackbar.setText(R.string.io_error).show();
            mViewModel.clearDocumentProperties();
            return;
        }

        if (isLoadingNewPdf) {
            mViewModel.clearDocumentProperties();
        }

        showSystemUi();
        mWebView.loadUrl("https://localhost/viewer.html");
    }

    private void renderPage(final int zoom) {
        mWebView.evaluateJavascript("onRenderPage(" + zoom + ")", null);
    }

    private void documentOrientationChanged(final int orientationDegreesOffset) {
        int newOrientation = (mViewModel.getDocumentOrientationDegrees()
                + orientationDegreesOffset) % 360;
        if (newOrientation < 0) {
            newOrientation += 360;
        }
        mViewModel.setDocumentOrientationDegrees(newOrientation);
        renderPage(0);
    }

    private ActivityResultLauncher<String> mGetDocumentUriLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) {
                        mViewModel.setUri(uri);
                        mViewModel.setPage(1);
                        loadPdf(true);
                    }
                }
            });

    private void openDocument() {
        mGetDocumentUriLauncher.launch("application/pdf");
    }

    private void zoomIn(float value, boolean end) {
        final float zoomRatio = mViewModel.getZoomRatio();
        if (zoomRatio < MAX_ZOOM_RATIO) {
            mViewModel.setZoomRatio(Math.min(zoomRatio + value, MAX_ZOOM_RATIO));
            renderPage(end ? 1 : 2);
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void zoomOut(float value, boolean end) {
        final float zoomRatio = mViewModel.getZoomRatio();
        if (zoomRatio > MIN_ZOOM_RATIO) {
            mViewModel.setZoomRatio(Math.max(zoomRatio - value, MIN_ZOOM_RATIO));
            renderPage(end ? 1 : 2);
            requireActivity().invalidateOptionsMenu();
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
        if (selected_page >= 1
                && selected_page <= mViewModel.getNumPages()
                && mViewModel.getPage() != selected_page) {
            mViewModel.setPage(selected_page);
            renderPage(0);
            showPageNumber();
            requireActivity().invalidateOptionsMenu();
        }
    }

    private void showSystemUi() {
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void hideSystemUi() {
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    private void showPageNumber() {
        if (mToast != null) {
            mToast.cancel();
        }
        mTextView.setText(String.format("%s/%s", mViewModel.getPage(), mViewModel.getNumPages()));
        mToast = new Toast(getContext());
        mToast.setGravity(Gravity.BOTTOM | Gravity.END, PADDING, PADDING);
        mToast.setDuration(Toast.LENGTH_SHORT);
        mToast.setView(mTextView);
        mToast.show();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_pdf_viewer, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        final int ids[] = {R.id.action_zoom_in, R.id.action_zoom_out, R.id.action_jump_to_page,
                R.id.action_next, R.id.action_previous, R.id.action_first, R.id.action_last,
                R.id.action_rotate_clockwise, R.id.action_rotate_counterclockwise,
                R.id.action_view_document_properties};
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

        enableDisableMenuItem(menu.findItem(R.id.action_zoom_in),
                mViewModel.getZoomRatio() != MAX_ZOOM_RATIO);
        enableDisableMenuItem(menu.findItem(R.id.action_zoom_out),
                mViewModel.getZoomRatio() != MIN_ZOOM_RATIO);
        enableDisableMenuItem(menu.findItem(R.id.action_next),
                mViewModel.getPage() < mViewModel.getNumPages());
        enableDisableMenuItem(menu.findItem(R.id.action_previous),
                mViewModel.getPage() > 1);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                onJumpToPageInDocument(mViewModel.getPage() - 1);
                return true;

            case R.id.action_next:
                onJumpToPageInDocument(mViewModel.getPage() + 1);
                return true;

            case R.id.action_first:
                onJumpToPageInDocument(1);
                return true;

            case R.id.action_last:
                onJumpToPageInDocument(mViewModel.getNumPages());
                return true;

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_out:
                zoomOut(0.25f, true);
                return true;

            case R.id.action_zoom_in:
                zoomIn(0.25f, true);
                return true;

            case R.id.action_rotate_clockwise:
                documentOrientationChanged(90);
                return true;

            case R.id.action_rotate_counterclockwise:
                documentOrientationChanged(-90);
                return true;

            case R.id.action_view_document_properties:
                DocumentPropertiesFragment
                        .newInstance()
                        .show(getParentFragmentManager(), DocumentPropertiesFragment.TAG);
                return true;

            case R.id.action_jump_to_page:
                new JumpToPageFragment()
                        .show(getParentFragmentManager(), JumpToPageFragment.TAG);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}