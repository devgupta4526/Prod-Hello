package com.example.romear.feature.events.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.romear.R
import com.example.romear.core.designsystem.HelloTransitions
import com.example.romear.core.designsystem.QuantityStepper
import com.example.romear.databinding.FragmentTicketsBinding
import com.example.romear.feature.events.domain.EventItem
import com.example.romear.feature.events.domain.UserTicketItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

@AndroidEntryPoint
class TicketsFragment : Fragment() {

    private var binding: FragmentTicketsBinding? = null
    private val viewModel: TicketsViewModel by viewModels()

    private lateinit var eventsAdapter: EventsAdapter
    private lateinit var ticketsAdapter: UserTicketsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = HelloTransitions.tab()
        exitTransition = HelloTransitions.tab()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentTicketsBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val views = requireNotNull(binding)
        views.feedRetry.setOnClickListener { viewModel.loadData() }

        eventsAdapter = EventsAdapter { event -> showEventDetailsSheet(event) }
        ticketsAdapter = UserTicketsAdapter { ticket -> showTicketQrDialog(ticket) }

        com.example.romear.core.designsystem.HelloLayouts.adaptiveCards(views.ticketsRecyclerView)
        views.ticketsRecyclerView.adapter = eventsAdapter

        views.ticketsTabGroup.check(when (viewModel.currentTab.value) {
            TicketsViewModel.Tab.EXPLORE -> R.id.tabExplore
            TicketsViewModel.Tab.MY_TICKETS -> R.id.tabMyTickets
        })
        views.ticketsRecyclerView.adapter = if (viewModel.currentTab.value == TicketsViewModel.Tab.MY_TICKETS) ticketsAdapter else eventsAdapter
        views.tabExplore.setOnClickListener { com.example.romear.core.designsystem.HelloHaptics.selection(it) }
        views.tabMyTickets.setOnClickListener { com.example.romear.core.designsystem.HelloHaptics.selection(it) }

        views.ticketsTabGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.tabExplore) -> {
                    views.ticketsRecyclerView.adapter = eventsAdapter
                    viewModel.switchTab(TicketsViewModel.Tab.EXPLORE)
                }
                checkedIds.contains(R.id.tabMyTickets) -> {
                    views.ticketsRecyclerView.adapter = ticketsAdapter
                    viewModel.switchTab(TicketsViewModel.Tab.MY_TICKETS)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.loading, viewModel.events, viewModel.tickets, viewModel.currentTab, viewModel.loadFailed) { _, _, _, _, _ -> Unit }.collect {
                        val explore = viewModel.currentTab.value == TicketsViewModel.Tab.EXPLORE
                        val loading = viewModel.loading.value
                        val failed = viewModel.loadFailed.value
                        eventsAdapter.submitList(viewModel.events.value)
                        ticketsAdapter.submitList(viewModel.tickets.value)
                        val empty = if (explore) viewModel.events.value.isEmpty() else viewModel.tickets.value.isEmpty()
                        views.loadingIndicator.isVisible = loading
                        views.emptyStateView.isVisible = !loading && !failed && empty
                        views.feedError.isVisible = !loading && failed
                        views.emptyStateTitle.setText(if (explore) R.string.events_empty else R.string.ui_empty_tickets)
                        views.emptyStateBody.setText(if (explore) R.string.ui_events_empty_body else R.string.tickets_empty)
                    }
                }
                launch {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is TicketsViewModel.TicketsEvent.Success -> {
                                com.example.romear.core.designsystem.HelloFeedback.success(views.root, event.message)
                                views.tabMyTickets.isChecked = true
                            }
                            is TicketsViewModel.TicketsEvent.Error -> {
                                com.example.romear.core.designsystem.HelloFeedback.error(views.root, event.error)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showEventDetailsSheet(event: EventItem) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_event_details, null)
        bottomSheet.setContentView(sheetView)

        val categoryView = sheetView.findViewById<TextView>(R.id.eventCategory)
        val titleView = sheetView.findViewById<TextView>(R.id.eventDetailTitle)
        val timeVenueView = sheetView.findViewById<TextView>(R.id.eventDetailTimeVenue)
        val descView = sheetView.findViewById<TextView>(R.id.eventDetailDescription)
        val radioGroup = sheetView.findViewById<RadioGroup>(R.id.tierRadioGroup)
        val stepper = sheetView.findViewById<QuantityStepper>(R.id.quantityStepper)
        val totalLabel = sheetView.findViewById<TextView>(R.id.totalPriceLabel)
        val confirmBtn = sheetView.findViewById<MaterialButton>(R.id.confirmBookingButton)

        categoryView.text = event.category.uppercase()
        titleView.text = event.title
        val eventTime = runCatching { java.time.Instant.parse(event.startsAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")) }.getOrDefault(event.startsAt)
        timeVenueView.text = "$eventTime\n${event.venueName} · ${event.distanceBucket}" + (event.address?.let { "\n$it" } ?: "")
        descView.text = event.description

        var selectedTierId = event.tiers.firstOrNull()?.id.orEmpty()
        var selectedPricePaise = event.tiers.firstOrNull()?.priceInPaise ?: 0

        fun updateTotal() {
            val total = (selectedPricePaise * stepper.quantity) / 100
            totalLabel.text = if (total == 0) "Total: Free" else "Total: ₹$total"
        }

        radioGroup.removeAllViews()
        event.tiers.forEachIndexed { index, tier ->
            val rb = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                val priceStr = if (tier.priceInPaise == 0) "Free" else "₹${tier.priceInPaise / 100}"
                text = "${tier.name} · $priceStr (${tier.remaining} left)"
                isChecked = index == 0
                setOnClickListener {
                    selectedTierId = tier.id
                    selectedPricePaise = tier.priceInPaise
                    updateTotal()
                }
            }
            radioGroup.addView(rb)
        }

        stepper.configure(minimum = 1, maximum = 6, value = 1)
        stepper.onQuantityChanged = { updateTotal() }
        updateTotal()

        confirmBtn.setOnClickListener {
            if (selectedTierId.isNotBlank()) {
                viewModel.bookTicket(event.id, selectedTierId, stepper.quantity) {
                    bottomSheet.dismiss()
                }
            }
        }

        bottomSheet.show()
    }

    private fun showTicketQrDialog(ticket: UserTicketItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ticket_qr, null)
        val title = dialogView.findViewById<TextView>(R.id.qrEventTitle)
        val tier = dialogView.findViewById<TextView>(R.id.qrTierName)
        val code = dialogView.findViewById<TextView>(R.id.qrPayloadCode)
        val closeBtn = dialogView.findViewById<MaterialButton>(R.id.closeQrButton)

        title.text = ticket.eventTitle
        tier.text = ticket.tierName
        code.text = ticket.qrPayload

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
