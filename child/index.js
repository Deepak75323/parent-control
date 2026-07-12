import 'react-native-gesture-handler';
import {enableScreens} from 'react-native-screens';
import {AppRegistry} from 'react-native';
import App from './App';
import {name as appName} from './app.json';
import streamingHeadlessTask from './src/headless/streamingHeadless';

// Native stack fragments break ScrollView on some Android devices (view flattening).
enableScreens(false);

if (__DEV__) {
  console.warn('[SCREEN:0000:BOOT] child index.js loaded (screens disabled)');
}

AppRegistry.registerComponent(appName, () => App);
AppRegistry.registerHeadlessTask('ChildMonitoringStream', () => streamingHeadlessTask);
