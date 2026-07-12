# Parent App (React Native)

Parent dashboard for the parental control platform.

**Setup:** [docs/DEVELOPER_ONBOARDING.md](../docs/DEVELOPER_ONBOARDING.md) §5

## Quick start

**Prerequisites:** Backend running on port 8080, Android emulator or device.

```bash
npm install --legacy-peer-deps
npm start          # terminal 1
npm run android    # terminal 2
```

**Emulator API URL** (default in `src/utils/config.ts`): `http://10.0.2.2:8080`

**Physical device:** Set `apiBaseUrl` / `wsNativeUrl` to your Mac LAN IP.

## Android bootstrap

If `android/gradlew` is missing, the project includes a full `android/` folder from RN 0.76.5. Regenerate only if deleted:

```bash
npx @react-native-community/cli@latest init ParentScaffold \
  --version 0.76.5 --directory /tmp/pcp-parent-scaffold --skip-install
cp -R /tmp/pcp-parent-scaffold/android .
# Set MainActivity getMainComponentName() to "ParentalControlParent"
```
