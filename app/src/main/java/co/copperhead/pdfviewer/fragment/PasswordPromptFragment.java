package co.copperhead.pdfviewer.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import co.copperhead.pdfviewer.PdfViewer;
import co.copperhead.pdfviewer.R;

import static co.copperhead.pdfviewer.PdfViewer.ACTION_OPEN_DOCUMENT_REQUEST_CODE_2;

public class PasswordPromptFragment extends DialogFragment {
    public static final String TAG = "password_prompt_fragment";

    private void enableDisablePositiveButton(final boolean enable) {
        final Button positiveButton = ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON_POSITIVE);
        if (enable) {
            if (!positiveButton.isEnabled()) {
                positiveButton.setEnabled(true);
            }
        } else if (positiveButton.isEnabled()) {
            positiveButton.setEnabled(false);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final AlertDialog.Builder passwordPrompt = new AlertDialog.Builder(getActivity());
        final View view = inflater.inflate(R.layout.password_dialog_fragment, null);
        final TextInputEditText passwordEditText = (TextInputEditText) view.findViewById(R.id.pdf_password_edittext);
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence input, int i, int i1, int i2) {
                if (input.length() > 0) {
                    enableDisablePositiveButton(true);
                } else {
                    enableDisablePositiveButton(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });
        passwordPrompt.setView(view);
        passwordPrompt.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dismissSoftInput();

                final String password = passwordEditText.getText().toString();
                ((PdfViewer) getActivity()).loadPdfWithPassword(password);
            }
        });
        passwordPrompt.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dismissSoftInput();

                ((PdfViewer) getActivity()).openDocument(ACTION_OPEN_DOCUMENT_REQUEST_CODE_2);
            }
        });
        final AlertDialog dialog = passwordPrompt.create();
        setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        final View passwordEditText = getDialog().findViewById(R.id.pdf_password_edittext);
        final Button positiveButton = ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE);

        if (TextUtils.isEmpty(((TextInputEditText) passwordEditText).getText().toString())) {
            positiveButton.setEnabled(false);
        }

        passwordEditText.requestFocus();

        final InputMethodManager imm =
                (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    private void dismissSoftInput() {
        final View passwordEditText = getDialog().findViewById(R.id.pdf_password_edittext);

        final InputMethodManager imm =
                (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(passwordEditText.getWindowToken(), 0);
        }
    }
}
