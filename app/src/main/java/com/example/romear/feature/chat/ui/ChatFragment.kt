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
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentChatBinding
import com.example.romear.feature.safety.data.SafetyRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatFragment : Fragment() {

    @javax.inject.Inject lateinit var socialRepository: com.example.romear.feature.social.SocialRepository
    private lateinit var socialUi: com.example.romear.feature.social.SocialUi

    private var binding: FragmentChatBinding? = null
    private val viewModel: ChatViewModel by viewModels()

    @Inject
    lateinit var safetyRepository: SafetyRepository

    private lateinit var adapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        socialUi = com.example.romear.feature.social.SocialUi(this, socialRepository)
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

        val conversationId = arguments?.getString("conversationId").orEmpty()
        views.planButton.setOnClickListener { socialUi.plans(conversationId) }
        val partnerName = arguments?.getString("partnerName").orEmpty()
        val partnerPhotoUrl = arguments?.getString("partnerPhotoUrl")
        val partnerId = arguments?.getString("partnerId").orEmpty()

        views.partnerName.text = partnerName
        views.partnerAvatar.load(partnerPhotoUrl, partnerName)

        adapter = MessagesAdapter()
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        views.messagesRecyclerView.layoutManager = layoutManager
        views.messagesRecyclerView.adapter = adapter

        views.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        views.messageInput.setText(viewModel.draft.value)
        views.messageInput.doAfterTextChanged { viewModel.updateDraft(it?.toString().orEmpty()) }
        views.sendButton.setOnClickListener {
            val text = views.messageInput.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)

            }
        }

        views.optionsButton.setOnClickListener {
            showSafetyMenu(partnerId, partnerName)
        }

        viewModel.initConversation(conversationId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                    viewModel.loading.collect { loading ->
                        views.loadingIndicator.isVisible = loading && adapter.itemCount == 0
                    }
                }
                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages) {
                            if (messages.isNotEmpty()) {
                                views.messagesRecyclerView.scrollToPosition(messages.size - 1)
                            }
                        }
                        views.emptyChatView.isVisible = messages.isEmpty() && !viewModel.loading.value
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
            getString(R.string.block_user),
            getString(R.string.report_user),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(partnerName)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> confirmBlock(partnerId, partnerName)
                    1 -> showReport(partnerId)
                }
            }
            .show()
    }

    private fun confirmBlock(partnerId: String, partnerName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.block_user)
            .setMessage(R.string.block_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.block_user) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        safetyRepository.blockUser(partnerId)
                        Toast.makeText(requireContext(), "Blocked $partnerName", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Block failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showReport(partnerId: String) {
        val reasons = arrayOf("Harassment", "Spam", "Inappropriate messages", "Under 18")
        var selected = reasons[0]
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.report_user)
            .setSingleChoiceItems(reasons, 0) { _, which -> selected = reasons[which] }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.submit) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        safetyRepository.report("USER", partnerId, selected)
                        Toast.makeText(requireContext(), R.string.report_submitted, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Report failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
