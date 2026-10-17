/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_WS_URL?: string;
  readonly VITE_BASE?: string;
  readonly VITE_API_PAYLOAD_ENCRYPTION_ENABLED?: string;
  readonly VITE_API_PAYLOAD_ENCRYPTION_KEY?: string;
  readonly VITE_API_PAYLOAD_ENCRYPTION_IV?: string;
  readonly VITE_ENABLE_VIEWER_ROTATE?: string;
  /** Set to "true" to show Add Friend / Friends list / friend requests in chat. Default off. */
  readonly VITE_ENABLE_CHAT_FRIENDS?: string;
  /** Set to "true" to show Direct messages in chat. Default off (servers only). */
  readonly VITE_ENABLE_CHAT_DMS?: string;
  /** Set to "true" to show Parents tab in the sidebar. Default off. */
  readonly VITE_ENABLE_PARENTS_TAB?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
