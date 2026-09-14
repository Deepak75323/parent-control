import {useEffect, useRef, type ReactNode, type CSSProperties} from 'react';
import type {useChatWorkspaceLayout} from '../../hooks/useChatWorkspaceLayout';
import type {MemberPresenceView} from '../../lib/chatMemberRoster';
import type {ChatCallParticipant, ChatMemberItem, ChatRoomSummary} from '../../types/api';
import {ChatRoomsDrawer} from './ChatRoomsDrawer';
import {ParentChatInfoPanel} from './ParentChatInfoPanel';
import {ResizeHandle} from './ResizeHandle';

export type ChatWorkspaceLayoutState = ReturnType<typeof useChatWorkspaceLayout>;

export type ChatInfoPanelContext = {
  room: ChatRoomSummary | null;
  members: ChatMemberItem[];
  memberPresenceByUserId?: Record<string, MemberPresenceView>;
  myUserId?: string;
  canManageMembers?: boolean;
  busy?: boolean;
  onInvite?: () => void;
  onManageMembers?: () => void;
  callParticipants?: ChatCallParticipant[] | null;
  callChannelName?: string | null;
  sideChat?: ReactNode;
};

type Props = {
  layout: ChatWorkspaceLayoutState;
  renderRooms: () => ReactNode;
  thread: ReactNode;
  infoContext?: ChatInfoPanelContext;
  /** On mobile, hide the thread until a room is selected. */
  mobileThreadVisible?: boolean;
  onMobileBackToApp?: () => void;
  onCloseRoomsDrawer?: () => void;
};

export function ChatWorkspaceLayout({
  layout,
  renderRooms,
  thread,
  infoContext,
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
          aria-label="Close details panel"
          onClick={layout.closeInfoPanel}
        />
      ) : null}
      <div
        ref={layoutRef}
        className={[
          'chat-layout',
          layout.roomsOpen ? 'chat-layout--rooms-open' : 'chat-layout--rooms-closed',
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
              } as CSSProperties)
            : undefined
        }>
        {!layout.isMobile && layout.roomsOpen ? (
          <div className="chat-rooms-wrap">{renderRooms()}</div>
        ) : null}

        {layout.isResizable && layout.roomsOpen ? (
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
            label="Resize details panel"
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
          room={infoContext?.room ?? null}
          members={infoContext?.members ?? []}
          memberPresenceByUserId={infoContext?.memberPresenceByUserId}
          myUserId={infoContext?.myUserId}
          canManageMembers={infoContext?.canManageMembers}
          busy={infoContext?.busy}
          onInvite={infoContext?.onInvite}
          onManageMembers={infoContext?.onManageMembers}
          callParticipants={infoContext?.callParticipants}
          callChannelName={infoContext?.callChannelName}
          sideChat={infoContext?.sideChat}
        />
      </div>
    </>
  );
}
