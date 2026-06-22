import {create} from 'zustand';

export interface ApiDebugEntry {
  at: string;
  method: string;
  url: string;
  status?: number;
  ok: boolean;
  requestBody?: string;
  responseBody?: string;
  curl?: string;
  error?: string;
}

interface ApiDebugState {
  last: ApiDebugEntry | null;
  setLast: (entry: ApiDebugEntry) => void;
}

export const useApiDebugStore = create<ApiDebugState>(set => ({
  last: null,
  setLast: last => set({last}),
}));
