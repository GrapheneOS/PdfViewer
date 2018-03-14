package co.copperhead.pdfviewer.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

public class JumpToPageFragment extends DialogFragment {
    public static final String TAG = JumpToPageFragment.class.getSimpleName();

    private static final String KEY_PAGE = "page";
    private static final String KEY_NUM_PAGES = "num_pages";

    private final static String STATE_PICKER_CUR = "picker_cur";
    private final static String STATE_PICKER_MIN = "picker_min";
    private final static String STATE_PICKER_MAX = "picker_max";

    private int mPage;
    private int mNumPages;

    private NumberPicker mPicker;
    private Listener mListener;

    public interface Listener {
        void onJumpToPageInDocument(int page);
    }

    public static JumpToPageFragment newInstance(int page, int numPages) {
        final Bundle args = new Bundle();
        args.putInt(KEY_PAGE, page);
        args.putInt(KEY_NUM_PAGES, numPages);
        final JumpToPageFragment jumpToPageFragment = new JumpToPageFragment();
        jumpToPageFragment.setArguments(args);
        return jumpToPageFragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mListener = (Listener) context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPage = getArguments().getInt(KEY_PAGE);
        mNumPages = getArguments().getInt(KEY_NUM_PAGES);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        mPicker = new NumberPicker(activity);
        mPicker.setMinValue(1);
        mPicker.setMaxValue(mNumPages);
        mPicker.setValue(mPage);

        final FrameLayout layout = new FrameLayout(activity);
        layout.addView(mPicker, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        return new AlertDialog.Builder(activity)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    mPicker.clearFocus();
                    mListener.onJumpToPageInDocument(mPicker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
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
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_PICKER_MIN, mPicker.getMinValue());
        outState.putInt(STATE_PICKER_MAX, mPicker.getMaxValue());
        outState.putInt(STATE_PICKER_CUR, mPicker.getValue());
    }
}
