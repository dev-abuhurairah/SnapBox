package com.snaptube.dl.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.snaptube.dl.databinding.FragmentSettingsBinding
import com.snaptube.dl.engine.DownloadManager
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dir = DownloadManager.getDownloadDir(requireContext())
        binding.tvDownloadPath.text = dir.absolutePath

        binding.btnUpdateYtdlp.setOnClickListener {
            binding.tvYtdlpVersion.text = "Checking and updating yt-dlp..."
            Toast.makeText(requireContext(), "Updating yt-dlp engine...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                val result = DownloadManager.updateEngine(requireContext())
                result.onSuccess { message ->
                    binding.tvYtdlpVersion.text = "yt-dlp is up to date"
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    binding.tvYtdlpVersion.text = "Update failed: ${err.localizedMessage}"
                    Toast.makeText(requireContext(), "Update check: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}