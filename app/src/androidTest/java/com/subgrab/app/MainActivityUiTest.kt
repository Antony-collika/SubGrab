package com.subgrab.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {
    @get:Rule
    val rule = createComposeRule()

    @org.junit.Before
    fun setUp() {
        rule.setContent { SubGrabTheme { SubGrabApp() } }
    }

    @Test
    fun homeScreenShowsPrimaryActions() {
        rule.onNodeWithText("Dán link").assertIsDisplayed()
        rule.onNodeWithText("Tìm từ khóa").assertIsDisplayed()
        rule.onNodeWithText("Phân tích link").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationShowsThreeTabs() {
        rule.onNodeWithText("Tải").assertIsDisplayed()
        rule.onNodeWithText("Thư viện").assertIsDisplayed()
        rule.onNodeWithText("Cài đặt").assertIsDisplayed()
    }

    @Test
    fun settingsTabShowsGroupedSections() {
        rule.onNodeWithText("Cài đặt").performClick()
        rule.onNodeWithText("Tải phụ đề").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Lưu trữ").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Nâng cao").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun libraryTabShowsVideoAndDownloadedTabs() {
        rule.onNodeWithText("Thư viện").performClick()
        rule.onNodeWithText("Video").assertIsDisplayed()
        rule.onNodeWithText("Đã tải").assertIsDisplayed()
    }
}
