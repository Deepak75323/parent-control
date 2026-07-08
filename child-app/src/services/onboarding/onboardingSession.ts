import {nativeModules} from '../../nativeModules';
import {flowLog} from '../flowLogger';
import type {PermissionId} from '../permissionDashboard/types';

export type OnboardingPersistence = {
  authenticated: boolean;
  authenticatedAt?: number;
  /** Permission wizard resumes at this step until granted. */
  currentStepId: PermissionId | null;
  /** Saved pairing code from login (used on Pairing screen). */
  pendingPairingCode?: string | null;
};

const EMPTY: OnboardingPersistence = {
  authenticated: false,
  currentStepId: null,
  pendingPairingCode: null,
};

function parse(raw: string | null): OnboardingPersistence {
  if (!raw?.trim()) {
    return {...EMPTY};
  }
  try {
    const parsed = JSON.parse(raw) as Partial<OnboardingPersistence>;
    return {
      authenticated: parsed.authenticated === true,
      authenticatedAt:
        typeof parsed.authenticatedAt === 'number' ? parsed.authenticatedAt : undefined,
      currentStepId: (parsed.currentStepId as PermissionId | null) ?? null,
      pendingPairingCode: parsed.pendingPairingCode ?? null,
    };
  } catch {
    flowLog('ONBOARDING', 'invalid wizard persistence json — resetting');
    return {...EMPTY};
  }
}

export async function loadOnboardingSession(): Promise<OnboardingPersistence> {
  const raw = await nativeModules.getWizardPersistenceState();
  return parse(raw);
}

async function save(session: OnboardingPersistence): Promise<void> {
  await nativeModules.setWizardPersistenceState(JSON.stringify(session));
}

export async function isOnboardingAuthenticated(): Promise<boolean> {
  const session = await loadOnboardingSession();
  return session.authenticated;
}

export async function markOnboardingAuthenticated(
  pendingPairingCode?: string | null,
): Promise<void> {
  const prev = await loadOnboardingSession();
  await save({
    ...prev,
    authenticated: true,
    authenticatedAt: Date.now(),
    pendingPairingCode: pendingPairingCode?.trim().toUpperCase() ?? prev.pendingPairingCode ?? null,
  });
  flowLog('ONBOARDING', 'authenticated');
}

export async function getPendingPairingCode(): Promise<string | null> {
  const session = await loadOnboardingSession();
  return session.pendingPairingCode?.trim() || null;
}

export async function setWizardStep(stepId: PermissionId | null): Promise<void> {
  const prev = await loadOnboardingSession();
  await save({...prev, currentStepId: stepId});
  flowLog('ONBOARDING', 'wizard step saved', {stepId});
}

export async function getWizardStep(): Promise<PermissionId | null> {
  const session = await loadOnboardingSession();
  return session.currentStepId;
}

export async function clearOnboardingSession(): Promise<void> {
  await nativeModules.clearWizardPersistenceState();
  flowLog('ONBOARDING', 'session cleared');
}
