module.exports = {
  presets: ['module:@react-native/babel-preset'],
  plugins: [
    [
      'transform-inline-environment-variables',
      {
        include: [
          'API_BASE_URL',
          'DEV_LAN_HOST',
          'ENABLE_SSL_PINNING',
        ],
      },
    ],
  ],
};
