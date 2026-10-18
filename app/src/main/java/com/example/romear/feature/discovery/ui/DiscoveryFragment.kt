package com.example.romear.feature.discovery.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.romear.R
import com.example.romear.core.designsystem.AvatarView
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentDiscoveryBinding
import com.example.romear.feature.discovery.domain.DiscoveryUser
import com.example.romear.feature.safety.data.SafetyRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@AndroidEntryPoint
class DiscoveryFragment : Fragment() {

    @javax.inject.Inject lateinit var socialRepository: com.example.romear.feature.social.SocialRepository
    private lateinit var presenceLocation: com.example.romear.core.security.PresenceLocation
    private lateinit var socialUi: com.example.romear.feature.social.SocialUi

    private var binding: FragmentDiscoveryBinding? = null
    private val viewModel: DiscoveryViewModel by viewModels()

    @Inject
    lateinit var safetyRepository: SafetyRepository

    private lateinit var adapter: DiscoveryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenceLocation = com.example.romear.core.security.PresenceLocation(this) { availability, location -> viewModel.setAvailability(availability, location) }
        socialUi = com.example.romear.feature.social.SocialUi(this, socialRepository)
        enterTransition = HelloTransitions.tab()
        exitTransition = HelloTransitions.tab()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentDiscoveryBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)

        views.homeButton.setOnClickListener { socialUi.home() }
        views.upForButton.setOnClickListener { socialUi.upFor() }

        adapter = DiscoveryAdapter(
            onUserClicked = { user -> showUserProfileBottomSheet(user) },
            onSayHelloClicked = { user -> showSayHelloDialog(user) },
        )

        com.example.romear.core.designsystem.HelloLayouts.adaptiveCards(views.usersRecyclerView)
        views.usersRecyclerView.adapter = adapter
        views.discoveryRetry.setOnClickListener { viewModel.loadNearby() }

        views.availabilityGroup.check(when (viewModel.availability.value) {
            "BROWSING" -> R.id.chipBrowsing
            "OFF" -> R.id.chipInvisible
            else -> R.id.chipOpen
        })
        fun chooseAvailability(availability: String, source: View) {

            com.example.romear.core.designsystem.HelloHaptics.selection(source)
            presenceLocation.choose(availability)
        }
        views.chipOpen.setOnClickListener { chooseAvailability("OPEN", it) }
        views.chipBrowsing.setOnClickListener { chooseAvailability("BROWSING", it) }
        views.chipInvisible.setOnClickListener { chooseAvailability("OFF", it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.loading, viewModel.users, viewModel.loadFailed) { loading, users, failed ->
                        Triple(loading, users, failed)
                    }.collect { (loading, users, failed) ->
                        adapter.submitList(users)
                        views.loadingIndicator.isVisible = loading
                        views.emptyStateView.isVisible = !loading && users.isEmpty() && !failed
                        views.discoveryError.isVisible = !loading && failed
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is DiscoveryViewModel.DiscoveryEvent.HelloSent -> {
                                com.example.romear.core.designsystem.HelloFeedback.success(views.root, event.message)
                            }
                            is DiscoveryViewModel.DiscoveryEvent.Error -> {
                                com.example.romear.core.designsystem.HelloFeedback.error(views.root, event.error)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showSayHelloDialog(user: DiscoveryUser, context: org.json.JSONObject? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_say_hello, null)
        val messageInput = dialogView.findViewById<EditText>(R.id.messageInput)
        context?.let { messageInput.hint = "Reply to: ${it.optString("text")}" }
        val sendButton = dialogView.findViewById<MaterialButton>(R.id.sendButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancelButton)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        sendButton.setOnClickListener {
            val text = messageInput.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                viewModel.sendHello(user.userId, text, context)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showUserProfileBottomSheet(user: DiscoveryUser) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_user_profile, null)
        bottomSheet.setContentView(sheetView)

        val avatar = sheetView.findViewById<AvatarView>(R.id.profileAvatar)
        val nameView = sheetView.findViewById<android.widget.TextView>(R.id.profileName)
        val distanceView = sheetView.findViewById<android.widget.TextView>(R.id.profileDistance)
        val bioView = sheetView.findViewById<android.widget.TextView>(R.id.profileBio)
        val interestsGroup = sheetView.findViewById<ChipGroup>(R.id.profileInterestsGroup)
        val sayHelloBtn = sheetView.findViewById<MaterialButton>(R.id.profileSayHelloButton)
        val safetyBtn = sheetView.findViewById<View>(R.id.safetyOptionsButton)

        val displayName = if (user.age != null) "${user.displayName}, ${user.age}" else user.displayName
        nameView.text = displayName
        distanceView.text = user.distanceBucket
        avatar.load(user.photoUrl, user.displayName)

        interestsGroup.removeAllViews()
        user.sharedInterests.forEach { interest ->
            val chip = Chip(requireContext()).apply {
                text = interest
                isClickable = false
            }
            interestsGroup.addView(chip)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val fullProfile = viewModel.getUserProfile(user.userId)
            if (fullProfile != null) {
                interestsGroup.removeAllViews()
                fullProfile.interests.forEach { interest -> interestsGroup.addView(Chip(requireContext()).apply { text = interest; isClickable = false }) }
                val personality = sheetView.findViewById<android.widget.LinearLayout>(R.id.personalityContainer)
                fun text(value: String) { personality.addView(android.widget.TextView(requireContext()).apply { this.text = value; textSize = 16f; setPadding(0,16,0,8) }) }
                text(if(fullProfile.phoneVerified) "Phone verified" else "Phone not verified")
                fullProfile.upFor?.let {
                    val until = fullProfile.upForUntil?.let { value -> java.time.format.DateTimeFormatter.ofPattern("EEE h:mm a").format(java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault())) }.orEmpty()
                    text("Up for $it · until $until")
                }
                if(fullProfile.idealWeekend.isNotBlank()) text("My ideal weekend\n${fullProfile.idealWeekend}")
                fullProfile.photos.forEachIndexed { index, url ->
                    val photo = AvatarView(requireContext()).apply { load(url, fullProfile.displayName, sizeDp = 160); contentDescription = "${fullProfile.displayName}, photo ${index+1}" }
                    personality.addView(photo, android.widget.LinearLayout.LayoutParams((160 * resources.displayMetrics.density).toInt(), (160 * resources.displayMetrics.density).toInt()))
                    personality.addView(MaterialButton(requireContext()).apply { this.text = "Reply to photo ${index+1}"; setOnClickListener { bottomSheet.dismiss(); showSayHelloDialog(user, org.json.JSONObject().put("type","PHOTO").put("id",fullProfile.photoIds[index]).put("text","Photo ${index+1}")) } })
                }
                fullProfile.prompts.forEach { prompt ->
                    val question = when(prompt.id) { "joy" -> "A small thing that brings me joy"; "learn" -> "Something I'd love to learn together"; else -> "A good friendship looks like" }
                    text("$question\n${prompt.answer}")
                    personality.addView(MaterialButton(requireContext()).apply { this.text = "Reply to this prompt"; setOnClickListener { bottomSheet.dismiss(); showSayHelloDialog(user, org.json.JSONObject().put("type","PROMPT").put("id",prompt.id).put("text",prompt.answer)) } })
                }
            }
            if (fullProfile?.bio != null) {
                bioView.text = fullProfile.bio
                bioView.isVisible = true
            } else {
                bioView.isVisible = false
            }
        }

        sayHelloBtn.setOnClickListener {
            bottomSheet.dismiss()
            showSayHelloDialog(user)
        }

        safetyBtn.setOnClickListener {
            showSafetyOptionsDialog(user.userId, user.displayName, bottomSheet)
        }

        bottomSheet.show()
    }

    private fun showSafetyOptionsDialog(userId: String, displayName: String, bottomSheet: BottomSheetDialog) {
        val options = arrayOf(
            getString(R.string.block_user),
            getString(R.string.report_user),
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmBlockUser(userId, displayName, bottomSheet)
                    1 -> showReportDialog(userId, "USER")
                }
            }
            .show()
    }

    private fun confirmBlockUser(userId: String, displayName: String, bottomSheet: BottomSheetDialog) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.block_user))
            .setMessage(getString(R.string.block_confirm))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.block_user) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        safetyRepository.blockUser(userId)
                        Toast.makeText(requireContext(), "Blocked $displayName", Toast.LENGTH_SHORT).show()
                        bottomSheet.dismiss()
                        viewModel.loadNearby()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), e.message ?: "Failed to block", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showReportDialog(targetId: String, targetType: String) {
        val reasons = arrayOf("Spam or scam", "Harassment or abuse", "Inappropriate content", "Under 18")
        var selectedReason = reasons[0]

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.report_user)
            .setSingleChoiceItems(reasons, 0) { _, which ->
                selectedReason = reasons[which]
            }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.submit) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        safetyRepository.report(targetType, targetId, selectedReason)
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
