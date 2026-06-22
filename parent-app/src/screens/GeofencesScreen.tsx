import React, {useCallback, useEffect, useMemo, useState} from 'react';
import {View, Text, StyleSheet, Alert, Dimensions} from 'react-native';
import {Circle, Marker} from 'react-native-maps';
import {NativeStackScreenProps} from '@react-navigation/native-stack';
import {RootStackParamList} from '../navigation/RootNavigator';
import {SafeMapView} from '../components/SafeMapView';
import {GeofenceZone, monitoringApi, policiesApi} from '../services/api';
import {Card, FormField, HeroBlock, PrimaryBtn, ScreenShell} from '../components/AppScreen';
import {useRealtimeStore} from '../store/realtimeStore';

type Props = NativeStackScreenProps<RootStackParamList, 'Geofences'>;

const DEFAULT = {latitude: 37.7749, longitude: -122.4194, latitudeDelta: 0.04, longitudeDelta: 0.04};

export function GeofencesScreen({navigation, route}: Props) {
  const {deviceId} = route.params;
  const [zones, setZones] = useState<GeofenceZone[]>([]);
  const [name, setName] = useState('School');
  const [radius, setRadius] = useState('200');
  const [pin, setPin] = useState({latitude: DEFAULT.latitude, longitude: DEFAULT.longitude});
  const liveLocation = useRealtimeStore(s => s.liveLocations[deviceId]);

  const load = useCallback(async () => {
    try {
      const {data} = await monitoringApi.geofences(deviceId);
      setZones(data.data);
    } catch {
      Alert.alert('Error', 'Could not load geofences');
    }
  }, [deviceId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (liveLocation) {
      setPin({latitude: liveLocation.latitude, longitude: liveLocation.longitude});
    }
  }, [liveLocation?.latitude, liveLocation?.longitude]);

  const region = useMemo(
    () => ({
      latitude: pin.latitude,
      longitude: pin.longitude,
      latitudeDelta: 0.04,
      longitudeDelta: 0.04,
    }),
    [pin],
  );

  const createGeofence = async () => {
    try {
      await policiesApi.createGeofence({
        name: `Geofence ${name}`,
        deviceIds: [deviceId],
        geofenceName: name,
        latitude: pin.latitude,
        longitude: pin.longitude,
        radiusM: parseFloat(radius) || 200,
      });
      await load();
      Alert.alert('Created', `Geofence "${name}" saved`);
    } catch {
      Alert.alert('Error', 'Failed to create geofence');
    }
  };

  const mapWidth = Dimensions.get('window').width - 32;

  return (
    <ScreenShell backLabel="← Back to device" onBack={() => navigation.goBack()}>
      <HeroBlock
        eyebrow="Location"
        title="Geofences"
        subtitle="Tap the map to place a zone. Child alerts when entering or leaving."
      />

      <View style={styles.mapWrap}>
        <SafeMapView
          style={[styles.map, {width: mapWidth}]}
          region={region}
          showMarker={false}
          fallbackLabel="Geofence map"
          onPress={e => setPin(e.nativeEvent.coordinate)}>
          <Marker coordinate={pin} title={name} pinColor="#2563eb" />
          <Circle
            center={pin}
            radius={parseFloat(radius) || 200}
            strokeColor="rgba(37, 99, 235, 0.9)"
            fillColor="rgba(34, 197, 94, 0.25)"
          />
          {zones.map(z => (
            <Circle
              key={z.ruleId}
              center={{latitude: z.latitude, longitude: z.longitude}}
              radius={z.radiusM}
              strokeColor="rgba(100, 116, 139, 0.8)"
              fillColor="rgba(148, 163, 184, 0.15)"
            />
          ))}
        </SafeMapView>
      </View>

      <Card>
        <FormField label="Location name" value={name} onChangeText={setName} />
        <FormField
          label="Radius (meters)"
          value={radius}
          onChangeText={setRadius}
          keyboardType="number-pad"
        />
        <Text style={styles.coords}>
          {pin.latitude.toFixed(5)}, {pin.longitude.toFixed(5)}
          {liveLocation ? ' · using live child location' : ''}
        </Text>
        <PrimaryBtn title="Save geofence here" onPress={createGeofence} />
      </Card>

      <Text style={styles.listHeading}>Active zones ({zones.length})</Text>
      {zones.length === 0 ? (
        <Card>
          <Text style={styles.empty}>No geofences yet.</Text>
        </Card>
      ) : (
        zones.map(z => (
          <Card key={z.ruleId}>
            <Text style={styles.zoneName}>{z.name}</Text>
            <Text style={styles.zoneMeta}>
              {z.radiusM}m · enter {z.alertOnEnter ? 'on' : 'off'} · exit {z.alertOnExit ? 'on' : 'off'}
            </Text>
          </Card>
        ))
      )}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  mapWrap: {marginBottom: 12, alignItems: 'center'},
  map: {height: 260, borderRadius: 16, overflow: 'hidden', borderWidth: 1, borderColor: '#e2e8f0'},
  coords: {fontSize: 12, color: '#64748b', marginBottom: 12},
  listHeading: {
    fontSize: 13,
    fontWeight: '600',
    color: '#64748b',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 10,
    marginTop: 4,
  },
  zoneName: {fontSize: 16, fontWeight: '600', color: '#0f172a'},
  zoneMeta: {fontSize: 12, color: '#64748b', marginTop: 4},
  empty: {color: '#64748b', textAlign: 'center'},
});
