import React, {useCallback, useEffect, useState} from 'react';
import {View, Text, StyleSheet, Pressable, Switch, ActivityIndicator} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useAuthStore} from '../store/authStore';
import {RootStackParamList} from '../navigation/RootNavigator';
import {Card, HeroBlock, MenuRow, ScreenShell} from '../components/AppScreen';
import {readReceiptsApi, sharePresenceApi} from '../services/api';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;

export function SettingsScreen({navigation}: Props) {
  const logout = useAuthStore(s => s.logout);
  const [readReceiptsEnabled, setReadReceiptsEnabled] = useState(true);
  const [receiptsLoading, setReceiptsLoading] = useState(true);
  const [receiptsBusy, setReceiptsBusy] = useState(false);
  const [sharePresenceEnabled, setSharePresenceEnabled] = useState(true);
  const [presenceLoading, setPresenceLoading] = useState(true);
  const [presenceBusy, setPresenceBusy] = useState(false);

  const loadReadReceipts = useCallback(async () => {
    setReceiptsLoading(true);
    try {
      const {data} = await readReceiptsApi.status();
      setReadReceiptsEnabled(data.data?.enabled !== false);
    } catch {
      // Keep default On if status fails — user can still retry via toggle.
    } finally {
      setReceiptsLoading(false);
    }
  }, []);

  const loadSharePresence = useCallback(async () => {
    setPresenceLoading(true);
    try {
      const {data} = await sharePresenceApi.status();
      setSharePresenceEnabled(data.data?.enabled !== false);
    } catch {
      // Keep default On if status fails.
    } finally {
      setPresenceLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadReadReceipts();
    void loadSharePresence();
  }, [loadReadReceipts, loadSharePresence]);

  const onToggleReadReceipts = async (next: boolean) => {
    if (receiptsBusy) {
      return;
    }
    const previous = readReceiptsEnabled;
    setReadReceiptsEnabled(next);
    setReceiptsBusy(true);
    try {
      const {data} = await readReceiptsApi.setEnabled(next);
      setReadReceiptsEnabled(data.data?.enabled !== false);
    } catch {
      setReadReceiptsEnabled(previous);
      appAlert('Couldn’t update', 'Check your connection and try again.');
    } finally {
      setReceiptsBusy(false);
    }
  };

  const onToggleSharePresence = async (next: boolean) => {
    if (presenceBusy) {
      return;
    }
    const previous = sharePresenceEnabled;
    setSharePresenceEnabled(next);
    setPresenceBusy(true);
    try {
      const {data} = await sharePresenceApi.setEnabled(next);
      setSharePresenceEnabled(data.data?.enabled !== false);
    } catch {
      setSharePresenceEnabled(previous);
      appAlert('Couldn’t update', 'Check your connection and try again.');
    } finally {
      setPresenceBusy(false);
    }
  };

  return (
    <ScreenShell backLabel="← Back to dashboard" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Account"
        title="Settings"
        subtitle="Manage security, billing, and account activity."
      />
      <Card style={styles.menuCard}>
        <MenuRow
          icon="@"
          title="Family handle"
          subtitle="Unique ID children use to find you"
          onPress={() => navigation.navigate('FamilyHandle')}
        />
        <MenuRow
          icon="🔑"
          title="Child login password"
          subtitle="Same password on every child phone"
          onPress={() => navigation.navigate('ChildLoginPassword')}
        />
        <MenuRow
          icon="🔒"
          title="Parent password"
          subtitle="Protects screen sharing and sensitive actions"
          onPress={() => navigation.navigate('ParentPin')}
        />
        <View style={[styles.toggleRow, styles.toggleRowBorder]}>
          <View style={styles.toggleIconWrap}>
            <Text style={styles.toggleIcon}>✓✓</Text>
          </View>
          <View style={styles.toggleText}>
            <Text style={styles.toggleTitle}>Read receipts</Text>
            <Text style={styles.toggleSub}>
              {readReceiptsEnabled
                ? 'You and others see green ticks when messages are read'
                : 'Green read ticks hidden for you and others'}
            </Text>
          </View>
          {receiptsLoading ? (
            <ActivityIndicator />
          ) : (
            <Switch
              value={readReceiptsEnabled}
              disabled={receiptsBusy}
              onValueChange={value => void onToggleReadReceipts(value)}
            />
          )}
        </View>
        <View style={[styles.toggleRow, styles.toggleRowBorder]}>
          <View style={styles.toggleIconWrap}>
            <Text style={styles.toggleIcon}>🟢</Text>
          </View>
          <View style={styles.toggleText}>
            <Text style={styles.toggleTitle}>Share presence</Text>
            <Text style={styles.toggleSub}>
              {sharePresenceEnabled
                ? 'Partner can see Online / Last seen in 2-person chats'
                : 'Online / Last seen hidden from chat partners'}
            </Text>
          </View>
          {presenceLoading ? (
            <ActivityIndicator />
          ) : (
            <Switch
              value={sharePresenceEnabled}
              disabled={presenceBusy}
              onValueChange={value => void onToggleSharePresence(value)}
            />
          )}
        </View>
        <MenuRow
          icon="💳"
          title="Subscription"
          subtitle="Plan, trial, and upgrades"
          onPress={() => navigation.navigate('Subscription')}
        />
        <MenuRow
          icon="📋"
          title="Audit log"
          subtitle="Recent account activity"
          onPress={() => navigation.navigate('AuditLog')}
        />
        <MenuRow
          icon="💬"
          title="Support"
          subtitle="Help, correlation ID, contact email"
          onPress={() => navigation.navigate('Support')}
        />
        <MenuRow
          icon="🗑️"
          title="Delete account"
          subtitle="Permanently remove your data"
          onPress={() => navigation.navigate('DeleteAccount')}
          last
        />
      </Card>
      <Pressable
        style={({pressed}) => [styles.signOut, pressed && styles.signOutPressed]}
        onPress={() => {
          void logout();
        }}>
        <Text style={styles.signOutText}>Sign out</Text>
      </Pressable>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  menuCard: {paddingVertical: 4, paddingHorizontal: 12},
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    gap: 12,
  },
  toggleRowBorder: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e2e8f0',
  },
  toggleIconWrap: {
    width: 36,
    height: 36,
    borderRadius: 10,
    backgroundColor: '#eef2ff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  toggleIcon: {fontSize: 12, fontWeight: '800', color: '#4f46e5'},
  toggleText: {flex: 1},
  toggleTitle: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  toggleSub: {marginTop: 2, fontSize: 12, color: '#64748b', lineHeight: 16},
  signOut: {
    marginTop: 8,
    backgroundColor: '#fff',
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#fecaca',
  },
  signOutPressed: {opacity: 0.85},
  signOutText: {color: '#dc2626', fontWeight: '700', fontSize: 15},
});
