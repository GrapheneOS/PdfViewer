package co.copperhead.pdfviewer;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class PdfViewer extends Activity {
    private static final int ACTION_OPEN_DOCUMENT_REQUEST_CODE = 1;
    private static final String STATE_URI = "uri";

    private WebView mWebView;
    private Uri mUri;
    private Channel mChannel;

    private class Channel {
        public String mUrl;

        Channel(String url) {
            mUrl = url;
        }

        @JavascriptInterface
        public String getUrl() {
            return mUrl;
        }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview);

        mWebView = (WebView) findViewById(R.id.webView1);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);

        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(true);

        mChannel = new Channel(null);
        mWebView.addJavascriptInterface(mChannel, "channel");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (mUri != null) {
                    PdfViewer.this.loadPdf();
                }
            }
        });

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action)) {
            if (!type.equals("application/pdf")) {
                throw new RuntimeException();
            }
            mUri = (Uri) intent.getData();
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable("uri");
        }

        mWebView.loadUrl("file:///android_asset/viewer.html");
    }

    private void loadPdf() {
        mChannel.mUrl = mUri.toString();
        mWebView.evaluateJavascript("onGetDocument()", null);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, ACTION_OPEN_DOCUMENT_REQUEST_CODE);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ACTION_OPEN_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                mUri = resultData.getData();
                loadPdf();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pdf_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                mWebView.evaluateJavascript("onPrevPage()", null);
                return super.onOptionsItemSelected(item);

            case R.id.action_next:
                mWebView.evaluateJavascript("onNextPage()", null);
                return super.onOptionsItemSelected(item);

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            default:
                return super.onOptionsItemSelected(item);
        }
    }
 }
