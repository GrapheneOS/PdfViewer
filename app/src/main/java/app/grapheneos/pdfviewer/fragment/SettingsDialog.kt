package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.PreferenceHelper
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.DialogSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogSettingsBinding.inflate(layoutInflater)

        val prefs = requireContext().getSharedPreferences(PreferenceHelper.PREF_NAME, Context.MODE_PRIVATE)
        binding.switchResumeDocument.isChecked =
            prefs.getBoolean(PreferenceHelper.KEY_RESUME_LAST_DOCUMENT, true)
        binding.switchResumeDocument.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean(PreferenceHelper.KEY_RESUME_LAST_DOCUMENT, isChecked)
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings)
            .setView(binding.root)
            .create()
    }
}