package app.grapheneos.pdfviewer.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import app.grapheneos.pdfviewer.R
import app.grapheneos.pdfviewer.databinding.DialogSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsDialog : DialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSettingsBinding.inflate(layoutInflater)

        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        binding.switchResumeDocument.isChecked =
            prefs.getBoolean(PREF_RESUME_LAST_DOCUMENT, true)
        binding.switchResumeDocument.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit {
                putBoolean(PREF_RESUME_LAST_DOCUMENT, isChecked)
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings)
            .setView(binding.root)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PREF_RESUME_LAST_DOCUMENT = "resume_last_document"
    }
}