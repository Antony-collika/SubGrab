package com.subgrab.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsPrimaryActions() {
        rule.onNodeWithText("Tải phụ đề YouTube hàng loạt").assertIsDisplayed()
        rule.onNodeWithText("PHÂN TÍCH").assertIsDisplayed()
        rule.onNodeWithContentDescription("Nhật ký debug").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cài đặt").assertIsDisplayed()
    }
}
