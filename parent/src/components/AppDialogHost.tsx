import React, {useEffect, useRef} from 'react';
import {
  Animated,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {
  AppAlertButton,
  useAppDialogStore,
} from '../store/appDialogStore';
import {Colors, Elevation, Layout, Radii, Spacing, Typography} from '../theme/design';

const TOAST_MS = 2600;

export function AppDialogHost() {
  const insets = useSafeAreaInsets();
  const dialog = useAppDialogStore(s => s.dialog);
  const toast = useAppDialogStore(s => s.toast);
  const hideDialog = useAppDialogStore(s => s.hideDialog);
  const hideToast = useAppDialogStore(s => s.hideToast);
  const toastAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!toast.visible) {
      return;
    }
    toastAnim.setValue(0);
    Animated.spring(toastAnim, {
      toValue: 1,
      useNativeDriver: true,
      friction: 8,
      tension: 80,
    }).start();
    const timer = setTimeout(() => hideToast(), TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast.token, toast.visible, hideToast, toastAnim]);

  const onButton = (btn: AppAlertButton) => {
    hideDialog();
    // Defer so the modal close animation isn't blocked by navigation/alerts.
    requestAnimationFrame(() => btn.onPress?.());
  };

  const toastTranslate = toastAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [-20, 0],
  });

  return (
    <>
      <Modal
        visible={dialog.visible}
        transparent
        animationType="fade"
        onRequestClose={hideDialog}>
        <Pressable style={styles.backdrop} onPress={hideDialog}>
          <Pressable
            style={[styles.sheet, {paddingBottom: Math.max(insets.bottom, Spacing.base) + Spacing.sm}]}
            onPress={e => e.stopPropagation()}>
            <View style={styles.handle} />
            <Text style={styles.title}>{dialog.title}</Text>
            {dialog.message ? (
              <Text style={styles.message}>{dialog.message}</Text>
            ) : null}
            <View
              style={[
                styles.actions,
                dialog.buttons.length > 2 && styles.actionsStack,
              ]}>
              {dialog.buttons.map((btn, index) => {
                const role = btn.style ?? 'default';
                const isPrimary =
                  role === 'default' &&
                  (dialog.buttons.length === 1 ||
                    index === dialog.buttons.length - 1);
                return (
                  <Pressable
                    key={`${btn.text}-${index}`}
                    style={[
                      styles.btn,
                      role === 'cancel' && styles.btnCancel,
                      role === 'destructive' && styles.btnDanger,
                      isPrimary && role === 'default' && styles.btnPrimary,
                      dialog.buttons.length > 2 && styles.btnFull,
                    ]}
                    onPress={() => onButton(btn)}>
                    <Text
                      style={[
                        styles.btnText,
                        role === 'cancel' && styles.btnTextCancel,
                        role === 'destructive' && styles.btnTextDanger,
                        isPrimary && role === 'default' && styles.btnTextPrimary,
                      ]}>
                      {btn.text}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      {toast.visible ? (
        <Animated.View
          pointerEvents="none"
          style={[
            styles.toastWrap,
            {
              top: Math.max(insets.top, Spacing.sm) + Spacing.sm,
              opacity: toastAnim,
              transform: [{translateY: toastTranslate}],
            },
          ]}>
          <View
            style={[
              styles.toast,
              toast.tone === 'success' && styles.toastSuccess,
              toast.tone === 'error' && styles.toastError,
            ]}>
            <Text style={styles.toastTitle}>{toast.title}</Text>
            {toast.message ? (
              <Text style={styles.toastMessage}>{toast.message}</Text>
            ) : null}
          </View>
        </Animated.View>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(15, 23, 42, 0.55)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: Colors.surface,
    borderTopLeftRadius: Radii.xl,
    borderTopRightRadius: Radii.xl,
    paddingHorizontal: Layout.gutter,
    paddingTop: Spacing.md,
    ...Elevation.cardHigh,
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: Colors.border,
    alignSelf: 'center',
    marginBottom: Layout.gutter,
  },
  title: {
    ...Typography.cardTitle,
    marginBottom: Spacing.sm,
  },
  message: {
    ...Typography.body,
    marginBottom: Layout.gutter,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  actionsStack: {
    flexDirection: 'column',
  },
  btn: {
    flex: 1,
    minHeight: Layout.touchTargetMin,
    borderRadius: Radii.md,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Colors.primarySurface,
    paddingHorizontal: Spacing.base,
  },
  btnFull: {
    flex: undefined,
    width: '100%',
  },
  btnCancel: {
    backgroundColor: Colors.surfaceContainer,
  },
  btnPrimary: {
    backgroundColor: Colors.primaryContainer,
    ...Elevation.cardHigh,
  },
  btnDanger: {
    backgroundColor: Colors.danger,
    shadowColor: Colors.danger,
  },
  btnText: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.primaryContainer,
  },
  btnTextCancel: {
    color: Colors.textSecondary,
  },
  btnTextPrimary: {
    color: Colors.textOnDark,
  },
  btnTextDanger: {
    color: Colors.textOnDark,
  },
  toastWrap: {
    position: 'absolute',
    left: Layout.gutter,
    right: Layout.gutter,
    zIndex: 1000,
  },
  toast: {
    backgroundColor: '#1E1B4B',
    borderRadius: Radii.lg,
    paddingHorizontal: Layout.componentInternal,
    paddingVertical: Spacing.md,
    borderLeftWidth: 4,
    borderLeftColor: Colors.primaryLight,
    ...Elevation.cardHigh,
  },
  toastSuccess: {
    borderLeftColor: Colors.success,
  },
  toastError: {
    borderLeftColor: Colors.danger,
  },
  toastTitle: {
    ...Typography.h4,
    color: Colors.textOnDark,
  },
  toastMessage: {
    ...Typography.body,
    color: Colors.textOnDarkSoft,
    marginTop: 2,
  },
});
