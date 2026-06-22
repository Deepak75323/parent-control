import type {AxiosError, AxiosResponse, InternalAxiosRequestConfig} from 'axios';

function shellEscape(value: string): string {
  return `'${value.replace(/'/g, `'\\''`)}'`;
}

/** Build a copy-paste curl command from an axios request (dev debugging). */
export function toCurl(config: InternalAxiosRequestConfig): string {
  const method = (config.method ?? 'get').toUpperCase();
  const base = config.baseURL?.replace(/\/$/, '') ?? '';
  const path = config.url ?? '';
  const url = path.startsWith('http') ? path : `${base}${path.startsWith('/') ? path : `/${path}`}`;

  const lines = [`curl -X ${method} ${shellEscape(url)}`];

  const headers = config.headers ?? {};
  for (const [key, value] of Object.entries(headers)) {
    if (value == null || key === 'common') continue;
    lines.push(`  -H ${shellEscape(`${key}: ${String(value)}`)}`);
  }

  if (config.data != null && method !== 'GET' && method !== 'HEAD') {
    const body =
      typeof config.data === 'string' ? config.data : JSON.stringify(config.data, null, 2);
    lines.push(`  -d ${shellEscape(body)}`);
  }

  return lines.join(' \\\n');
}

export function logRequestCurl(config: InternalAxiosRequestConfig): void {
  if (!__DEV__) return;
  console.log(`\n[API →] ${(config.method ?? 'get').toUpperCase()} ${config.baseURL ?? ''}${config.url ?? ''}`);
  console.log(toCurl(config));
}

export function logResponseCurl(response: AxiosResponse): void {
  if (!__DEV__) return;
  const {status, config} = response;
  console.log(
    `[API ←] ${status} ${(config.method ?? 'get').toUpperCase()} ${config.baseURL ?? ''}${config.url ?? ''}`,
  );
}

export function logErrorCurl(error: AxiosError): void {
  if (!__DEV__) return;
  if (error.config) {
    console.log('\n[API ✗] Request failed — equivalent curl:');
    console.log(toCurl(error.config));
  }
  if (error.response) {
    console.log(`[API ✗] HTTP ${error.response.status}:`, error.response.data);
  } else {
    console.log('[API ✗] No response (network error). URL:', error.config?.baseURL, error.message);
  }
}
