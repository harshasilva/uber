package lk.ridewise

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import lk.ridewise.domain.model.VehicleCostSettings
import org.junit.*
import org.junit.runner.RunWith

/** All requests and locations in these flows are synthetic. No third-party app is used. */
@RunWith(AndroidJUnit4::class)
class RideWiseFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as RideWiseApplication
    @Before fun prepare() = runBlocking {
        app.repository.clear()
        app.repository.updateSettings(VehicleCostSettings.Demo)
        app.captureSession.reset()
    }
    private fun demo() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Try Demo Ride").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Try Demo Ride").performScrollTo().performClick()
        rule.onNodeWithText("Check the details.").assertIsDisplayed()
    }
    private fun confirmAndCalculate() {
        rule.onNode(isToggleable()).performScrollTo().performClick()
        rule.onNodeWithText("Calculate Profitability").performScrollTo().performClick()
        rule.onNodeWithText("ESTIMATED NET EARNINGS").assertIsDisplayed()
    }
    private fun openHistory() {
        rule.waitUntil(10_000) { rule.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("History").performClick()
    }
    @Test fun demoToResultsSaveReopenAndDelete() {
        demo(); confirmAndCalculate()
        rule.onAllNodesWithText("LKR 1,420.00").onFirst().assertExists()
        rule.onNodeWithText("Save analysis & return").performScrollTo().performClick()
        openHistory()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Nugegoda town centre").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Nugegoda town centre").performClick()
        rule.onNodeWithText("Saved locally").assertIsDisplayed()
        // A save snackbar can briefly cover the bottom action after rapid navigation.
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Analysis saved on this device").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Delete this analysis").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Delete analysis").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Delete analysis").performClick()
        openHistory()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("0 saved analyses · stored only on this device").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun editingExtractedDemoChangesCalculation() {
        demo()
        rule.onNodeWithText("Driver payout (LKR)").performScrollTo().performTextReplacement("1000.00")
        confirmAndCalculate()
        rule.onAllNodesWithText("LKR 620.00").onFirst().assertExists()
    }
    @Test fun manualEntryToResults() {
        rule.onNodeWithText("Enter Ride Manually").performScrollTo().performClick()
        listOf("Driver payout (LKR)" to "1800", "Pickup distance (km)" to "1.5", "Passenger trip distance (km)" to "8.5",
            "Pickup time (minutes)" to "5", "Trip time (minutes)" to "25").forEach { (label, value) ->
            rule.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        }
        confirmAndCalculate()
        rule.onNodeWithText("MANUAL").assertIsDisplayed()
        rule.onAllNodesWithText("LKR 1,420.00").onFirst().assertExists()
    }
    @Test fun deniedCaptureHasManualRecovery() {
        rule.onNodeWithText("Capture", substring = false).performClick()
        rule.runOnUiThread { app.captureSession.permissionDenied() }
        rule.onNodeWithText("Screen access was cancelled or denied. Try demo or manual entry, or start a new session.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Enter Ride Manually").performScrollTo().performClick()
        rule.onNodeWithText("Check the details.").assertIsDisplayed()
    }
    @Test fun clearHistoryRequiresConfirmation() {
        demo(); confirmAndCalculate()
        rule.onNodeWithText("Save analysis & return").performScrollTo().performClick()
        openHistory()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Clear all history").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Clear all history").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("1 saved analyses · stored only on this device").assertExists()
        rule.onNodeWithText("Clear all history").performClick()
        rule.onNodeWithText("Delete all analyses").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("0 saved analyses · stored only on this device").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun vehicleSettingValidationAndSave() {
        rule.onNodeWithContentDescription("Vehicle settings").performClick()
        rule.onNodeWithText("Fuel economy (km / litre)").performScrollTo().performTextReplacement("0")
        rule.onNodeWithText("Save vehicle settings").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Fuel economy (km / litre)").performScrollTo().performTextReplacement("16.5")
        rule.onNodeWithText("Save vehicle settings").performScrollTo().performClick()
        rule.waitUntil(10_000) { runBlocking { app.repository.settings.first() }.fuelEconomyKmPerLitre?.toPlainString() == "16.5" }
    }
}
