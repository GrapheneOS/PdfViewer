package org.grapheneos.pdfviewer.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import org.grapheneos.pdfviewer.R;
import org.grapheneos.pdfviewer.viewmodel.PdfViewerViewModel;

import java.util.Collections;
import java.util.List;

public class DocumentPropertiesFragment extends DialogFragment {
    public static final String TAG = "DocumentPropertiesFragment";

    private ArrayAdapter<CharSequence> mAdapter;
    private PdfViewerViewModel mModel;
    private Observer<List<CharSequence>> mPropertiesObserver;

    public static DocumentPropertiesFragment newInstance() {
        return new DocumentPropertiesFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mModel.getDocumentProperties().removeObserver(mPropertiesObserver);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = requireActivity();
        final AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setPositiveButton(android.R.string.ok, null);

        mModel = new ViewModelProvider(requireActivity()).get(PdfViewerViewModel.class);
        final List<CharSequence> list = mModel.getDocumentProperties().getValue();
        mAdapter = new ArrayAdapter<>(requireActivity(), android.R.layout.simple_list_item_1,
                list != null ? list : Collections.emptyList());
        dialog.setAdapter(mAdapter, null);

        dialog.setTitle(getTitleStringIdForPropertiesState(list));

        final AlertDialog alertDialog = dialog.create();
        mPropertiesObserver = new Observer<List<CharSequence>>() {
            @Override
            public void onChanged(List<CharSequence> charSequences) {
                Log.d(TAG, "Properties changed!");
                alertDialog.setTitle(getTitleStringIdForPropertiesState(charSequences));
                mAdapter.notifyDataSetChanged();
            }
        };
        mModel.getDocumentProperties().observe(requireActivity(), mPropertiesObserver);

        return alertDialog;
    }

    private int getTitleStringIdForPropertiesState(final List<CharSequence> properties) {
        return properties == null || properties.isEmpty()
                ? R.string.document_properties_retrieval_failed
                : R.string.action_view_document_properties;
    }
}
