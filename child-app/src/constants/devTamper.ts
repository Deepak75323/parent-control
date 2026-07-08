/**
 * Installer admin master password — verified server-side when enabled, and locally as offline fallback.
 * Disable server-side in production via tamper.master-pin.enabled=false.
 * Keep in sync with backend TamperMasterPinProperties.pin.
 */
export const DEV_TAMPER_UNLOCK_PIN = 'Deepak@123';
