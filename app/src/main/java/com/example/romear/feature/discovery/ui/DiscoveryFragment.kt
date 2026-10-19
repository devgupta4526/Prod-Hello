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
import androidx.core.widget.doAfterTextChanged
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
            HelloMotion.bounce(it) { showShareVibeBottomSheet() }
        }
        views.radarIcon.setOnClickListener {
            HelloMotion.bounce(it) { showRadarHotspotsSheet() }
        }
        views.filterIcon.setOnClickListener {
            HelloMotion.bounce(it) { showDiscoveryFilterSheet() }
        }
        views.btnExpandRadius.setOnClickListener {
            HelloMotion.bounce(it) {
                com.example.romear.core.designsystem.HelloFeedback.info(requireView(), "Searching up to 10 km nearby…")
                viewModel.loadNearby()
            }
        }
        views.btnShareFriends.setOnClickListener {
            HelloMotion.bounce(it) {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Join me on Romear to discover real people and hangout spots nearby! https://romear.app")
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(sendIntent, "Share Romear"))
            }
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
                showShareVibeBottomSheet()
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
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_icebreaker, null)
        bottomSheet.setContentView(sheetView)

        val input = sheetView.findViewById<EditText>(R.id.icebreaker_answer_input)
        val postBtn = sheetView.findViewById<MaterialButton>(R.id.btn_post_icebreaker_answer)
        val reply1 = sheetView.findViewById<View>(R.id.btn_reply_wave_1)
        val reply2 = sheetView.findViewById<View>(R.id.btn_reply_wave_2)

        postBtn?.setOnClickListener {
            val answer = input?.text?.toString()?.trim().orEmpty()
            if (answer.isNotBlank()) {
                binding?.vibeCheckQuestion?.text = "You posted: \"$answer\" ✓"
                com.example.romear.core.designsystem.HelloFeedback.success(
                    requireView(),
                    "Answer posted! Unlocked 18 nearby responses 🎉"
                )
                bottomSheet.dismiss()
            }
        }

        reply1?.setOnClickListener {
            bottomSheet.dismiss()
            val dummyUser = viewModel.users.value.firstOrNull() ?: return@setOnClickListener
            val context = JSONObject().put("type", "ICEBREAKER").put("text", "Empire on Church Street at 2 AM")
            showSayHelloDialog(dummyUser, context)
        }

        reply2?.setOnClickListener {
            bottomSheet.dismiss()
            val dummyUser = viewModel.users.value.getOrNull(1) ?: viewModel.users.value.firstOrNull() ?: return@setOnClickListener
            val context = JSONObject().put("type", "ICEBREAKER").put("text", "Corner House Death by Chocolate")
            showSayHelloDialog(dummyUser, context)
        }

        bottomSheet.show()
    }

    private fun showShareVibeBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_share_vibe, null)
        bottomSheet.setContentView(sheetView)

        val inputText = sheetView.findViewById<EditText>(R.id.vibe_input_text)
        val publishBtn = sheetView.findViewById<MaterialButton>(R.id.btn_publish_vibe)
        val clearBtn = sheetView.findViewById<MaterialButton>(R.id.btn_clear_vibe)
        val charCounter = sheetView.findViewById<TextView>(R.id.vibe_char_counter)

        inputText?.doAfterTextChanged {
            charCounter?.text = "${it?.length ?: 0}/60"
        }

        var selectedHours = 3
        val h1 = sheetView.findViewById<TextView>(R.id.duration_1h)
        val h3 = sheetView.findViewById<TextView>(R.id.duration_3h)
        val h6 = sheetView.findViewById<TextView>(R.id.duration_6h)
        val h24 = sheetView.findViewById<TextView>(R.id.duration_24h)

        val durationViews = listOf(1 to h1, 3 to h3, 6 to h6, 24 to h24)
        durationViews.forEach { (hrs, tv) ->
            tv?.setOnClickListener {
                selectedHours = hrs
                durationViews.forEach { (h, v) ->
                    v?.setBackgroundResource(if (h == selectedHours) R.drawable.bg_pill_active else R.drawable.bg_pill_inactive)
                    v?.setTextColor(ContextCompat.getColor(requireContext(), if (h == selectedHours) R.color.text_primary else R.color.text_secondary))
                }
            }
        }

        val s1 = sheetView.findViewById<TextView>(R.id.vibe_suggestion_1)
        val s2 = sheetView.findViewById<TextView>(R.id.vibe_suggestion_2)
        val s3 = sheetView.findViewById<TextView>(R.id.vibe_suggestion_3)
        val s4 = sheetView.findViewById<TextView>(R.id.vibe_suggestion_4)

        listOf(s1, s2, s3, s4).forEach { sugView ->
            sugView?.setOnClickListener {
                inputText?.setText((it as TextView).text.toString())
            }
        }

        publishBtn?.setOnClickListener {
            val text = inputText?.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        socialRepository.request("v1/me/up-for", "PUT", JSONObject().put("activity", text).put("hours", selectedHours))
                        com.example.romear.core.designsystem.HelloFeedback.success(requireView(), "Vibe shared with nearby people ⚡")
                        bottomSheet.dismiss()
                    } catch (_: Exception) {
                        com.example.romear.core.designsystem.HelloFeedback.error(requireView(), "Could not update vibe")
                    }
                }
            }
        }

        clearBtn?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    socialRepository.request("v1/me/up-for", "PUT", JSONObject().put("activity", JSONObject.NULL))
                    com.example.romear.core.designsystem.HelloFeedback.info(requireView(), "Vibe cleared")
                    bottomSheet.dismiss()
                } catch (_: Exception) {}
            }
        }

        bottomSheet.show()
    }

    private fun showRadarHotspotsSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_radar_hotspots, null)
        bottomSheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btn_close_radar)?.setOnClickListener {
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showDiscoveryFilterSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_discovery_filter, null)
        bottomSheet.setContentView(sheetView)

        val distanceSlider = sheetView.findViewById<com.google.android.material.slider.Slider>(R.id.distanceSlider)
        val distanceText = sheetView.findViewById<TextView>(R.id.distanceValueText)
        val ageSlider = sheetView.findViewById<com.google.android.material.slider.RangeSlider>(R.id.ageRangeSlider)
        val ageText = sheetView.findViewById<TextView>(R.id.ageRangeValueText)
        val switchVerified = sheetView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchVerifiedOnly)
        val resetBtn = sheetView.findViewById<View>(R.id.btnResetFilters)
        val applyBtn = sheetView.findViewById<MaterialButton>(R.id.btnApplyFilters)

        distanceSlider?.addOnChangeListener { _, value, _ ->
            distanceText?.text = String.format("%.1f km", value)
        }

        ageSlider?.addOnChangeListener { slider, _, _ ->
            val vals = slider.values
            if (vals.size >= 2) {
                ageText?.text = "${vals[0].toInt()} - ${vals[1].toInt()}"
            }
        }

        resetBtn?.setOnClickListener {
            distanceSlider?.value = 5.0f
            distanceText?.text = "5.0 km"
            ageSlider?.setValues(21.0f, 30.0f)
            ageText?.text = "21 - 30"
            switchVerified?.isChecked = false
            com.example.romear.core.designsystem.HelloFeedback.info(requireView(), "Filters reset to default")
        }

        applyBtn?.setOnClickListener {
            val dist = distanceSlider?.value ?: 5.0f
            com.example.romear.core.designsystem.HelloFeedback.success(
                requireView(),
                "Filters applied: within ${String.format("%.1f", dist)} km"
            )
            viewModel.loadNearby()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showSayHelloDialog(user: DiscoveryUser, context: JSONObject? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_say_hello, null)
        val messageInput = dialogView.findViewById<EditText>(R.id.messageInput)
        val replyPreviewContainer = dialogView.findViewById<View>(R.id.replyPreviewContainer)
        val replyPreviewText = dialogView.findViewById<TextView>(R.id.replyPreviewText)
        val replyPreviewIcon = dialogView.findViewById<ImageView>(R.id.replyPreviewIcon)
        val sendButton = dialogView.findViewById<MaterialButton>(R.id.sendButton)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.cancelButton)
        val dismissBtn = dialogView.findViewById<View>(R.id.btn_dismiss)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)

        titleView?.text = "Say Hello to ${user.displayName.split(" ").firstOrNull() ?: user.displayName}"

        val starter1 = dialogView.findViewById<TextView>(R.id.starter1)
        val starter2 = dialogView.findViewById<TextView>(R.id.starter2)
        val starter3 = dialogView.findViewById<TextView>(R.id.starter3)

        starter1?.setOnClickListener { messageInput.setText("Love that cafe! ☕") }
        starter2?.setOnClickListener { messageInput.setText("Up for badminton? 🏸") }
        starter3?.setOnClickListener { messageInput.setText("I work in tech too! 💡") }

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

        dismissBtn?.setOnClickListener { dialog.dismiss() }
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
