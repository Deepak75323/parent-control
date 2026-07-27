import {
  AppAlertButton,
  useAppDialogStore,
} from '../store/appDialogStore';

/**
 * Drop-in replacement for React Native `Alert.alert` with branded UI.
 * Simple success acknowledgements become soft toasts; everything else uses
 * the bottom-sheet dialog hosted by `AppDialogHost`.
 */
export function appAlert(
  title: string,
  message?: string,
  buttons?: AppAlertButton[],
): void {
  const btns = buttons?.length ? buttons : [{text: 'OK' as const}];
  const onlyAck =
    btns.length === 1 &&
    (btns[0].style == null || btns[0].style === 'default');

  if (onlyAck && isSoftSuccess(title, message)) {
    useAppDialogStore.getState().showToast({
      title,
      message,
      tone: 'success',
    });
    btns[0].onPress?.();
    return;
  }

  if (onlyAck && isSoftError(title)) {
    useAppDialogStore.getState().showToast({
      title,
      message,
      tone: 'error',
    });
    btns[0].onPress?.();
    return;
  }

  useAppDialogStore.getState().showDialog({
    title,
    message,
    buttons: btns,
  });
}

/** Exported for unit tests. */
export function isSoftSuccess(title: string, message?: string): boolean {
  const t = title.trim();
  const m = (message ?? '').trim();
  return (
    /^(sent|success|done|saved|updated|ready|ok)$/i.test(t) ||
    /command sent/i.test(m) ||
    /successfully/i.test(m)
  );
}

/** Exported for unit tests. */
export function isSoftError(title: string): boolean {
  return /^(error|failed|couldn't|couldn’t|unable)$/i.test(title.trim());
}
