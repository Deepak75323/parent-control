import {create} from 'zustand';

export type AppAlertButton = {
  text: string;
  onPress?: () => void;
  style?: 'default' | 'cancel' | 'destructive';
};

export type AppToastTone = 'success' | 'error' | 'info';

type DialogState = {
  visible: boolean;
  title: string;
  message?: string;
  buttons: AppAlertButton[];
};

type ToastState = {
  visible: boolean;
  title: string;
  message?: string;
  tone: AppToastTone;
  token: number;
};

type AppDialogStore = {
  dialog: DialogState;
  toast: ToastState;
  showDialog: (input: {
    title: string;
    message?: string;
    buttons?: AppAlertButton[];
  }) => void;
  hideDialog: () => void;
  showToast: (input: {
    title: string;
    message?: string;
    tone?: AppToastTone;
  }) => void;
  hideToast: () => void;
};

const idleDialog: DialogState = {
  visible: false,
  title: '',
  message: undefined,
  buttons: [{text: 'OK'}],
};

const idleToast: ToastState = {
  visible: false,
  title: '',
  message: undefined,
  tone: 'info',
  token: 0,
};

export const useAppDialogStore = create<AppDialogStore>(set => ({
  dialog: idleDialog,
  toast: idleToast,
  showDialog: ({title, message, buttons}) =>
    set({
      dialog: {
        visible: true,
        title,
        message,
        buttons: buttons?.length ? buttons : [{text: 'OK'}],
      },
    }),
  hideDialog: () => set(state => ({dialog: {...state.dialog, visible: false}})),
  showToast: ({title, message, tone = 'info'}) =>
    set(state => ({
      toast: {
        visible: true,
        title,
        message,
        tone,
        token: state.toast.token + 1,
      },
    })),
  hideToast: () => set(state => ({toast: {...state.toast, visible: false}})),
}));
