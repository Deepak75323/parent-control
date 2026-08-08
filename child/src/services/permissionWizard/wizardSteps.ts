import {nativeModules} from '../../nativeModules';
import {
  ALL_PERMISSION_ORDER,
  RUNTIME_PERMISSION_ORDER,
  SCREEN_CAPTURE_ORDER,
  SPECIAL_PERMISSION_ORDER,
} from '../permissionDashboard/catalog';
import type {PermissionId} from '../permissionDashboard/types';
import {grantSinglePermission} from '../permissionDashboard/grantOrchestrator';
import {isPermissionGranted} from './permissionChecks';
import {shouldReturnToAccessibilityForScreenCapture} from './screenCaptureWizardPolicy';

export type WizardExplainCallbacks = {
  onExplain: (id: PermissionId, title: string, body: string) => Promise<void>;
  onManualConfirm: (id: PermissionId, title: string, body: string) => Promise<void>;
};

/** Runtime dialogs first, then manual Settings permissions, then screen capture. */
export async function buildWizardStepOrder(): Promise<PermissionId[]> {
  const requiresAutoStart = await nativeModules.requiresOemBackgroundSetup();
  const special = SPECIAL_PERMISSION_ORDER.filter(
    id => id !== 'autoStart' || requiresAutoStart,
  );
  return [...RUNTIME_PERMISSION_ORDER, ...special, ...SCREEN_CAPTURE_ORDER];
}

export async function resolveResumeStep(
  savedStepId: PermissionId | null,
): Promise<PermissionId | null> {
  const order = await buildWizardStepOrder();
  if (order.length === 0) {
    return null;
  }

  const startIndex =
    savedStepId != null ? Math.max(0, order.indexOf(savedStepId)) : 0;

  for (let i = startIndex; i < order.length; i++) {
    const id = order[i];
    if (!(await isPermissionGranted(id))) {
      if (id === 'screenCapture') {
        const projectionGranted = await nativeModules.isMediaProjectionGranted();
        const accessibilityReady = await isPermissionGranted('accessibility');
        if (
          shouldReturnToAccessibilityForScreenCapture({
            mediaProjectionGranted: projectionGranted,
            accessibilityReady,
          })
        ) {
          return 'accessibility';
        }
      }
      return id;
    }
  }
  return null;
}

export async function isWizardComplete(): Promise<boolean> {
  const next = await resolveResumeStep(null);
  return next === null;
}

/** Grant one wizard step — returns true only when permission is verified granted. */
export async function grantWizardStep(
  id: PermissionId,
  callbacks: WizardExplainCallbacks,
): Promise<boolean> {
  if (await isPermissionGranted(id)) {
    return true;
  }
  const ok = await grantSinglePermission(id, callbacks);
  if (!ok) {
    return false;
  }
  return isPermissionGranted(id);
}

export function stepIndexInOrder(order: PermissionId[], id: PermissionId): number {
  const idx = order.indexOf(id);
  return idx >= 0 ? idx + 1 : 1;
}

export function wizardStepCount(order: PermissionId[]): number {
  return order.length;
}

export {ALL_PERMISSION_ORDER};
