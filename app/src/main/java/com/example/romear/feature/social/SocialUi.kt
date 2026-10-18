package com.example.romear.feature.social

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.romear.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Dialogs share the fragment view lifecycle; all writes are confirmed by the server. */
class SocialUi(private val fragment: Fragment, private val repo: SocialRepository) {
    private val context get() = fragment.requireContext()
    private val restored = fragment.savedStateRegistry.consumeRestoredStateForKey("social_photo")
    private var memoryPlan: String? = restored?.getString("plan")
    private var galleryUpload: Boolean = restored?.getBoolean("gallery") ?: false
    private var homeContainer: LinearLayout? = null
    init {
        fragment.savedStateRegistry.registerSavedStateProvider("social_photo") {
            Bundle().apply { putString("plan", memoryPlan); putBoolean("gallery", galleryUpload) }
        }
    }
    private val dialogs = mutableMapOf<String, androidx.appcompat.app.AlertDialog>()
    private fun track(title: String, dialog: androidx.appcompat.app.AlertDialog) {
        dialogs.remove(title)?.dismiss()
        dialogs[title] = dialog
        val lifecycle = fragment.viewLifecycleOwner.lifecycle
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { dialog.dismiss() }
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener { lifecycle.removeObserver(observer); if(dialogs[title] === dialog) dialogs.remove(title) }
    }
    private val picker = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val plan = memoryPlan
        if (uri != null && galleryUpload) run {
            galleryUpload = false
            val file = com.example.romear.core.security.ProfilePhotoProcessor.prepare(context, uri)
            try { repo.uploadProfilePhoto(file); gallery() } finally { file.delete() }
        }
        else if (uri != null && plan != null) run {
            val bytes = withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                require(options.outWidth > 0 && options.outHeight > 0) { "Invalid photo" }
                options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 600)
                options.inJustDecodeBounds = false
                val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Could not read this photo")
                val scale = minOf(1f, 640f / maxOf(bitmap.width, bitmap.height))
                val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                val output = ByteArrayOutputStream()
                var quality = 80
                do { output.reset(); scaled.compress(Bitmap.CompressFormat.JPEG, quality, output); quality -= 10 } while (output.size() > 70000 && quality >= 20)
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                require(output.size() <= 70000) { "Choose a simpler or smaller photo" }
                output.toByteArray()
            }
            val column = column()
            val caption = input(column, "Caption (optional)", max = 240)
            val group = CheckBox(context).apply { text = "Share with current plan participants" }; column.addView(group)
            val consent = CheckBox(context).apply { text = "I have permission from everyone pictured to share this photo" }; column.addView(consent)
            form("Share a memory", column, "Save") {
                require(consent.isChecked) { "Confirm everyone's permission first" }
                repo.request("v1/plans/$plan/memories", "POST", JSONObject().put("caption", caption.text.toString()).put("imageBase64", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("audience", if (group.isChecked) "GROUP" else "PRIVATE").put("consent", true))
                memories(plan)
            }
        }
    }
    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; val p = (20 * resources.displayMetrics.density).toInt(); setPadding(p,p,p,p) }
    private fun label(c: LinearLayout, value: String) { c.addView(TextView(context).apply { text = value; textSize = 16f; setPadding(0,12,0,12) }) }
    private fun button(c: LinearLayout, value: String, action: () -> Unit) { c.addView(MaterialButton(context).apply { text = value; isAllCaps = false; setOnClickListener { action() } }) }
    private fun input(c: LinearLayout, hint: String, value: String = "", max: Int = 240): EditText = EditText(context).apply { this.hint = hint; setText(value); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE; filters = arrayOf(android.text.InputFilter.LengthFilter(max)); c.addView(this) }
    private fun show(title: String, c: LinearLayout) { val dialog = MaterialAlertDialogBuilder(context).setTitle(title).setView(ScrollView(context).apply { addView(c) }).setNegativeButton("Close", null).show(); track(title, dialog) }
    private fun run(action: suspend () -> Unit) { fragment.viewLifecycleOwner.lifecycleScope.launch { try { action() } catch (e: CancellationException) { throw e } catch (e: Exception) { Toast.makeText(context, if(e is retrofit2.HttpException) "Could not save or load (${e.code()}). Check access, availability and agreement, then retry." else e.message ?: "Please retry", Toast.LENGTH_LONG).show() } } }
    private fun form(title: String, c: LinearLayout, positive: String = "Save", save: suspend () -> Unit) {
        val d = MaterialAlertDialogBuilder(context).setTitle(title).setView(ScrollView(context).apply { addView(c) }).setNegativeButton("Cancel", null).setPositiveButton(positive, null).create()
        track(title, d)
        d.setOnShowListener { d.getButton(-1).setOnClickListener { run { d.getButton(-1).isEnabled = false; try { save(); d.dismiss() } finally { d.getButton(-1).isEnabled = true } } } }; d.show()
    }
    private fun date(value: String): String = try { DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a").format(Instant.parse(value).atZone(java.time.ZoneId.systemDefault())) } catch (_: Exception) { value }
    private fun objects(array: JSONArray): List<JSONObject> = List(array.length()) { array.getJSONObject(it) }

    fun gallery(): Unit = run {
        val data = repo.request("v1/me/profile"); val c = column()
        val photos = objects(data.getJSONArray("photos"))
        label(c,"Your profile gallery · ${photos.size}/6 photos. Keep at least one photo.")
        if(photos.size < 6) button(c,"Add a photo") { galleryUpload = true; memoryPlan = null; picker.launch("image/*") }
        photos.forEachIndexed { index, photo ->
            val avatar = com.example.romear.core.designsystem.AvatarView(context).apply { load(photo.getString("url"),"Photo ${index+1}",sizeDp=160); contentDescription = "Profile photo ${index+1}" }
            val size = (160 * context.resources.displayMetrics.density).toInt(); c.addView(avatar,LinearLayout.LayoutParams(size,size))
            if(photos.size > 1) button(c,"Remove photo ${index+1}") { MaterialAlertDialogBuilder(context).setTitle("Remove this profile photo?").setNegativeButton("Keep",null).setPositiveButton("Remove") { _,_ -> run { repo.request("v1/me/photos/${photo.getString("id")}","DELETE"); gallery() } }.show() }
        }
        show("My photos",c)
    }
    fun editBasics(): Unit = run {
        val p = repo.request("v1/me/profile").getJSONObject("profile"); val c = column()
        val name = input(c,"Display name",p.getString("displayName"),30)
        val bio = input(c,"About me",p.optString("bio"),160)
        label(c,"Choose 3 to 8 interests")
        val current = p.getJSONArray("interests"); val selected = List(current.length()) { current.getString(it) }.toMutableSet()
        listOf("Coffee","Music","Food","Books","Art","Travel","Fitness","Outdoors","Gaming","Photography","Sports","Technology").forEach { interest ->
            c.addView(CheckBox(context).apply { text = interest; isChecked = interest in selected; setOnCheckedChangeListener { _,checked -> if(checked) selected.add(interest) else selected.remove(interest) } })
        }
        form("Edit profile",c) { require(selected.size in 3..8 && name.text.trim().length >= 2) { "Enter your name and choose 3 to 8 interests" }; repo.updateProfile(name.text.toString(),bio.text.toString(),selected.toList()) }
    }
    fun personality(): Unit = run {
        val data = repo.request("v1/me/personality")
        val c = column()
        label(c, "Give someone a thoughtful place to start.")
        val prompts = objects(data.getJSONArray("prompts")).associate { it.getString("id") to it.getString("answer") }
        val questions = listOf("joy" to "A small thing that brings me joy", "learn" to "Something I'd love to learn together", "friendship" to "A good friendship looks like")
        val fields = questions.map { (id, question) -> label(c, question); id to input(c, "Your answer", prompts[id].orEmpty()) }
        label(c, "My ideal weekend")
        val weekend = input(c, "Your ideal weekend", data.optString("idealWeekend"), 400)
        form("More about me", c) {
            require(fields.all { it.second.text.isNotBlank() } && weekend.text.isNotBlank()) { "Answer all three prompts and your ideal weekend" }
            repo.request("v1/me/personality", "PUT", JSONObject().put("prompts", JSONArray(fields.map { (id, field) -> JSONObject().put("id", id).put("answer", field.text.toString()) })).put("idealWeekend", weekend.text.toString()))
        }
    }
    fun upFor(): Unit = run {
        val data = repo.request("v1/me/personality")
        val c = column()
        label(c, if(data.isNull("upFor")) "What would you enjoy doing? This expires automatically." else "Currently up for ${data.getString("upFor")} until ${date(data.getString("upForUntil"))}")
        val activity = input(c, "Coffee, a walk, badminton…", max = 60)
        val hours = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, listOf("1 hour", "3 hours", "6 hours", "24 hours")); setSelection(1) }; c.addView(hours)
        button(c, "Clear availability") { run { repo.request("v1/me/up-for", "PUT", JSONObject().put("activity", JSONObject.NULL)); Toast.makeText(context, "Availability cleared", Toast.LENGTH_SHORT).show() } }
        form("Up for…", c) { require(activity.text.isNotBlank()) { "Choose an activity" }; repo.request("v1/me/up-for", "PUT", JSONObject().put("activity", activity.text.toString()).put("hours", listOf(1,3,6,24)[hours.selectedItemPosition])) }
    }
    fun home(container: LinearLayout? = null): Unit = run {
        if(container != null) homeContainer = container
        val data = repo.request("v1/home"); val c = column()
        label(c, "Your week")
        button(c, "Notifications") { notifications() }
        button(c, "Refresh") { home() }
        button(c, "Discover people nearby") { fragment.findNavController().navigate(R.id.discoverFragment) }
        button(c, "Interest circles") { circles() }
        button(c, "Up for…") { upFor() }
        label(c, "People with shared interests")
        val people = objects(data.getJSONArray("relevantPeople"))
        if(people.isEmpty()) label(c, "People appear here while nearby discovery is active.")
        people.forEach { person -> button(c, person.getString("displayName") + if(person.isNull("upFor")) "" else " · Up for ${person.getString("upFor")}") { publicProfile(person.getString("userId")) } }
        label(c, "Upcoming commitments")
        val commitments = objects(data.getJSONArray("commitments"))
        if(commitments.isEmpty()) label(c, "Your booked events will appear here.")
        commitments.forEach { label(c, "${it.getString("title")} · ${date(it.getString("startsAt"))}") }
        label(c, "Your plans")
        val planItems = objects(data.getJSONArray("plans"))
        if(planItems.isEmpty()) label(c, "Make a plan from a conversation in your inbox.")
        planItems.forEach { p -> button(c, "${p.getString("activity")} · ${p.getString("status")}") { plans(p.getString("conversationId")) } }
        label(c, "Saved experiences")
        val saved = objects(data.getJSONArray("saved"))
        if(saved.isEmpty()) label(c, "Save an experience below for later.")
        saved.forEach { e -> button(c, "${e.getString("title")} · ${date(e.getString("startsAt"))} · Unsave") { run { save(e, false); home() } } }
        val digest = Switch(context).apply { text = "Receive an optional weekly in-app digest"; isChecked = data.getBoolean("weeklyDigest") }; c.addView(digest)
        digest.setOnCheckedChangeListener { _, enabled -> run { try { repo.request("v1/me/weekly-digest", "PUT", JSONObject().put("enabled", enabled)); home() } catch(e: Exception) { digest.setOnCheckedChangeListener(null); digest.isChecked = !enabled; throw e } } }
        if(data.getBoolean("weeklyDigest")) { label(c, "This week's shortlist"); val shortlist = objects(data.getJSONArray("shortlist")); if(shortlist.isEmpty()) label(c, "No matching experiences this week yet."); shortlist.forEach { label(c, "${it.getString("title")} · ${date(it.getString("startsAt"))}") } }
        label(c, "Nearby plans")
        val nearby = objects(data.getJSONArray("nearbyPlans"))
        if(nearby.isEmpty()) label(c, "No upcoming plans in your discovery area yet.")
        nearby.forEach { label(c, "${it.getString("title")} · ${date(it.getString("startsAt"))}") }
        label(c, "Experiences for you")
        val experiences = objects(data.getJSONArray("experiences"))
        if(experiences.isEmpty()) label(c, "No upcoming experiences yet. Check Happening for new plans.")
        experiences.forEach { e -> button(c, "${e.getString("title")} · ${date(e.getString("startsAt"))} · Save") { run { save(e, true); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } } }
        val target = homeContainer
        if(target == null) show("Your week", c)
        else { target.removeAllViews(); target.addView(c) }
    }
    private fun notifications(): Unit = run {
        val c = column()
        val items = objects(repo.request("v1/notifications?limit=50").getJSONArray("items"))
        if(items.isEmpty()) label(c,"You're up to date.")
        items.forEach { item ->
            val payload = item.getJSONObject("payload")
            val type = item.getString("type")
            val title = when(type) { "WEEKLY_DIGEST" -> "Your weekly shortlist is ready"; "MEET_AGAIN" -> "A mutual meet-again choice"; "PLAN_CONFIRMED" -> "Plan confirmed"; "PLAN_CANCELLED" -> "Plan cancelled"; else -> type.lowercase().replace('_',' ') }
            button(c,"$title · ${date(item.getString("createdAt"))}") {
                run {
                    repo.request("v1/notifications/read","POST",JSONObject().put("ids",JSONArray(listOf(item.getString("id")))))
                    if(type == "WEEKLY_DIGEST") home()
                    else if(payload.has("conversationId")) plans(payload.getString("conversationId"))
                    else Toast.makeText(context,title,Toast.LENGTH_SHORT).show()
                }
            }
        }
        show("Notifications",c)
    }
    private fun publicProfile(id: String): Unit = run {
        val p = repo.request("v1/users/$id"); val c = column()
        label(c, if(p.optBoolean("phoneVerified")) "Phone verified" else "Phone not verified")
        label(c, p.optString("bio", ""))
        label(c, "My ideal weekend\n${p.optString("idealWeekend")}")
        objects(p.getJSONArray("photos")).forEachIndexed { index, photo ->
            val avatar = com.example.romear.core.designsystem.AvatarView(context).apply { load(photo.getString("url"),p.getString("displayName"),sizeDp=160) }
            val size = (160 * context.resources.displayMetrics.density).toInt()
            c.addView(avatar, LinearLayout.LayoutParams(size,size))
            button(c, "Reply to photo ${index+1}") { hello(id,JSONObject().put("type","PHOTO").put("id",photo.getString("id")).put("text","Photo ${index+1}")) }
        }
        objects(p.getJSONArray("prompts")).forEach { prompt -> label(c,prompt.getString("answer")); button(c,"Reply to this prompt") { hello(id,JSONObject().put("type","PROMPT").put("id",prompt.getString("id")).put("text",prompt.getString("answer"))) } }
        button(c,"Say Hello") { hello(id,null) }
        button(c,"Report profile") { report(id) }
        show(p.getString("displayName"),c)
    }
    private fun hello(id: String, replyContext: JSONObject?) {
        val c = column(); replyContext?.let { label(c,"Reply to: ${it.optString("text")}") }; val message = input(c,"Your Hello",max=280)
        form("Say Hello",c,"Send") {
            require(message.text.isNotBlank()) { "Write a message first" }
            val body = JSONObject().put("receiverId",id).put("message",message.text.toString())
            replyContext?.let { body.put("context",it) }
            repo.request("v1/hellos","POST",body)
        }
    }
    private suspend fun save(e: JSONObject, saved: Boolean) { repo.request("v1/me/saved", "PUT", JSONObject().put("targetId", e.getString("id")).put("kind", e.getString("kind")).put("saved", saved)) }
    fun circles(): Unit = run {
        val c = column(); button(c, "Start an interest circle") { createCircle() }
        val items = objects(repo.request("v1/circles").getJSONArray("items"))
        if(items.isEmpty()) label(c, "Be the first to gather people around an interest.")
        items.forEach { item ->
            label(c, "${item.getString("name")} · ${item.getString("interest")}\n${item.getInt("count")}/${item.getInt("capacity")} members\n${item.getString("rules")}")
            if(item.getBoolean("joined")) {
                button(c, "Circle discussion") { circlePosts(item) }
                button(c, "View members") { circleMembers(item) }
                if(!item.getBoolean("isHost")) button(c, "Leave circle") { membership(item, false) }
            } else button(c, "Accept rules and join") { membership(item, true) }
        }; show("Interest circles", c)
    }
    private fun circlePosts(circle: JSONObject): Unit = run {
        val id = circle.getString("id"); val c = column()
        label(c,circle.getString("rules"))
        button(c,"Start a conversation") {
            val fields = column(); val body = input(fields,"Share an idea or suggest a meetup",max=1000)
            form("Post to circle",fields,"Post") { repo.request("v1/circles/$id/posts","POST",JSONObject().put("body",body.text.toString())); circlePosts(circle) }
        }
        val posts = objects(repo.request("v1/circles/$id/posts").getJSONArray("items"))
        if(posts.isEmpty()) label(c,"No posts yet. Introduce yourself or suggest something to do together.")
        posts.forEach { post ->
            label(c,"${post.getString("displayName")} · ${date(post.getString("createdAt"))}\n${post.getString("body")}")
            button(c,"Report post") { val fields = column(); val reason = input(fields,"Reason",max=100); form("Report post",fields,"Report") { repo.request("v1/reports","POST",JSONObject().put("targetType","CIRCLE_POST").put("targetId",post.getString("id")).put("targetUserId",post.getString("authorId")).put("reason",reason.text.toString())) } }
            if(post.getBoolean("canRemove")) button(c,"Remove post") { MaterialAlertDialogBuilder(context).setTitle("Remove this post?").setNegativeButton("Cancel",null).setPositiveButton("Remove") { _,_ -> run { repo.request("v1/circles/$id/posts/${post.getString("id")}","DELETE"); circlePosts(circle) } }.show() }
        }
        show(circle.getString("name"),c)
    }
    private fun membership(c: JSONObject, joined: Boolean): Unit = run { repo.request("v1/circles/${c.getString("id")}/membership", "PUT", JSONObject().put("joined", joined).put("acceptRules", joined)); circles() }
    private fun createCircle() {
        val c = column(); val name = input(c,"Circle name",max=80); val interest = input(c,"Shared interest",max=40); val rules = input(c,"Group rules", "Meet in public, respect boundaries, and ask before sharing photos.",1000)
        form("Start a circle",c,"Create") { repo.request("v1/circles","POST",JSONObject().put("name",name.text.toString()).put("interest",interest.text.toString()).put("rules",rules.text.toString()).put("capacity",30)); circles() }
    }
    private fun circleMembers(circle: JSONObject): Unit = run {
        val c = column(); objects(repo.request("v1/circles/${circle.getString("id")}/members").getJSONArray("items")).forEach { member ->
            label(c, member.getString("displayName") + if(member.getBoolean("phoneVerified")) " · Phone verified" else " · Phone not verified")
            if(circle.getBoolean("isHost")) button(c,"Remove ${member.getString("displayName")}") { MaterialAlertDialogBuilder(context).setTitle("Remove this member?").setMessage("They will no longer be able to rejoin this circle.").setNegativeButton("Cancel",null).setPositiveButton("Remove") { _,_ -> run { repo.request("v1/circles/${circle.getString("id")}/members/${member.getString("id")}","DELETE"); circleMembers(circle) } }.show() }
            button(c,"Report member") { report(member.getString("id")) }
        }; show(circle.getString("name"),c)
    }
    private fun report(id: String) { val c = column(); val reason = input(c,"Reason for report"); form("Report member",c,"Submit") { repo.request("v1/reports","POST",JSONObject().put("targetType","USER").put("targetId",id).put("reason",reason.text.toString())) } }
    fun plans(conversationId: String): Unit = run {
        val data = repo.request("v1/conversations/$conversationId/plans"); val userId = data.getString("userId"); val c = column()
        button(c,"Suggest a plan") { createPlan(conversationId) }
        val items = objects(data.getJSONArray("items")); if(items.isEmpty()) label(c,"Choose something to do together and a public place to meet.")
        items.forEach { p ->
            val id = p.getString("id"); val status = p.getString("status"); val host = p.getString("creatorId") == userId
            label(c,"${p.getString("activity")}\n${p.getString("venue")} · $status")
            if(status == "PROPOSED") {
                val options = p.getJSONArray("timeOptions"); val votes = objects(p.getJSONArray("votes"))
                for(i in 0 until options.length()) {
                    val option = i
                    button(c,"${date(options.getString(i))} · ${votes.count { it.getInt("option") == i }} votes${if(votes.any { it.getString("userId") == userId && it.getInt("option") == i }) " · Your choice" else ""}") { run { repo.request("v1/plans/$id/vote","PUT",JSONObject().put("option",option)); plans(conversationId) } }
                    if(host) button(c,"Confirm option ${i+1}") { run { repo.request("v1/plans/$id/confirm","POST",JSONObject().put("option",option)); plans(conversationId) } }
                }
                label(c,"Everyone in the chat must vote for the same time before confirmation.")
            }
            if(status == "CONFIRMED") { label(c,date(p.getString("confirmedTime")) + " · ${p.optInt("durationMinutes",60)} minutes"); button(c,"Memories") { memories(id) }; button(c,"Meet again") { reconnect(id) } }
            if(host && status != "CANCELLED") button(c,"Cancel plan") { MaterialAlertDialogBuilder(context).setTitle("Cancel this plan?").setNegativeButton("Keep plan",null).setPositiveButton("Cancel plan") { _,_ -> run { repo.request("v1/plans/$id/cancel","POST"); plans(conversationId) } }.show() }
        }; show("Make a plan",c)
    }
    private fun createPlan(conversationId: String) {
        val c = column(); val activity = input(c,"Activity",max=100); val venue = input(c,"Public venue name and address",max=200)
        val duration = Spinner(context).apply { adapter = ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,listOf("30 minutes","1 hour","2 hours","3 hours")); setSelection(1) }; c.addView(duration)
        val publicVenue = CheckBox(context).apply { text = "This is a public meeting place" }; c.addView(publicVenue)
        val times = mutableListOf<Instant>()
        val selected = TextView(context); c.addView(selected)
        button(c,"Add a time (up to 3)") {
            if(times.size >= 3) { Toast.makeText(context,"Three options added",Toast.LENGTH_SHORT).show() } else {
                val now = ZonedDateTime.now()
                DatePickerDialog(context,{ _,y,m,d -> TimePickerDialog(context,{ _,h,min -> val instant = now.withDayOfMonth(1).withYear(y).withMonth(m+1).withDayOfMonth(d).withHour(h).withMinute(min).withSecond(0).withNano(0).toInstant(); if(instant > Instant.now() && instant !in times) { times.add(instant); selected.text = times.joinToString("\n") { date(it.toString()) } } else Toast.makeText(context,"Choose a distinct future time",Toast.LENGTH_SHORT).show() },now.hour,now.minute,false).show() },now.year,now.monthValue-1,now.dayOfMonth).show()
            }
        }
        button(c,"Clear times") { times.clear(); selected.text = "" }
        form("Suggest a plan",c,"Propose") { require(publicVenue.isChecked && times.isNotEmpty()) { "Choose a time and confirm a public venue" }; repo.request("v1/conversations/$conversationId/plans","POST",JSONObject().put("activity",activity.text.toString()).put("venue",venue.text.toString()).put("publicVenue",true).put("durationMinutes",listOf(30,60,120,180)[duration.selectedItemPosition]).put("timeOptions",JSONArray(times.map { it.toString() }))); plans(conversationId) }
    }
    private fun reconnect(planId: String): Unit = run {
        val c = column(); label(c,"After your plan, privately choose someone to meet again. Only mutual choices are revealed.")
        objects(repo.request("v1/plans/$planId/participants").getJSONArray("items")).forEach { person -> button(c,person.getString("displayName")) {
            val reconnectForm = column(); val activity = input(reconnectForm,"Suggest another activity",max=100)
            button(reconnectForm,"Withdraw my choice") { run { repo.request("v1/plans/$planId/reconnect","PUT",JSONObject().put("targetId",person.getString("id")).put("activity","Withdraw").put("interested",false)); Toast.makeText(context,"Choice withdrawn",Toast.LENGTH_SHORT).show() } }
            form("Meet ${person.getString("displayName")} again",reconnectForm) { val result = repo.request("v1/plans/$planId/reconnect","PUT",JSONObject().put("targetId",person.getString("id")).put("activity",activity.text.toString()).put("interested",true)); Toast.makeText(context,if(result.getBoolean("mutual")) "Mutual! They suggested ${result.getString("activity")}. Start a new plan in chat." else "Your choice is private unless they choose you too.",Toast.LENGTH_LONG).show() }
        } }; show("Meet again",c)
    }
    private fun memories(planId: String): Unit = run {
        val data = repo.request("v1/plans/$planId/memories"); val c = column()
        button(c,"Add a photo") { galleryUpload = false; memoryPlan = planId; picker.launch("image/*") }
        val items = objects(data.getJSONArray("items")); if(items.isEmpty()) label(c,"Photos shared with the group appear here. Ask everyone pictured before uploading.")
        items.forEach { m ->
            val id = m.getString("id")
            label(c,"${m.getString("caption")} · ${m.getString("audience")}")
            button(c,"Request removal / report photo") {
                val fields = column(); val reason = input(fields,"Reason for removal",max=100)
                form("Report photo",fields,"Submit") { repo.request("v1/reports","POST",JSONObject().put("targetType","PLAN_MEMORY").put("targetId",id).put("targetUserId",m.getString("ownerId")).put("reason",reason.text.toString())) }
            }
            button(c,"View photo") { run { val bytes = repo.image(id); val image = ImageView(context).apply { adjustViewBounds = true; setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.size)); contentDescription = m.getString("caption") }; MaterialAlertDialogBuilder(context).setView(image).setNegativeButton("Close",null).show() } }
            if(m.getString("ownerId") == data.getString("userId")) button(c,if(m.getString("audience") == "GROUP") "Make private" else "Share with group") { MaterialAlertDialogBuilder(context).setTitle("Change audience?").setMessage("Confirm that everyone pictured agrees to the new audience.").setNegativeButton("Cancel",null).setPositiveButton("Confirm") { _,_ -> run { repo.request("v1/memories/$id/audience","PUT",JSONObject().put("audience",if(m.getString("audience") == "GROUP") "PRIVATE" else "GROUP").put("consent",true)); memories(planId) } }.show() }
            button(c,"Remove photo (owner or plan host)") { MaterialAlertDialogBuilder(context).setTitle("Remove this photo?").setNegativeButton("Keep",null).setPositiveButton("Remove") { _,_ -> run { repo.request("v1/memories/$id","DELETE"); memories(planId) } }.show() }
        }; show("Plan memories",c)
    }
}
