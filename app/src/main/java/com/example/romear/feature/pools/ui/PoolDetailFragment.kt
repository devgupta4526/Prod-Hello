package com.example.romear.feature.pools.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.romear.R
import com.example.romear.core.designsystem.AvatarView
import com.example.romear.core.designsystem.HelloFeedback
import com.example.romear.core.designsystem.HelloHaptics
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentPoolDetailBinding
import com.example.romear.feature.pools.domain.PoolDetail
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class PoolDetailFragment : Fragment() {

    private var binding: FragmentPoolDetailBinding? = null
    private val viewModel: HappeningViewModel by viewModels()
    private var currentPoolId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = HelloTransitions.forward()
        exitTransition = HelloTransitions.backward()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPoolDetailBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)
        currentPoolId = arguments?.getString("poolId") ?: return

        views.btnBack.setOnClickListener {
            HelloHaptics.selection(it)
            findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is HappeningViewModel.HappeningEvent.Success -> HelloFeedback.success(views.root, event.message)
                        is HappeningViewModel.HappeningEvent.Error -> HelloFeedback.error(views.root, event.error)
                    }
                }
            }
        }

        loadPoolDetails()
    }

    private fun loadPoolDetails() {
        val poolId = currentPoolId ?: return
        val views = binding ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val detail = viewModel.getPoolDetail(poolId)
            if (detail != null && isAdded && binding != null) {
                bindDetails(detail)
            }
        }
    }

    private fun bindDetails(detail: PoolDetail) {
        val views = binding ?: return

        views.topBarTitle.text = detail.title
        views.poolArtworkHero.category = detail.activity
        views.poolDetailTitle.text = detail.title
        views.poolDetailActivity.text = detail.activity.uppercase()
        views.poolDetailDescription.text = "Casual ${detail.activity.lowercase()} hangout: ${detail.title}. Meet real people offline in Nagpur."

        val startFormatted = formatTime(detail.timeWindowStart)
        val endFormatted = formatTime(detail.timeWindowEnd)
        views.poolDetailTime.text = "$startFormatted – $endFormatted"
        views.poolDetailTimeBadge.text = "Starts ${startFormatted.substringAfterLast(",")}"

        val venueText = detail.distanceBucket.removePrefix("📍").trim()
        views.poolDetailLocation.text = detail.title
        views.poolDetailAddress.text = venueText

        views.btnOpenMaps.setOnClickListener {
            HelloHaptics.selection(it)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode("$venueText Nagpur")}"))
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(requireContext(), "Opening $venueText", Toast.LENGTH_SHORT).show()
            }
        }

        views.btnShare.setOnClickListener {
            HelloHaptics.selection(it)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, detail.title)
                putExtra(Intent.EXTRA_TEXT, "Join me for this hangout on RomEar: ${detail.title} in Nagpur! ⚡")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Hangout"))
        }

        val spotsLeft = (detail.capacity - detail.members.size).coerceAtLeast(0)
        views.poolDetailCapacity.text = "${detail.members.size} of ${detail.capacity} joined · $spotsLeft places left"
        views.attendeesCountBadge.text = "${detail.members.size} Joined"

        // Host card
        views.poolDetailHostAvatar.load(detail.host.photoUrl, detail.host.displayName, sizeDp = 48)
        views.poolDetailHostName.text = "Hosted by ${detail.host.displayName}"

        // Attendees rows (NO weird oval!)
        views.attendeesContainer.removeAllViews()
        detail.members.forEach { member ->
            val attendeeView = LayoutInflater.from(requireContext()).inflate(R.layout.item_pool_attendee, views.attendeesContainer, false)
            val avatar = attendeeView.findViewById<AvatarView>(R.id.attendeeAvatar)
            val name = attendeeView.findViewById<TextView>(R.id.attendeeName)
            val role = attendeeView.findViewById<TextView>(R.id.attendeeRole)
            val statusBadge = attendeeView.findViewById<TextView>(R.id.attendeeStatusBadge)

            avatar.load(member.user.photoUrl, member.user.displayName, sizeDp = 42)
            name.text = member.user.displayName
            val isHost = member.role == "HOST"
            role.text = if (isHost) "👑 Hangout Host · Verified" else "🏸 Member · IRL Verified"
            statusBadge.text = if (isHost) "Host" else "Going"
            views.attendeesContainer.addView(attendeeView)
        }

        // Action Buttons Setup
        val isJoined = detail.isMember || detail.isHost
        if (isJoined) {
            views.notJoinedActions.isVisible = false
            views.joinedActions.isVisible = true

            views.btnOpenGroupChat.setOnClickListener {
                HelloHaptics.success(it)
                val convId = detail.conversationId ?: "pool-${detail.id}"
                val bundle = bundleOf(
                    "conversationId" to convId,
                    "partnerName" to detail.title,
                    "partnerPhotoUrl" to null,
                    "partnerId" to "pool-group",
                )
                findNavController().navigate(R.id.chatFragment, bundle)
            }

            if (!detail.isHost) {
                views.btnLeavePool.isVisible = true
                views.btnLeavePool.setOnClickListener {
                    HelloHaptics.selection(it)
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Leave Hangout?")
                        .setMessage("Your spot will be released to other members nearby.")
                        .setPositiveButton("Leave") { _, _ ->
                            viewModel.leavePool(detail.id) {
                                loadPoolDetails()
                            }
                        }
                        .setNegativeButton("Stay", null)
                        .show()
                }
            } else {
                views.btnLeavePool.isVisible = false
                views.btnClosePool.isVisible = true
                views.btnClosePool.setOnClickListener {
                    HelloHaptics.selection(it)
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cancel Hangout?")
                        .setMessage("This will cancel the hangout and notify all joined attendees.")
                        .setPositiveButton("Cancel Hangout") { _, _ ->
                            viewModel.closePool(detail.id) {
                                findNavController().navigateUp()
                            }
                        }
                        .setNegativeButton("Dismiss", null)
                        .show()
                }
            }
        } else {
            views.notJoinedActions.isVisible = true
            views.joinedActions.isVisible = false

            views.btnInstantJoin.isEnabled = spotsLeft > 0
            views.btnInstantJoin.text = if (spotsLeft > 0) "⚡ Instant Join Hangout" else "Hangout is Full"
            views.btnInstantJoin.setOnClickListener {
                HelloHaptics.success(it)
                views.btnInstantJoin.isEnabled = false
                viewModel.joinPool(detail.id) { success ->
                    views.btnInstantJoin.isEnabled = true
                    if (success) {
                        loadPoolDetails()
                    }
                }
            }
        }
    }

    private fun formatTime(value: String): String = runCatching {
        Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, h:mm a"))
    }.getOrDefault(value)

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
