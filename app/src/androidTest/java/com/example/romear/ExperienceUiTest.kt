package com.example.romear

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.romear.core.designsystem.DesignGalleryActivity
import com.example.romear.core.designsystem.HelloHaptics
import com.example.romear.core.designsystem.HelloLayouts
import com.example.romear.databinding.FragmentDiscoveryBinding
import com.example.romear.databinding.ItemDiscoveryUserBinding
import com.example.romear.feature.discovery.domain.DiscoveryUser
import com.example.romear.feature.discovery.domain.FuzzedMarker
import com.example.romear.feature.discovery.ui.DiscoveryAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExperienceUiTest {
    @Test
    fun compact_discovery_card_keeps_long_identity_clear_of_actions_at_large_font() {
        ActivityScenario.launch(DesignGalleryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply { fontScale = 2f }
                val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_Romear)
                val card = ItemDiscoveryUserBinding.inflate(LayoutInflater.from(context))
                card.userName.text = "Alexandra Longfamilyname, 29"
                card.distanceBucket.text = "Within your surrounding neighborhood"
                card.statusBadge.text = "Open to meet"
                val width = (320 * context.resources.displayMetrics.density).toInt()
                card.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                card.root.layout(0, 0, width, card.root.measuredHeight)
                assertTrue(card.locationRow.top >= card.userName.bottom)
                assertTrue(card.interestsGroup.top >= card.locationRow.bottom)
                assertTrue(card.sayHelloButton.top >= card.interestsGroup.bottom)
                assertTrue(card.sayHelloButton.height >= 48 * context.resources.displayMetrics.density)
                assertTrue(card.sayHelloButton.right <= (card.sayHelloButton.parent as View).width)
            }
        }
    }

    @Test
    fun haptic_preference_is_persistent_and_reversible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = HelloHaptics.isEnabled(context)
        try {
            HelloHaptics.setEnabled(context, false)
            assertEquals(false, HelloHaptics.isEnabled(context))
            HelloHaptics.setEnabled(context, true)
            assertEquals(true, HelloHaptics.isEnabled(context))
        } finally {
            HelloHaptics.setEnabled(context, original)
        }
    }

    @Test
    fun capture_discovery_light_dark_and_large_text_with_explicit_test_fixtures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for ((name, dark, scale) in listOf(Triple("discovery-light", false, 1f), Triple("discovery-dark", true, 1f), Triple("discovery-large-text", false, 2f))) {
            ActivityScenario.launch(DesignGalleryActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val config = Configuration(activity.resources.configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        fontScale = scale
                    }
                    val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_Romear)
                    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(context.getColor(R.color.hello_background)) }
                    root.addView(TextView(context).apply {
                        text = "DESIGN REVIEW · SAMPLE PEOPLE"
                        textSize = 11f
                        gravity = android.view.Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                        setTextColor(context.getColor(R.color.hello_text_secondary))
                    })
                    val screen = FragmentDiscoveryBinding.inflate(LayoutInflater.from(context), root, false)
                    root.addView(screen.root, LinearLayout.LayoutParams(-1, 0, 1f))
                    val adapter = DiscoveryAdapter({}, {})
                    HelloLayouts.adaptiveCards(screen.usersRecyclerView)
                    screen.usersRecyclerView.adapter = adapter
                    adapter.submitList(listOf(
                        DiscoveryUser("fixture-1", "Aarav Sharma", 27, null, "OPEN", "Within 2 km", FuzzedMarker(0.0, 0.0), listOf("Coffee", "Weekend walks", "Photography")),
                        DiscoveryUser("fixture-2", "Meera Kapoor", 25, null, "BROWSING", "Within 5 km", FuzzedMarker(0.0, 0.0), listOf("Live music", "Design", "Good books")),
                    ))
                    val navigation = BottomNavigationView(context).apply {
                        inflateMenu(R.menu.bottom_navigation)
                        selectedItemId = R.id.discover_graph
                        setBackgroundColor(context.getColor(R.color.hello_surface))
                        labelVisibilityMode = 1
                        isItemHorizontalTranslationEnabled = false
                    }
                    root.addView(navigation)
                    activity.setContentView(root)
                    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                        view.setPadding(bars.left, bars.top, bars.right, 0)
                        insets
                    }
                    ViewCompat.requestApplyInsets(root)
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(400)
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-review").apply { mkdirs() }
                File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
        }
    }
}
