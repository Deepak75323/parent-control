module.exports = {
  preset: 'react-native',
  testMatch: ['**/__tests__/**/*.test.ts'],
  moduleFileExtensions: ['ts', 'tsx', 'js'],
  transformIgnorePatterns: [
    'node_modules/(?!(react-native|@react-native|@stomp/stompjs)/)',
  ],
};
