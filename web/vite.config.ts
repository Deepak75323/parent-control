import {defineConfig} from 'vitest/config';
import {type ProxyOptions} from 'vite';
import react from '@vitejs/plugin-react';
import type {ClientRequest, IncomingMessage} from 'node:http';

/** Drop browser Origin so Oracle CORS does not reject the proxied request. */
function stripCorsHeaders(): NonNullable<ProxyOptions['configure']> {
  return proxy => {
    proxy.on('proxyReq', (proxyReq: ClientRequest, _req: IncomingMessage) => {
      proxyReq.removeHeader('origin');
      proxyReq.removeHeader('referer');
    });
  };
}

// Production on Oracle: `VITE_BASE=/portal/ npm run build`
export default defineConfig({
  base: process.env.VITE_BASE || '/',
  plugins: [react()],
  test: {
    environment: 'node',
    env: {
      VITE_API_PAYLOAD_ENCRYPTION_KEY: '0123456789abcdef',
      VITE_API_PAYLOAD_ENCRYPTION_IV: 'fedcba9876543210',
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://80.225.196.181',
        changeOrigin: true,
        configure: stripCorsHeaders(),
      },
      '/ws': {
        target: 'http://80.225.196.181',
        changeOrigin: true,
        ws: true,
        configure: stripCorsHeaders(),
      },
    },
  },
  define: {
    // sockjs-client expects Node's global
    global: 'globalThis',
  },
});
