package co.copperhead.pdfviewer.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

import co.copperhead.pdfviewer.PdfViewer;

public class JumpToPageFragment extends DialogFragment {
    public static final String TAG = "jump_to_page_fragment";

    private static final String STATE_PAGE = "page";
    private static final String STATE_NUM_PAGES = "num_pages";

    private final static String STATE_PICKER_CUR = "picker_cur";
    private final static String STATE_PICKER_MIN = "picker_min";
    private final static String STATE_PICKER_MAX = "picker_max";

    private NumberPicker mPicker;
    private int mPage;
    private int mNumPages;

    public static JumpToPageFragment newInstance(int page, int numPages) {
        final Bundle args = new Bundle();
        args.putInt(STATE_PAGE, page);
        args.putInt(STATE_NUM_PAGES, numPages);
        final JumpToPageFragment jumpToPageFragment = new JumpToPageFragment();
        jumpToPageFragment.setArguments(args);
        return jumpToPageFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPage = getArguments().getInt(STATE_PAGE);
        mNumPages = getArguments().getInt(STATE_NUM_PAGES);
    }

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
        mPicker = new NumberPicker(getActivity());
        mPicker.setMinValue(1);
        mPicker.setMaxValue(mNumPages);
        mPicker.setValue(mPage);

        final FrameLayout layout = new FrameLayout(getActivity());
        layout.addView(mPicker, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        return new AlertDialog.Builder(getActivity())
                .setView(layout)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ((PdfViewer)getActivity()).positiveButtonRenderPage(mPicker.getValue());
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
