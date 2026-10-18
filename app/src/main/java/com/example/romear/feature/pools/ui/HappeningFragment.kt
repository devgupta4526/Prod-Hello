package com.example.romear.feature.pools.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.romear.R
import com.example.romear.core.designsystem.AvatarView
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentHappeningBinding
import com.example.romear.feature.pools.domain.PoolDetail
import com.example.romear.feature.pools.domain.PoolItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

@AndroidEntryPoint
class HappeningFragment : Fragment() {

    private var binding: FragmentHappeningBinding? = null
    private val viewModel: HappeningViewModel by viewModels()

    private lateinit var adapter: PoolsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = HelloTransitions.tab()
        exitTransition = HelloTransitions.tab()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentHappeningBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)
        views.feedRetry.setOnClickListener { viewModel.loadPools() }

        adapter = PoolsAdapter { pool ->
            viewLifecycleOwner.lifecycleScope.launch {
                val detail = viewModel.getPoolDetail(pool.id)
                if (detail != null) {
                    showPoolDetailsBottomSheet(detail)
                }
            }
        }

        com.example.romear.core.designsystem.HelloLayouts.adaptiveCards(views.poolsRecyclerView)
        views.poolsRecyclerView.adapter = adapter

        views.hostPoolFab.setOnClickListener {
            showCreatePoolDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.loading, viewModel.pools, viewModel.loadFailed) { loading, pools, failed ->
                        Triple(loading, pools, failed)
                    }.collect { (loading, pools, failed) ->
                        adapter.submitList(pools)
                        views.loadingIndicator.isVisible = loading
                        views.emptyStateView.isVisible = !loading && !failed && pools.isEmpty()
                        views.feedError.isVisible = !loading && failed
                        views.hostPoolFab.isVisible = !failed
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is HappeningViewModel.HappeningEvent.Success -> {
                                com.example.romear.core.designsystem.HelloFeedback.success(views.root, event.message)
                            }
                            is HappeningViewModel.HappeningEvent.Error -> {
                                com.example.romear.core.designsystem.HelloFeedback.error(views.root, event.error)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showCreatePoolDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_pool, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.poolTitleInput)
        val activityInput = dialogView.findViewById<EditText>(R.id.poolActivityInput)
        val capacityInput = dialogView.findViewById<EditText>(R.id.poolCapacityInput)
        val submitBtn = dialogView.findViewById<MaterialButton>(R.id.submitCreatePoolButton)
        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.cancelCreatePoolButton)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        cancelBtn.setOnClickListener { dialog.dismiss() }
        submitBtn.setOnClickListener {
            val title = titleInput.text?.toString()?.trim().orEmpty()
            val activity = activityInput.text?.toString()?.trim().orEmpty()
            val capacity = capacityInput.text?.toString()?.toIntOrNull() ?: 4

            if (title.isNotBlank() && activity.isNotBlank() && capacity in 2..12) {
                viewModel.createPool(title, activity, capacity)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Capacity must be between 2 and 12", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showPoolDetailsBottomSheet(detail: PoolDetail) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_pool_details, null)
        bottomSheet.setContentView(sheetView)

        val titleView = sheetView.findViewById<TextView>(R.id.poolDetailTitle)
        val activityView = sheetView.findViewById<TextView>(R.id.poolDetailActivity)
        val capacityView = sheetView.findViewById<TextView>(R.id.poolDetailCapacity)
        val membersContainer = sheetView.findViewById<LinearLayout>(R.id.membersContainer)
        val knocksHeader = sheetView.findViewById<TextView>(R.id.knocksHeader)
        val knocksContainer = sheetView.findViewById<LinearLayout>(R.id.knocksContainer)
        val knockBtn = sheetView.findViewById<MaterialButton>(R.id.knockToJoinButton)
        val chatBtn = sheetView.findViewById<MaterialButton>(R.id.poolChatButton)
        val memberActions = sheetView.findViewById<View>(R.id.memberActionsContainer)
        val leaveBtn = sheetView.findViewById<MaterialButton>(R.id.leavePoolButton)
        val closeBtn = sheetView.findViewById<MaterialButton>(R.id.closePoolButton)

        titleView.text = detail.title
        activityView.text = detail.activity.uppercase()
        capacityView.text = "${detail.members.size} / ${detail.capacity} joined · ${detail.distanceBucket}"

        // Populate members
        membersContainer.removeAllViews()
        detail.members.forEach { m ->
            val memberRow = LayoutInflater.from(requireContext()).inflate(R.layout.item_connection, membersContainer, false)
            val avatar = memberRow.findViewById<AvatarView>(R.id.avatar)
            val name = memberRow.findViewById<TextView>(R.id.partnerName)
            val role = memberRow.findViewById<TextView>(R.id.connectionDate)
            avatar.load(m.user.photoUrl, m.user.displayName, sizeDp = 48)
            name.text = m.user.displayName
            role.text = if (m.role == "HOST") "Host" else "Member"
            membersContainer.addView(memberRow)
        }

        // Host controls
        if (detail.isHost) {
            closeBtn.isVisible = true
            closeBtn.setOnClickListener {
                viewModel.closePool(detail.id) { bottomSheet.dismiss() }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                val knocks = viewModel.listKnocks(detail.id)
                if (knocks.isNotEmpty()) {
                    knocksHeader.isVisible = true
                    knocksContainer.isVisible = true
                    knocksContainer.removeAllViews()
                    knocks.filter { it.status == "PENDING" }.forEach { k ->
                        val knockRow = LayoutInflater.from(requireContext()).inflate(R.layout.item_hello, knocksContainer, false)
                        val avatar = knockRow.findViewById<AvatarView>(R.id.avatar)
                        val name = knockRow.findViewById<TextView>(R.id.userName)
                        val intro = knockRow.findViewById<TextView>(R.id.helloMessage)
                        val acceptBtn = knockRow.findViewById<MaterialButton>(R.id.acceptButton)
                        val declineBtn = knockRow.findViewById<MaterialButton>(R.id.declineButton)

                        avatar.load(k.user.photoUrl, k.user.displayName)
                        name.text = k.user.displayName
                        intro.text = k.introMessage
                        acceptBtn.setOnClickListener {
                            viewModel.acceptKnock(detail.id, k.id) { bottomSheet.dismiss() }
                        }
                        declineBtn.setOnClickListener {
                            viewModel.declineKnock(detail.id, k.id) { knocksContainer.removeView(knockRow) }
                        }
                        knocksContainer.addView(knockRow)
                    }
                }
            }
        }

        // Non-member knock button
        if (!detail.isMember && detail.status == "ACTIVE") {
            knockBtn.isVisible = true
            knockBtn.setOnClickListener {
                showKnockPrompt(detail.id, bottomSheet)
            }
        }

        // Member/Host chat & leave
        if (detail.isMember) {
            memberActions.isVisible = true
            if (!detail.isHost) {
                leaveBtn.isVisible = true
                leaveBtn.setOnClickListener {
                    viewModel.leavePool(detail.id) { bottomSheet.dismiss() }
                }
            }
            if (detail.conversationId != null) {
                chatBtn.isVisible = true
                chatBtn.setOnClickListener {
                    bottomSheet.dismiss()
                    findNavController().navigate(
                        R.id.action_happening_to_chat,
                        bundleOf(
                            "conversationId" to detail.conversationId,
                            "partnerName" to detail.title,
                            "partnerPhotoUrl" to detail.host.photoUrl,
                            "partnerId" to detail.host.id,
                        ),
                    )
                }
            }
        }

        bottomSheet.show()
    }

    private fun showKnockPrompt(poolId: String, parentSheet: BottomSheetDialog) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.pool_knock_hint)
            minLines = 2
            setPadding(32, 24, 32, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pool_knock_prompt)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.pool_knock_button) { _, _ ->
                val intro = input.text?.toString()?.trim().orEmpty()
                viewModel.knock(poolId, intro)
                parentSheet.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
