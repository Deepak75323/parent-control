import {appAlert} from './appAlert';
import {useAppDialogStore} from '../store/appDialogStore';

export function showError(title: string, message: string): void {
  useAppDialogStore.getState().showToast({
    title,
    message,
    tone: 'error',
  });
}

/** Shows a visible success dialog before optional follow-up (e.g. navigate after register). */
export function showSuccessAlert(
  title: string,
  message: string,
  onContinue?: () => void,
): void {
  if (onContinue) {
    appAlert(title, message, [{text: 'Continue', onPress: onContinue}]);
    return;
  }
  useAppDialogStore.getState().showToast({
    title,
    message,
    tone: 'success',
  });
}

export function showSuccess(message: string): void {
  showSuccessAlert('Success', message);
}
