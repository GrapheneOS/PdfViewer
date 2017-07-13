package co.copperhead.pdfviewer;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import co.copperhead.pdfviewer.fragment.DocumentPropertiesFragment;
import co.copperhead.pdfviewer.fragment.JumpToPageFragment;

public class PdfViewer extends Activity {
    private static final String TAG = "PdfViewer";

    private static final int MIN_ZOOM_LEVEL = 0;
    private static final int MAX_ZOOM_LEVEL = 4;
    private static final int ALPHA_LOW = 130;
    private static final int ALPHA_HIGH = 255;
    private static final int ACTION_OPEN_DOCUMENT_REQUEST_CODE = 1;
    private static final int STATE_DEFAULT = 0;
    private static final int STATE_LOADED = 1;
    private static final int STATE_END = 2;
    private static final String STATE_URI = "uri";
    private static final String STATE_PAGE = "page";
    private static final String STATE_ZOOM_LEVEL = "zoomLevel";
    private static final int PADDING = 10;

    private WebView mWebView;
    private Uri mUri;
    public int mPage;
    public int mNumPages;
    private int mZoomLevel = 2;
    private int mDocumentState;
    private Channel mChannel;
    private boolean mZoomInState = true;
    private boolean mZoomOutState = true;
    private String mDocumentProperties;
    private InputStream mInputStream;
    private TextView mTextView;
    private Toast mToast;

    private class Channel {
        @JavascriptInterface
        public int getPage() {
            return mPage;
        }

        @JavascriptInterface
        public int getZoomLevel() {
            return mZoomLevel;
        }

        @JavascriptInterface
        public void setNumPages(int numPages) {
            mNumPages = numPages;
        }

        @JavascriptInterface
        public void setDocumentProperties(final String properties) {
            if (mDocumentProperties != null) {
                throw new SecurityException("mDocumentProperties not null");
            }
            new AsyncTask<Void, Void, Void>() {
                private String formatProperty(int resId, String propertyValue) {
                    return String.format("\n%s:\n%s\n", getString(resId), propertyValue);
                }

                private String parseFileSize(long fileSize) {
                    final double kb = fileSize / 1000;
                    if (((long) kb) == 0) {
                        return String.format("%s bytes", String.valueOf(fileSize));
                    } else {
                        final DecimalFormat format = new DecimalFormat("#.##");
                        format.setRoundingMode(RoundingMode.CEILING);
                        if (kb < 1000) {
                            return String.format("%s kB (%s bytes)", format.format(kb), String.valueOf(fileSize));
                        }
                        return String.format("%s MB (%s bytes)", format.format(kb / 1000), String.valueOf(fileSize));
                    }
                }

                private String parseDate(String dateToParse) {
                    // No date property found
                    if (dateToParse.equals("-")) {
                        return "-";
                    }

                    // Date must at least contain a year
                    final boolean dateHasPrefix = dateToParse.startsWith("D:");
                    final int dateMinLength = dateHasPrefix ? 6 : 4;
                    if (dateToParse.length() < dateMinLength || dateToParse.length() > 23) {
                        Log.e(TAG, "Supplied date length mismatch");
                        return getString(R.string.document_properties_invalid_date);
                    }

                    // Date can have a D: prefix
                    if (dateHasPrefix) {
                        dateToParse = dateToParse.substring(2);
                    }

                    // Calendar month starts at 0
                    int year = 1970, month = 0, day = 1, hours = 0, minutes = 0;
                    int seconds = 0, utRelOffset = 4;
                    final Calendar calendar = Calendar.getInstance();

                    // Year is mandatory
                    try {
                        final int parsedYear = Integer.valueOf(dateToParse.substring(0, 4));
                        if (parsedYear <= calendar.get(Calendar.YEAR) && parsedYear > year) {
                            year = parsedYear;
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.getMessage());
                        return getString(R.string.document_properties_invalid_date);
                    }
                    // Those fields are optional
                    try {
                        month = Integer.valueOf(dateToParse.substring(4, 6)) - 1;
                        utRelOffset += 2;
                        day = Integer.valueOf(dateToParse.substring(6, 8));
                        utRelOffset += 2;
                        hours = Integer.valueOf(dateToParse.substring(8, 10));
                        utRelOffset += 2;
                        minutes = Integer.valueOf(dateToParse.substring(10, 12));
                        utRelOffset += 2;
                        seconds = Integer.valueOf(dateToParse.substring(12, 14));
                        utRelOffset += 2;
                    } catch (IndexOutOfBoundsException ignored) {
                        // It is allowed for all fields except year to be missing
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid date supplied");
                        return getString(R.string.document_properties_invalid_date);
                    }

                    // Perform basic date validation
                    if ((month < 0 || month > 11) || (day < 1 || day > 31) ||
                            (hours < 0 || hours > 23) || (minutes < 0 || minutes > 59) ||
                            (seconds < 0 || seconds > 59)) {
                        Log.e(TAG, "Date validation failed");
                        return getString(R.string.document_properties_invalid_date);
                    }

                    int offsetHours = 0, offsetMinutes = 0, combinedOffset = 0;
                    String utRel = "";

                    try {
                        utRel = String.valueOf(dateToParse.charAt(utRelOffset));
                        if (utRel.equals("+") || utRel.equals("-") || utRel.equals("Z")) {
                            offsetHours = Integer.parseInt(dateToParse.substring(15, 17));
                            final String rawOffsetMinutes = dateToParse.substring(18, 20);
                            offsetMinutes = Integer.parseInt(rawOffsetMinutes);
                            combinedOffset = Integer.valueOf(String.format("%s%s%s", utRel, offsetHours, rawOffsetMinutes));
                        } else {
                            offsetHours = Integer.parseInt(dateToParse.substring(14, 16));
                            final String rawOffsetMinutes = dateToParse.substring(17, 19);
                            offsetMinutes = Integer.parseInt(rawOffsetMinutes);
                            combinedOffset = Integer.valueOf(String.format("%s%s", offsetHours, rawOffsetMinutes));
                        }
                    } catch (IndexOutOfBoundsException ignored) {
                        // It is allowed for all fields except year to be missing
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid UTC offset supplied");
                        return getString(R.string.document_properties_invalid_date);
                    }

                    // Validate UT offset
                    if (combinedOffset < -1200 || combinedOffset > 1400) {
                        Log.e(TAG, "UTC offset out of bounds");
                        return getString(R.string.document_properties_invalid_date);
                    }


                    switch (utRel) {
                        case "+":
                            hours += offsetHours;
                            minutes += offsetMinutes;
                            break;
                        case "-":
                            hours -= offsetHours;
                            minutes -= offsetMinutes;
                            break;
                        case "Z":
                            // "Z" means local time equal to UT
                            break;
                        default:
                            break;
                    }

                    calendar.set(year, month, day, hours, minutes, seconds);
                    final Locale locale = getResources().getConfiguration().getLocales().get(0);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", locale);
                    final TimeZone timeZone = TimeZone.getTimeZone("UTC");
                    dateFormat.setTimeZone(timeZone);
                    final Date date = calendar.getTime();
                    final String dateString = dateFormat.format(date);
                    dateFormat = new SimpleDateFormat("HH:mm:ss", locale);
                    dateFormat.setTimeZone(timeZone);
                    final String timeString = dateFormat.format(date);

                    return String.format("%s %s", dateString, timeString);
                }

                @Override
                protected Void doInBackground(Void... voids) {
                    try {
                        final Cursor cursor = getContentResolver().query(mUri, null, null, null, null);
                        if (cursor == null || cursor.getCount() == 0) {
                            return null;
                        }

                        final int fileNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        final int fileSizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                        cursor.moveToFirst();

                        final String fileName = cursor.getString(fileNameIndex);
                        final String fileSize = parseFileSize(Long.valueOf(cursor.getString(fileSizeIndex)));

                        cursor.close();

                        final JSONObject json = new JSONObject(properties);
                        final String title = json.optString("Title", "-");
                        final String author = json.optString("Author", "-");
                        final String subject = json.optString("Subject", "-");
                        final String keywords = json.optString("Keywords", "-");
                        final String creationDate = parseDate(json.optString("CreationDate", "-"));
                        final String modifyDate = parseDate(json.optString("ModDate", "-"));
                        final String producer = json.optString("Producer", "-");
                        final String creator = json.optString("Creator", "-");
                        final String pdfVersion = json.optString("PDFFormatVersion", "-");
                        final String pages = String.valueOf(mNumPages);

                        mDocumentProperties = String.format("%s%s%s%s%s%s%s%s%s%s%s%s",
                                formatProperty(R.string.document_properties_file_name, fileName),
                                formatProperty(R.string.document_properties_file_size, fileSize),
                                formatProperty(R.string.document_properties_title, title),
                                formatProperty(R.string.document_properties_author, author),
                                formatProperty(R.string.document_properties_subject, subject),
                                formatProperty(R.string.document_properties_keywords, keywords),
                                formatProperty(R.string.document_properties_creation_date, creationDate),
                                formatProperty(R.string.document_properties_modify_date, modifyDate),
                                formatProperty(R.string.document_properties_producer, producer),
                                formatProperty(R.string.document_properties_creator, creator),
                                formatProperty(R.string.document_properties_pdf_version, pdfVersion),
                                formatProperty(R.string.document_properties_pages, pages));
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                    }
                    return null;
                }
            }.execute();
        }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.webview);

        mWebView = (WebView) findViewById(R.id.webview);
        WebSettings settings = mWebView.getSettings();
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(settings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        settings.setSaveFormData(false);

        CookieManager.getInstance().setAcceptCookie(false);

        mChannel = new Channel();
        mWebView.addJavascriptInterface(mChannel, "channel");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("GET".equals(request.getMethod()) && "https://localhost/placeholder.pdf".equals(request.getUrl().toString())) {
                    return new WebResourceResponse("application/pdf", null, mInputStream);
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
            }
        });

        mTextView = new TextView(this);
        mTextView.setBackgroundColor(Color.DKGRAY);
        mTextView.setTextColor(ColorStateList.valueOf(Color.WHITE));
        mTextView.setTextSize(18);
        mTextView.setPadding(PADDING, 0, PADDING, 0);

        final Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (!"application/pdf".equals(intent.getType())) {
                throw new RuntimeException();
            }
            mUri = (Uri) intent.getData();
            mPage = 1;
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(STATE_URI);
            mPage = savedInstanceState.getInt(STATE_PAGE);
            mZoomLevel = savedInstanceState.getInt(STATE_ZOOM_LEVEL);
        }

        if (mUri != null) {
            loadPdf();
        }
    }

    private void loadPdf() {
        try {
            if (mInputStream != null) {
                mInputStream.close();
            }
            mInputStream = getContentResolver().openInputStream(mUri);
        } catch (IOException e) {
            return;
        }
        mWebView.loadUrl("file:///android_asset/viewer.html");
    }

    private void renderPage() {
        mWebView.evaluateJavascript("onRenderPage()", null);
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, ACTION_OPEN_DOCUMENT_REQUEST_CODE);
    }

    private void saveMenuItemState(MenuItem item, boolean state) {
        if (item.getItemId() == R.id.action_zoom_in) {
            mZoomInState = state;
        } else {
            mZoomOutState = state;
        }
    }

    private void restoreMenuItemState(Menu menu) {
        if (menu.findItem(R.id.action_zoom_in).isEnabled() != mZoomInState) {
            menu.findItem(R.id.action_zoom_in).setEnabled(mZoomInState);
        } else if (menu.findItem(R.id.action_zoom_out).isEnabled() != mZoomOutState) {
            menu.findItem(R.id.action_zoom_out).setEnabled(mZoomOutState);
        }
    }

    private void disableItem(MenuItem item) {
        item.setEnabled(false);
        final Drawable icon = item.getIcon();
        if (icon != null) {
            icon.setAlpha(ALPHA_LOW);
        }
    }

    private void enableItem(MenuItem item) {
        item.setEnabled(true);
        final Drawable icon = item.getIcon();
        if (icon != null) {
            icon.setAlpha(ALPHA_HIGH);
        }
    }

    private void checkDisableMenuItem(MenuItem item) {
        if (item.isEnabled()) {
            disableItem(item);
            saveMenuItemState(item, false);
        }
    }

    private void checkEnableMenuItem(MenuItem item) {
        if (!item.isEnabled()) {
            enableItem(item);
            saveMenuItemState(item, true);
        }
    }

    private void enableDisableItems(Menu menu, boolean disable) {
        final int ids[] = { R.id.action_zoom_in, R.id.action_zoom_out, R.id.action_jump_to_page,
            R.id.action_next, R.id.action_previous, R.id.action_view_document_properties };
        for (final int id : ids) {
            if (disable) {
                disableItem(menu.findItem(id));
            } else {
                enableItem(menu.findItem(id));
            }
        }
    }

    public void positiveButtonRenderPage(int selected_page) {
        if (selected_page >= 1 && selected_page <= mNumPages) {
            mPage = selected_page;
            renderPage();
            showPageNumber();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
        savedInstanceState.putInt(STATE_PAGE, mPage);
        savedInstanceState.putInt(STATE_ZOOM_LEVEL, mZoomLevel);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ACTION_OPEN_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                mUri = resultData.getData();
                mPage = 1;
                mDocumentProperties = null;
                loadPdf();
                mDocumentState = STATE_DEFAULT;
                invalidateOptionsMenu();
            }
        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pdf_viewer, menu);
        restoreMenuItemState(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        switch (mDocumentState) {
            case STATE_DEFAULT:
                enableDisableItems(menu, true);
                return true;
            case STATE_LOADED:
                enableDisableItems(menu, false);
                mDocumentState = STATE_END;
                return true;
            default:
                break;
        }
        switch (mZoomLevel) {
            case MAX_ZOOM_LEVEL:
                checkDisableMenuItem(menu.findItem(R.id.action_zoom_in));
                return true;
            case MIN_ZOOM_LEVEL:
                checkDisableMenuItem(menu.findItem(R.id.action_zoom_out));
                return true;
            default:
                checkEnableMenuItem(menu.findItem(R.id.action_zoom_in));
                checkEnableMenuItem(menu.findItem(R.id.action_zoom_out));
                return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                if (mPage > 1) {
                    mPage--;
                    renderPage();
                    showPageNumber();
                }
                return true;

            case R.id.action_next:
                if (mPage < mNumPages) {
                    mPage++;
                    renderPage();
                    showPageNumber();
                }
                return true;

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_out:
                if (mZoomLevel > 0) {
                    mZoomLevel--;
                    renderPage();
                    invalidateOptionsMenu();
                }
                return true;

            case R.id.action_zoom_in:
                if (mZoomLevel < MAX_ZOOM_LEVEL) {
                    mZoomLevel++;
                    renderPage();
                    invalidateOptionsMenu();
                }
                return true;

            case R.id.action_view_document_properties:
                if (mDocumentProperties == null) {
                    mDocumentProperties = getString(R.string.document_properties_retrieval_failed);
                }
                DocumentPropertiesFragment.getInstance(mDocumentProperties).show(getFragmentManager(), null);
                return true;

            case R.id.action_jump_to_page:
                new JumpToPageFragment().show(getFragmentManager(), null);
                return true;

            default:
                return true;
        }
    }
}
