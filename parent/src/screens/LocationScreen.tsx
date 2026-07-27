import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {View, Text, StyleSheet, Dimensions} from 'react-native';
import {Marker, Polyline} from 'react-native-maps';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {SafeMapView} from '../components/SafeMapView';
import {LocationPoint, monitoringApi} from '../services/api';
import {useRealtimeStore} from '../store/realtimeStore';
import {Card, HeroBlock, ScreenShell} from '../components/AppScreen';
import {appAlert} from '../utils/appAlert';

type Props = NativeStackScreenProps<RootStackParamList, 'Location'>;

const DEFAULT_REGION = {
  latitude: 37.7749,
  longitude: -122.4194,
  latitudeDelta: 0.05,
  longitudeDelta: 0.05,
};

export function LocationScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [points, setPoints] = useState<LocationPoint[]>([]);
  const liveLocation = useRealtimeStore(s => s.liveLocations[deviceId]);

  const load = useCallback(async () => {
    try {
      const {data} = await monitoringApi.locationHistory(deviceId);
      setPoints(data.data.content);
    } catch {
      appAlert('Error', 'Couldn’t load location history. Check your connection and try again.');
    }
  }, [deviceId]);

  useEffect(() => {
    load();
  }, [load]);

  const allPoints = useMemo(() => {
    if (!liveLocation) return points;
    const exists = points.some(p => p.id === liveLocation.id);
    return exists ? points : [liveLocation, ...points];
  }, [points, liveLocation]);

  const latest = allPoints[0];
  const region = latest
    ? {
        latitude: latest.latitude,
        longitude: latest.longitude,
        latitudeDelta: 0.03,
        longitudeDelta: 0.03,
      }
    : DEFAULT_REGION;

  const path = allPoints
    .slice()
    .reverse()
    .map(p => ({latitude: p.latitude, longitude: p.longitude}));

  return (
    <ScreenShell backLabel="← Back to device" onBack={() => navigation.goBack()} scroll={false}>
      <View style={styles.wrap}>
        <View style={styles.header}>
          <HeroBlock
            eyebrow={liveLocation ? '● Live location' : 'History'}
            title="Location"
            subtitle={
              liveLocation
                ? `${liveLocation.latitude.toFixed(5)}, ${liveLocation.longitude.toFixed(5)}`
                : `${allPoints.length} recorded point${allPoints.length === 1 ? '' : 's'}`
            }
          />
        </View>
        <View style={styles.mapWrap} collapsable={false}>
          <SafeMapView
            key={latest?.id ?? 'empty'}
            style={styles.map}
            region={region}
            showMarker={false}
            zoomEnabled
            scrollEnabled
            zoomControlEnabled
            fallbackLabel="Location history">
            {path.length > 1 ? (
              <Polyline coordinates={path} strokeColor="#2563eb" strokeWidth={3} />
            ) : null}
            {allPoints.map(p => (
              <Marker
                key={p.id}
                coordinate={{latitude: p.latitude, longitude: p.longitude}}
                title={new Date(p.recordedAt).toLocaleString()}
              />
            ))}
          </SafeMapView>
        </View>
        <Card style={styles.footerCard}>
          <Text style={styles.footerText}>
            {allPoints.length} point(s) · map updates in real time
          </Text>
        </Card>
      </View>
    </ScreenShell>
  );
}

const mapWidth = Dimensions.get('window').width - 32;

const styles = StyleSheet.create({
  wrap: {flex: 1, paddingHorizontal: 16},
  header: {paddingTop: 8},
  mapWrap: {
    flex: 1,
    marginVertical: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 16,
    overflow: 'hidden',
  },
  map: {
    width: mapWidth,
    flex: 1,
    minHeight: 280,
  },
  footerCard: {marginBottom: 16},
  footerText: {fontSize: 13, color: '#64748b', textAlign: 'center'},
});
