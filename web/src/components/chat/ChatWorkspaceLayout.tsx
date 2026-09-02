import {useEffect, useRef} from 'react';
import type {useChatWorkspaceLayout} from '../../hooks/useChatWorkspaceLayout';
import {ChatRoomsDrawer} from './ChatRoomsDrawer';
import {ParentChatInfoPanel} from './ParentChatInfoPanel';
import {ResizeHandle} from './ResizeHandle';

export type ChatWorkspaceLayoutState = ReturnType<typeof useChatWorkspaceLayout>;

type Props = {
  layout: ChatWorkspaceLayoutState;
  renderRooms: () => React.ReactNode;
  thread: React.ReactNode;
  /** On mobile, hide the thread until a room is selected. */
  mobileThreadVisible?: boolean;
  onMobileBackToApp?: () => void;
  onCloseRoomsDrawer?: () => void;
};

export function ChatWorkspaceLayout({
  layout,
  renderRooms,
  thread,
  mobileThreadVisible = true,
  onMobileBackToApp,
  onCloseRoomsDrawer,
}: Props) {
  const layoutRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    return layout.observeLayout(layoutRef.current);
  }, [layout.observeLayout]);

  return (
    <>
      {layout.isMobile && layout.roomsDrawerOpen ? (
        <ChatRoomsDrawer
          open
          onClose={onCloseRoomsDrawer ?? layout.closeRoomsDrawer}
          onBackToApp={onMobileBackToApp}>
          {renderRooms()}
        </ChatRoomsDrawer>
      ) : null}
      {layout.isMobile && layout.infoOpen ? (
        <button
          type="button"
          className="chat-info-backdrop"
          aria-label="Close parent chat panel"
          onClick={layout.closeInfoPanel}
        />
      ) : null}
      <div
        ref={layoutRef}
        className={[
          'chat-layout',
          layout.infoOpen ? 'chat-layout--info-open' : 'chat-layout--info-closed',
          layout.isResizable ? 'chat-layout--resizable' : '',
          layout.isMobile ? 'chat-layout--mobile' : '',
          layout.isMobile && !mobileThreadVisible ? 'chat-layout--mobile-list-only' : '',
        ]
          .filter(Boolean)
          .join(' ')}
        style={
          layout.isResizable
            ? ({
                '--chat-rooms-width': `${layout.roomsWidth}px`,
                '--chat-info-width': layout.infoOpen ? `${layout.infoWidth}px` : '0px',
                '--chat-info-divider': layout.infoOpen ? '7px' : '0px',
              } as React.CSSProperties)
            : undefined
        }>
        {!layout.isMobile ? <div className="chat-rooms-wrap">{renderRooms()}</div> : null}

        {layout.isResizable ? (
          <ResizeHandle
            label="Resize rooms panel"
            onDrag={layout.dragRooms}
            onDragEnd={layout.onRoomsDragEnd}
            onDoubleClick={layout.resetRoomsWidth}
          />
        ) : null}

        <div className="chat-thread-wrap">{thread}</div>

        {layout.isResizable && layout.infoOpen ? (
          <ResizeHandle
            label="Resize parent chat panel"
            onDrag={layout.dragInfo}
            onDragEnd={layout.onInfoDragEnd}
            onDoubleClick={layout.resetInfoWidth}
          />
        ) : null}

        <ParentChatInfoPanel
          open={layout.infoOpen}
          width={layout.infoWidth}
          isMobile={layout.isMobile}
          isResizable={layout.isResizable}
          onClose={layout.closeInfoPanel}
        />
      </div>
    </>
  );
}
