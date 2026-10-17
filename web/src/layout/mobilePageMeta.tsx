import {createContext, useContext, useEffect, useMemo, useState, type ReactNode} from 'react';

export type MobilePageMeta = {
  title: string;
  subtitle?: string;
  /** Show ▾ and wire the title area to the registered menu handler. */
  titleMenu?: boolean;
  titleMenuExpanded?: boolean;
};

type MobilePageMetaContextValue = {
  meta: MobilePageMeta | null;
  setMeta: (meta: MobilePageMeta | null) => void;
  titleMenuHandler: (() => void) | null;
  setTitleMenuHandler: (handler: (() => void) | null) => void;
};

const MobilePageMetaContext = createContext<MobilePageMetaContextValue | null>(null);

export function MobilePageMetaProvider({children}: {children: ReactNode}) {
  const [meta, setMeta] = useState<MobilePageMeta | null>(null);
  const [titleMenuHandler, setTitleMenuHandler] = useState<(() => void) | null>(null);
  const value = useMemo(
    () => ({meta, setMeta, titleMenuHandler, setTitleMenuHandler}),
    [meta, titleMenuHandler],
  );
  return (
    <MobilePageMetaContext.Provider value={value}>{children}</MobilePageMetaContext.Provider>
  );
}

/** Override the mobile header title/subtitle while this page is mounted. */
export function useMobilePageMeta(meta: MobilePageMeta | null) {
  const ctx = useContext(MobilePageMetaContext);
  const title = meta?.title;
  const subtitle = meta?.subtitle;
  const titleMenu = meta?.titleMenu;
  const titleMenuExpanded = meta?.titleMenuExpanded;

  useEffect(() => {
    if (!ctx) {
      return;
    }
    if (!title) {
      ctx.setMeta(null);
      return;
    }
    ctx.setMeta({title, subtitle, titleMenu, titleMenuExpanded});
    return () => ctx.setMeta(null);
  }, [ctx, title, subtitle, titleMenu, titleMenuExpanded]);
}

/** Register a click handler for the mobile header title menu (when titleMenu is true). */
export function useMobilePageTitleMenu(handler: (() => void) | null, enabled = true) {
  const ctx = useContext(MobilePageMetaContext);

  useEffect(() => {
    if (!ctx) {
      return;
    }
    ctx.setTitleMenuHandler(enabled && handler ? handler : null);
    return () => ctx.setTitleMenuHandler(null);
  }, [ctx, handler, enabled]);
}

export function useMobilePageMetaOverride() {
  return useContext(MobilePageMetaContext)?.meta ?? null;
}

export function useMobilePageTitleMenuHandler() {
  return useContext(MobilePageMetaContext)?.titleMenuHandler ?? null;
}
