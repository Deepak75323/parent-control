import React from 'react';
import {StyleProp, StyleSheet, Text, View, ViewStyle} from 'react-native';
import MapView, {MapViewProps, Marker} from 'react-native-maps';
import {mapsAvailable} from '../nativeModules';

type Props = MapViewProps & {
  fallbackStyle?: StyleProp<ViewStyle>;
  fallbackLabel?: string;
  showMarker?: boolean;
};

export function SafeMapView({
  fallbackStyle,
  fallbackLabel,
  showMarker = true,
  region,
  style,
  children,
  ...mapProps
}: Props) {
  if (!mapsAvailable || !region) {
    const lat = region?.latitude?.toFixed(5) ?? '—';
    const lon = region?.longitude?.toFixed(5) ?? '—';
    return (
      <View style={[styles.fallback, fallbackStyle, style]}>
        <Text style={styles.fallbackIcon}>🗺️</Text>
        <Text style={styles.fallbackTitle}>{fallbackLabel ?? 'Map preview unavailable'}</Text>
        <Text style={styles.fallbackCoords}>
          {lat}, {lon}
        </Text>
        {!mapsAvailable ? (
          <Text style={styles.fallbackHint}>
            Add GOOGLE_MAPS_API_KEY to parent-app/android/local.properties to enable maps.
          </Text>
        ) : null}
      </View>
    );
  }

  return (
    <MapView style={style} region={region} {...mapProps}>
      {showMarker && region ? <Marker coordinate={region} /> : null}
      {children}
    </MapView>
  );
}

const styles = StyleSheet.create({
  fallback: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E2E8F0',
    borderRadius: 12,
    padding: 16,
  },
  fallbackIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  fallbackTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#334155',
    textAlign: 'center',
  },
  fallbackCoords: {
    marginTop: 6,
    fontSize: 12,
    color: '#64748B',
  },
  fallbackHint: {
    marginTop: 10,
    fontSize: 11,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 16,
  },
});
