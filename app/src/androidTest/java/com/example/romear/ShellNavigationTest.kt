package com.example.romear

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellNavigationTest {
    @Test
    fun BR_10_preview_has_no_live_people_and_navigation_survives_recreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.preview)).perform(scrollTo(), click())
            onView(withId(R.id.empty_title)).check(matches(withText(R.string.discover_empty_title)))
            onView(withId(R.id.happening_graph)).perform(click())
            onView(withId(R.id.title)).check(matches(withText(R.string.happening_title)))
            onView(withId(R.id.inbox_graph)).perform(click())
            onView(withId(R.id.title)).check(matches(withText(R.string.inbox_title)))
            onView(withId(R.id.tickets_graph)).perform(click())
            onView(withId(R.id.title)).check(matches(withText(R.string.tickets_title)))
            onView(withId(R.id.profile_graph)).perform(click())
            scenario.recreate()
            onView(withId(R.id.title)).check(matches(withText(R.string.profile_title)))
            onView(withId(R.id.service_button)).perform(scrollTo()).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.title)).check(matches(withText(R.string.discover_title)))
        }
    }
}
