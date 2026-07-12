import {LinkingOptions} from '@react-navigation/native';
import {RootStackParamList} from './RootNavigator';

export const linking: LinkingOptions<RootStackParamList> = {
  prefixes: ['parentalcontrol://', 'https://parentalcontrol.local', 'http://localhost:8080'],
  config: {
    screens: {
      ResetPassword: {
        path: 'reset-password',
        parse: {
          token: (value: string) => decodeURIComponent(value),
        },
      },
      VerifyEmail: {
        path: 'verify-email',
        parse: {
          token: (value: string) => decodeURIComponent(value),
        },
      },
    },
  },
};
