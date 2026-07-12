import {Alert, ToastAndroid} from 'react-native';

export function showError(title: string, message: string): void {
  ToastAndroid.show(`${title}: ${message}`, ToastAndroid.LONG);
  Alert.alert(title, message);
}

/** Shows a visible success dialog before optional follow-up (e.g. navigate after register). */
export function showSuccessAlert(
  title: string,
  message: string,
  onContinue?: () => void,
): void {
  ToastAndroid.show(`${title}: ${message}`, ToastAndroid.LONG);
  Alert.alert(title, message, [{text: 'Continue', onPress: onContinue}]);
}

export function showSuccess(message: string): void {
  showSuccessAlert('Success', message);
}
