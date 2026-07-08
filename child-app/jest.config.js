module.exports = {
  preset: 'react-native',
  testMatch: ['**/src/**/*.test.ts'],
  moduleFileExtensions: ['ts', 'tsx', 'js'],
  transformIgnorePatterns: [
    'node_modules/(?!(react-native|@react-native)/)',
  ],
};
