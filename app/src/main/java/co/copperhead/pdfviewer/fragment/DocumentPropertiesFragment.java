package co.copperhead.pdfviewer.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

import co.copperhead.pdfviewer.R;

public class DocumentPropertiesFragment extends DialogFragment {
    public static final String TAG = "document_properties_fragment";

    private static final String KEY_DOCUMENT_PROPERTIES = "key_document_properties";

    private static DocumentPropertiesFragment sDocumentPropertiesFragment;

    private String mPDFDocumentProperties;

    public static DocumentPropertiesFragment getInstance(final String metaData) {
        if (sDocumentPropertiesFragment == null) {
            sDocumentPropertiesFragment = new DocumentPropertiesFragment();
            final Bundle args = new Bundle();
            args.putString(KEY_DOCUMENT_PROPERTIES, metaData);
            sDocumentPropertiesFragment.setArguments(args);
        } else {
            final Bundle args = sDocumentPropertiesFragment.getArguments();
            args.clear();
            args.putString(KEY_DOCUMENT_PROPERTIES, metaData);
        }
        return sDocumentPropertiesFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPDFDocumentProperties = getArguments().getString(KEY_DOCUMENT_PROPERTIES);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.action_view_document_properties))
                .setMessage(mPDFDocumentProperties)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
