import {AppRegistry, LogBox} from 'react-native';
import App from './App';
import {name as appName} from './app.json';
import {wrapRootComponent} from './src/services/telemetry';

if (__DEV__) {
  // Keep Metro terminal logs; hide noisy stream/pairing traces from on-device LogBox.
  LogBox.ignoreLogs([
    /\[TRACE_ID=/,
    /\[SCREEN:/,
    /\[PCP:/,
    /\[STREAM_STATE\]/,
    /\[webrtc:/,
    /\[PAIR\]/,
    /\[WebRTC\]/,
    /FAILURE REPORT/,
  ]);
}

AppRegistry.registerComponent(appName, () => wrapRootComponent(App));
