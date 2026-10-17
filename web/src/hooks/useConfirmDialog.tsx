import {useCallback, useState} from 'react';
import {ConfirmDialog, type ConfirmDialogProps} from '../components/ConfirmDialog';

type ConfirmOptions = Pick<
  ConfirmDialogProps,
  'title' | 'message' | 'confirmLabel' | 'cancelLabel' | 'danger' | 'hideCancel'
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

  /** Single-button info/success prompt (same chrome as confirm). */
  const alert = useCallback((options: Omit<ConfirmOptions, 'hideCancel' | 'cancelLabel' | 'danger'>) => {
    return confirm({
      ...options,
      confirmLabel: options.confirmLabel ?? 'OK',
      hideCancel: true,
    });
  }, [confirm]);

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
      hideCancel={pending?.hideCancel}
      danger={pending?.danger}
      busy={busy}
      onConfirm={() => close(true)}
      onCancel={() => close(false)}
    />
  );

  return {confirm, alert, dialog};
}
