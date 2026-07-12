import React from 'react';
import {ScrollView, StyleSheet, Text, View} from 'react-native';
import {useApiDebugStore} from '../store/apiDebugStore';
import {config} from '../utils/config';

/** Visible on-screen API trace — DevTools often misses RN console logs. */
export function ApiDebugPanel() {
  const last = useApiDebugStore(s => s.last);

  if (!__DEV__) return null;

  return (
    <View style={styles.wrap}>
      <Text style={styles.title}>API debug (dev)</Text>
      <Text style={styles.meta}>baseURL: {config.apiBaseUrl}</Text>
      <Text style={styles.meta}>Logs also print in Metro terminal (`npm start`)</Text>
      {!last ? (
        <Text style={styles.meta}>No API call yet — tap Register to trace.</Text>
      ) : (
        <ScrollView style={styles.scroll} nestedScrollEnabled>
          <Text style={styles.meta}>{last.at}</Text>
          <Text style={[styles.line, last.ok ? styles.ok : styles.fail]}>
            {last.ok ? 'OK' : 'FAILED'} {last.method} {last.url}
            {last.status != null ? ` → HTTP ${last.status}` : ' → no response'}
          </Text>
          {last.error ? <Text style={styles.fail}>Error: {last.error}</Text> : null}
          {last.requestBody ? (
            <>
              <Text style={styles.label}>Request body</Text>
              <Text style={styles.code}>{last.requestBody}</Text>
            </>
          ) : null}
          {last.responseBody ? (
            <>
              <Text style={styles.label}>Response body</Text>
              <Text style={styles.code}>{last.responseBody}</Text>
            </>
          ) : null}
          {last.curl ? (
            <>
              <Text style={styles.label}>curl</Text>
              <Text style={styles.code}>{last.curl}</Text>
            </>
          ) : null}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    marginTop: 16,
    padding: 12,
    borderRadius: 8,
    backgroundColor: '#f8fafc',
    borderWidth: 1,
    borderColor: '#cbd5e1',
    maxHeight: 220,
  },
  title: {fontWeight: '700', marginBottom: 4, color: '#0f172a'},
  meta: {fontSize: 11, color: '#64748b', marginBottom: 4},
  scroll: {maxHeight: 160},
  line: {fontSize: 12, fontWeight: '600', marginBottom: 6},
  ok: {color: '#15803d'},
  fail: {color: '#b91c1c', fontSize: 12, marginBottom: 4},
  label: {fontSize: 11, fontWeight: '600', color: '#334155', marginTop: 4},
  code: {fontSize: 10, color: '#1e293b', fontFamily: 'monospace'},
});
