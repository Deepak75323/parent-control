import React, {useMemo, useState} from 'react';
import {
  Linking,
  Pressable,
  Share,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {Card, HeroBlock, ScreenShell} from '../components/AppScreen';
import {getCorrelationId} from '../utils/correlation';
import {config} from '../utils/config';
import {track} from '../services/telemetry';
import {TelemetryEvents} from '../services/telemetryEvents';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'Support'>;

const SUPPORT_EMAIL =
  process.env.SUPPORT_EMAIL?.trim() || 'support@example.com';

export function SupportScreen({navigation}: Props) {
  const correlationId = useMemo(() => getCorrelationId(), []);
  const [shared, setShared] = useState(false);

  const shareCorrelation = async () => {
    try {
      await Share.share({message: correlationId, title: 'Support correlation ID'});
      setShared(true);
      track(TelemetryEvents.SUPPORT_CORRELATION_COPIED);
    } catch {
      appAlert('Correlation ID', correlationId);
    }
  };

  const emailSupport = async () => {
    const subject = encodeURIComponent('Parent app support');
    const body = encodeURIComponent(
      [
        'Please describe your issue below:',
        '',
        '',
        '---',
        `Correlation ID: ${correlationId}`,
        `API: ${config.apiBaseUrl}`,
        'App: Clock (Android)',
      ].join('\n'),
    );
    const url = `mailto:${SUPPORT_EMAIL}?subject=${subject}&body=${body}`;
    track(TelemetryEvents.SUPPORT_EMAIL_OPENED);
    try {
      const can = await Linking.canOpenURL(url);
      if (!can) {
        appAlert('Email not available', `Write to ${SUPPORT_EMAIL} and include your correlation ID.`);
        return;
      }
      await Linking.openURL(url);
    } catch {
      appAlert('Could not open mail', `Email ${SUPPORT_EMAIL} with correlation ID ${correlationId}.`);
    }
  };

  return (
    <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Help"
        title="Support"
        subtitle="We use your correlation ID to find logs related to your session — never share passwords."
      />
      <Card>
        <Text style={styles.label}>Your correlation ID</Text>
        <Text style={styles.mono} selectable>
          {correlationId}
        </Text>
        <Pressable style={styles.secondaryBtn} onPress={() => void shareCorrelation()}>
          <Text style={styles.secondaryBtnText}>{shared ? 'Shared' : 'Share / copy ID'}</Text>
        </Pressable>

        <Text style={[styles.label, {marginTop: 20}]}>Contact</Text>
        <Text style={styles.body}>
          Email {SUPPORT_EMAIL}. Describe what you were doing (pairing, live view, revoke, billing)
          and paste the correlation ID above.
        </Text>
        <Pressable style={styles.primaryBtn} onPress={() => void emailSupport()}>
          <Text style={styles.primaryBtnText}>Email support</Text>
        </Pressable>
      </Card>
      <Card style={{marginTop: 12}}>
        <Text style={styles.label}>Before you write</Text>
        <Text style={styles.bullet}>• Child and parent on same account / family handle</Text>
        <Text style={styles.bullet}>• Child permissions finished in Clock Time</Text>
        <Text style={styles.bullet}>• Try Sign out → Sign in if live view stalls</Text>
        <Text style={styles.bullet}>• For a new child phone: Revoke the old device first on a 1-seat plan</Text>
      </Card>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  label: {fontSize: 13, fontWeight: '700', color: '#374151', marginBottom: 8},
  mono: {
    fontFamily: 'Courier',
    fontSize: 13,
    color: '#0f172a',
    backgroundColor: '#f8fafc',
    padding: 12,
    borderRadius: 10,
    overflow: 'hidden',
    marginBottom: 10,
  },
  body: {fontSize: 14, color: '#64748b', lineHeight: 20, marginBottom: 14},
  bullet: {fontSize: 13, color: '#475569', lineHeight: 20, marginBottom: 4},
  primaryBtn: {
    backgroundColor: '#2563eb',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  primaryBtnText: {color: '#fff', fontWeight: '700', fontSize: 15},
  secondaryBtn: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
    backgroundColor: '#fff',
  },
  secondaryBtnText: {color: '#0f172a', fontWeight: '600', fontSize: 14},
});
