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
import androidx.core.widget.doAfterTextChanged
import coil3.load
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.romear.R
import androidx.navigation.fragment.findNavController
import com.example.romear.core.designsystem.HelloFeedback
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
    private var eventLoading = false
    private var eventDialog: BottomSheetDialog? = null

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

        eventsAdapter = EventsAdapter { event -> openEvent(event.id) }
        ticketsAdapter = UserTicketsAdapter { ticket -> showBookingDetailsSheet(ticket) }

        views.chipActivityTimeline.setOnClickListener {
            findNavController().navigate(R.id.activityFragment)
        }
        views.chipHostingQuotes.setOnClickListener {
            showHostingQuoteSheet()
        }
        views.chipVenueCalendar.setOnClickListener {
            showVenueCalendarSheet()
        }

        views.eventSearchInput.setText(viewModel.query.value)
        views.eventSearchInput.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }
        views.searchEvents.setOnClickListener {
            views.eventSearchLayout.isVisible = !views.eventSearchLayout.isVisible
            if (views.eventSearchLayout.isVisible) views.eventSearchInput.requestFocus()
            else views.eventSearchInput.text?.clear()
        }

        fun updateEvents() {
            val query = viewModel.query.value.trim()
            val filtered = viewModel.events.value.filter {
                query.isEmpty() || listOf(it.title, it.venueName, it.category, it.description).any { value -> value.contains(query, ignoreCase = true) }
            }
            eventsAdapter.submitList(filtered)
            if (viewModel.currentTab.value == TicketsViewModel.Tab.EXPLORE && !viewModel.loading.value && !viewModel.loadFailed.value) {
                views.emptyStateView.isVisible = filtered.isEmpty()
                if (query.isNotEmpty()) {
                    views.emptyStateTitle.text = "No plans found yet."
                    views.emptyStateBody.text = "Try another event, venue or interest."
                }
            }
        }

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
                launch { viewModel.query.collect { updateEvents() } }
                launch {
                    combine(viewModel.loading, viewModel.events, viewModel.tickets, viewModel.currentTab, viewModel.loadFailed) { _, _, _, _, _ -> Unit }.collect {
                        val explore = viewModel.currentTab.value == TicketsViewModel.Tab.EXPLORE
                        val loading = viewModel.loading.value
                        val failed = viewModel.loadFailed.value
                        ticketsAdapter.submitList(viewModel.tickets.value)
                        val empty = if (explore) viewModel.events.value.isEmpty() else viewModel.tickets.value.isEmpty()
                        views.loadingIndicator.isVisible = loading
                        views.emptyStateView.isVisible = !loading && !failed && empty
                        views.feedError.isVisible = !loading && failed
                        views.emptyStateTitle.setText(if (explore) R.string.events_empty else R.string.ui_empty_tickets)
                        views.emptyStateBody.setText(if (explore) R.string.ui_events_empty_body else R.string.tickets_empty)
                        views.searchEvents.isVisible = explore
                        if (!explore) views.eventSearchLayout.isVisible = false
                        updateEvents()
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

    private fun openEvent(id: String) {
        if (eventLoading) return
        eventLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val event = viewModel.eventDetail(id)
                showEventOverview(event)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) {
                binding?.root?.let { com.example.romear.core.designsystem.HelloFeedback.error(it, "Couldn’t load this event. Please try again.") }
            } finally { eventLoading = false }
        }
    }

    private fun showEventOverview(event: EventItem) {
        val ui = com.example.romear.core.designsystem.ExperienceViews(requireContext())
        val content = ui.column()
        event.coverUrl?.let { url ->
            val photo = com.google.android.material.imageview.ShapeableImageView(requireContext()).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(ui.dp(22).toFloat()).build()
                load(url)
            }
            content.addView(photo, android.widget.LinearLayout.LayoutParams(-1, ui.dp(210)))
        }
        ui.header(content, event.category.uppercase(), event.title)
        val time = runCatching { java.time.Instant.parse(event.startsAt).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")) }.getOrDefault(event.startsAt)
        ui.card(content, "YOUR EVENING", time, "${event.tiers.sumOf { it.remaining }} places available")
        ui.card(content, "THE VENUE", event.venueName, event.address.orEmpty())
        event.organizerName?.takeIf { it.isNotBlank() }?.let { name ->
            ui.add(content, ui.text("Meet your host", 18f, R.color.text_primary, true))
            val row = android.widget.LinearLayout(requireContext()).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            val avatar = com.example.romear.core.designsystem.AvatarView(requireContext()).apply { load(event.organizerPhotoUrl, name, sizeDp = 48) }
            row.addView(avatar, android.widget.LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            row.addView(ui.text(name, 16f, R.color.text_primary, true), android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(12) })
            ui.add(content, row)
        }
        ui.add(content, ui.text("What to expect", 18f, R.color.text_primary, true))
        ui.add(content, ui.text(event.description), 8)
        val minimum = event.tiers.filter { it.remaining > 0 }.minOfOrNull { it.priceInPaise }
        val label = minimum?.let { if (it == 0) "Choose tickets · Free" else "Choose tickets · ${money(it.toLong())} / person" } ?: "Sold out"
        val dialog = BottomSheetDialog(requireContext())
        ui.button(content, label, primary = true) { dialog.dismiss(); showEventDetailsSheet(event) }.isEnabled = minimum != null && event.status == "PUBLISHED"
        ui.button(content, "Close") { dialog.dismiss() }
        dialog.setContentView(android.widget.ScrollView(requireContext()).apply { addView(content) })
        eventDialog?.dismiss()
        eventDialog = dialog
        dialog.show()
        dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private fun money(paise: Long): String = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("en-IN")).format(paise / 100.0)

    private fun showEventDetailsSheet(event: EventItem) {
        val bottomSheet = BottomSheetDialog(requireContext())
        eventDialog?.dismiss()
        eventDialog = bottomSheet
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

        sheetView.findViewById<android.widget.ImageView>(R.id.eventArtworkHero).load(event.coverUrl)
        val available = event.tiers.filter { it.remaining > 0 && it.id.isNotBlank() }
        var selectedTierId = available.firstOrNull()?.id.orEmpty()
        var selectedPricePaise = available.firstOrNull()?.priceInPaise ?: 0
        confirmBtn.isEnabled = available.isNotEmpty()
        if (available.isEmpty()) confirmBtn.text = "Sold out"

        fun updateTotal() {
            val total = selectedPricePaise.toLong() * stepper.quantity
            totalLabel.text = if (total == 0L) "Free" else money(total)
        }

        radioGroup.removeAllViews()
        event.tiers.forEachIndexed { index, tier ->
            val rb = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                val priceStr = if (tier.priceInPaise == 0) "Free" else money(tier.priceInPaise.toLong())
                text = "${tier.name} · $priceStr (${tier.remaining} left)"
                isEnabled = tier.remaining > 0 && tier.id.isNotBlank()
                isChecked = tier.id == selectedTierId
                setOnClickListener {
                    selectedTierId = tier.id
                    selectedPricePaise = tier.priceInPaise
                    stepper.configure(minimum = 1, maximum = minOf(tier.remaining, tier.maxPerOrder).coerceAtLeast(1), value = 1)
                    updateTotal()
                }
            }
            radioGroup.addView(rb)
        }

        val firstTier = available.firstOrNull()
        stepper.configure(minimum = 1, maximum = firstTier?.let { minOf(it.remaining, it.maxPerOrder).coerceAtLeast(1) } ?: 1, value = 1)
        stepper.onQuantityChanged = { updateTotal() }
        updateTotal()

        confirmBtn.setOnClickListener {
            if (selectedTierId.isNotBlank()) {
                bottomSheet.dismiss()
                showCheckoutSheet(event, selectedTierId, stepper.quantity, selectedPricePaise)
            }
        }

        bottomSheet.show()
    }

    private fun showCheckoutSheet(event: EventItem, tierId: String, quantity: Int, pricePaise: Int) {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_checkout, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.checkoutEventSubtitle)?.text = "$quantity ticket · ${event.title}"
        val total = pricePaise.toLong() * quantity
        val totalFormatted = if (total == 0L) "Free" else money(total)
        sheetView.findViewById<TextView>(R.id.checkoutTotalAmount)?.text = totalFormatted

        val submitBtn = sheetView.findViewById<MaterialButton>(R.id.btnSubmitPayment)
        submitBtn?.text = "Pay $totalFormatted & Confirm Ticket ⚡"

        val upiContainer = sheetView.findViewById<View>(R.id.upiInputContainer)
        val radioGroup = sheetView.findViewById<RadioGroup>(R.id.paymentMethodRadioGroup)
        radioGroup?.setOnCheckedChangeListener { _, checkedId ->
            upiContainer?.isVisible = checkedId == R.id.radioUpi
        }

        submitBtn?.setOnClickListener {
            submitBtn.isEnabled = false
            viewModel.bookTicket(event.id, tierId, quantity) {
                sheet.dismiss()
                HelloFeedback.success(requireView(), "Ticket confirmed! See you there 🎟️")
            }
        }

        sheet.show()
    }

    private fun showBookingDetailsSheet(ticket: UserTicketItem) {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_booking_details, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.bookingDetailTitle)?.text = ticket.eventTitle
        sheetView.findViewById<TextView>(R.id.bookingDetailTimeVenue)?.text = "Booking Reference · ${ticket.id.take(8).uppercase()}"
        sheetView.findViewById<TextView>(R.id.bookingTierInfo)?.text = "1 × ${ticket.tierName} · Confirmed"

        sheetView.findViewById<View>(R.id.btnViewQrPass)?.setOnClickListener {
            sheet.dismiss()
            showTicketQrDialog(ticket)
        }

        sheetView.findViewById<View>(R.id.btnCancelBooking)?.setOnClickListener {
            sheet.dismiss()
            showCancelBookingSheet(ticket)
        }

        sheetView.findViewById<View>(R.id.btnBookingSupport)?.setOnClickListener {
            sheet.dismiss()
            showSupportSheet(ticket)
        }

        sheet.show()
    }

    private fun showCancelBookingSheet(ticket: UserTicketItem) {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_cancel_booking, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnKeepBooking)?.setOnClickListener {
            sheet.dismiss()
        }

        sheetView.findViewById<View>(R.id.btnConfirmCancellation)?.setOnClickListener {
            sheet.dismiss()
            showRefundStatusSheet(ticket)
        }

        sheet.show()
    }

    private fun showRefundStatusSheet(ticket: UserTicketItem) {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_refund_status, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnDoneRefund)?.setOnClickListener {
            sheet.dismiss()
            viewModel.loadData()
        }

        sheet.show()
    }

    private fun showHostingQuoteSheet() {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_hosting_quote, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnReviseQuote)?.setOnClickListener {
            HelloFeedback.info(requireView(), "Revision note sent to Aditi · Common Ground")
            sheet.dismiss()
        }

        sheetView.findViewById<View>(R.id.btnAcceptQuote)?.setOnClickListener {
            HelloFeedback.success(requireView(), "Quote Q-203 accepted! Awaiting confirmation.")
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showVenueCalendarSheet() {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_venue_calendar, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.btnBlockDates)?.setOnClickListener {
            HelloFeedback.info(requireView(), "Unavailable dates locked on venue calendar.")
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showSupportSheet(ticket: UserTicketItem) {
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_support, null)
        val sheet = BottomSheetDialog(requireContext())
        sheet.setContentView(sheetView)

        sheetView.findViewById<TextView>(R.id.supportBookingTitle)?.text = ticket.eventTitle
        sheetView.findViewById<TextView>(R.id.supportBookingRef)?.text = "BOOKING ${ticket.id.take(8).uppercase()}"

        sheetView.findViewById<View>(R.id.btnSubmitSupport)?.setOnClickListener {
            sheet.dismiss()
            HelloFeedback.success(requireView(), "Support ticket CS-1042 opened. We'll update you in Activity.")
        }

        sheet.show()
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
        eventDialog?.dismiss()
        eventDialog = null
        binding = null
        super.onDestroyView()
    }
}
