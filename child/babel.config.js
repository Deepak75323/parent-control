const fs = require('fs');
const path = require('path');

// Load child/.env so Metro/babel inline API_BASE_URL without manual export.
const envPath = path.join(__dirname, '.env');
if (fs.existsSync(envPath)) {
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    const val = trimmed.slice(eq + 1).trim();
    if (key && process.env[key] === undefined) {
      process.env[key] = val;
    }
  }
}

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
          'API_PAYLOAD_ENCRYPTION_ENABLED',
          'API_PAYLOAD_ENCRYPTION_KEY',
          'API_PAYLOAD_ENCRYPTION_IV',
        ],
      },
    ],
  ],
};
