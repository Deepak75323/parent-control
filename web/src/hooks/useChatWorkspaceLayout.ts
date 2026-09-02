import {useCallback, useEffect, useRef, useState} from 'react';

const STORAGE_KEY = 'parentChat.layout';

export const CHAT_LAYOUT_LIMITS = {
  roomsMin: 240,
  roomsMax: 420,
  roomsDefault: 280,
  infoMin: 240,
  infoMax: 420,
  infoDefault: 280,
  chatMin: 420,
  handleWidth: 7,
} as const;

type PersistedLayout = {
  roomsWidth: number;
  infoWidth: number;
  infoOpen: boolean;
};

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function loadPersistedLayout(): PersistedLayout {
  const defaults: PersistedLayout = {
    roomsWidth: CHAT_LAYOUT_LIMITS.roomsDefault,
    infoWidth: CHAT_LAYOUT_LIMITS.infoDefault,
    infoOpen: true,
  };
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return defaults;
    }
    const parsed = JSON.parse(raw) as Partial<PersistedLayout>;
    return {
      roomsWidth: clamp(
        parsed.roomsWidth ?? defaults.roomsWidth,
        CHAT_LAYOUT_LIMITS.roomsMin,
        CHAT_LAYOUT_LIMITS.roomsMax,
      ),
      infoWidth: clamp(
        parsed.infoWidth ?? defaults.infoWidth,
        CHAT_LAYOUT_LIMITS.infoMin,
        CHAT_LAYOUT_LIMITS.infoMax,
      ),
      infoOpen: parsed.infoOpen ?? defaults.infoOpen,
    };
  } catch {
    return defaults;
  }
}

function savePersistedLayout(layout: PersistedLayout) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(layout));
  } catch {
    /* quota / private mode */
  }
}

function readViewportMode() {
  if (typeof window === 'undefined') {
    return {isMobile: false, isTablet: false, isResizable: true};
  }
  const isMobile = window.matchMedia('(max-width: 900px)').matches;
  const isTablet = window.matchMedia('(max-width: 1280px)').matches;
  return {isMobile, isTablet, isResizable: !isMobile};
}

export function useChatWorkspaceLayout() {
  const initial = useRef(loadPersistedLayout());
  const [roomsWidth, setRoomsWidth] = useState(initial.current.roomsWidth);
  const [infoWidth, setInfoWidth] = useState(initial.current.infoWidth);
  const [infoOpen, setInfoOpen] = useState(initial.current.infoOpen);
  const [viewport, setViewport] = useState(readViewportMode);
  const layoutWidthRef = useRef(0);
  const hasAppliedTabletDefault = useRef(false);

  const persist = useCallback(
    (next: Partial<PersistedLayout>) => {
      const merged = {
        roomsWidth: next.roomsWidth ?? roomsWidth,
        infoWidth: next.infoWidth ?? infoWidth,
        infoOpen: next.infoOpen ?? infoOpen,
      };
      savePersistedLayout(merged);
    },
    [roomsWidth, infoWidth, infoOpen],
  );

  useEffect(() => {
    const onResize = () => setViewport(readViewportMode());
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    if (hasAppliedTabletDefault.current || !viewport.isTablet || viewport.isMobile) {
      return;
    }
    try {
      if (!localStorage.getItem(STORAGE_KEY)) {
        setInfoOpen(false);
      }
    } catch {
      setInfoOpen(false);
    }
    hasAppliedTabletDefault.current = true;
  }, [viewport.isMobile, viewport.isTablet]);

  const observeLayout = useCallback((node: HTMLDivElement | null) => {
    if (!node) {
      return;
    }
    const observer = new ResizeObserver(entries => {
      const width = entries[0]?.contentRect.width ?? 0;
      layoutWidthRef.current = width;
    });
    observer.observe(node);
    layoutWidthRef.current = node.getBoundingClientRect().width;
    return () => observer.disconnect();
  }, []);

  const maxRoomsWidth = useCallback(() => {
    const handles =
      viewport.isResizable && infoOpen ? CHAT_LAYOUT_LIMITS.handleWidth * 2 : CHAT_LAYOUT_LIMITS.handleWidth;
    const info = viewport.isResizable && infoOpen ? infoWidth : 0;
    const available = layoutWidthRef.current - info - handles - CHAT_LAYOUT_LIMITS.chatMin;
    return clamp(available, CHAT_LAYOUT_LIMITS.roomsMin, CHAT_LAYOUT_LIMITS.roomsMax);
  }, [infoOpen, infoWidth, viewport.isResizable]);

  const maxInfoWidth = useCallback(() => {
    const handles = viewport.isResizable ? CHAT_LAYOUT_LIMITS.handleWidth * 2 : 0;
    const available = layoutWidthRef.current - roomsWidth - handles - CHAT_LAYOUT_LIMITS.chatMin;
    return clamp(available, CHAT_LAYOUT_LIMITS.infoMin, CHAT_LAYOUT_LIMITS.infoMax);
  }, [roomsWidth, viewport.isResizable]);

  const dragRooms = useCallback(
    (deltaX: number) => {
      setRoomsWidth(prev => clamp(prev + deltaX, CHAT_LAYOUT_LIMITS.roomsMin, maxRoomsWidth()));
    },
    [maxRoomsWidth],
  );

  const dragInfo = useCallback(
    (deltaX: number) => {
      setInfoWidth(prev => clamp(prev + deltaX, CHAT_LAYOUT_LIMITS.infoMin, maxInfoWidth()));
    },
    [maxInfoWidth],
  );

  const resetRoomsWidth = useCallback(() => {
    setRoomsWidth(CHAT_LAYOUT_LIMITS.roomsDefault);
    persist({roomsWidth: CHAT_LAYOUT_LIMITS.roomsDefault});
  }, [persist]);

  const resetInfoWidth = useCallback(() => {
    setInfoWidth(CHAT_LAYOUT_LIMITS.infoDefault);
    persist({infoWidth: CHAT_LAYOUT_LIMITS.infoDefault});
  }, [persist]);

  const closeInfoPanel = useCallback(() => {
    setInfoOpen(false);
    persist({infoOpen: false});
  }, [persist]);

  const openInfoPanel = useCallback(() => {
    setInfoOpen(true);
    persist({infoOpen: true});
  }, [persist]);

  const [roomsDrawerOpen, setRoomsDrawerOpen] = useState(false);

  const openRoomsDrawer = useCallback(() => {
    setRoomsDrawerOpen(true);
  }, []);

  const closeRoomsDrawer = useCallback(() => {
    setRoomsDrawerOpen(false);
  }, []);

  useEffect(() => {
    if (!viewport.isMobile) {
      setRoomsDrawerOpen(false);
      return;
    }
    setInfoOpen(false);
  }, [viewport.isMobile]);

  const onRoomsDragEnd = useCallback(() => {
    persist({roomsWidth});
  }, [persist, roomsWidth]);

  const onInfoDragEnd = useCallback(() => {
    persist({infoWidth});
  }, [persist, infoWidth]);

  return {
    roomsWidth,
    infoWidth,
    infoOpen,
    isMobile: viewport.isMobile,
    isResizable: viewport.isResizable,
    observeLayout,
    dragRooms,
    dragInfo,
    resetRoomsWidth,
    resetInfoWidth,
    closeInfoPanel,
    openInfoPanel,
    roomsDrawerOpen,
    openRoomsDrawer,
    closeRoomsDrawer,
    onRoomsDragEnd,
    onInfoDragEnd,
  };
}
