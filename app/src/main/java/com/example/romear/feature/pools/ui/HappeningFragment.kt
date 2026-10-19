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
import com.example.romear.core.designsystem.HelloMotion
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

    private lateinit var hostLocation: com.example.romear.core.security.PresenceLocation
    private var locationResult: ((android.location.Location) -> Unit)? = null
    private lateinit var adapter: PoolsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hostLocation = com.example.romear.core.security.PresenceLocation(this) { _, location -> location?.let { locationResult?.invoke(it) } }
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

        views.btnTopHost.setOnClickListener {
            HelloMotion.bounce(it) { showCreatePoolDialog() }
        }
        views.btnEmptyHost.setOnClickListener {
            HelloMotion.bounce(it) { showCreatePoolDialog() }
        }
        views.hostPoolFab.setOnClickListener {
            HelloMotion.bounce(it) { showCreatePoolDialog() }
        }

        views.categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipAll
            val allPools = viewModel.pools.value
            val filtered = when (checkedId) {
                R.id.chipCoffee -> allPools.filter { it.activity.contains("coffee", ignoreCase = true) || it.title.contains("coffee", ignoreCase = true) }
                R.id.chipSports -> allPools.filter { it.activity.contains("sport", ignoreCase = true) || it.activity.contains("badminton", ignoreCase = true) || it.title.contains("badminton", ignoreCase = true) }
                R.id.chipFood -> allPools.filter { it.activity.contains("food", ignoreCase = true) || it.activity.contains("breakfast", ignoreCase = true) || it.title.contains("food", ignoreCase = true) }
                R.id.chipGames -> allPools.filter { it.activity.contains("game", ignoreCase = true) || it.activity.contains("tech", ignoreCase = true) || it.title.contains("game", ignoreCase = true) }
                R.id.chipDrinks -> allPools.filter { it.activity.contains("drink", ignoreCase = true) || it.activity.contains("beer", ignoreCase = true) }
                else -> allPools
            }
            adapter.submitList(filtered)
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

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(dialogView)
        var start: java.time.ZonedDateTime? = null
        var location: android.location.Location? = null
        val dateButton = dialogView.findViewById<MaterialButton>(R.id.poolDateButton)
        val locationButton = dialogView.findViewById<MaterialButton>(R.id.poolLocationButton)
        val duration = dialogView.findViewById<android.widget.Spinner>(R.id.poolDuration)
        duration.adapter = android.widget.ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("1 hour", "2 hours", "3 hours"))
        dateButton.setOnClickListener {
            val now = java.time.ZonedDateTime.now()
            android.app.DatePickerDialog(requireContext(), { _,y,m,d ->
                android.app.TimePickerDialog(requireContext(), { _,h,min ->
                    start = now.withDayOfMonth(1).withYear(y).withMonth(m+1).withDayOfMonth(d).withHour(h).withMinute(min).withSecond(0).withNano(0)
                    dateButton.text = start!!.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a"))
                },now.hour,now.minute,false).show()
            },now.year,now.monthValue-1,now.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() }.show()
        }
        locationResult = { result -> location = result; locationButton.text = "Nearby location selected ✓" }
        locationButton.setOnClickListener { hostLocation.choose("OPEN") }
        dialog.setOnDismissListener { locationResult = null }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        submitBtn.setOnClickListener {
            val title = titleInput.text?.toString()?.trim().orEmpty()
            val activity = activityInput.text?.toString()?.trim().orEmpty()
            val capacity = capacityInput.text?.toString()?.toIntOrNull() ?: 0

            titleInput.error = if(title.isBlank()) "Give your plan a name" else null
            activityInput.error = if(activity.isBlank()) "Choose an activity" else null
            capacityInput.error = if(capacity !in 2..12) "Choose 2–12 people, including you" else null
            val chosenStart = start
            val chosenLocation = location
            if(title.isBlank() || activity.isBlank() || capacity !in 2..12) return@setOnClickListener
            if(chosenStart == null || chosenStart.toInstant() <= java.time.Instant.now()) {
                Toast.makeText(requireContext(),"Choose a future date and time",Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            if(chosenLocation == null) { Toast.makeText(requireContext(),"Choose your nearby location",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            submitBtn.isEnabled = false
            viewModel.createPool(title,activity,capacity,chosenStart.toInstant().toString(),chosenStart.plusHours((duration.selectedItemPosition+1).toLong()).toInstant().toString(),chosenLocation.latitude,chosenLocation.longitude) { success -> if(success) dialog.dismiss() else submitBtn.isEnabled = true }
        }

        dialog.show()
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
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
        capacityView.text = "${detail.members.size} of ${detail.capacity} joined · ${(detail.capacity - detail.members.size).coerceAtLeast(0)} places left\n${detail.distanceBucket}\n${formatTime(detail.timeWindowStart)} – ${formatTime(detail.timeWindowEnd)}\nHosted by ${detail.host.displayName}"

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

    private fun formatTime(value: String): String = runCatching { java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")) }.getOrDefault(value)

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
