/** Premium Design System — Parental Control App */

export const Colors = {
  // Brand
  primary: '#4F46E5',       // Indigo 600
  primaryDark: '#3730A3',   // Indigo 800
  primaryLight: '#818CF8',  // Indigo 400
  primarySurface: '#EEF2FF', // Indigo 50

  accent: '#7C3AED',        // Violet 600
  accentLight: '#DDD6FE',   // Violet 200
  accentSurface: '#F5F3FF', // Violet 50

  // Semantic
  success: '#059669',       // Emerald 600
  successLight: '#D1FAE5',  // Emerald 100
  warning: '#D97706',       // Amber 600
  warningLight: '#FEF3C7',  // Amber 100
  danger: '#DC2626',        // Red 600
  dangerLight: '#FEE2E2',   // Red 100
  info: '#2563EB',          // Blue 600
  infoLight: '#DBEAFE',     // Blue 100

  // Online indicator
  online: '#10B981',        // Emerald 500
  offline: '#94A3B8',       // Slate 400

  // Neutrals
  background: '#F7F7FF',    // Almost white with hint of indigo
  surface: '#FFFFFF',
  surfaceElevated: '#FAFAFA',
  border: '#E8E8F0',
  borderLight: '#F1F0FF',

  // Text
  textPrimary: '#0F172A',   // Slate 900
  textSecondary: '#475569', // Slate 600
  textTertiary: '#94A3B8',  // Slate 400
  textOnDark: '#FFFFFF',
  textOnDarkSoft: '#C7D2FE',

  // Cards
  cardBg: '#FFFFFF',
  cardShadowColor: '#4F46E5',

  // Live monitoring gradients (simulated with opacity layers)
  monitorPurple: '#6D28D9',
  monitorBlue: '#2563EB',
  monitorTeal: '#0891B2',
};

export const Typography = {
  display: {fontSize: 32, fontWeight: '900' as const, letterSpacing: -0.8, color: Colors.textPrimary},
  h1: {fontSize: 26, fontWeight: '800' as const, letterSpacing: -0.5, color: Colors.textPrimary},
  h2: {fontSize: 20, fontWeight: '800' as const, letterSpacing: -0.3, color: Colors.textPrimary},
  h3: {fontSize: 17, fontWeight: '700' as const, color: Colors.textPrimary},
  h4: {fontSize: 15, fontWeight: '700' as const, color: Colors.textPrimary},
  body: {fontSize: 15, fontWeight: '500' as const, color: Colors.textSecondary, lineHeight: 22},
  bodySmall: {fontSize: 13, fontWeight: '500' as const, color: Colors.textSecondary, lineHeight: 19},
  caption: {fontSize: 11, fontWeight: '700' as const, letterSpacing: 0.6, textTransform: 'uppercase' as const, color: Colors.textTertiary},
  label: {fontSize: 13, fontWeight: '600' as const, color: Colors.textSecondary},
  number: {fontSize: 36, fontWeight: '900' as const, letterSpacing: -1, color: Colors.primary},
};

export const Spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  base: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
  xxxl: 40,
};

export const Radii = {
  sm: 10,
  md: 14,
  lg: 18,
  xl: 22,
  xxl: 28,
  pill: 100,
};

export const Elevation = {
  card: {
    shadowColor: Colors.primary,
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.07,
    shadowRadius: 16,
    elevation: 4,
  },
  cardHigh: {
    shadowColor: Colors.primary,
    shadowOffset: {width: 0, height: 8},
    shadowOpacity: 0.12,
    shadowRadius: 24,
    elevation: 8,
  },
  hero: {
    shadowColor: Colors.primaryDark,
    shadowOffset: {width: 0, height: 12},
    shadowOpacity: 0.18,
    shadowRadius: 32,
    elevation: 12,
  },
};
