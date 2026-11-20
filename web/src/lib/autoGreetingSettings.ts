export type AutoGreetingFormInput = {
  enabled: boolean;
  roomId: string;
  morningStart: string;
  morningEnd: string;
  eveningStart: string;
  eveningEnd: string;
  morningMessage: string;
  eveningMessage: string;
};

const HH_MM = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Returns an error message, or null when the form is valid. */
export function validateAutoGreetingForm(input: AutoGreetingFormInput): string | null {
  if (!input.enabled) {
    return null;
  }
  if (!input.roomId.trim()) {
    return 'Select a chat room before enabling auto greetings';
  }
  if (!input.morningMessage.trim()) {
    return 'Enter a morning message';
  }
  if (!input.eveningMessage.trim()) {
    return 'Enter an evening message';
  }
  for (const [label, value] of [
    ['Morning start', input.morningStart],
    ['Morning end', input.morningEnd],
    ['Evening start', input.eveningStart],
    ['Evening end', input.eveningEnd],
  ] as const) {
    if (!HH_MM.test(value.trim())) {
      return `${label} must be HH:mm`;
    }
  }
  return null;
}
