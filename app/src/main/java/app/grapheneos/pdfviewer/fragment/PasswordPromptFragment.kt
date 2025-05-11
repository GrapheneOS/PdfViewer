package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.WindowManager
import android.view.inputmethod.EditorInfo.IME_ACTION_DONE
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.PdfViewer
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.PasswordDialogFragmentBinding
import app.grapheneos.pdfviewer.viewModel.PdfViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PasswordPromptFragment : DialogFragment() {

    private lateinit var passwordLayout : TextInputLayout
    private lateinit var passwordEditText : TextInputEditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val passwordPrompt = MaterialAlertDialogBuilder(requireContext())
        val passwordDialogFragmentBinding =
            PasswordDialogFragmentBinding.inflate(getLayoutInflater())
        passwordLayout = passwordDialogFragmentBinding.pdfPasswordTextInputLayout
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
        passwordPrompt.setPositiveButton(R.string.open, null)
        passwordPrompt.setNegativeButton(R.string.cancel, null)
        val dialog = passwordPrompt.create()
        passwordPrompt.setCancelable(false)
        isCancelable = false
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        (requireActivity() as PdfViewer).viewModel.passwordStatus.observe(
            this
        ) {
            when (it) {
                PdfViewModel.PasswordStatus.MissingPassword -> {
                    passwordEditText.editableText.clear()
                    passwordDialogFragmentBinding.title.setText(R.string.password_prompt_description)
                }
                PdfViewModel.PasswordStatus.InvalidPassword -> {
                    passwordEditText.editableText.clear()
                    passwordDialogFragmentBinding.pdfPasswordTextInputLayout.error =
                        "invalid password"
                }
                PdfViewModel.PasswordStatus.Validated -> {
                    //Activity will dismiss the dialog
                }
                else -> {
                    throw NullPointerException("status shouldn't be null")
                }
            }
        }
        return dialog
    }

    private fun updatePositiveButton() {
        passwordLayout.error = ""
        val btn = (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
        btn.isEnabled = passwordEditText.text?.isNotEmpty() ?: false
    }

    private fun sendPassword() {
        val password = passwordEditText.text.toString()
        if (!TextUtils.isEmpty(password)) {
            (activity as PdfViewer).loadPdfWithPassword(password)
        }
    }

    override fun onStart() {
        super.onStart()
        updatePositiveButton()
        passwordEditText.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            sendPassword()
        }
    }
}
