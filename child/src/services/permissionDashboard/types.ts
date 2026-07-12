export type PermissionId =
  | 'location'
  | 'microphone'
  | 'camera'
  | 'postNotifications'
  | 'accessibility'
  | 'usageStats'
  | 'notificationAccess'
  | 'overlay'
  | 'battery'
  | 'autoStart'
  | 'deviceAdmin'
  | 'fullScreenIntent'
  | 'screenCapture';

export type PermissionKind = 'runtime' | 'special' | 'screen';

export type PermissionDef = {
  id: PermissionId;
  kind: PermissionKind;
  label: string;
  explanation: string;
  /** Shown before opening Settings for special permissions. */
  settingsHint?: string;
  /** User must confirm manually after returning (autostart). */
  manualConfirm?: boolean;
};

export type PermissionGroupDef = {
  id: string;
  icon: string;
  title: string;
  description: string;
  permissionIds: PermissionId[];
};

export type PermissionStatusMap = Record<PermissionId, boolean>;

export type GrantProgress = {
  phase: 'runtime' | 'special' | 'screen' | 'done';
  stepId: PermissionId | null;
  stepIndex: number;
  stepTotal: number;
  label: string;
};
