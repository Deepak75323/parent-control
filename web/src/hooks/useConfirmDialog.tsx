import {useCallback, useState} from 'react';
import {ConfirmDialog, type ConfirmDialogProps} from '../components/ConfirmDialog';

type ConfirmOptions = Pick<
  ConfirmDialogProps,
  'title' | 'message' | 'confirmLabel' | 'cancelLabel' | 'danger'
>;

type PendingConfirm = ConfirmOptions & {
  resolve: (value: boolean) => void;
};

export function useConfirmDialog(busy = false) {
  const [pending, setPending] = useState<PendingConfirm | null>(null);

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>(resolve => {
      setPending({...options, resolve});
    });
  }, []);

  const close = useCallback((result: boolean) => {
    setPending(current => {
      current?.resolve(result);
      return null;
    });
  }, []);

  const dialog = (
    <ConfirmDialog
      open={pending != null}
      title={pending?.title ?? ''}
      message={pending?.message ?? ''}
      confirmLabel={pending?.confirmLabel}
      cancelLabel={pending?.cancelLabel}
      danger={pending?.danger}
      busy={busy}
      onConfirm={() => close(true)}
      onCancel={() => close(false)}
    />
  );

  return {confirm, dialog};
}
