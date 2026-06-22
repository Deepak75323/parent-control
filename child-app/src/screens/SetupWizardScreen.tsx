import React from 'react';
import {View, Text, StyleSheet} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {ChildStackParamList} from './RootNavigator';
import {
  Card,
  HeroIconBlock,
  NoteCard,
  PrimaryBtn,
  ScreenShell,
  SectionTitle,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<ChildStackParamList, 'SetupWizard'>;

const SETUP_STEPS = [
  {icon: '🔐', title: 'Grant permissions', detail: 'Notifications, accessibility, overlay, battery, screen capture, and more'},
  {icon: '🔗', title: 'Pair with parent', detail: 'Enter the code from the parent app on this device'},
  {icon: '🛡️', title: 'Start monitoring', detail: 'Runs safely in the background after setup'},
] as const;

export function SetupWizardScreen({navigation}: Props) {
  return (
    <ScreenShell>
      <HeroIconBlock
        icon="📱"
        title="Device Monitor"
        subtitle="Set up this phone for lawful parental monitoring. A parent must pair their account and you will grant the required permissions."
      />

      <Card>
        <SectionTitle>What happens next</SectionTitle>
        {SETUP_STEPS.map((step, index) => (
          <View key={step.title}>
            {index > 0 ? <View style={styles.stepDivider} /> : null}
            <View style={styles.stepRow}>
              <View style={styles.stepIcon}>
                <Text style={styles.stepEmoji}>{step.icon}</Text>
              </View>
              <View style={styles.stepText}>
                <Text style={styles.stepTitle}>{step.title}</Text>
                <Text style={styles.stepDetail}>{step.detail}</Text>
              </View>
              <View style={styles.stepBadge}>
                <Text style={styles.stepBadgeText}>{index + 1}</Text>
              </View>
            </View>
          </View>
        ))}
      </Card>

      <NoteCard
        title="Before you continue"
        body="Keep this phone on Wi‑Fi (same network as the parent's device) during pairing. Setup takes about 5 minutes."
      />

      <PrimaryBtn title="Get started" onPress={() => navigation.navigate('PermissionSetup')} />

      <Text style={styles.footer}>
        By continuing, you confirm monitoring is enabled with parent consent.
      </Text>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  stepRow: {flexDirection: 'row', alignItems: 'center', paddingVertical: 10},
  stepIcon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    backgroundColor: '#f1f5f9',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  stepEmoji: {fontSize: 18},
  stepText: {flex: 1},
  stepTitle: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  stepDetail: {fontSize: 12, color: '#94a3b8', marginTop: 2, lineHeight: 17},
  stepBadge: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: '#eff6ff',
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8,
  },
  stepBadgeText: {fontSize: 12, fontWeight: '700', color: '#2563eb'},
  stepDivider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: '#e2e8f0',
    marginLeft: 52,
  },
  footer: {
    textAlign: 'center',
    fontSize: 11,
    color: '#94a3b8',
    lineHeight: 16,
    paddingHorizontal: 8,
    marginTop: 4,
  },
});
