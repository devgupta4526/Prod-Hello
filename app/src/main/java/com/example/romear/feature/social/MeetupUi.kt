package com.example.romear.feature.social

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import com.example.romear.R
import com.example.romear.core.designsystem.ExperienceScreen
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Live meetup agreement flows from Figma 11.5–11.12. */
class MeetupUi(
    private val fragment: Fragment,
    private val repo: SocialRepository,
    private val onMemories: (String) -> Unit = {},
    private val onReconnect: (String) -> Unit = {},
) {
    private var current: ExperienceScreen? = null
    private var conversation: String? = null
    private val formatter = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a").withZone(ZoneId.systemDefault())
    private fun date(value: String) = runCatching { formatter.format(Instant.parse(value)) }.getOrDefault(value)
    private fun screen(title: String, description: String): ExperienceScreen {
        current?.dismiss()
        return ExperienceScreen(fragment).also {
            current = it
            it.ui.header(it.content, "MEETUP AGREEMENT", title, description)
        }
    }

    private fun request(screen: ExperienceScreen, button: MaterialButton? = null, action: suspend () -> Unit) {
        button?.isEnabled = false
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                com.example.romear.core.designsystem.HelloFeedback.error(screen.content, failure.message ?: "Couldn’t save. Please try again.")
            } finally { button?.isEnabled = true }
        }
    }

    fun open(conversationId: String) {
        conversation = conversationId
        val page = screen("Your meetup plans.", "Choose a plan to see the invitation and everyone’s response.")
        val progress = ProgressBar(fragment.requireContext())
        page.ui.add(page.content, progress)
        page.show()
        request(page) {
            val data = repo.request("v1/conversations/$conversationId/plans")
            if (current !== page) return@request
            page.content.removeView(progress)
            val items = data.getJSONArray("items")
            if (items.length() == 1) { detail(items.getJSONObject(0), data.getString("userId")); return@request }
            if (items.length() == 0) page.ui.card(page.content, title = "Make a plan together", body = "Choose something to do and a public place to meet.")
            for (index in 0 until items.length()) {
                val plan = items.getJSONObject(index)
                page.ui.card(page.content, eyebrow = plan.getString("status"), title = plan.getString("activity"), body = plan.getString("venue")) { detail(plan, data.getString("userId")) }
            }
            page.ui.button(page.actions, "Suggest a meetup", true) { compose(conversationId) }
        }
    }

    private fun time(plan: JSONObject): String = if (plan.isNull("confirmedTime")) plan.getJSONArray("timeOptions").getString(0) else plan.getString("confirmedTime")

    private fun detail(plan: JSONObject, userId: String) {
        val status = plan.getString("status")
        if (status == "CANCELLED" || status == "DECLINED") {
            outcome(if (status == "DECLINED") "Invitation declined." else "Meetup cancelled.", "There is no meetup scheduled.", if (status == "DECLINED") "Keep the conversation open" else "No meetup is scheduled", "Make a new plan together when the time is right.")
            return
        }
        val confirmed = status == "CONFIRMED"
        val page = screen(if (confirmed) "It’s a plan." else "${plan.getString("activity")} ?", if (confirmed) "Everyone has accepted this meetup." else "A public meetup, planned together.")
        page.ui.card(page.content, title = if (confirmed) date(time(plan)) else plan.getString("venue"), body = if (confirmed) plan.getString("venue") else "${date(time(plan))}\n${plan.optInt("durationMinutes", 60)} minutes")
        page.ui.card(page.content, title = if (confirmed) "Before you go" else "Make a plan together", body = if (confirmed) "Choose your own transport and let someone you trust know your plan." else "Accept only if the place and time work for you. Either person can change their mind.")
        page.ui.button(page.content, if (confirmed) "Propose a different time" else "Suggest another time") { reschedule(plan, userId) }
        page.ui.button(page.content, if (confirmed) "Cancel meetup" else "Decline invitation") { confirmEnd(plan, userId, !confirmed) }
        if (confirmed) {
            page.ui.button(page.content, "Plan memories") { onMemories(plan.getString("id")) }
            page.ui.button(page.content, "Meet again") { onReconnect(plan.getString("id")) }
            page.ui.button(page.actions, "Open conversation", true) { openConversation(page) }
        }
        else {
            val choices = plan.getJSONArray("timeOptions")
            var selected = 0
            if (choices.length() > 1) {
                val group = com.google.android.material.chip.ChipGroup(fragment.requireContext()).apply { isSingleSelection = true; isSelectionRequired = true }
                for (index in 0 until choices.length()) {
                    group.addView(com.google.android.material.chip.Chip(fragment.requireContext()).apply {
                        id = View.generateViewId(); text = date(choices.getString(index)); isCheckable = true; isChecked = index == 0
                        setOnClickListener { selected = index }
                    })
                }
                page.ui.add(page.content, group)
            }
            lateinit var accept: MaterialButton
            accept = page.ui.button(page.actions, "Accept meetup", true) {
                request(page, accept) {
                    val response = repo.request("v1/plans/${plan.getString("id")}/respond", "POST", JSONObject().put("response", "ACCEPT").put("option", selected).put("expectedTime", choices.getString(selected)))
                    if (response.getString("status") == "CONFIRMED") open(plan.getString("conversationId"))
                    else outcome("Response sent.", "Waiting for everyone’s acceptance.", "Not confirmed yet", "Everyone needs to agree to the same time before this meetup is confirmed.")
                }
            }
        }
        page.show()
    }

    private fun field(page: ExperienceScreen, label: String, hint: String, max: Int = 240): EditText {
        page.ui.add(page.content, page.ui.text(label, 11f), 18)
        return EditText(fragment.requireContext()).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_tertiary))
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(page.ui.dp(16), page.ui.dp(16), page.ui.dp(16), page.ui.dp(16))
            filters = arrayOf(InputFilter.LengthFilter(max))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            page.ui.add(page.content, this, 8)
        }
    }

    private fun chooseTime(page: ExperienceScreen, onSelected: (Instant) -> Unit) {
        val field = field(page, "DATE & TIME", "Choose date and time")
        field.isFocusable = false
        field.setOnClickListener {
            val now = ZonedDateTime.now()
            DatePickerDialog(fragment.requireContext(), { _, year, month, day ->
                TimePickerDialog(fragment.requireContext(), { _, hour, minute ->
                    val chosen = java.time.LocalDate.of(year, month + 1, day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()
                    if (chosen <= Instant.now() || chosen > Instant.now().plusSeconds(90L * 86400)) {
                        field.error = "Choose a future time within 90 days"
                    } else {
                        field.error = null; field.setText(formatter.format(chosen)); onSelected(chosen)
                    }
                }, now.hour, now.minute, false).show()
            }, now.year, now.monthValue - 1, now.dayOfMonth).apply { datePicker.minDate = System.currentTimeMillis() }.show()
        }
    }

    fun compose(conversationId: String) {
        conversation = conversationId
        val page = screen("Plan a Safe Public Meetup", "Choose an activity and a public venue that works for everyone.")
        val activity = field(page, "ACTIVITY", "Coffee, a walk, or something you both enjoy", 100)
        val venue = field(page, "PUBLIC MEETING PLACE", "Venue name and address", 200)
        var selected: Instant? = null
        chooseTime(page) { selected = it }
        val publicPlace = com.google.android.material.checkbox.MaterialCheckBox(fragment.requireContext()).apply { text = "This is a public meeting place" }
        page.ui.add(page.content, publicPlace)
        page.ui.card(page.content, eyebrow = "✓ MEETUP SAFETY", title = "Meet in a public place", body = "Let a friend know your plan and keep your transportation independent.")
        lateinit var submit: MaterialButton
        submit = page.ui.button(page.actions, "Send Meetup Invite", true) {
            val chosen = selected
            when {
                activity.text.trim().length < 2 -> activity.error = "Enter an activity"
                venue.text.trim().length < 3 -> venue.error = "Enter a public venue"
                chosen == null -> com.example.romear.core.designsystem.HelloFeedback.error(page.content, "Choose a date and time")
                !publicPlace.isChecked -> publicPlace.error = "Confirm a public meeting place"
                else -> request(page, submit) {
                    repo.request("v1/conversations/$conversationId/plans", "POST", JSONObject().put("activity", activity.text.toString().trim()).put("venue", venue.text.toString().trim()).put("publicVenue", true).put("timeOptions", JSONArray(listOf(chosen.toString()))).put("durationMinutes", 60))
                    outcome("Invitation sent.", "Waiting for their reply.", "Not confirmed yet", "Check the conversation for the response. Everyone must accept before the meetup is confirmed.")
                }
            }
        }
        page.show()
    }

    private fun reschedule(plan: JSONObject, userId: String) {
        val page = screen("Find a better time.", "Your proposal needs everyone’s acceptance.")
        var selected: Instant? = null
        chooseTime(page) { selected = it }
        val note = field(page, "NOTE · OPTIONAL", "Would another time work better?")
        lateinit var submit: MaterialButton
        submit = page.ui.button(page.actions, "Send new time", true) {
            val chosen = selected
            if (chosen == null) com.example.romear.core.designsystem.HelloFeedback.error(page.content, "Choose a date and time")
            else request(page, submit) {
                repo.request("v1/plans/${plan.getString("id")}/reschedule", "POST", JSONObject().put("time", chosen.toString()).put("expectedTime", time(plan)).put("note", note.text.toString().trim()))
                outcome("New time proposed.", "Waiting for their reply.", date(chosen.toString()), "${plan.getString("venue")}\n\nNot confirmed yet. Your new proposal replaces the earlier invitation.")
            }
        }
        page.show()
    }

    private fun confirmEnd(plan: JSONObject, userId: String, decline: Boolean) {
        val page = screen(if (decline) "Decline this invite?" else "Cancel the meetup?", if (decline) "You can keep chatting without meeting." else "Everyone will receive a cancellation update.")
        page.ui.card(page.content, title = if (decline) "They will be notified" else date(time(plan)), body = if (decline) "Only your response and optional message will be shared." else plan.getString("venue"))
        val note = if (decline) field(page, "MESSAGE · OPTIONAL", "I cannot make it this time.") else null
        if (!decline) page.ui.card(page.content, title = "Your conversation stays available", body = "Cancelling the plan does not block or unmatch anyone.")
        page.ui.button(page.content, if (decline) "Keep invitation" else "Keep the plan") { detail(plan, userId) }
        lateinit var submit: MaterialButton
        submit = page.ui.button(page.actions, if (decline) "Decline invite" else "Confirm cancellation", true) {
            request(page, submit) {
                if (decline) repo.request("v1/plans/${plan.getString("id")}/respond", "POST", JSONObject().put("response", "DECLINE").put("expectedTime", time(plan)).put("note", note?.text.toString().trim()))
                else repo.request("v1/plans/${plan.getString("id")}/cancel", "POST")
                outcome(if (decline) "Invitation declined." else "Meetup cancelled.", "There is no meetup scheduled.", if (decline) "Keep the conversation open" else "No meetup is scheduled", "Make a new plan together when the time is right.")
            }
        }
        page.show()
    }

    private fun outcome(title: String, subtitle: String, cardTitle: String, body: String) {
        val page = screen(title, subtitle)
        page.ui.card(page.content, title = cardTitle, body = body)
        page.ui.button(page.actions, "Back to conversation", true) { openConversation(page) }
        page.show()
    }

    private fun openConversation(page: ExperienceScreen) {
        if (fragment.findNavController().currentDestination?.id == R.id.chatFragment) { page.dismiss(); return }
        val id = conversation ?: return
        request(page) {
            val detail = repo.request("v1/conversations/$id")
            val partner = detail.optJSONArray("participants")?.optJSONObject(0)
            val args = android.os.Bundle().apply {
                putString("conversationId", id)
                putString("partnerName", detail.getString("title"))
                putString("partnerId", partner?.optString("id").orEmpty())
                putString("partnerPhotoUrl", partner?.optString("photoUrl")?.takeIf { it.startsWith("https://") })
            }
            page.dismiss()
            fragment.findNavController().navigate(R.id.chatFragment, args)
        }
    }
}
