import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {InstalledAppItem, monitoringApi} from '../../services/api';
import {STREAM_COLORS} from './layout';

type Props = {
  deviceId: string;
  visible: 'type' | 'launch' | null;
  onClose: () => void;
  onSendTypeText: (text: string) => void;
  onSendLaunchApp: (packageName: string) => void;
};

export function RemoteControlActionModals({
  deviceId,
  visible,
  onClose,
  onSendTypeText,
  onSendLaunchApp,
}: Props) {
  const [text, setText] = useState('');
  const [apps, setApps] = useState<InstalledAppItem[]>([]);
  const [loadingApps, setLoadingApps] = useState(false);
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (visible !== 'launch') return;
    setLoadingApps(true);
    monitoringApi
      .installedApps(deviceId)
      .then(res => setApps(res.data.data ?? []))
      .catch(() => setApps([]))
      .finally(() => setLoadingApps(false));
  }, [deviceId, visible]);

  const filteredApps = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return apps.slice(0, 80);
    return apps
      .filter(
        a => a.appLabel.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q),
      )
      .slice(0, 80);
  }, [apps, search]);

  const submitText = useCallback(() => {
    const trimmed = text.trim();
    if (!trimmed) return;
    onSendTypeText(trimmed);
    setText('');
    onClose();
  }, [text, onSendTypeText, onClose]);

  return (
    <>
      <Modal visible={visible === 'type'} animationType="slide" transparent onRequestClose={onClose}>
        <Pressable style={styles.backdrop} onPress={onClose}>
          <Pressable style={styles.sheet} onPress={() => undefined}>
            <Text style={styles.title}>Type on child screen</Text>
            <Text style={styles.subtitle}>
              Text is inserted into the focused field on the child phone (search box, chat, etc.).
            </Text>
            <TextInput
              style={styles.input}
              value={text}
              onChangeText={setText}
              placeholder="Message to type…"
              placeholderTextColor="#64748b"
              autoFocus
              multiline
            />
            <View style={styles.row}>
              <Pressable style={styles.secondaryBtn} onPress={onClose}>
                <Text style={styles.secondaryBtnText}>Cancel</Text>
              </Pressable>
              <Pressable style={styles.primaryBtn} onPress={submitText}>
                <Text style={styles.primaryBtnText}>Send</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      <Modal visible={visible === 'launch'} animationType="slide" transparent onRequestClose={onClose}>
        <Pressable style={styles.backdrop} onPress={onClose}>
          <Pressable style={[styles.sheet, styles.tallSheet]} onPress={() => undefined}>
            <Text style={styles.title}>Open app on child phone</Text>
            <TextInput
              style={styles.search}
              value={search}
              onChangeText={setSearch}
              placeholder="Search apps…"
              placeholderTextColor="#64748b"
              autoFocus
            />
            {loadingApps ? (
              <ActivityIndicator color="#38bdf8" style={styles.loader} />
            ) : (
              <FlatList
                data={filteredApps}
                keyExtractor={item => item.packageName}
                keyboardShouldPersistTaps="handled"
                ListEmptyComponent={
                  <Text style={styles.empty}>No apps found — sync apps from the child device first.</Text>
                }
                renderItem={({item}) => (
                  <Pressable
                    style={styles.appRow}
                    onPress={() => {
                      onSendLaunchApp(item.packageName);
                      onClose();
                    }}>
                    <Text style={styles.appLabel}>{item.appLabel}</Text>
                    <Text style={styles.appPkg} numberOfLines={1}>
                      {item.packageName}
                    </Text>
                  </Pressable>
                )}
              />
            )}
            <Pressable style={styles.secondaryBtn} onPress={onClose}>
              <Text style={styles.secondaryBtnText}>Cancel</Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: '#0f172a',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    paddingBottom: 28,
    maxHeight: '55%',
  },
  tallSheet: {maxHeight: '75%'},
  title: {color: STREAM_COLORS.text, fontSize: 18, fontWeight: '700'},
  subtitle: {color: STREAM_COLORS.textMuted, fontSize: 13, marginTop: 8, lineHeight: 18},
  input: {
    marginTop: 16,
    minHeight: 88,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
    padding: 12,
    color: STREAM_COLORS.text,
    fontSize: 16,
    textAlignVertical: 'top',
  },
  search: {
    marginTop: 12,
    marginBottom: 8,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: STREAM_COLORS.text,
    fontSize: 15,
  },
  row: {flexDirection: 'row', justifyContent: 'flex-end', gap: 10, marginTop: 16},
  primaryBtn: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 12,
  },
  primaryBtnText: {color: '#fff', fontWeight: '700'},
  secondaryBtn: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 8,
  },
  secondaryBtnText: {color: STREAM_COLORS.textMuted, fontWeight: '600'},
  loader: {marginVertical: 24},
  empty: {color: STREAM_COLORS.textMuted, textAlign: 'center', padding: 24},
  appRow: {
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#334155',
  },
  appLabel: {color: STREAM_COLORS.text, fontSize: 15, fontWeight: '600'},
  appPkg: {color: STREAM_COLORS.textMuted, fontSize: 12, marginTop: 2},
});
