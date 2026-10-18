package com.example.romear.feature.profile.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.romear.R
import com.example.romear.core.designsystem.AvatarView
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentProfileBinding
import com.example.romear.feature.auth.data.AccountSession
import com.example.romear.feature.safety.domain.BlockedUserItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    @javax.inject.Inject lateinit var socialRepository: com.example.romear.feature.social.SocialRepository
    private lateinit var presenceLocation: com.example.romear.core.security.PresenceLocation
    private lateinit var socialUi: com.example.romear.feature.social.SocialUi

    private var binding: FragmentProfileBinding? = null
    private val viewModel: ProfileViewModel by viewModels()

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenceLocation = com.example.romear.core.security.PresenceLocation(this) { availability, location -> viewModel.updateAvailability(availability, location) }
        socialUi = com.example.romear.feature.social.SocialUi(this, socialRepository)
        enterTransition = HelloTransitions.tab()
        exitTransition = HelloTransitions.tab()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentProfileBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)

        views.editBasicsButton.setOnClickListener { socialUi.editBasics() }
        views.galleryButton.setOnClickListener { socialUi.gallery() }
        views.personalityButton.setOnClickListener { socialUi.personality() }
        views.upForButton.setOnClickListener { socialUi.upFor() }

        views.hapticsSwitch.isChecked = com.example.romear.core.designsystem.HelloHaptics.isEnabled(requireContext())
        views.hapticsSwitch.setOnCheckedChangeListener { button, enabled ->
            com.example.romear.core.designsystem.HelloHaptics.setEnabled(requireContext(), enabled)
            if (enabled) com.example.romear.core.designsystem.HelloHaptics.selection(button)
        }
        fun chooseAvailability(availability: String, source: View) {

            com.example.romear.core.designsystem.HelloHaptics.selection(source)
            presenceLocation.choose(availability)
        }
        views.profileChipOpen.setOnClickListener { chooseAvailability("OPEN", it) }
        views.profileChipBrowsing.setOnClickListener { chooseAvailability("BROWSING", it) }
        views.profileChipInvisible.setOnClickListener { chooseAvailability("OFF", it) }

        views.viewBlockedUsersButton.setOnClickListener {
            showBlockedUsersDialog()
        }

        views.exportDataButton.setOnClickListener {
            viewModel.requestDataExport()
        }

        views.deleteAccountButton.setOnClickListener {
            val status = viewModel.safetyStatus.value
            if (status?.pendingDeletion == true) {
                viewModel.cancelAccountDeletion()
            } else {
                confirmDeleteAccount()
            }
        }

        views.profileSignOutButton.setOnClickListener {
            confirmSignOut()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.profile.collect { profile ->
                        if (profile != null) {
                            views.profileDisplayName.text = profile.displayName
                            views.profileAge.text = if (profile.age != null) "${profile.age} years old" else ""
                            views.profileAvatar.load(profile.photos.firstOrNull(), profile.displayName, sizeDp = 72)
                            views.profileBio.text = profile.bio ?: "No bio yet"

                            views.profileInterestsGroup.removeAllViews()
                            profile.interests.forEach { interest ->
                                val chip = Chip(requireContext()).apply {
                                    text = interest
                                    isClickable = false
                                }
                                views.profileInterestsGroup.addView(chip)
                            }

                            when (profile.availability) {
                                "OPEN" -> views.profileChipOpen.isChecked = true
                                "BROWSING" -> views.profileChipBrowsing.isChecked = true
                                "OFF" -> views.profileChipInvisible.isChecked = true
                            }
                        }
                    }
                }
                launch {
                    viewModel.safetyStatus.collect { status ->
                        if (status != null && status.pendingDeletion) {
                            views.deletionNotice.isVisible = true
                            views.deletionNotice.text = getString(R.string.profile_deletion_scheduled, status.deletionRequestedAt ?: "")
                            views.deleteAccountButton.setText(R.string.profile_delete_cancel)
                            views.deleteAccountButton.setTextColor(resources.getColor(R.color.hello_primary, null))
                        } else {
                            views.deletionNotice.isVisible = false
                            views.deleteAccountButton.setText(R.string.profile_delete_account)
                            views.deleteAccountButton.setTextColor(resources.getColor(R.color.hello_error, null))
                        }
                    }
                }
                launch {
                    viewModel.sessions.collect { sessions ->
                        renderSessions(sessions, views.profileSessionsContainer)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ProfileViewModel.ProfileEvent.Success -> {
                                com.example.romear.core.designsystem.HelloFeedback.success(views.root, event.message)
                            }
                            is ProfileViewModel.ProfileEvent.Error -> {
                                com.example.romear.core.designsystem.HelloFeedback.error(views.root, event.error)
                            }
                            ProfileViewModel.ProfileEvent.SignedOut -> {
                                findNavController().setGraph(R.navigation.main_navigation)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderSessions(sessions: List<AccountSession>, container: LinearLayout) {
        container.removeAllViews()
        sessions.forEach { session ->
            val dateStr = try {
                dateFormatter.format(Instant.parse(session.createdAt))
            } catch (_: Exception) {
                ""
            }
            val btn = MaterialButton(requireContext()).apply {
                text = if (session.current) "This device Â· $dateStr (Revoke)" else "Other device Â· $dateStr (Revoke)"
                setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.account_revoke_title)
                        .setMessage(R.string.account_revoke_body)
                        .setNegativeButton(R.string.account_cancel, null)
                        .setPositiveButton(R.string.auth_sign_out) { _, _ -> viewModel.revokeSession(session) }
                        .show()
                }
            }
            container.addView(btn)
        }
    }

    private fun showBlockedUsersDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_blocked_users, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.blockedListContainer)
        val emptyText = dialogView.findViewById<TextView>(R.id.emptyBlockedText)
        val closeBtn = dialogView.findViewById<MaterialButton>(R.id.closeBlockedDialogButton)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }

        viewModel.loadBlockedUsers()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.blockedUsers.collect { list ->
                container.removeAllViews()
                emptyText.isVisible = list.isEmpty()
                list.forEach { item ->
                    val row = LayoutInflater.from(requireContext()).inflate(R.layout.item_connection, container, false)
                    val avatar = row.findViewById<AvatarView>(R.id.avatar)
                    val name = row.findViewById<TextView>(R.id.partnerName)
                    val date = row.findViewById<TextView>(R.id.connectionDate)
                    val arrow = row.findViewById<View>(R.id.openChatArrow)

                    avatar.load(item.photoUrl, item.displayName, sizeDp = 48)
                    name.text = item.displayName
                    date.text = "Blocked"
                    arrow.isVisible = false

                    row.setOnClickListener {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Unblock ${item.displayName}?")
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.profile_unblock) { _, _ ->
                                viewModel.unblockUser(item.blockedUserId)
                            }
                            .show()
                    }
                    container.addView(row)
                }
            }
        }

        dialog.show()
    }

    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_delete_account)
            .setMessage(R.string.profile_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.profile_delete_account) { _, _ ->
                viewModel.requestAccountDeletion()
            }
            .show()
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_sign_out)
            .setMessage("Are you sure you want to sign out?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.auth_sign_out) { _, _ ->
                viewModel.signOut()
            }
            .show()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
