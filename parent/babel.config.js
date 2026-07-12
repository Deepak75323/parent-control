module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    [
      'transform-inline-environment-variables',
      {
        include: [
          'API_BASE_URL',
          'WS_BASE_URL',
          'WS_NATIVE_URL',
          'DEV_AUTO_LOGIN',
          'DEV_AUTO_EMAIL',
          'DEV_AUTO_PASSWORD',
          'ENABLE_SSL_PINNING',
        ],
      },
    ],
  ],
};
