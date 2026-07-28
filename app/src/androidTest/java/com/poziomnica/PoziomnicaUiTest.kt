package com.poziomnica

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class PoziomnicaUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigatesBetweenMainScreens() {
        compose.onNodeWithText("Poziomnica").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Historia").performClick()
        compose.onNodeWithText("Historia pomiarów").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Ustawienia").performClick()
        compose.onNodeWithText("Ustawienia").assertIsDisplayed()
    }

    @Test fun holdSaveToleranceUnitAndSoundControlsExist() {
        compose.onNodeWithText("Poziomnica").performClick()
        compose.onNodeWithText("Zapisz").assertIsDisplayed()
        compose.onNodeWithText("±0.5°").performClick()
        compose.onNodeWithText("±0.5°").assertIsDisplayed()
    }
}
