package com.subgrab.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
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
        rule.onNodeWithText("Tải phụ đề YouTube hàng loạt").assertIsDisplayed()
        rule.onNodeWithText("PHÂN TÍCH").assertIsDisplayed()
        rule.onNodeWithContentDescription("Nhật ký debug").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cài đặt").assertIsDisplayed()
    }
}
