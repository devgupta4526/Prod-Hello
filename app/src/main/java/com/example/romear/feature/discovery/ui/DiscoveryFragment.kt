package com.example.romear.feature.discovery.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.load as loadImage
import coil3.request.crossfade
import com.example.romear.R
import com.example.romear.core.designsystem.AvatarView
import com.example.romear.core.designsystem.HelloMotion
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentDiscoveryBinding
import com.example.romear.feature.discovery.domain.DiscoveryUser
import com.example.romear.feature.discovery.domain.LiveStatusNote
import com.example.romear.feature.safety.data.SafetyRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class DiscoveryFragment : Fragment() {

    @Inject lateinit var socialRepository: com.example.romear.feature.social.SocialRepository
    private lateinit var presenceLocation: com.example.romear.core.security.PresenceLocation
    private lateinit var socialUi: com.example.romear.feature.social.SocialUi

    private var binding: FragmentDiscoveryBinding? = null
    private val viewModel: DiscoveryViewModel by viewModels()

    @Inject
    lateinit var safetyRepository: SafetyRepository

    private lateinit var adapter: DiscoveryAdapter
    private lateinit var liveStatusAdapter: LiveStatusAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenceLocation = com.example.romear.core.security.PresenceLocation(this) { availability, location ->
            viewModel.setAvailability(availability, location)
        }
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

        views.homeButton.setOnClickListener {
            HelloMotion.bounce(it) { socialUi.home() }
        }
        views.upForButton.setOnClickListener {
            HelloMotion.bounce(it) { socialUi.upFor() }
        }

        // Live Status Stories row (Instagram Notes style)
        liveStatusAdapter = LiveStatusAdapter(
            onNoteClicked = { note ->
                val matchingUser = viewModel.users.value.firstOrNull { it.userId == note.userId }
                if (matchingUser != null) {
                    val context = JSONObject().put("type", "NOTE").put("text", note.noteText)
                    showSayHelloDialog(matchingUser, context)
                }
            },
            onAddNoteClicked = {
                socialUi.upFor()
            }
        )
        views.liveStatusRecyclerView.adapter = liveStatusAdapter

        // Daily Vibe Check icebreaker
        views.vibeCheckBanner.setOnClickListener {
            HelloMotion.bounce(it) {
                showDailyIcebreakerDialog()
            }
        }

        adapter = DiscoveryAdapter(
            onUserClicked = { user -> showUserProfileBottomSheet(user) },
            onSayHelloClicked = { user -> showSayHelloDialog(user) },
            onContextualHelloClicked = { user, type, content ->
                val context = JSONObject().put("type", type).put("text", content)
                showSayHelloDialog(user, context)
            }
        )

        com.example.romear.core.designsystem.HelloLayouts.adaptiveCards(views.usersRecyclerView)
        views.usersRecyclerView.adapter = adapter
        views.discoveryRetry.setOnClickListener { viewModel.loadNearby() }

        views.availabilityGroup.check(
            when (viewModel.availability.value) {
                "BROWSING" -> R.id.chipBrowsing
                "OFF" -> R.id.chipInvisible
                else -> R.id.chipOpen
            }
        )

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

                        // Update Instagram Notes strip with active vibes
                        val notes = mutableListOf<LiveStatusNote>()
                        notes.add(
                            LiveStatusNote(
                                userId = "self",
                                displayName = "You",
                                avatarUrl = null,
                                noteText = "⚡ Share vibe",
                                isCurrentUser = true
                            )
                        )
                        users.forEach { u ->
                            val text = if (!u.upFor.isNullOrBlank()) u.upFor else if (u.availability.equals("OPEN", true)) "Open to meet" else null
                            if (text != null) {
                                notes.add(
                                    LiveStatusNote(
                                        userId = u.userId,
                                        displayName = u.displayName,
                                        avatarUrl = u.photoUrl,
                                        noteText = text
                                    )
                                )
                            }
                        }
                        liveStatusAdapter.submitList(notes)

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

    private fun showDailyIcebreakerDialog() {
        val input = EditText(requireContext()).apply {
            hint = "e.g. Nagarjuna for Andhra meals or CTR for Benne Masala Dosa"
            setPadding(48, 36, 48, 36)
            textSize = 15f
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✨ Daily Bangalore Icebreaker")
            .setMessage("What's the best late-night food spot in the city?\n\nShare yours to unlock answers from 18 nearby people!")
            .setView(input)
            .setPositiveButton("Post & Unlock") { _, _ ->
                val answer = input.text?.toString()?.trim().orEmpty()
                if (answer.isNotBlank()) {
                    binding?.vibeCheckQuestion?.text = "You posted: \"$answer\" ✓"
                    com.example.romear.core.designsystem.HelloFeedback.success(
                        requireView(),
                        "Answer posted! Unlocked 18 nearby responses 🎉"
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSayHelloDialog(user: DiscoveryUser, context: JSONObject? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_say_hello, null)
        val messageInput = dialogView.findViewById<EditText>(R.id.messageInput)
        val replyPreviewContainer = dialogView.findViewById<View>(R.id.replyPreviewContainer)
        val replyPreviewText = dialogView.findViewById<TextView>(R.id.replyPreviewText)
        val replyPreviewIcon = dialogView.findViewById<ImageView>(R.id.replyPreviewIcon)
        val sendButton = dialogView.findViewById<MaterialButton>(R.id.sendButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancelButton)

        if (context != null) {
            replyPreviewContainer.visibility = View.VISIBLE
            val contextType = context.optString("type")
            val previewText = context.optString("text")
            replyPreviewText.text = "Replying to: \"$previewText\""
            replyPreviewIcon.setImageResource(
                when (contextType) {
                    "PHOTO" -> R.drawable.ic_profile
                    "VIBE" -> R.drawable.ic_bolt
                    "INTEREST" -> R.drawable.ic_discover
                    else -> R.drawable.ic_heart
                }
            )
            messageInput.hint = "Add your thoughtful reply..."
        } else {
            replyPreviewContainer.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener { dialog.dismiss() }
        sendButton.setOnClickListener {
            HelloMotion.bounce(it) {
                val text = messageInput.text?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) {
                    viewModel.sendHello(user.userId, text, context)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showUserProfileBottomSheet(user: DiscoveryUser) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_user_profile, null)
        bottomSheet.setContentView(sheetView)

        val heroPhoto = sheetView.findViewById<ImageView>(R.id.profileHeroPhoto)
        val avatar = sheetView.findViewById<AvatarView>(R.id.profileAvatar)
        val nameView = sheetView.findViewById<TextView>(R.id.profileName)
        val distanceView = sheetView.findViewById<TextView>(R.id.profileDistance)
        val verifiedBadge = sheetView.findViewById<ImageView>(R.id.profileVerifiedBadge)
        val irlBadge = sheetView.findViewById<TextView>(R.id.profileIrlBadge)
        val bioView = sheetView.findViewById<TextView>(R.id.profileBio)
        val interestsGroup = sheetView.findViewById<ChipGroup>(R.id.profileInterestsGroup)
        val sayHelloBtn = sheetView.findViewById<MaterialButton>(R.id.profileSayHelloButton)
        val safetyBtn = sheetView.findViewById<View>(R.id.safetyOptionsButton)
        val upForContainer = sheetView.findViewById<View>(R.id.profileUpForContainer)
        val upForText = sheetView.findViewById<TextView>(R.id.profileUpForText)
        val voiceCard = sheetView.findViewById<MaterialCardView>(R.id.voiceBioCard)
        val voicePlayBtn = sheetView.findViewById<View>(R.id.voicePlayButton)
        val voiceBars = sheetView.findViewById<TextView>(R.id.voiceWaveformBars)

        val displayName = if (user.age != null) "${user.displayName}, ${user.age}" else user.displayName
        nameView.text = displayName
        distanceView.text = user.distanceBucket
        avatar.load(user.photoUrl, user.displayName, sizeDp = 52)

        // Load Hero Photo
        if (!user.photoUrl.isNullOrBlank() && user.photoUrl.startsWith("https://")) {
            heroPhoto.loadImage(user.photoUrl) { crossfade(true) }
        } else {
            heroPhoto.setImageResource(R.drawable.illustration_people)
        }

        // Voice Bio interactive simulation
        voiceCard.visibility = View.VISIBLE
        var isPlayingVoice = false
        voicePlayBtn.setOnClickListener {
            isPlayingVoice = !isPlayingVoice
            HelloMotion.bounce(it)
            if (isPlayingVoice) {
                voiceBars.text = "▇▅▃▂ ▂▃▅▆▇▆▅▃▂ ▂▃"
                Toast.makeText(requireContext(), "Playing 15s voice bio 🎙️", Toast.LENGTH_SHORT).show()
            } else {
                voiceBars.text = " ▂▃▅▆▇▆▅▃▂ ▂▃▅▇▆▅▃"
            }
        }

        // Up for intent
        if (!user.upFor.isNullOrBlank()) {
            upForContainer.visibility = View.VISIBLE
            upForText.text = "Up for: ${user.upFor}"
            upForContainer.setOnClickListener {
                bottomSheet.dismiss()
                showSayHelloDialog(user, JSONObject().put("type", "VIBE").put("text", user.upFor))
            }
        } else {
            upForContainer.visibility = View.GONE
        }

        interestsGroup.removeAllViews()
        user.sharedInterests.forEach { interest ->
            val chip = Chip(requireContext()).apply {
                text = interest
                isClickable = true
                textSize = 12f
                setChipBackgroundColorResource(R.color.hello_surface_variant)
                setTextColor(ContextCompat.getColor(context, R.color.hello_text))
                setOnClickListener {
                    bottomSheet.dismiss()
                    showSayHelloDialog(user, JSONObject().put("type", "INTEREST").put("text", interest))
                }
            }
            interestsGroup.addView(chip)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val fullProfile = viewModel.getUserProfile(user.userId)
            if (fullProfile != null) {
                if (fullProfile.phoneVerified) {
                    verifiedBadge.visibility = View.VISIBLE
                }
                if (fullProfile.isIrlVerified) {
                    irlBadge.visibility = View.VISIBLE
                }

                interestsGroup.removeAllViews()
                fullProfile.interests.forEach { interest ->
                    val chip = Chip(requireContext()).apply {
                        text = interest
                        isClickable = true
                        textSize = 12f
                        setChipBackgroundColorResource(R.color.hello_surface_variant)
                        setTextColor(ContextCompat.getColor(context, R.color.hello_text))
                        setOnClickListener {
                            bottomSheet.dismiss()
                            showSayHelloDialog(user, JSONObject().put("type", "INTEREST").put("text", interest))
                        }
                    }
                    interestsGroup.addView(chip)
                }

                val personality = sheetView.findViewById<LinearLayout>(R.id.personalityContainer)
                personality.removeAllViews()

                // Hinge-Style Prompts as Quote Cards
                fullProfile.prompts.forEach { prompt ->
                    val question = when (prompt.id) {
                        "joy" -> "A small thing that brings me joy"
                        "learn" -> "Something I'd love to learn together"
                        else -> "A good friendship looks like"
                    }

                    val promptCard = MaterialCardView(requireContext()).apply {
                        radius = 20 * resources.displayMetrics.density
                        cardElevation = 0f
                        strokeWidth = (1 * resources.displayMetrics.density).toInt()
                        strokeColor = ContextCompat.getColor(context, R.color.hello_outline_subtle)
                        setCardBackgroundColor(ContextCompat.getColor(context, R.color.hello_surface_variant))
                        val layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, (14 * resources.displayMetrics.density).toInt())
                        }
                        this.layoutParams = layoutParams

                        val cardContent = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(
                                (18 * resources.displayMetrics.density).toInt(),
                                (16 * resources.displayMetrics.density).toInt(),
                                (18 * resources.displayMetrics.density).toInt(),
                                (16 * resources.displayMetrics.density).toInt()
                            )

                            // Question
                            addView(TextView(requireContext()).apply {
                                text = question
                                setTextAppearance(R.style.TextAppearance_Hello_Eyebrow)
                                setTextColor(ContextCompat.getColor(context, R.color.hello_primary))
                            })

                            // Answer
                            addView(TextView(requireContext()).apply {
                                text = prompt.answer
                                textSize = 16f
                                setTextColor(ContextCompat.getColor(context, R.color.hello_text))
                                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (12 * resources.displayMetrics.density).toInt())
                                setLineSpacing(3 * resources.displayMetrics.density, 1f)
                            })

                            // Contextual Reply Action
                            addView(MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
                                text = "Reply to this prompt 💬"
                                setBackgroundColor(ContextCompat.getColor(context, R.color.hello_surface))
                                setTextColor(ContextCompat.getColor(context, R.color.hello_text))
                                cornerRadius = (18 * resources.displayMetrics.density).toInt()
                                minHeight = (40 * resources.displayMetrics.density).toInt()
                                setOnClickListener {
                                    bottomSheet.dismiss()
                                    val contextJson = JSONObject()
                                        .put("type", "PROMPT")
                                        .put("id", prompt.id)
                                        .put("text", prompt.answer)
                                    showSayHelloDialog(user, contextJson)
                                }
                            })
                        }
                        addView(cardContent)
                    }
                    personality.addView(promptCard)
                }

                // Extra Photos as Hero Cards
                fullProfile.photos.forEachIndexed { index, url ->
                    val photoCard = MaterialCardView(requireContext()).apply {
                        radius = 20 * resources.displayMetrics.density
                        cardElevation = 0f
                        strokeWidth = 0
                        val layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, (14 * resources.displayMetrics.density).toInt())
                        }
                        this.layoutParams = layoutParams

                        val container = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL

                            val img = ImageView(requireContext()).apply {
                                this.layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    (240 * resources.displayMetrics.density).toInt()
                                )
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                loadImage(url) { crossfade(true) }
                                contentDescription = "${fullProfile.displayName}, photo ${index + 1}"
                            }
                            addView(img)

                            val replyBtn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
                                text = "Reply to photo 📸"
                                setBackgroundColor(ContextCompat.getColor(context, R.color.hello_surface_variant))
                                setTextColor(ContextCompat.getColor(context, R.color.hello_text))
                                cornerRadius = (18 * resources.displayMetrics.density).toInt()
                                minHeight = (40 * resources.displayMetrics.density).toInt()
                                val btnParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(
                                        (12 * resources.displayMetrics.density).toInt(),
                                        (8 * resources.displayMetrics.density).toInt(),
                                        (12 * resources.displayMetrics.density).toInt(),
                                        (12 * resources.displayMetrics.density).toInt()
                                    )
                                }
                                this.layoutParams = btnParams
                                setOnClickListener {
                                    bottomSheet.dismiss()
                                    val photoId = fullProfile.photoIds.getOrElse(index) { "$index" }
                                    val contextJson = JSONObject()
                                        .put("type", "PHOTO")
                                        .put("id", photoId)
                                        .put("text", "Photo ${index + 1}")
                                    showSayHelloDialog(user, contextJson)
                                }
                            }
                            addView(replyBtn)
                        }
                        addView(container)
                    }
                    personality.addView(photoCard)
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
