package com.example.romear.feature.shell.ui

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.romear.core.designsystem.HelloTransitions
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.example.romear.R
import com.example.romear.core.data.ConnectionResult
import com.example.romear.databinding.FragmentShellBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShellFragment : Fragment() {
    private var binding: FragmentShellBinding? = null
    private val viewModel: ShellViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = HelloTransitions.tab()
        exitTransition = HelloTransitions.tab()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentShellBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)
        val section = arguments?.getString("section") ?: "discover"
        val copy = when (section) {
            "happening" -> listOf(R.string.happening_eyebrow, R.string.happening_title, R.string.happening_body, R.string.happening_empty_title, R.string.happening_empty_body)
            "inbox" -> listOf(R.string.inbox_eyebrow, R.string.inbox_title, R.string.inbox_body, R.string.inbox_empty_title, R.string.inbox_empty_body)
            "tickets" -> listOf(R.string.tickets_eyebrow, R.string.tickets_title, R.string.tickets_body, R.string.tickets_empty_title, R.string.tickets_empty_body)
            "profile" -> listOf(R.string.profile_eyebrow, R.string.profile_title, R.string.profile_body, R.string.profile_empty_title, R.string.profile_empty_body)
            else -> listOf(R.string.discover_eyebrow, R.string.discover_title, R.string.discover_body, R.string.discover_empty_title, R.string.discover_empty_body)
        }
        listOf(views.eyebrow, views.title, views.body, views.emptyTitle, views.emptyBody)
            .zip(copy).forEach { (label, text) -> label.setText(text) }
        views.servicePanel.isVisible = section == "profile"
        views.accountPanel.isVisible = section == "profile"
        views.sessionsButton.setOnClickListener { accountViewModel.loadSessions() }
        views.logoutButton.setOnClickListener { confirmSignOut { accountViewModel.signOut() } }
        views.serviceButton.setOnClickListener { viewModel.checkConnection() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    accountViewModel.events.collect {
                        // Reset the whole graph, including saved tab stacks, after removing credentials.
                        findNavController().setGraph(R.navigation.main_navigation)
                    }
                }
                launch {
                    accountViewModel.state.collect { state ->
                        views.sessionsButton.isEnabled = !state.loading
                        views.logoutButton.isEnabled = !state.loading
                        views.accountError.isVisible = state.error != null
                        views.accountError.setText(if (state.error == "offline") R.string.service_offline else if (state.error == "unauthorized") R.string.auth_error_unauthorized else R.string.service_error)
                        views.sessionList.removeAllViews()
                        state.sessions.forEach { session ->
                            val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                .withZone(ZoneId.systemDefault()).format(Instant.parse(session.createdAt))
                            views.sessionList.addView(MaterialButton(requireContext()).apply {
                                text = getString(if (session.current) R.string.account_current_session else R.string.account_other_session, date)
                                isEnabled = !state.loading
                                setOnClickListener { confirmSignOut { accountViewModel.revoke(session) } }
                            })
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    views.serviceProgress.isVisible = state is ConnectionUiState.Loading
                    views.serviceButton.isEnabled = state !is ConnectionUiState.Loading
                    views.serviceStatus.setText(when (state) {
                        ConnectionUiState.Idle -> R.string.service_body
                        is ConnectionUiState.Loading -> if (state.waking) R.string.service_waking else R.string.service_loading
                        is ConnectionUiState.Finished -> when (state.result) {
                            ConnectionResult.Connected -> R.string.service_connected
                            ConnectionResult.Offline -> R.string.service_offline
                            ConnectionResult.Unavailable -> R.string.service_error
                            ConnectionResult.Unconfigured -> R.string.service_unconfigured
                            ConnectionResult.UpgradeRequired -> R.string.service_upgrade
                            ConnectionResult.Maintenance -> R.string.auth_maintenance_body
                        }
                    })
                }
            }
        }
    }

    private fun confirmSignOut(action: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_revoke_title)
            .setMessage(R.string.account_revoke_body)
            .setNegativeButton(R.string.account_cancel, null)
            .setPositiveButton(R.string.auth_sign_out) { _, _ -> action() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        binding?.locationLimited?.isVisible = arguments?.getString("section") == "discover" &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
