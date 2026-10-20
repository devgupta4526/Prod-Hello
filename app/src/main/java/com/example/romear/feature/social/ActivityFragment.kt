package com.example.romear.feature.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.example.romear.R
import com.example.romear.core.designsystem.ExperienceViews
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ActivityState(
    val items: List<JSONObject> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val cursor: String? = null,
    val filter: String = "All",
)

@HiltViewModel
class ActivityViewModel @Inject constructor(private val repository: SocialRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(ActivityState(filter = saved["filter"] ?: "All"))
    val state = mutable.asStateFlow()
    init { load() }

    fun filter(value: String) {
        saved["filter"] = value
        mutable.value = mutable.value.copy(filter = value)
    }

    fun load(more: Boolean = false) {
        if (mutable.value.loading) return
        val cursor = if (more) mutable.value.cursor ?: return else null
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val query = cursor?.let { "&cursor=${android.net.Uri.encode(it)}" }.orEmpty()
                val result = repository.request("v1/notifications?limit=50$query")
                val array = result.getJSONArray("items")
                val items = List(array.length()) { array.getJSONObject(it) }
                mutable.value = mutable.value.copy(
                    items = ((if (more) mutable.value.items else emptyList()) + items).distinctBy { it.getString("id") },
                    cursor = if (result.isNull("nextCursor")) null else result.getString("nextCursor"),
                    loading = false,
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(loading = false, error = failure.message ?: "Your updates could not load.") }
        }
    }

    fun read(item: JSONObject) {
        viewModelScope.launch {
            try {
                repository.request("v1/notifications/read", "POST", JSONObject().put("ids", JSONArray(listOf(item.getString("id")))))
                mutable.value = mutable.value.copy(items = mutable.value.items.map {
                    if (it.getString("id") == item.getString("id")) JSONObject(it.toString()).put("readAt", Instant.now().toString()) else it
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.value = mutable.value.copy(error = failure.message) }
        }
    }
}

@AndroidEntryPoint
class ActivityFragment : Fragment() {
    private val model: ActivityViewModel by viewModels()
    @Inject lateinit var repository: SocialRepository
    private lateinit var social: SocialUi
    private lateinit var support: SupportUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        social = SocialUi(this, repository)
        support = SupportUi(this, repository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ScrollView(requireContext()).apply { isFillViewport = true; addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val column = (view as ScrollView).getChildAt(0) as LinearLayout
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state -> render(column, state) }
            }
        }
    }

    private fun render(container: LinearLayout, state: ActivityState) {
        val ui = ExperienceViews(requireContext())
        val content = ui.column()
        ui.header(content, "ROMEAR · YOUR PLANS", "Your activity.", "The hellos, plans, and updates that matter.")
        val filters = ChipGroup(requireContext()).apply { isSingleSelection = true; isSelectionRequired = true; chipSpacingHorizontal = ui.dp(6) }
        listOf("All", "People", "Plans").forEach { label ->
            filters.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId(); text = label; isCheckable = true; isChecked = state.filter == label
                textSize = 12f
                setChipBackgroundColorResource(R.color.hello_chip_background)
                setChipStrokeColorResource(R.color.hello_chip_stroke)
                chipStrokeWidth = resources.displayMetrics.density
                setTextColor(requireContext().getColorStateList(R.color.hello_chip_text))
                isCheckedIconVisible = false
                setOnClickListener { model.filter(label) }
            })
        }
        ui.add(content, filters)
        val items = state.items.filter { state.filter == "All" || (state.filter == "People") == isPeople(it.getString("type")) }
        items.forEach { item ->
            val type = item.getString("type")
            val payload = item.optJSONObject("payload") ?: JSONObject()
            val title = payload.optString("title").ifBlank { type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } }
            val time = runCatching { DateTimeFormatter.ofPattern("d MMM · h:mm a").withZone(ZoneId.systemDefault()).format(Instant.parse(item.getString("createdAt"))) }.getOrDefault("")
            val eyebrow = (if (item.isNull("readAt")) "NEW · " else "") + time
            ui.card(content, eyebrow, title, payload.optString("message").ifBlank { payload.optString("body") }) {
                model.read(item)
                when {
                    payload.optString("caseId").isNotBlank() -> support.conversation(payload.getString("caseId"))
                    payload.optString("conversationId").isNotBlank() -> social.plans(payload.getString("conversationId"))
                    isPeople(type) -> findNavController().navigate(R.id.inbox_graph)
                    type.contains("EVENT") || type.contains("TICKET") || type.contains("BOOKING") || type.contains("ORDER") -> findNavController().navigate(R.id.tickets_graph)
                    type.contains("POOL") -> findNavController().navigate(R.id.happening_graph)
                }
            }
        }
        if (state.loading) ui.add(content, ui.text("Loading your updates…"))
        else if (items.isEmpty() && state.error == null) ui.card(content, title = "You’re up to date.", body = "Your hellos and plan updates will appear here.")
        state.error?.let {
            ui.card(content, title = "Couldn’t load your updates", body = it)
            ui.button(content, "Try again") { model.load() }
        }
        if (!state.loading && state.cursor != null) ui.button(content, "Load more updates") { model.load(more = true) }
        ui.button(content, "Explore events", primary = true) { findNavController().navigate(R.id.tickets_graph) }
        container.removeAllViews()
        container.addView(content)
    }

    private fun isPeople(type: String) = type.contains("HELLO") || type.contains("CONNECTION") || type.contains("MEET_AGAIN")
}
