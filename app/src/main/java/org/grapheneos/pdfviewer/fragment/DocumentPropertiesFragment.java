package org.grapheneos.pdfviewer.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;

import org.grapheneos.pdfviewer.R;

public class DocumentPropertiesFragment extends DialogFragment {
    public static final String TAG = "DocumentPropertiesFragment";

    private static final String KEY_DOCUMENT_PROPERTIES = "key_document_properties";

    private static DocumentPropertiesFragment sDocumentPropertiesFragment;

    private List<String> mDocumentProperties;

    public static DocumentPropertiesFragment getInstance(final ArrayList<CharSequence> metaData) {
        if (sDocumentPropertiesFragment == null) {
            sDocumentPropertiesFragment = new DocumentPropertiesFragment();
            final Bundle args = new Bundle();
            args.putCharSequenceArrayList(KEY_DOCUMENT_PROPERTIES, metaData);
            sDocumentPropertiesFragment.setArguments(args);
        } else {
            final Bundle args = sDocumentPropertiesFragment.getArguments();
            args.clear();
            args.putCharSequenceArrayList(KEY_DOCUMENT_PROPERTIES, metaData);
        }
        return sDocumentPropertiesFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDocumentProperties = getArguments().getStringArrayList(KEY_DOCUMENT_PROPERTIES);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        final AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setPositiveButton(android.R.string.ok, null);

        if (mDocumentProperties != null) {
            dialog.setTitle(getString(R.string.action_view_document_properties));
            dialog.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1,
                    mDocumentProperties), null);
        } else {
            dialog.setTitle(R.string.document_properties_retrieval_failed);
        }
        return dialog.create();
    }
}
