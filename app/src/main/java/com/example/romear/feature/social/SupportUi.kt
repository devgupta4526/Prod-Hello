package com.example.romear.feature.social

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.InputFilter
import android.text.InputType
import android.util.Base64
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.romear.R
import com.example.romear.core.designsystem.ExperienceScreen
import com.example.romear.core.designsystem.HelloFeedback
import com.example.romear.feature.events.domain.UserTicketItem
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

class SupportUi(private val fragment: Fragment, private val repo: SocialRepository) {
    private var current: ExperienceScreen? = null
    private var attachment: String? = null
    private var attachmentButton: MaterialButton? = null
    private val picker = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) current?.let { page -> run(page) {
            val encoded = image(uri)
            if (current === page) {
                attachment = encoded
                attachmentButton?.text = "Receipt attached · tap to replace"
            }
        } }
    }

    private fun page(title: String, subtitle: String): ExperienceScreen {
        current?.dismiss()
        return ExperienceScreen(fragment).also {
            current = it
            it.ui.header(it.content, "SUPPORT", title, subtitle)
        }
    }

    private fun run(page: ExperienceScreen, button: MaterialButton? = null, action: suspend () -> Unit) {
        button?.isEnabled = false
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { HelloFeedback.error(page.content, failure.message ?: "Couldn’t load support. Please retry.") }
            finally { button?.isEnabled = true }
        }
    }

    private fun input(page: ExperienceScreen, label: String, hint: String): EditText {
        page.ui.add(page.content, page.ui.text(label, 11f))
        return EditText(fragment.requireContext()).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_tertiary))
            setBackgroundResource(R.drawable.bg_input_field)
            setPadding(page.ui.dp(16), page.ui.dp(16), page.ui.dp(16), page.ui.dp(16))
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(2000))
            page.ui.add(page.content, this, 8)
        }
    }

    fun create(ticket: UserTicketItem) {
        attachment = null
        val page = page("We’re here to help.", "Choose the plan you need help with.")
        page.ui.card(page.content, title = ticket.eventTitle, body = "Booking ${ticket.id.take(8).uppercase()}")
        page.ui.add(page.content, page.ui.text("ISSUE", 11f))
        val issue = Spinner(fragment.requireContext()).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("Payment or refund", "Tickets or entry", "Event changed", "Safety concern", "Something else"))
            setBackgroundResource(R.drawable.bg_input_field)
            minimumHeight = page.ui.dp(56)
        }
        page.ui.add(page.content, issue, 8)
        val body = input(page, "WHAT HAPPENED?", "Describe the issue and include any useful details.")
        page.ui.add(page.content, page.ui.text("ATTACHMENT · OPTIONAL", 11f))
        attachmentButton = page.ui.button(page.content, "+ Add a receipt or screenshot") { picker.launch("image/*") }
        page.ui.add(page.content, page.ui.text("Never include passwords, OTPs, or full payment-card details.", 12f, R.color.text_tertiary))
        val requestId = UUID.randomUUID().toString()
        lateinit var send: MaterialButton
        send = page.ui.button(page.actions, "Send support request", true) {
            if (body.text.trim().length < 2) body.error = "Describe what happened"
            else run(page, send) {
                val request = JSONObject().put("requestId", requestId).put("ticketId", ticket.id).put("issue", issue.selectedItem.toString()).put("body", body.text.toString().trim())
                attachment?.let { request.put("imageBase64", it) }
                val result = repo.request("v1/me/support", "POST", request)
                if (current === page) confirmation(result, false)
            }
        }
        page.show()
    }

    fun list() {
        val page = page("We’re here to help.", "Your support cases and replies.")
        page.show()
        run(page) {
            val items = repo.request("v1/me/support").getJSONArray("items")
            if (current !== page) return@run
            if (items.length() == 0) page.ui.card(page.content, title = "No open requests", body = "Open a booking and choose Help & Support to ask about it.")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                page.ui.card(page.content, eyebrow = item.getString("status"), title = reference(item), body = item.getString("issue")) { conversation(item.getString("id")) }
            }
        }
    }

    fun conversation(id: String) {
        val page = page("Your support conversation.", "Loading your case…")
        page.show()
        run(page) {
            val item = repo.request("v1/support/$id")
            if (current !== page) return@run
            page.content.removeAllViews()
            page.ui.header(page.content, "SUPPORT", "Your support conversation.", "${reference(item)} · ${item.getString("issue")}")
            val messages = item.getJSONArray("messages")
            for (index in 0 until messages.length()) {
                val message = messages.getJSONObject(index)
                val card = page.ui.card(page.content, title = if (message.getBoolean("fromSupport")) "Romear Support" else "You", body = message.getString("body"))
                if (message.optBoolean("hasAttachment")) page.ui.button(card, "View attachment") {
                    run(page) {
                        val bytes = repo.binary("v1/support/$id/attachments/${message.getString("id")}")
                        val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        val image = android.widget.ImageView(fragment.requireContext()).apply { adjustViewBounds = true; setImageBitmap(bitmap); contentDescription = "Support attachment" }
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(fragment.requireContext()).setView(image).setPositiveButton("Close", null).show()
                    }
                }
            }
            if (item.getString("status") == "OPEN") {
                val reply = input(page, "YOUR REPLY", "Type your message…")
                val requestId = UUID.randomUUID().toString()
                lateinit var send: MaterialButton
                send = page.ui.button(page.actions, "Send reply", true) {
                    if (reply.text.trim().length < 2) reply.error = "Write your reply"
                    else run(page, send) {
                        repo.request("v1/support/$id/replies", "POST", JSONObject().put("requestId", requestId).put("body", reply.text.toString().trim()))
                        if (current === page) confirmation(item, true)
                    }
                }
            } else page.ui.card(page.content, title = "Case closed", body = "This conversation is available for your records.")
            page.ui.button(page.content, "Refresh conversation") { conversation(id) }
        }
    }

    private fun reference(item: JSONObject) = "Case CS-${item.getString("id").takeLast(8).uppercase()}"

    private fun confirmation(item: JSONObject, reply: Boolean) {
        val page = page(if (reply) "Reply sent." else "Support request sent.", if (reply) "Your case remains open." else "You can return to this case from Help & Support.")
        page.ui.card(page.content, eyebrow = item.getString("status"), title = reference(item), body = item.getString("issue"))
        page.ui.card(page.content, title = "What happens next?", body = "We will send an in-app update when there is a reply. Keep your booking reference handy.")
        page.ui.button(page.actions, if (reply) "Back to support conversation" else "Open support conversation", true) { conversation(item.getString("id")) }
        page.show()
    }

    private suspend fun image(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = fragment.requireContext().contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Choose a valid image" }
        options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 640)
        options.inJustDecodeBounds = false
        val source = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Image could not be read")
        val scale = minOf(1f, 640f / maxOf(source.width, source.height))
        val bitmap = Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
        try {
            val stream = ByteArrayOutputStream()
            var quality = 85
            do { stream.reset(); bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream); quality -= 10 } while (stream.size() > 70000 && quality >= 25)
            require(stream.size() <= 70000) { "Choose a smaller or simpler image" }
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } finally { if (bitmap !== source) bitmap.recycle(); source.recycle() }
    }
}
