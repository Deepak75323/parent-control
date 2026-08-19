import {create} from 'zustand';
import {nextClockLockPhase, type ClockLockEvent, type ClockLockPhase} from '../utils/clockLockState';
import {useAuthStore} from './authStore';

type ClockLockStore = {
  phase: ClockLockPhase;
  dispatch: (event: ClockLockEvent) => void;
};

export const useClockLockStore = create<ClockLockStore>((set, get) => ({
  phase: 'open',
  dispatch: event => {
    const authenticated = Boolean(useAuthStore.getState().accessToken);
    set({
      phase: nextClockLockPhase(get().phase, event, {authenticated}),
    });
  },
}));
