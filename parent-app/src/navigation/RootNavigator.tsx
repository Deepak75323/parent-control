import React from 'react';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import {useAuthStore} from '../store/authStore';
import {SplashScreen} from '../screens/SplashScreen';
import {LoginScreen} from '../screens/LoginScreen';
import {RegisterScreen} from '../screens/RegisterScreen';
import {MainTabsScreen} from '../screens/MainTabsScreen';
import {DeviceListScreen} from '../screens/DeviceListScreen';
import {DeviceDetailScreen} from '../screens/DeviceDetailScreen';
import {LiveMonitoringScreen} from '../screens/LiveMonitoringScreen';
import {NotificationsScreen} from '../screens/NotificationsScreen';
import {LocationScreen} from '../screens/LocationScreen';
import {AlertsScreen} from '../screens/AlertsScreen';
import {SettingsScreen} from '../screens/SettingsScreen';
import {ParentPinScreen} from '../screens/ParentPinScreen';
import {PoliciesScreen} from '../screens/PoliciesScreen';
import {ScreenTimeScreen} from '../screens/ScreenTimeScreen';
import {RequestsScreen} from '../screens/RequestsScreen';
import {GeofencesScreen} from '../screens/GeofencesScreen';
import {AuditLogScreen} from '../screens/AuditLogScreen';
import {SubscriptionScreen} from '../screens/SubscriptionScreen';
import {AppRulesScreen} from '../screens/AppRulesScreen';
import {AppTimeLimitsScreen} from '../screens/AppTimeLimitsScreen';

export type RootStackParamList = {
  Splash: undefined;
  Login: undefined;
  Register: undefined;
  MainTabs: undefined;
  DeviceList: undefined;
  DeviceDetail: {deviceId: string};
  LiveMonitoring: {deviceId: string; initialStream?: 'SCREEN' | 'CAMERA' | 'AUDIO'};
  Policies: {deviceId: string};
  ScreenTime: {deviceId: string};
  Requests: undefined;
  Geofences: {deviceId: string};
  Notifications: {deviceId: string};
  Location: {deviceId: string};
  Alerts: undefined;
  Settings: undefined;
  ParentPin: undefined;
  AuditLog: undefined;
  Subscription: undefined;
  AppRules: {deviceId: string};
  AppTimeLimits: {deviceId: string};
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const accessToken = useAuthStore(s => s.accessToken);

  return (
    <Stack.Navigator
      key={accessToken ? 'app' : 'auth'}
      screenOptions={{headerShown: false, contentStyle: {flex: 1, backgroundColor: '#f1f5f9'}}}>
      {!accessToken ? (
        <>
          <Stack.Screen name="Splash" component={SplashScreen} />
          <Stack.Screen name="Login" component={LoginScreen} />
          <Stack.Screen name="Register" component={RegisterScreen} />
        </>
      ) : (
        <>
          <Stack.Screen name="MainTabs" component={MainTabsScreen} />
          <Stack.Screen name="DeviceList" component={DeviceListScreen} />
          <Stack.Screen name="DeviceDetail" component={DeviceDetailScreen} />
          <Stack.Screen name="LiveMonitoring" component={LiveMonitoringScreen} />
          <Stack.Screen name="Policies" component={PoliciesScreen} />
          <Stack.Screen name="ScreenTime" component={ScreenTimeScreen} />
          <Stack.Screen name="Requests" component={RequestsScreen} />
          <Stack.Screen name="Geofences" component={GeofencesScreen} />
          <Stack.Screen name="Notifications" component={NotificationsScreen} />
          <Stack.Screen name="Location" component={LocationScreen} />
          <Stack.Screen name="Alerts" component={AlertsScreen} />
          <Stack.Screen name="Settings" component={SettingsScreen} />
          <Stack.Screen name="ParentPin" component={ParentPinScreen} />
          <Stack.Screen name="Subscription" component={SubscriptionScreen} />
          <Stack.Screen name="AuditLog" component={AuditLogScreen} />
          <Stack.Screen name="AppRules" component={AppRulesScreen} />
          <Stack.Screen name="AppTimeLimits" component={AppTimeLimitsScreen} />
        </>
      )}
    </Stack.Navigator>
  );
}
