package com.snaptube.dl.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.snaptube.dl.R
import com.snaptube.dl.data.FormatOption
import com.snaptube.dl.data.VideoMetadata
import com.snaptube.dl.databinding.DialogFormatBottomSheetBinding
import com.snaptube.dl.engine.DownloadManager

class FormatBottomSheetDialog(
    private val metadata: VideoMetadata,
    private val onDownloadStarted: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogFormatBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var selectedFormat: FormatOption? = null
    private val formatViews = mutableListOf<Pair<FormatOption, View>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFormatBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvVideoTitle.text = metadata.title
        binding.tvVideoAuthor.text = metadata.uploader
        binding.tvVideoDuration.text = metadata.duration

        Glide.with(this)
            .load(metadata.thumbnailUrl)
            .placeholder(R.drawable.bg_card_dark)
            .into(binding.ivThumbnail)

        binding.btnCloseDialog.setOnClickListener {
            dismiss()
        }

        // Populate Audio Formats
        metadata.audioFormats.forEach { format ->
            val chipView = createFormatChipView(format)
            binding.containerAudioFormats.addView(chipView)
        }

        // Populate Video Formats
        metadata.videoFormats.forEach { format ->
            val chipView = createFormatChipView(format)
            binding.containerVideoFormats.addView(chipView)
        }

        // Select default format (e.g. 720p HD or first available)
        val defaultFormat = metadata.videoFormats.find { it.height == 720 }
            ?: metadata.videoFormats.firstOrNull()
            ?: metadata.audioFormats.firstOrNull()

        defaultFormat?.let { selectFormat(it) }

        binding.btnConfirmDownload.setOnClickListener {
            val format = selectedFormat
            if (format == null) {
                Toast.makeText(requireContext(), "Please select a download format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            DownloadManager.startDownload(requireContext(), metadata, format)
            Toast.makeText(requireContext(), getString(R.string.download_started), Toast.LENGTH_SHORT).show()
            onDownloadStarted?.invoke()
            dismiss()
        }
    }

    private fun createFormatChipView(format: FormatOption): View {
        val chip = layoutInflater.inflate(R.layout.item_format_chip, null, false)
        val tvTitle = chip.findViewById<TextView>(R.id.tv_format_title)
        val tvExt = chip.findViewById<TextView>(R.id.tv_format_ext)
        val tvSize = chip.findViewById<TextView>(R.id.tv_format_size)

        tvTitle.text = format.label
        tvExt.text = format.ext.uppercase()
        tvSize.text = format.fileSizeEstimate

        chip.setOnClickListener {
            selectFormat(format)
        }

        formatViews.add(Pair(format, chip))
        return chip
    }

    private fun selectFormat(format: FormatOption) {
        selectedFormat = format
        for ((f, view) in formatViews) {
            val isSelected = (f == format)
            view.isSelected = isSelected
            val radio = view.findViewById<ImageView>(R.id.iv_radio)
            radio.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FormatBottomSheetDialog"
    }
}
