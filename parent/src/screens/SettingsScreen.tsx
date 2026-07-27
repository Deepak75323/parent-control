import React from 'react';
import {View, Text, StyleSheet, Pressable} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useAuthStore} from '../store/authStore';
import {RootStackParamList} from '../navigation/RootNavigator';
import {Card, HeroBlock, MenuRow, ScreenShell} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;

export function SettingsScreen({navigation}: Props) {
  const logout = useAuthStore(s => s.logout);

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
