package local.phonemanager

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialNavigationTest {
    @Test fun darkThemeKeepsNavigationAndCaptureAccessible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.delegate.localNightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES }
            onView(isAssignableFrom(BottomNavigationView::class.java)).check(matches(isDisplayed()))
            onView(withId(103)).perform(click())
            onView(withText("看见每一次变化")).check(matches(isDisplayed()))
            onView(withId(100)).perform(click())
            onView(withText("拍照识别")).check(matches(isDisplayed()))
        }
    }
    @Test fun materialNavigationKeepsAllFourWorkflowsAccessible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(isAssignableFrom(BottomNavigationView::class.java)).check(matches(isDisplayed()))
            onView(withId(101)).perform(click())
            onView(withText("学生名单")).check(matches(isDisplayed()))
            onView(withId(102)).perform(click())
            onView(withText("每次登记，都有记录")).check(matches(isDisplayed()))
            onView(withId(103)).perform(click())
            onView(withText("看见每一次变化")).check(matches(isDisplayed()))
            onView(withId(100)).perform(click())
            onView(withText("拍照识别")).check(matches(isDisplayed()))
        }
    }
}
