import React, {useCallback, useMemo, useState} from 'react';
import {View, Text, StyleSheet, Pressable, FlatList, ListRenderItem, AppState} from 'react-native';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {useFocusEffect} from '@react-navigation/native';
import {ChildStackParamList} from './RootNavigator';
import {nativeModules} from '../nativeModules';
import {
  Card,
  HeroBlock,
  PrimaryBtn,
  ProgressBar,
  ScreenShell,
  StatusBadge,
} from '../components/AppScreen';

type Props = NativeStackScreenProps<ChildStackParamList, 'OemSetupWizard'>;

type WizardStep = {
  id: string;
  title: string;
  description: string;
  instructions: string[];
  autoVerifiable: boolean;
  complete: boolean;
};

export function OemSetupWizardScreen({navigation}: Props) {
  const [vendorName, setVendorName] = useState('Your phone');
  const [steps, setSteps] = useState<WizardStep[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);

  const refresh = useCallback(async () => {
    const data = await nativeModules.getOemSetupWizard();
    setVendorName(data.vendorName ?? 'Your phone');
    setSteps(data.steps ?? []);
    if (data.complete) {
      setActiveIndex((data.steps ?? []).length);
    } else {
      const firstIncomplete = (data.steps ?? []).findIndex(s => !s.complete);
      if (firstIncomplete >= 0) {
        setActiveIndex(firstIncomplete);
      }
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      refresh();
      const sub = AppState.addEventListener('change', state => {
        if (state === 'active') refresh();
      });
      return () => sub.remove();
    }, [refresh]),
  );

  const completedCount = useMemo(() => steps.filter(s => s.complete).length, [steps]);
  const allComplete = steps.length > 0 && completedCount === steps.length;
  const current = steps[activeIndex];

  const openCurrentStep = useCallback(async () => {
    if (!current) return;
    await nativeModules.openOemSetupWizardStep(current.id);
  }, [current]);

  const confirmCurrentStep = useCallback(async () => {
    if (!current || current.autoVerifiable) return;
    await nativeModules.confirmOemSetupWizardStep(current.id);
    await refresh();
  }, [current, refresh]);

  const renderInstruction: ListRenderItem<string> = useCallback(
    ({item, index}) => (
      <View style={styles.instructionRow}>
        <Text style={styles.instructionNum}>{index + 1}.</Text>
        <Text style={styles.instructionText}>{item}</Text>
      </View>
    ),
    [],
  );

  if (steps.length === 0) {
    return (
      <ScreenShell backLabel="← Back" onBack={() => navigation.goBack()}>
        <HeroBlock
          eyebrow="Phone setup"
          title="Checking your device…"
          subtitle="Loading manufacturer-specific steps."
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      backLabel="← Back to permissions"
      onBack={() => navigation.goBack()}
      scroll={false}>
      <FlatList
        style={styles.list}
        data={current?.instructions ?? []}
        keyExtractor={(_, i) => String(i)}
        renderItem={renderInstruction}
        ListHeaderComponent={
          <View style={styles.headerWrap}>
            <HeroBlock
              eyebrow={`${vendorName} setup`}
              title="Keep monitoring alive in the background"
              subtitle={
                'Some phones stop apps after a few minutes unless you change hidden battery settings. ' +
                'Follow each step — this takes about 2 minutes.'
              }
            />
            <ProgressBar granted={completedCount} total={steps.length} />
            <Card style={styles.stepNav}>
              {steps.map((step, index) => (
                <Pressable
                  key={step.id}
                  style={[styles.stepChip, index === activeIndex && styles.stepChipActive]}
                  onPress={() => setActiveIndex(index)}>
                  <StatusBadge granted={step.complete} />
                  <Text
                    style={[styles.stepChipText, index === activeIndex && styles.stepChipTextActive]}
                    numberOfLines={1}>
                    {index + 1}. {step.title}
                  </Text>
                </Pressable>
              ))}
            </Card>
            {current ? (
              <Card style={styles.currentCard}>
                <View style={styles.currentHeader}>
                  <Text style={styles.currentTitle}>{current.title}</Text>
                  <StatusBadge granted={current.complete} />
                </View>
                <Text style={styles.currentDesc}>{current.description}</Text>
                {current.complete ? (
                  <Text style={styles.doneHint}>This step looks good.</Text>
                ) : null}
              </Card>
            ) : null}
            <Text style={styles.sectionLabel}>Instructions</Text>
          </View>
        }
        ListFooterComponent={
          <View style={styles.footer}>
            {current && !current.complete ? (
              <>
                <PrimaryBtn title="Open settings for this step" onPress={openCurrentStep} />
                {!current.autoVerifiable ? (
                  <Pressable style={styles.confirmBtn} onPress={confirmCurrentStep}>
                    <Text style={styles.confirmBtnText}>I've done this — mark complete</Text>
                  </Pressable>
                ) : (
                  <Text style={styles.autoHint}>
                    Return here after changing settings — we'll detect it automatically.
                  </Text>
                )}
              </>
            ) : null}
            <PrimaryBtn
              title={allComplete ? 'Continue setup' : 'Skip for now'}
              onPress={() => navigation.navigate('DeviceOwnerSetup')}
            />
            {!allComplete ? (
              <Text style={styles.skipWarn}>
                Skipping may cause screen share and commands to stop on this phone after a few minutes.
              </Text>
            ) : null}
          </View>
        }
        contentContainerStyle={styles.listContent}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  list: {flex: 1},
  listContent: {paddingHorizontal: 16, paddingBottom: 24},
  headerWrap: {paddingTop: 8},
  stepNav: {marginBottom: 12, gap: 8},
  stepChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 8,
    paddingHorizontal: 4,
  },
  stepChipActive: {
    backgroundColor: '#eff6ff',
    borderRadius: 10,
    paddingHorizontal: 8,
  },
  stepChipText: {flex: 1, fontSize: 13, color: '#64748b'},
  stepChipTextActive: {color: '#0f172a', fontWeight: '600'},
  currentCard: {marginBottom: 12},
  currentHeader: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8},
  currentTitle: {flex: 1, fontSize: 16, fontWeight: '700', color: '#0f172a'},
  currentDesc: {fontSize: 14, color: '#64748b', marginTop: 8, lineHeight: 20},
  doneHint: {fontSize: 13, color: '#15803d', marginTop: 10, fontWeight: '600'},
  sectionLabel: {fontSize: 13, fontWeight: '700', color: '#94a3b8', marginBottom: 8, letterSpacing: 0.5},
  instructionRow: {flexDirection: 'row', marginBottom: 10, paddingRight: 8},
  instructionNum: {width: 22, fontSize: 14, fontWeight: '700', color: '#2563eb'},
  instructionText: {flex: 1, fontSize: 14, color: '#334155', lineHeight: 20},
  footer: {paddingTop: 8, gap: 10},
  confirmBtn: {
    paddingVertical: 14,
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    backgroundColor: '#fff',
  },
  confirmBtnText: {fontSize: 15, fontWeight: '600', color: '#0f172a'},
  autoHint: {fontSize: 13, color: '#64748b', textAlign: 'center', lineHeight: 18},
  skipWarn: {fontSize: 12, color: '#92400e', textAlign: 'center', lineHeight: 17},
});
