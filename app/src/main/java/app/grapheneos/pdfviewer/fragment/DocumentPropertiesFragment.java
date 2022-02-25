package app.grapheneos.pdfviewer.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import app.grapheneos.pdfviewer.R;

import java.util.ArrayList;
import java.util.List;

public class DocumentPropertiesFragment extends DialogFragment {
    public static final String TAG = "DocumentPropertiesFragment";

    private static final String KEY_DOCUMENT_PROPERTIES = "document_properties";

    private List<String> mDocumentProperties;

    public static DocumentPropertiesFragment newInstance(final List<CharSequence> metaData) {
        final DocumentPropertiesFragment fragment = new DocumentPropertiesFragment();
        final Bundle args = new Bundle();

        args.putCharSequenceArrayList(KEY_DOCUMENT_PROPERTIES, (ArrayList<CharSequence>) metaData);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mDocumentProperties = getArguments().getStringArrayList(KEY_DOCUMENT_PROPERTIES);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = requireActivity();
        final MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity)
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
