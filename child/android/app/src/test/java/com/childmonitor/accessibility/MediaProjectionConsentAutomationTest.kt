package com.childmonitor.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [MediaProjectionConsentAutomation.looksLikeMediaProjectionContent].
 *
 * The method under test is pure (List<String> → Boolean) and requires no Android
 * framework, so these tests run on the JVM without Robolectric.
 *
 * OEM coverage (true-positive sections):
 *   · AOSP / Google Pixel  (Android 13, 14, 15, 16)
 *   · Samsung One UI 5/6/7 (Android 13–16)
 *   · Xiaomi / MIUI 14 / HyperOS 1-2 (Android 13–16, English + Chinese)
 *   · vivo / iQOO — Funtouch OS / OriginOS (Android 13–16, English + Chinese)
 *   · OPPO / realme / OnePlus — ColorOS 13/14/15 (Android 13–16, English + Chinese)
 *   · Motorola (Android 13–16)
 *   · Nothing Phone (Android 14–16)
 *
 * False-positive coverage (these must NEVER be auto-clicked):
 *   · Camera, location, microphone, notification, storage, contacts permissions
 *   · Battery optimisation, overlay permission
 *   · Generic OK / Accept / Agree / alert dialogs
 *   · "Screen recording" notification toast (no approve button in APPROVE_TEXTS)
 *   · Chromecast / Cast dialogs
 */
@RunWith(JUnit4::class)
class MediaProjectionConsentAutomationTest {

    private fun detect(vararg texts: String): Boolean =
        MediaProjectionConsentAutomation.looksLikeMediaProjectionContent(texts.toList())

    // =========================================================================
    // TRUE POSITIVES — AOSP / Google Pixel
    // =========================================================================

    @Test
    fun pixel_android14_startRecordingOrCasting_detected() {
        // Android 14-16 Pixel: "Start recording or casting with Child Monitor?"
        assertTrue(detect("Start recording or casting with Child Monitor?", "Start now", "Cancel"))
    }

    @Test
    fun pixel_android14_willHaveAccessToEverything_detected() {
        // Android 14-16 body text appearing alongside the dialog title
        assertTrue(detect(
            "Start recording or casting with Child Monitor?",
            "Child Monitor will have access to everything on your screen.",
            "Start now", "Cancel",
        ))
    }

    @Test
    fun pixel_android13_recordYourScreen_detected() {
        // Android 13 AOSP: "Allow <App> to record your screen?"
        assertTrue(detect("Allow Child Monitor to record your screen?", "Start now", "Cancel"))
    }

    @Test
    fun pixel_android13_captureYourScreen_detected() {
        // AOSP variant: "Allow <App> to capture your screen?"
        assertTrue(detect("Allow Child Monitor to capture your screen?", "Start now", "Cancel"))
    }

    @Test
    fun aosp_pre14_castYourScreen_detected() {
        // Android 12-: "Would you like to cast your screen with <App>?"
        assertTrue(detect("Would you like to cast your screen with Child Monitor?", "Start now", "Cancel"))
    }

    @Test
    fun aosp_willStartCapturing_detected() {
        // AOSP variant: "Child Monitor will start capturing everything on your screen."
        assertTrue(detect("Child Monitor will start capturing everything on your screen.", "Start now", "Cancel"))
    }

    @Test
    fun aosp_olderVariant_recordingOrCasting_allow_detected() {
        // Older AOSP "recording or casting" phrasing
        assertTrue(detect("Recording or casting in progress with Child Monitor", "Allow", "Deny"))
    }

    @Test
    fun aosp_shareEntireScreen_detected() {
        // AOSP / stock share entire screen
        assertTrue(detect("Share entire screen", "Share screen", "Cancel"))
    }

    // =========================================================================
    // TRUE POSITIVES — Samsung One UI 5 / 6 / 7
    // =========================================================================

    @Test
    fun samsung_screenshotOrScreenRecord_startNow_detected() {
        // One UI consent bottom-sheet title (One UI 5/6)
        assertTrue(detect("Screenshot or screen record", "Start now", "Cancel"))
    }

    @Test
    fun samsung_startScreenRecording_allow_detected() {
        // One UI alternative title
        assertTrue(detect("Start screen recording", "Allow", "Deny"))
    }

    @Test
    fun samsung_screenRecording_startNow_detected() {
        // Samsung uses "Screen recording" as dialog header in some One UI builds
        assertTrue(detect("Screen recording", "Start now", "Cancel"))
    }

    @Test
    fun samsung_oneUi7_shareYourScreen_detected() {
        // One UI 7 consent phrasing: "Share your screen with <App>?"
        assertTrue(detect("Share your screen with Child Monitor?", "Start now", "Cancel"))
    }

    @Test
    fun samsung_android14_standardAosp_detected() {
        // One UI 7 on Android 14+ also honours standard AOSP phrasing
        assertTrue(detect("Start recording or casting with Child Monitor?", "Start now", "Cancel"))
    }

    // =========================================================================
    // TRUE POSITIVES — Xiaomi / Redmi / POCO (MIUI 14 & HyperOS 1/2)
    // =========================================================================

    @Test
    fun miui_startCapture_startNow_detected() {
        // MIUI English MediaProjection consent
        assertTrue(detect("Start capture", "Start now", "Cancel"))
    }

    @Test
    fun miui_captureEverything_allow_detected() {
        assertTrue(detect("Capture everything on your screen", "Allow", "Deny"))
    }

    @Test
    fun miui_screenRecording_startNow_detected() {
        // MIUI English "Screen recording" dialog header
        assertTrue(detect("Screen recording", "Start now", "Cancel"))
    }

    @Test
    fun miui_chineseCapture_lijijiKaishi_detected() {
        // MIUI Chinese: 屏幕捕获 + 立即开始
        assertTrue(detect("屏幕捕获", "立即开始", "取消"))
    }

    @Test
    fun miui_chineseRecordPermission_allow_detected() {
        // HyperOS: 录制屏幕权限 + 允许
        assertTrue(detect("录制屏幕权限", "允许", "拒绝"))
    }

    @Test
    fun miui_chineseStartRecordingScreen_confirm_detected() {
        // Older MIUI: 开始录制屏幕 + 确认
        assertTrue(detect("开始录制屏幕", "确认", "取消"))
    }

    @Test
    fun miui_chineseScreenRecordingNoun_allow_detected() {
        // HyperOS noun form: 屏幕录制 + 允许
        assertTrue(detect("屏幕录制", "允许", "取消"))
    }

    @Test
    fun miui_chineseRecordScreen_verb_ok_detected() {
        // Verb form: 录制屏幕 + 确定
        assertTrue(detect("录制屏幕", "确定", "取消"))
    }

    @Test
    fun miui_chineseCapture_agree_detected() {
        // 屏幕捕获 + 同意
        assertTrue(detect("屏幕捕获", "同意", "拒绝"))
    }

    // =========================================================================
    // TRUE POSITIVES — vivo / iQOO (Funtouch OS / OriginOS)
    // =========================================================================

    @Test
    fun vivo_screenRecording_allow_detected() {
        // Funtouch OS English consent
        assertTrue(detect("Screen recording", "Allow", "Deny"))
    }

    @Test
    fun vivo_chineseRecordScreen_allow_detected() {
        // vivo Chinese: 录制屏幕 + 允许
        assertTrue(detect("录制屏幕", "允许", "拒绝"))
    }

    @Test
    fun vivo_chineseScreenRecordingNoun_agree_detected() {
        // vivo alternative: 屏幕录制 + 同意
        assertTrue(detect("屏幕录制", "同意", "取消"))
    }

    @Test
    fun iqoo_android14_aosp_detected() {
        // iQOO on Android 14 uses AOSP phrasing
        assertTrue(detect("Start recording or casting with Child Monitor?", "Start now", "Cancel"))
    }

    // =========================================================================
    // TRUE POSITIVES — OPPO / ColorOS 13-15
    // =========================================================================

    @Test
    fun oppo_screenRecording_agree_detected() {
        // ColorOS English approve button is "Agree"
        assertTrue(detect("Screen recording", "Agree", "Cancel"))
    }

    @Test
    fun oppo_shareYourScreen_allow_detected() {
        // ColorOS: "Share your screen with <App>?"
        assertTrue(detect("Share your screen with Child Monitor?", "Allow", "Deny"))
    }

    @Test
    fun oppo_chineseScreenRecording_confirm_detected() {
        // OPPO ColorOS Chinese: 屏幕录制 + 确认
        assertTrue(detect("屏幕录制", "确认", "取消"))
    }

    @Test
    fun oppo_chineseScreenSharing_agree_detected() {
        // ColorOS Chinese screen sharing: 屏幕共享 + 同意
        assertTrue(detect("屏幕共享", "同意", "拒绝"))
    }

    @Test
    fun oppo_chineseRecordScreen_ok_detected() {
        // OPPO ColorOS: 录制屏幕 + 确定
        assertTrue(detect("录制屏幕", "确定", "取消"))
    }

    // =========================================================================
    // TRUE POSITIVES — realme (realme UI / ColorOS base)
    // =========================================================================

    @Test
    fun realme_screenRecording_allow_detected() {
        assertTrue(detect("Screen recording", "Allow", "Deny"))
    }

    @Test
    fun realme_chineseRecordScreen_allow_detected() {
        // realme Chinese: 录制屏幕 + 允许
        assertTrue(detect("录制屏幕", "允许", "取消"))
    }

    @Test
    fun realme_chineseScreenSharing_agree_detected() {
        assertTrue(detect("屏幕共享", "同意", "取消"))
    }

    // =========================================================================
    // TRUE POSITIVES — OnePlus (OxygenOS 14+ / ColorOS base)
    // =========================================================================

    @Test
    fun oneplus_oxygenOs14_shareYourScreen_detected() {
        // OxygenOS 14 (ColorOS): "Share your screen with <App>?"
        assertTrue(detect("Share your screen with Child Monitor?", "Start now", "Cancel"))
    }

    @Test
    fun oneplus_chineseScreenSharing_startNow_detected() {
        // OnePlus ColorOS Chinese: 屏幕共享 + 确认
        assertTrue(detect("屏幕共享", "确认", "取消"))
    }

    @Test
    fun oneplus_android14_aosp_detected() {
        // OxygenOS 14 on Android 14 also uses AOSP phrasing
        assertTrue(detect("Start recording or casting with Child Monitor?", "Start now", "Cancel"))
    }

    // =========================================================================
    // TRUE POSITIVES — Motorola (near-stock Android)
    // =========================================================================

    @Test
    fun motorola_android13_recordYourScreen_detected() {
        assertTrue(detect("Allow Child Monitor to record your screen?", "Start now", "Cancel"))
    }

    @Test
    fun motorola_android14_startRecordingOrCasting_detected() {
        assertTrue(detect("Start recording or casting with Child Monitor?", "Start now", "Cancel"))
    }

    // =========================================================================
    // TRUE POSITIVES — Nothing Phone (near-stock Android 14-16)
    // =========================================================================

    @Test
    fun nothing_android14_willHaveAccessToEverything_detected() {
        assertTrue(detect(
            "Start recording or casting with Child Monitor?",
            "Child Monitor will have access to everything on your screen.",
            "Start now", "Cancel",
        ))
    }

    // =========================================================================
    // TRUE POSITIVES — cross-OEM / generic cases
    // =========================================================================

    @Test
    fun titleAndButtonInSameString_detected() {
        // Some OEMs put everything in a single TextView
        assertTrue(detect("Recording or casting – Start now"))
    }

    @Test
    fun approveTextContainsMatch_longLabel_detected() {
        // "share screen" (len 12 ≥ 5) inside a longer button label
        assertTrue(detect("Start recording or casting", "Share screen now", "Cancel"))
    }

    @Test
    fun caseInsensitive_detected() {
        assertTrue(detect("START RECORDING OR CASTING", "START NOW"))
        assertTrue(detect("start recording or casting", "start now"))
    }

    @Test
    fun android16_newPhrasing_matchesByContains() {
        // Future Android versions may add new wording containing a known hint
        assertTrue(detect("Your screen will be captured – recording or casting begins now", "Start now"))
    }

    @Test
    fun samsungShareEntireScreen_startNow_detected() {
        assertTrue(detect("Share entire screen", "Start now", "Cancel"))
    }

    @Test
    fun captureEverything_startCapture_detected() {
        assertTrue(detect("Capture everything on your screen", "Start capture", "Cancel"))
    }

    @Test
    fun veryLongDialogText_detected() {
        val body = "Your screen will be shared. This app will start recording or casting " +
            "everything that appears on your screen, including notifications and other data."
        assertTrue(detect(body, "Start now"))
    }

    // =========================================================================
    // FALSE POSITIVES — dialogs that must NEVER be auto-clicked
    // =========================================================================

    @Test
    fun cameraPermissionDialog_notDetected() {
        assertFalse(detect("Allow access to camera", "Allow", "Deny"))
    }

    @Test
    fun locationPermissionDialog_notDetected() {
        assertFalse(detect("Allow this app to access your location", "Allow while using the app", "Deny"))
    }

    @Test
    fun batteryOptimisationDialog_notDetected() {
        assertFalse(detect("Allow background activity", "OK", "Cancel"))
    }

    @Test
    fun overlayPermissionDialog_notDetected() {
        // "allow display over other apps" is NOT in DIALOG_TITLE_HINTS
        assertFalse(detect("Allow display over other apps", "Allow", "Deny"))
    }

    @Test
    fun microphonePermissionDialog_notDetected() {
        assertFalse(detect("Allow access to microphone", "Allow", "Deny"))
    }

    @Test
    fun notificationPermissionDialog_notDetected() {
        assertFalse(detect("Allow notifications", "Allow", "Don't allow"))
    }

    @Test
    fun contactsPermissionDialog_notDetected() {
        assertFalse(detect("Allow access to contacts", "Allow", "Deny"))
    }

    @Test
    fun storagePermissionDialog_notDetected() {
        assertFalse(detect("Allow access to files", "Allow", "Deny"))
    }

    @Test
    fun genericOkDialog_notDetected() {
        assertFalse(detect("Something happened", "OK"))
    }

    @Test
    fun genericAcceptDialog_notDetected() {
        assertFalse(detect("Terms of service", "Accept", "Decline"))
    }

    @Test
    fun genericAgreeDialog_notDetected() {
        // "agree" is in APPROVE_TEXTS, but a lone agree button with no MediaProjection title
        assertFalse(detect("Privacy policy update", "Agree", "Decline"))
    }

    @Test
    fun screenRecordingNotification_stopShare_notDetected() {
        // "Screen recording" IS now in DIALOG_TITLE_HINTS (for OEM support), but
        // "Stop" and "Share" alone are NOT in APPROVE_TEXTS, so AND logic prevents the click.
        assertFalse(detect("Screen recording – tap to stop", "Stop", "Share"))
    }

    @Test
    fun screenRecordingNotification_stopOnly_notDetected() {
        assertFalse(detect("Screen recording active", "Stop"))
    }

    @Test
    fun castYourScreenToDevice_notDetected() {
        // Chromecast system dialog: "Cast your screen to [Device]?" with "Cast" button.
        // "cast your screen" matches a title hint, but "Cast" alone is NOT in APPROVE_TEXTS.
        assertFalse(detect("Cast your screen to Living Room TV?", "Cast", "Cancel"))
    }

    @Test
    fun titleOnlyNoApproveButton_notDetected() {
        // Title hint present but no approve text in the window — must not click
        assertFalse(detect("This app will have access to everything on your screen."))
    }

    @Test
    fun approveButtonOnlyNoTitle_notDetected() {
        // Approve button text present but no MediaProjection title — must not click
        assertFalse(detect("Start now"))
        assertFalse(detect("Allow"))
        assertFalse(detect("OK"))
        assertFalse(detect("Share screen"))
        assertFalse(detect("Agree"))
    }

    @Test
    fun chineseApproveOnlyNoTitle_notDetected() {
        assertFalse(detect("允许"))
        assertFalse(detect("确定"))
        assertFalse(detect("同意"))
        assertFalse(detect("立即开始"))
    }

    @Test
    fun chineseDenyOnly_notDetected() {
        assertFalse(detect("取消"))
        assertFalse(detect("拒绝"))
    }

    @Test
    fun emptyInput_notDetected() {
        assertFalse(detect())
        assertFalse(detect(""))
        assertFalse(detect("   "))
    }

    // =========================================================================
    // EDGE CASES — boundary / adversarial inputs
    // =========================================================================

    @Test
    fun partialTitleMatch_withNoButton_notDetected() {
        // "casting" without an approve button label and without a full title hint
        assertFalse(detect("broadcasting and casting information"))
    }

    @Test
    fun titleWithDenyWordInParentheses_detectsCleanButton() {
        // Deny word in parentheses inside the title does not prevent detection when
        // the approve button text is clean (tested at text-matching layer only).
        assertTrue(detect("Start recording or casting (cancel anytime)", "Start now"))
    }

    @Test
    fun shortCircuitsOnFirstMatchPair() {
        val texts = listOf(
            "Start recording or casting",
            "Start now",
            "This extra text should never be needed",
        )
        assertTrue(MediaProjectionConsentAutomation.looksLikeMediaProjectionContent(texts))
    }

    @Test
    fun miuiChineseScreenCapture_agreePlusCancel_detected() {
        // Cancel word is present but there is also a valid approve text
        assertTrue(detect("屏幕捕获", "同意", "取消"))
    }
}
