package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo.IME_ACTION_DONE
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.PasswordDialogFragmentBinding
import com.google.android.material.textfield.TextInputEditText

class PasswordPromptFragment : DialogFragment() {

    private lateinit var passwordEditText : TextInputEditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val passwordPrompt = AlertDialog.Builder(requireContext())
        val passwordDialogFragmentBinding =
            PasswordDialogFragmentBinding.inflate(LayoutInflater.from(requireContext()))
        passwordEditText = passwordDialogFragmentBinding.pdfPasswordEditText
        passwordPrompt.setView(passwordDialogFragmentBinding.root)
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(input: CharSequence, i: Int, i1: Int, i2: Int) {
                updatePositiveButton()
            }
            override fun afterTextChanged(editable: Editable) {}
        })
        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != IME_ACTION_DONE) return@setOnEditorActionListener false
            sendPassword()
            true
        }
        passwordPrompt.setPositiveButton(R.string.open) { _, _ -> sendPassword() }
        passwordPrompt.setNegativeButton(R.string.cancel, null)
        val dialog = passwordPrompt.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    private fun updatePositiveButton() {
        val btn = (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
        btn.isEnabled = passwordEditText.text?.isNotEmpty() ?: false
    }

    private fun sendPassword() {
        val password = passwordEditText.text.toString()
        if (!TextUtils.isEmpty(password)) {
            (activity as PdfViewer).loadPdfWithPassword(password)
            dialog?.dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        updatePositiveButton()
        passwordEditText.requestFocus()
    }
}