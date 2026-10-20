package com.example.romear.feature.hello.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.romear.R
import com.example.romear.core.designsystem.ExperienceViews
import com.example.romear.core.designsystem.HelloFeedback
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentInboxBinding
import com.example.romear.feature.hello.domain.ConnectionItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InboxFragment : Fragment() {
    private var binding: FragmentInboxBinding? = null
    private val viewModel: InboxViewModel by viewModels()
    private lateinit var hellosAdapter: HellosAdapter
    private lateinit var connectionsAdapter: ConnectionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = HelloTransitions.tab()
        exitTransition = HelloTransitions.tab()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentInboxBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)
        views.headerEyebrow.text = "GOOD THINGS START WITH HELLO"
        views.feedRetry.setOnClickListener { viewModel.loadData() }
        views.btnActivity.setOnClickListener {
            findNavController().navigate(R.id.activityFragment)
        }
        views.inboxTabGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.contains(views.tabSent.id)) {
                viewModel.switchTab(InboxViewModel.Tab.SENT)
            } else {
                viewModel.switchTab(InboxViewModel.Tab.RECEIVED)
            }
        }
        hellosAdapter = HellosAdapter(
            isReceivedBox = { viewModel.currentTab.value != InboxViewModel.Tab.SENT },
            onAcceptClicked = viewModel::acceptHello,
            onDeclineClicked = viewModel::declineHello,
            onWithdrawClicked = viewModel::withdrawHello,
            onItemClicked = ::showWaveReviewSheet,
        )
        connectionsAdapter = ConnectionsAdapter(::openChat)
        val conversationsHeading = InboxSectionAdapter { parent ->
            val ui = ExperienceViews(parent.context)
            ui.column().apply {
                setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(8))
                addView(ui.text("Your conversations", 18f, R.color.text_primary, true))
            }
        }
        val combined = ConcatAdapter(hellosAdapter, conversationsHeading, connectionsAdapter)
        val sent = ConcatAdapter(hellosAdapter)
        views.inboxRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        views.inboxRecyclerView.adapter = combined
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.loading, viewModel.hellos, viewModel.connections, viewModel.currentTab, viewModel.loadFailed) { _, _, _, _, _ -> Unit }.collect {
                        val sentBox = viewModel.currentTab.value == InboxViewModel.Tab.SENT
                        val loading = viewModel.loading.value
                        val failed = viewModel.loadFailed.value
                        val pending = viewModel.hellos.value.filter { it.status == "PENDING" }
                        val list = if (sentBox) viewModel.hellos.value else pending
                        views.inboxRecyclerView.adapter = if (sentBox) sent else combined
                        // Sync tab chip if needed
                        if (sentBox && !views.tabSent.isChecked) views.tabSent.isChecked = true
                        else if (!sentBox && !views.tabReceived.isChecked) views.tabReceived.isChecked = true

                        hellosAdapter.submitList(list) { hellosAdapter.notifyDataSetChanged() }
                        connectionsAdapter.submitList(viewModel.connections.value)
                        conversationsHeading.visible = !sentBox && viewModel.connections.value.isNotEmpty()
                        views.headerTitle.text = if (sentBox) "Sent hellos." else "Inbox."
                        views.inboxSummary.text = if (sentBox) "The conversations you’ve started." else "${pending.size} new waves · ${viewModel.connections.value.size} conversations"
                        views.loadingIndicator.isVisible = loading && list.isEmpty()
                        val isEmpty = if (sentBox) list.isEmpty() else (list.isEmpty() && viewModel.connections.value.isEmpty())
                        views.emptyStateView.isVisible = !loading && !failed && isEmpty
                        views.feedError.isVisible = !loading && failed
                        views.inboxRecyclerView.isVisible = !failed && !isEmpty
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is InboxViewModel.InboxEvent.OpenChat -> showMatchCelebrationDialog(event.connection)
                            is InboxViewModel.InboxEvent.Success -> HelloFeedback.success(views.root, event.message)
                            is InboxViewModel.InboxEvent.Error -> HelloFeedback.error(views.root, event.error)
                        }
                    }
                }
            }
        }
    }

    private fun openChat(connection: ConnectionItem) {
        val conversationId = connection.conversationId ?: return
        if (findNavController().currentDestination?.id != R.id.inboxFragment) return
        val args = android.os.Bundle().apply {
            putString("conversationId", conversationId)
            putString("partnerName", connection.partner.displayName)
            putString("partnerPhotoUrl", connection.partner.photoUrl)
            putString("partnerId", connection.partner.id)
        }
        findNavController().navigate(R.id.action_inbox_to_chat, args)
    }

    private fun showMatchCelebrationDialog(connection: ConnectionItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_match_celebration, null)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)

        val partnerName = connection.partner.displayName
        dialogView.findViewById<TextView>(R.id.celebrationTitle)?.text = "It's a Hello! 🎉"
        dialogView.findViewById<TextView>(R.id.celebrationSubtitle)?.text =
            "You and $partnerName are now connected. Say hello back to keep the connection warm."

        dialogView.findViewById<View>(R.id.btnCelebrationChat)?.setOnClickListener {
            dialog.dismiss()
            openChat(connection)
        }
        dialogView.findViewById<View>(R.id.btnKeepBrowsing)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showWaveReviewSheet(item: com.example.romear.feature.hello.domain.HelloItem) {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_wave_review, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        val isReceived = viewModel.currentTab.value != InboxViewModel.Tab.SENT
        val user = if (isReceived) item.sender else item.receiver
        val displayName = user?.displayName ?: "Someone nearby"

        sheetView.findViewById<com.example.romear.core.designsystem.AvatarView>(R.id.waveSenderAvatar)?.load(user?.photoUrl, displayName)
        sheetView.findViewById<TextView>(R.id.waveSenderName)?.text = displayName
        sheetView.findViewById<TextView>(R.id.waveSenderDistance)?.text = "📍 Nearby · Open to chat"

        val contextSnippet = sheetView.findViewById<TextView>(R.id.waveContextSnippet)
        if (item.contextText != null) {
            contextSnippet?.text = item.contextText
        } else {
            sheetView.findViewById<View>(R.id.waveContextContainer)?.visibility = View.GONE
        }

        sheetView.findViewById<TextView>(R.id.waveMessageNote)?.text = item.message.ifBlank { "“Sent you a hello wave 👋”" }

        sheetView.findViewById<View>(R.id.btnCloseWaveReview)?.setOnClickListener { sheet.dismiss() }

        val btnAccept = sheetView.findViewById<View>(R.id.btnAcceptWaveReview)
        val btnDecline = sheetView.findViewById<View>(R.id.btnDeclineWaveReview)

        if (isReceived && item.status == "PENDING") {
            btnAccept?.visibility = View.VISIBLE
            btnDecline?.visibility = View.VISIBLE
            btnAccept?.setOnClickListener {
                sheet.dismiss()
                viewModel.acceptHello(item)
            }
            btnDecline?.setOnClickListener {
                sheet.dismiss()
                viewModel.declineHello(item)
            }
        } else {
            btnAccept?.visibility = View.GONE
            btnDecline?.visibility = View.GONE
        }

        sheet.show()
    }

    override fun onDestroyView() {
        binding?.inboxRecyclerView?.adapter = null
        binding = null
        super.onDestroyView()
    }
}

private class InboxSectionAdapter(private val create: (ViewGroup) -> View) : RecyclerView.Adapter<InboxSectionAdapter.Holder>() {
    var visible = true
        set(value) { if (field != value) { field = value; if (value) notifyItemInserted(0) else notifyItemRemoved(0) } }
    class Holder(val container: android.widget.FrameLayout) : RecyclerView.ViewHolder(container)
    override fun getItemCount() = if (visible) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(android.widget.FrameLayout(parent.context).apply {
        layoutParams = RecyclerView.LayoutParams(-1, -2)
    })
    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.container.removeAllViews()
        holder.container.addView(create(holder.container))
    }
}
