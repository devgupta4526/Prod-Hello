package com.example.romear.feature.hello.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.databinding.FragmentInboxBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentInboxBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)
        views.feedRetry.setOnClickListener { viewModel.loadData() }

        hellosAdapter = HellosAdapter(
            isReceivedBox = { viewModel.currentTab.value == InboxViewModel.Tab.RECEIVED },
            onAcceptClicked = { hello -> viewModel.acceptHello(hello) },
            onDeclineClicked = { hello -> viewModel.declineHello(hello) },
            onWithdrawClicked = { hello -> viewModel.withdrawHello(hello) },
        )

        connectionsAdapter = ConnectionsAdapter(
            onConnectionClicked = { connection ->
                if (connection.conversationId != null) {
                    findNavController().navigate(
                        R.id.action_inbox_to_chat,
                        bundleOf(
                            "conversationId" to connection.conversationId,
                            "partnerName" to connection.partner.displayName,
                            "partnerPhotoUrl" to connection.partner.photoUrl,
                            "partnerId" to connection.partner.id,
                        ),
                    )
                }
            },
        )

        views.inboxRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        views.inboxRecyclerView.adapter = hellosAdapter

        views.inboxTabGroup.check(when (viewModel.currentTab.value) {
            InboxViewModel.Tab.RECEIVED -> R.id.tabReceived
            InboxViewModel.Tab.SENT -> R.id.tabSent
            InboxViewModel.Tab.CONNECTIONS -> R.id.tabConnections
        })
        views.inboxRecyclerView.adapter = if (viewModel.currentTab.value == InboxViewModel.Tab.CONNECTIONS) connectionsAdapter else hellosAdapter
        views.tabReceived.setOnClickListener { com.example.romear.core.designsystem.HelloHaptics.selection(it) }
        views.tabSent.setOnClickListener { com.example.romear.core.designsystem.HelloHaptics.selection(it) }
        views.tabConnections.setOnClickListener { com.example.romear.core.designsystem.HelloHaptics.selection(it) }

        views.inboxTabGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.tabReceived) -> {
                    views.inboxRecyclerView.adapter = hellosAdapter
                    viewModel.switchTab(InboxViewModel.Tab.RECEIVED)
                }
                checkedIds.contains(R.id.tabSent) -> {
                    views.inboxRecyclerView.adapter = hellosAdapter
                    viewModel.switchTab(InboxViewModel.Tab.SENT)
                }
                checkedIds.contains(R.id.tabConnections) -> {
                    views.inboxRecyclerView.adapter = connectionsAdapter
                    viewModel.switchTab(InboxViewModel.Tab.CONNECTIONS)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.loading, viewModel.hellos, viewModel.connections, viewModel.currentTab, viewModel.loadFailed) { _, _, _, _, _ -> Unit }.collect {
                        val tab = viewModel.currentTab.value
                        val loading = viewModel.loading.value
                        val failed = viewModel.loadFailed.value
                        hellosAdapter.submitList(viewModel.hellos.value)
                        connectionsAdapter.submitList(viewModel.connections.value)
                        val empty = if (tab == InboxViewModel.Tab.CONNECTIONS) viewModel.connections.value.isEmpty() else viewModel.hellos.value.isEmpty()
                        views.loadingIndicator.isVisible = loading
                        views.emptyStateView.isVisible = !loading && !failed && empty
                        views.feedError.isVisible = !loading && failed
                        views.emptyStateTitle.setText(when (tab) {
                            InboxViewModel.Tab.RECEIVED -> R.string.ui_empty_received
                            InboxViewModel.Tab.SENT -> R.string.ui_empty_sent
                            InboxViewModel.Tab.CONNECTIONS -> R.string.ui_empty_connections
                        })
                        views.emptyStateBody.setText(when (tab) {
                            InboxViewModel.Tab.RECEIVED -> R.string.ui_received_body
                            InboxViewModel.Tab.SENT -> R.string.ui_sent_body
                            InboxViewModel.Tab.CONNECTIONS -> R.string.ui_connections_body
                        })
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is InboxViewModel.InboxEvent.Success -> {
                                com.example.romear.core.designsystem.HelloFeedback.success(views.root, event.message)
                            }
                            is InboxViewModel.InboxEvent.Error -> {
                                com.example.romear.core.designsystem.HelloFeedback.error(views.root, event.error)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
