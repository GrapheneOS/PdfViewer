package org.grapheneos.pdfviewer.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import org.grapheneos.pdfviewer.viewmodel.PdfViewerViewModel;

public class JumpToPageFragment extends DialogFragment {
    public static final String TAG = "JumpToPageFragment";

    public static final String REQUEST_KEY = "jumpToPage";
    public static final String BUNDLE_KEY = "jumpToPageBundle";

    private final static String STATE_PICKER_CUR = "picker_cur";
    private final static String STATE_PICKER_MIN = "picker_min";
    private final static String STATE_PICKER_MAX = "picker_max";

    private NumberPicker mPicker;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mPicker.setMinValue(savedInstanceState.getInt(STATE_PICKER_MIN));
            mPicker.setMaxValue(savedInstanceState.getInt(STATE_PICKER_MAX));
            mPicker.setValue(savedInstanceState.getInt(STATE_PICKER_CUR));
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final PdfViewerViewModel model =
                new ViewModelProvider(requireActivity()).get(PdfViewerViewModel.class);

        mPicker = new NumberPicker(getActivity());
        mPicker.setMinValue(1);
        mPicker.setMaxValue(model.getNumPages());
        mPicker.setValue(model.getPage());

        final FrameLayout layout = new FrameLayout(requireActivity());
        layout.addView(mPicker, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        return new AlertDialog.Builder(requireActivity())
                .setView(layout)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mPicker.clearFocus();

                        Bundle result = new Bundle();
                        result.putInt(BUNDLE_KEY, mPicker.getValue());
                        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_PICKER_MIN, mPicker.getMinValue());
        outState.putInt(STATE_PICKER_MAX, mPicker.getMaxValue());
        outState.putInt(STATE_PICKER_CUR, mPicker.getValue());
    }
}
