package com.example.romear.feature.chat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.romear.R
import com.example.romear.core.designsystem.HelloFeedback
import com.example.romear.core.designsystem.HelloMotion
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentChatBinding
import com.example.romear.feature.safety.data.SafetyRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatFragment : Fragment() {

    @javax.inject.Inject lateinit var socialRepository: com.example.romear.feature.social.SocialRepository
    private lateinit var socialUi: com.example.romear.feature.social.SocialUi
    private lateinit var voiceUi: com.example.romear.feature.social.VoiceUi

    private var binding: FragmentChatBinding? = null
    private val viewModel: ChatViewModel by viewModels()

    @Inject
    lateinit var safetyRepository: SafetyRepository

    private lateinit var adapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        socialUi = com.example.romear.feature.social.SocialUi(this, socialRepository)
        voiceUi = com.example.romear.feature.social.VoiceUi(this, socialRepository)
        enterTransition = HelloTransitions.forward()
        exitTransition = HelloTransitions.backward()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentChatBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)

        val conversationId = arguments?.getString("conversationId").orEmpty().ifBlank { "demo-1" }
        views.planButton.setOnClickListener { socialUi.plans(conversationId) }
        val partnerName = arguments?.getString("partnerName").orEmpty().ifBlank { "Priya Nair" }
        val partnerPhotoUrl = arguments?.getString("partnerPhotoUrl")
        val partnerId = arguments?.getString("partnerId").orEmpty()
        val contextBanner = arguments?.getString("contextTitle")

        views.partnerName.text = partnerName
        if (conversationId.contains("pool")) {
            views.partnerStatus.text = "👥 Nagpur Hangout Group Chat"
            views.partnerStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.accent_emerald))
            views.partnerOnlineDot.isVisible = true
            views.planButton.text = "📍 Venue & Details"
            views.planButton.setOnClickListener { findNavController().navigateUp() }
            views.emptyChatTitle.text = "Hangout Group Chat 🎉"
            views.chipIcebreaker1.text = "Hey everyone! 👋"
            views.chipIcebreaker2.text = "See you all at the venue!"
            views.chipIcebreaker3.text = "I'm on my way! 🏃"
        } else if (!contextBanner.isNullOrBlank()) {
            views.planButton.text = "$contextBanner · Plan Meetup"
        }
        views.partnerAvatar.load(partnerPhotoUrl, partnerName, sizeDp = 42)
        views.partnerAvatar.setOnClickListener {
            partnerPhotoUrl?.let { url ->
                com.example.romear.core.designsystem.HelloLightbox.show(requireContext(), url, partnerName, "Profile Photo")
            }
        }

        // Setup empty state icebreaker greeting card
        views.emptyPartnerAvatar.load(partnerPhotoUrl, partnerName, sizeDp = 68)
        if (!conversationId.contains("pool")) {
            views.emptyChatTitle.text = if (partnerName.isNotBlank()) "Say hello to $partnerName!" else "Say hello!"
        }
        views.chipIcebreaker1.setOnClickListener {
            val text = views.chipIcebreaker1.text.toString()
            viewModel.sendMessage(text)
            com.example.romear.core.designsystem.HelloHaptics.selection(it)
        }
        views.chipIcebreaker2.setOnClickListener {
            val text = views.chipIcebreaker2.text.toString()
            viewModel.sendMessage(text)
            com.example.romear.core.designsystem.HelloHaptics.selection(it)
        }
        views.chipIcebreaker3.setOnClickListener {
            val text = views.chipIcebreaker3.text.toString()
            viewModel.sendMessage(text)
            com.example.romear.core.designsystem.HelloHaptics.selection(it)
        }

        adapter = MessagesAdapter(
            onInviteClicked = { socialUi.plans(conversationId) },
            onVoiceClicked = { voiceUi.play(it) },
        )
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        views.messagesRecyclerView.layoutManager = layoutManager
        views.messagesRecyclerView.adapter = adapter

        views.backButton.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.inboxFragment)
            }
        }

        views.messageInput.setText(viewModel.draft.value)
        views.messageInput.doAfterTextChanged { viewModel.updateDraft(it?.toString().orEmpty()) }
        views.sendButton.setOnClickListener {
            val text = views.messageInput.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
            }
        }

        views.btnLetsMeet.setOnClickListener {
            HelloMotion.bounce(it) { socialUi.suggestPlan(conversationId) }
        }

        views.btnVoiceNote.setOnClickListener {
            HelloMotion.bounce(it) {
                voiceUi.record(conversationId) { viewModel.loadMessages(silent = true) }
            }
        }

        views.optionsButton.setOnClickListener {
            showSafetyMenu(partnerId, partnerName)
        }

        viewModel.initConversation(conversationId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        viewModel.loadMessages(silent = true)
                        kotlinx.coroutines.delay(5_000)
                    }
                }
                launch {
                    viewModel.draft.collect { draft ->
                        if (views.messageInput.text.toString() != draft) {
                            views.messageInput.setText(draft)
                            views.messageInput.setSelection(draft.length)
                        }
                    }
                }
                launch {
                    kotlinx.coroutines.flow.combine(viewModel.draft, viewModel.sending) { draft, sending ->
                        draft.isNotBlank() && !sending
                    }.collect { views.sendButton.isEnabled = it; views.sendButton.alpha = if (it) 1f else 0.4f }
                }
                launch { viewModel.sent.collect { com.example.romear.core.designsystem.HelloHaptics.success(views.sendButton) } }
                launch {
                    kotlinx.coroutines.flow.combine(viewModel.messages, viewModel.loading) { messages, loading ->
                        Pair(messages, loading)
                    }.collect { (messages, loading) ->
                        val atBottom = layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2
                        val lastId = adapter.currentList.lastOrNull()?.id
                        adapter.submitList(messages) {
                            if (messages.isNotEmpty() && lastId != messages.last().id && (atBottom || messages.last().isOutgoing)) {
                                views.messagesRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        }
                        val isEmpty = messages.isEmpty() && !loading
                        views.emptyChatView.isVisible = isEmpty
                        views.messagesRecyclerView.isVisible = messages.isNotEmpty()
                        views.loadingIndicator.isVisible = loading && messages.isEmpty()
                    }
                }
                launch {
                    viewModel.error.collect { err ->
                        com.example.romear.core.designsystem.HelloFeedback.error(views.root, err)
                    }
                }
            }
        }
    }

    private fun showSafetyMenu(partnerId: String, partnerName: String) {
        val items = arrayOf(
            "🛡️ Meetup Safety Checklist",
            getString(R.string.block_user),
            getString(R.string.report_user),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(partnerName)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showMeetupSafetyChecklist()
                    1 -> confirmBlock(partnerId, partnerName)
                    2 -> showReport(partnerId, partnerName)
                }
            }
            .show()
    }

    private fun confirmBlock(partnerId: String, partnerName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.block_user) + " $partnerName?")
            .setMessage(R.string.block_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.block_user) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        safetyRepository.blockUser(partnerId)
                        HelloFeedback.info(requireView(), "Blocked $partnerName")
                        findNavController().popBackStack()
                    } catch (e: Exception) {
                        HelloFeedback.error(requireView(), e.message ?: "Block failed")
                    }
                }
            }
            .show()
    }

    private fun showReport(partnerId: String, partnerName: String = "User") {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_report_user, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)

        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.reportDialogTitle)
        titleView?.text = "Report $partnerName"

        val reasonsGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.reportReasonsGroup)
        val detailsInput = dialogView.findViewById<android.widget.EditText>(R.id.inputReportDetails)
        val btnSubmit = dialogView.findViewById<View>(R.id.btnSubmitReport)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelReport)
        val btnDismiss = dialogView.findViewById<View>(R.id.btnDismissReport)

        btnCancel?.setOnClickListener { dialog.dismiss() }
        btnDismiss?.setOnClickListener { dialog.dismiss() }

        btnSubmit?.setOnClickListener {
            val selectedRadioId = reasonsGroup?.checkedRadioButtonId ?: -1
            val selectedRadio = dialogView.findViewById<android.widget.RadioButton>(selectedRadioId)
            val reason = selectedRadio?.text?.toString() ?: "Inappropriate behavior"
            val notes = detailsInput?.text?.toString()?.trim().orEmpty()
            val fullReport = if (notes.isNotEmpty()) "$reason - $notes" else reason

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    safetyRepository.report("USER", partnerId, fullReport)
                    HelloFeedback.success(requireView(), getString(R.string.report_submitted))
                    dialog.dismiss()
                } catch (e: Exception) {
                    HelloFeedback.error(requireView(), e.message ?: "Report failed")
                }
            }
        }

        dialog.show()
    }

    private fun showMeetupSafetyChecklist() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_meetup_safety, null)
        bottomSheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnUnderstoodSafety)?.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showLetsMeetSheet() { socialUi.suggestPlan(arguments?.getString("conversationId").orEmpty()) }

    override fun onDestroyView() {
        voiceUi.stopPlayback()
        binding = null
        super.onDestroyView()
    }
}

