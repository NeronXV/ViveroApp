package com.intutec.viveroapp.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.intutec.viveroapp.feature.scanner.presentation.CameraCodeDialog
import com.intutec.viveroapp.ui.theme.ViveroAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CameraCodeDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Before fun requireSeparatePackageWithoutCameraPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Run on the separate readiness package, never the operational app", context.packageName.endsWith(".readiness"))
        assumeTrue("These checks exercise the permission fallback", context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
    }

    @Test fun manualEntryRemainsAvailableWithoutCameraPermission() {
        var dismissed = 0
        var detected = 0
        composeRule.setContent { ViveroAppTheme { CameraCodeDialog({ dismissed++ }, { detected++ }) } }
        composeRule.onNodeWithText("Permitir cámara").assertIsDisplayed()
        composeRule.onNodeWithText("Ingresar código manualmente").performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(0, detected)
        }
    }

    @Test fun backClosesCameraWithoutProducingACode() {
        var dismissed = 0
        var detected = 0
        composeRule.setContent { ViveroAppTheme { CameraCodeDialog({ dismissed++ }, { detected++ }) } }
        composeRule.onNodeWithContentDescription("Volver").performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissed)
            assertEquals(0, detected)
        }
    }
}
