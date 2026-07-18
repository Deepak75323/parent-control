import React, {useEffect, useState} from 'react';
import {View, Text, StyleSheet, Alert, Linking, Pressable} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {billingApi, PlanInfo, SubscriptionInfo} from '../services/api';
import {
  Card,
  HeroBlock,
  LoadingCenter,
  MetaRow,
  PrimaryBtn,
  ScreenShell,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<RootStackParamList, 'Subscription'>;

export function SubscriptionScreen({navigation}: Props) {
  const [sub, setSub] = useState<SubscriptionInfo | null>(null);
  const [plans, setPlans] = useState<PlanInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [checkoutPlan, setCheckoutPlan] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    Promise.all([billingApi.subscription(), billingApi.plans()])
      .then(([s, p]) => {
        setSub(s.data.data);
        setPlans(p.data.data);
      })
      .catch(() => Alert.alert('Error', 'Couldn’t load subscription. Check your connection and try again.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const checkout = async (planId: string) => {
    setCheckoutPlan(planId);
    try {
      const {data} = await billingApi.checkout(planId);
      if (data.data.checkoutUrl) {
        await Linking.openURL(data.data.checkoutUrl);
      }
    } catch {
      Alert.alert('Checkout failed', 'Stripe may not be configured in this environment.');
    } finally {
      setCheckoutPlan(null);
    }
  };

  if (loading) return <LoadingCenter />;

  const upgradePlans = plans.filter(p => p.planId !== 'free');

  return (
    <ScreenShell backLabel="← Back to settings" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Billing"
        title="Subscription"
        subtitle="Manage your plan and device limits."
      />

      {sub ? (
        <Card>
          <Text style={styles.cardTitle}>Current plan</Text>
          <MetaRow label="Plan" value={sub.planId} />
          <MetaRow label="Status" value={sub.status} />
          <MetaRow label="Active" value={sub.active ? 'Yes' : 'No'} />
          <MetaRow label="Max devices" value={String(sub.maxDevices)} />
          {sub.trialEndsAt ? (
            <MetaRow label="Trial ends" value={new Date(sub.trialEndsAt).toLocaleDateString()} />
          ) : null}
        </Card>
      ) : null}

      <Text style={styles.sectionTitle}>Upgrade</Text>
      {upgradePlans.map(plan => {
        const isCurrent = sub?.planId === plan.planId;
        return (
          <Card key={plan.planId} style={isCurrent ? styles.currentPlan : undefined}>
            <View style={styles.planHeader}>
              <Text style={styles.planName}>{plan.planId.toUpperCase()}</Text>
              {isCurrent ? (
                <View style={styles.currentBadge}>
                  <Text style={styles.currentBadgeText}>Current</Text>
                </View>
              ) : null}
            </View>
            <Text style={styles.planDetail}>
              {plan.maxDevices} devices · Live stream: {plan.liveStreamingAllowed ? 'Yes' : 'No'}
            </Text>
            {!isCurrent ? (
              <PrimaryBtn
                title="Choose plan"
                onPress={() => checkout(plan.planId)}
                loading={checkoutPlan === plan.planId}
              />
            ) : (
              <Pressable style={styles.currentBtn} disabled>
                <Text style={styles.currentBtnText}>Your active plan</Text>
              </Pressable>
            )}
          </Card>
        );
      })}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  cardTitle: {fontSize: 15, fontWeight: '700', color: '#0f172a', marginBottom: 4},
  sectionTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: '#64748b',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 10,
    marginTop: 4,
  },
  planHeader: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  planName: {fontWeight: '700', fontSize: 17, color: '#0f172a'},
  planDetail: {color: '#64748b', fontSize: 13, marginTop: 6, marginBottom: 4},
  currentPlan: {borderColor: '#2563eb', backgroundColor: '#eff6ff'},
  currentBadge: {backgroundColor: '#2563eb', borderRadius: 12, paddingHorizontal: 10, paddingVertical: 4},
  currentBadgeText: {color: '#fff', fontSize: 11, fontWeight: '700'},
  currentBtn: {
    marginTop: 8,
    paddingVertical: 12,
    alignItems: 'center',
    backgroundColor: '#e2e8f0',
    borderRadius: 12,
  },
  currentBtnText: {color: '#64748b', fontWeight: '600', fontSize: 14},
});
