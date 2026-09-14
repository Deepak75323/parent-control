import {type FormEvent, type ReactNode, useEffect, useMemo, useState} from 'react';
import {Avatar} from '../Avatar';
import {textChannels, voiceChannels} from '../../lib/chatChannelNav';
import type {ChatChannelItem, ChatRoomSummary} from '../../types/api';
import {ChatVoiceSelfBar} from './ChatVoiceSelfBar';

/** Friend DMs use directMessage; fall back to 2-member heuristic for older API payloads. */
export function isDirectStyleRoom(room: ChatRoomSummary): boolean {
  if (typeof room.directMessage === 'boolean') {
    return room.directMessage;
  }
  return room.memberCount === 2;
}

export type ChatListFilter = 'all' | 'rooms' | 'friends' | 'dms';

/** Which sidebar sections appear for All / Rooms / Friends / DMs tabs. */
export function chatListSectionVisible(
  filter: ChatListFilter,
  section: 'rooms' | 'friends' | 'dms',
): boolean {
  return filter === 'all' || filter === section;
}

type Props = {
  rooms: ChatRoomSummary[];
  selectedRoomId: string | null;
  selectedChannelId: string | null;
  activeVoiceChannelId?: string | null;
  voiceOccupancy?: Record<string, number>;
  newRoomName: string;
  busy: boolean;
  swipeEnabled?: boolean;
  showClose?: boolean;
  onSelectTextChannel: (roomId: string, channelId: string) => void;
  onSelectVoiceChannel: (roomId: string, channelId: string) => void;
  onSelectServer: (roomId: string) => void;
  onRemoveRoom: (roomId: string) => void;
  onCreateChannel: (roomId: string, name: string, channelType: 'TEXT' | 'VOICE') => void;
  onClose?: () => void;
  onNewRoomNameChange: (value: string) => void;
  onCreateRoom: (event: FormEvent) => void;
  onConnectParent?: () => void;
  onInviteParents?: () => void;
  onManageMembers?: () => void;
  /** Accepted friends (graph); click opens dmRoomId when present. */
  friends?: Array<{
    friendshipId?: string;
    userId: string;
    label: string;
    nickname: string;
    dmRoomId?: string | null;
  }>;
  onSelectFriend?: (friend: {
    friendshipId?: string;
    userId: string;
    label: string;
    nickname: string;
    dmRoomId?: string | null;
  }) => void;
  onRemoveFriend?: (friend: {
    friendshipId?: string;
    userId: string;
    label: string;
    nickname: string;
    dmRoomId?: string | null;
  }) => void;
  /** Shown in Discord-style self / mute bar. */
  selfDisplayName?: string;
  selfSeed?: string;
  onOpenSelfSettings?: () => void;
};

export function ChatRoomsPanel({
  rooms,
  selectedRoomId,
  selectedChannelId,
  activeVoiceChannelId = null,
  voiceOccupancy = {},
  newRoomName,
  busy,
  showClose = false,
  onSelectTextChannel,
  onSelectVoiceChannel,
  onSelectServer,
  onRemoveRoom,
  onCreateChannel,
  onClose,
  onNewRoomNameChange,
  onCreateRoom,
  onConnectParent,
  onInviteParents,
  onManageMembers,
  friends = [],
  onSelectFriend,
  onRemoveFriend,
  selfDisplayName = 'You',
  selfSeed = 'self',
  onOpenSelfSettings,
}: Props) {
  const directRooms = useMemo(() => rooms.filter(isDirectStyleRoom), [rooms]);
  const servers = useMemo(() => rooms.filter(room => !isDirectStyleRoom(room)), [rooms]);
  const friendByDmRoomId = useMemo(() => {
    const map = new Map<string, (typeof friends)[number]>();
    for (const friend of friends) {
      if (friend.dmRoomId) {
        map.set(friend.dmRoomId, friend);
      }
    }
    return map;
  }, [friends]);

  const selectedRoom = rooms.find(room => room.id === selectedRoomId) ?? null;
  const viewingDirect = Boolean(selectedRoom && isDirectStyleRoom(selectedRoom));

  /** Keep last server for channels/header while a DM is open — otherwise you can't switch back. */
  const [serverContextId, setServerContextId] = useState<string | null>(null);
  useEffect(() => {
    if (selectedRoom && !isDirectStyleRoom(selectedRoom)) {
      setServerContextId(selectedRoom.id);
    }
  }, [selectedRoom]);
  useEffect(() => {
    if (serverContextId && !servers.some(room => room.id === serverContextId)) {
      setServerContextId(servers[0]?.id ?? null);
    } else if (!serverContextId && servers.length > 0) {
      setServerContextId(servers[0].id);
    }
  }, [servers, serverContextId]);

  const selectedServer =
    servers.find(room => room.id === serverContextId) ?? servers[0] ?? null;

  const [serverMenuOpen, setServerMenuOpen] = useState(false);
  const [serversOpen, setServersOpen] = useState(true);
  const [textOpen, setTextOpen] = useState(true);
  const [voiceOpen, setVoiceOpen] = useState(true);
  const [dmOpen, setDmOpen] = useState(true);
  const [friendsOpen, setFriendsOpen] = useState(true);
  const [listFilter, setListFilter] = useState<ChatListFilter>('all');
  const [listQuery, setListQuery] = useState('');
  const [addingType, setAddingType] = useState<'TEXT' | 'VOICE' | null>(null);
  const [newChannelName, setNewChannelName] = useState('');

  const texts = textChannels(selectedServer);
  const voices = voiceChannels(selectedServer);
  const canCreateChannels =
    Boolean(selectedServer) &&
    (selectedServer!.owner || selectedServer!.myRole === 'ADMIN');

  const query = listQuery.trim().toLowerCase();
  const matchesQuery = (value: string) => !query || value.toLowerCase().includes(query);
  const showRooms = chatListSectionVisible(listFilter, 'rooms');
  const showFriends = chatListSectionVisible(listFilter, 'friends');
  const showDms = chatListSectionVisible(listFilter, 'dms');
  const filteredFriends = friends.filter(friend =>
    matchesQuery(`${friend.nickname} ${friend.label}`),
  );
  const filteredServers = servers.filter(room => matchesQuery(room.name));
  const filteredDirectRooms = directRooms.filter(room => {
    const linked = friendByDmRoomId.get(room.id);
    return matchesQuery(linked ? `${linked.nickname} ${linked.label}` : room.name);
  });
  const filteredTexts = texts.filter(channel => matchesQuery(channel.name));
  const filteredVoices = voices.filter(channel => matchesQuery(channel.name));

  function openServer(roomId: string) {
    setServerContextId(roomId);
    setServerMenuOpen(false);
    onSelectServer(roomId);
  }

  function submitNewChannel(event: FormEvent) {
    event.preventDefault();
    if (!selectedServer || !addingType || !newChannelName.trim()) {
      return;
    }
    onCreateChannel(selectedServer.id, newChannelName.trim(), addingType);
    setNewChannelName('');
    setAddingType(null);
  }

  return (
    <aside className="chat-rooms-panel chat-rooms-panel--community" aria-label="Servers and channels">
      <div className="chat-server-header">
        <button
          type="button"
          className="chat-server-header-btn"
          aria-expanded={serverMenuOpen}
          aria-haspopup="menu"
          onClick={() => setServerMenuOpen(open => !open)}>
          <div className="chat-server-header-copy">
            <strong className="chat-server-header-name">
              {selectedServer?.name ?? 'Parent Community'}
            </strong>
            <span className="chat-server-header-meta">
              {viewingDirect
                ? 'Direct message open — pick a server to return'
                : selectedServer
                  ? `${selectedServer.memberCount} member${selectedServer.memberCount === 1 ? '' : 's'}`
                  : 'Select or create a server'}
            </span>
          </div>
          <span className="chat-server-header-caret" aria-hidden>
            ▾
          </span>
        </button>
        {showClose && onClose ? (
          <button
            type="button"
            className="chat-panel-close"
            aria-label="Hide channels panel"
            title="Hide channels"
            onClick={onClose}>
            ×
          </button>
        ) : null}
      </div>

      {serverMenuOpen ? (
        <div className="chat-server-menu" role="menu">
          {servers.length === 0 ? (
            <p className="chat-room-empty">No servers yet</p>
          ) : (
            <>
              {servers.map(room => (
                <button
                  key={room.id}
                  type="button"
                  role="menuitem"
                  className={`chat-server-menu-item${
                    room.id === selectedServer?.id && !viewingDirect ? ' active' : ''
                  }`}
                  onClick={() => {
                    openServer(room.id);
                  }}>
                  <span className="chat-server-glyph" aria-hidden>
                    {room.name.slice(0, 1).toUpperCase()}
                  </span>
                  <span>
                    <span className="chat-room-name">{room.name}</span>
                    <span className="chat-room-sub">
                      {room.memberCount} members
                      {(room.unreadCount ?? 0) > 0 ? ` · ${room.unreadCount} unread` : ''}
                    </span>
                  </span>
                </button>
              ))}
              {selectedServer && !viewingDirect ? (
                <>
                  <div className="chat-server-menu-divider" />
                  {onInviteParents ? (
                    <button
                      type="button"
                      role="menuitem"
                      className="chat-server-menu-action"
                      onClick={() => {
                        setServerMenuOpen(false);
                        onInviteParents();
                      }}>
                      Invite parents
                    </button>
                  ) : null}
                  {onManageMembers ? (
                    <button
                      type="button"
                      role="menuitem"
                      className="chat-server-menu-action"
                      onClick={() => {
                        setServerMenuOpen(false);
                        onManageMembers();
                      }}>
                      Manage members
                    </button>
                  ) : null}
                  <button
                    type="button"
                    role="menuitem"
                    className="chat-server-menu-action chat-server-menu-action--danger"
                    disabled={busy}
                    onClick={() => {
                      setServerMenuOpen(false);
                      onRemoveRoom(selectedServer.id);
                    }}>
                    {selectedServer.owner ? 'Delete server' : 'Leave server'}
                  </button>
                </>
              ) : null}
            </>
          )}
          <form className="chat-create chat-create--menu" onSubmit={onCreateRoom}>
            <input
              type="text"
              placeholder="New server name"
              value={newRoomName}
              maxLength={64}
              aria-label="New server name"
              onChange={event => onNewRoomNameChange(event.target.value)}
            />
            <button
              type="submit"
              className="btn primary"
              disabled={busy || !newRoomName.trim()}
              aria-label="Create server">
              +
            </button>
          </form>
        </div>
      ) : null}

      <div className="chat-list-toolbar">
        <label className="chat-list-search">
          <span className="visually-hidden">Search chats</span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3-3" />
          </svg>
          <input
            type="search"
            value={listQuery}
            placeholder="Search chats, rooms or parents…"
            onChange={event => setListQuery(event.target.value)}
          />
        </label>
        <div className="chat-list-filters" role="tablist" aria-label="Chat list filter">
          {(
            [
              ['all', 'All'],
              ['rooms', 'Rooms'],
              ['friends', 'Friends'],
              ['dms', 'DMs'],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={listFilter === id}
              className={`chat-list-filter${listFilter === id ? ' active' : ''}`}
              onClick={() => setListFilter(id)}>
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="chat-comm-scroll">
        {showRooms ? (
          <CategorySection
            id="servers"
            title="Servers"
            open={serversOpen}
            onToggle={() => setServersOpen(v => !v)}>
            {filteredServers.length === 0 ? (
              <p className="chat-channel-empty">
                {servers.length === 0
                  ? 'Create a server to chat with other parents.'
                  : 'No servers match your search.'}
              </p>
            ) : (
              filteredServers.map(room => (
                <button
                  key={room.id}
                  type="button"
                  className={`chat-dm-item${
                    !viewingDirect && room.id === selectedRoomId ? ' active' : ''
                  }`}
                  onClick={() => openServer(room.id)}>
                  <span className="chat-server-glyph" aria-hidden>
                    {room.name.slice(0, 1).toUpperCase()}
                  </span>
                  <span className="chat-dm-meta">
                    <span className="chat-dm-name">{room.name}</span>
                    <span className="muted tiny">
                      {room.memberCount} member{room.memberCount === 1 ? '' : 's'}
                      {(room.unreadCount ?? 0) > 0 ? ` · ${room.unreadCount} unread` : ''}
                    </span>
                  </span>
                </button>
              ))
            )}
          </CategorySection>
        ) : null}

        {showDms ? (
        <CategorySection
          id="direct-messages"
          title="Direct messages"
          open={dmOpen}
          onToggle={() => setDmOpen(v => !v)}
          onAdd={onConnectParent}
          addLabel="Add friend">
          {filteredDirectRooms.length === 0 ? (
            <p className="chat-channel-empty">
              {directRooms.length === 0
                ? 'Direct chats open after a friend accepts your request.'
                : 'No direct messages match your search.'}
            </p>
          ) : (
            filteredDirectRooms.map(room => {
              const defaultText = textChannels(room)[0];
              const linkedFriend = friendByDmRoomId.get(room.id);
              const displayName = linkedFriend
                ? linkedFriend.nickname || linkedFriend.label
                : room.name;
              return (
                <button
                  key={room.id}
                  type="button"
                  className={`chat-dm-item${room.id === selectedRoomId ? ' active' : ''}`}
                  onClick={() => {
                    if (defaultText) {
                      onSelectTextChannel(room.id, defaultText.id);
                    } else {
                      onSelectServer(room.id);
                    }
                  }}>
                  <Avatar name={displayName} seed={linkedFriend?.userId ?? room.id} size="sm" />
                  <span className="chat-dm-meta">
                    <span className="chat-dm-name">{displayName}</span>
                    {(room.unreadCount ?? 0) > 0 ? (
                      <span className="chat-room-unread-badge">{room.unreadCount}</span>
                    ) : null}
                  </span>
                </button>
              );
            })
          )}
        </CategorySection>
        ) : null}

        {showFriends ? (
        <CategorySection
          id="friends"
          title="Friends"
          open={friendsOpen}
          onToggle={() => setFriendsOpen(v => !v)}
          onAdd={onConnectParent}
          addLabel="Add friend">
          {filteredFriends.length === 0 ? (
            <p className="chat-channel-empty">
              {friends.length === 0
                ? 'Add a parent by @handle or email. After they accept, they appear here.'
                : 'No friends match your search.'}
            </p>
          ) : (
            filteredFriends.map(friend => (
              <div
                key={friend.userId}
                className={`chat-dm-row${
                  friend.dmRoomId && friend.dmRoomId === selectedRoomId ? ' active' : ''
                }`}>
                <button
                  type="button"
                  className="chat-dm-item"
                  disabled={busy}
                  onClick={() => onSelectFriend?.(friend)}>
                  <Avatar name={friend.nickname || friend.label} seed={friend.userId} size="sm" />
                  <span className="chat-dm-meta">
                    <span className="chat-dm-name">{friend.nickname || friend.label}</span>
                    <span className="muted tiny">{friend.label}</span>
                  </span>
                </button>
                {onRemoveFriend ? (
                  <button
                    type="button"
                    className="chat-dm-remove"
                    title="Remove friend"
                    aria-label={`Remove ${friend.nickname || friend.label}`}
                    disabled={busy}
                    onClick={event => {
                      event.stopPropagation();
                      onRemoveFriend(friend);
                    }}>
                    ×
                  </button>
                ) : null}
              </div>
            ))
          )}
        </CategorySection>
        ) : null}

        {showRooms && selectedServer ? (
          <>
            {viewingDirect ? (
              <p className="chat-channel-empty chat-channel-empty--dm-hint">
                Viewing a direct message. Click a server or channel above to return.
              </p>
            ) : null}
            <CategorySection
              id="text-channels"
              title="Text channels"
              open={textOpen}
              onToggle={() => setTextOpen(v => !v)}
              onAdd={
                canCreateChannels
                  ? () => setAddingType(addingType === 'TEXT' ? null : 'TEXT')
                  : undefined
              }
              addLabel="Create text channel">
              {filteredTexts.length === 0 ? (
                <p className="chat-channel-empty">No text channels</p>
              ) : (
                filteredTexts.map(channel => (
                  <ChannelRow
                    key={channel.id}
                    channel={channel}
                    selected={
                      !viewingDirect &&
                      selectedServer.id === selectedRoomId &&
                      channel.id === selectedChannelId
                    }
                    unread={
                      channel.id === selectedChannelId &&
                      selectedServer.id === selectedRoomId &&
                      !viewingDirect
                        ? 0
                        : (selectedServer.unreadCount ?? 0) > 0 &&
                            channel.name.toLowerCase() === 'general'
                          ? selectedServer.unreadCount
                          : 0
                    }
                    onSelect={() => onSelectTextChannel(selectedServer.id, channel.id)}
                  />
                ))
              )}
            </CategorySection>

            <CategorySection
              id="voice-channels"
              title="Voice channels"
              open={voiceOpen}
              onToggle={() => setVoiceOpen(v => !v)}
              onAdd={
                canCreateChannels
                  ? () => setAddingType(addingType === 'VOICE' ? null : 'VOICE')
                  : undefined
              }
              addLabel="Create voice channel">
              {filteredVoices.length === 0 ? (
                <p className="chat-channel-empty">No voice channels</p>
              ) : (
                filteredVoices.map(channel => (
                  <ChannelRow
                    key={channel.id}
                    channel={channel}
                    voice
                    selected={channel.id === activeVoiceChannelId}
                    connected={channel.id === activeVoiceChannelId}
                    occupancy={voiceOccupancy[channel.id] ?? 0}
                    onSelect={() => onSelectVoiceChannel(selectedServer.id, channel.id)}
                  />
                ))
              )}
            </CategorySection>

            {addingType ? (
              <form className="chat-channel-create" onSubmit={submitNewChannel}>
                <input
                  type="text"
                  placeholder={addingType === 'TEXT' ? 'new-text-channel' : 'New voice channel'}
                  value={newChannelName}
                  maxLength={64}
                  autoFocus
                  aria-label="New channel name"
                  onChange={event => setNewChannelName(event.target.value)}
                />
                <button
                  type="submit"
                  className="btn primary"
                  disabled={busy || !newChannelName.trim()}>
                  Create
                </button>
              </form>
            ) : null}
          </>
        ) : null}
      </div>

      <div className="chat-comm-footer">
        <ChatVoiceSelfBar
          displayName={selfDisplayName}
          seed={selfSeed}
          onOpenSettings={onOpenSelfSettings}
        />
        {onConnectParent ? (
          <button
            type="button"
            className="btn chat-connect-parent-btn"
            disabled={busy}
            onClick={onConnectParent}>
            + Add Friend
          </button>
        ) : null}
      </div>
    </aside>
  );
}

function CategorySection({
  id,
  title,
  open,
  onToggle,
  onAdd,
  addLabel,
  children,
}: {
  id: string;
  title: string;
  open: boolean;
  onToggle: () => void;
  onAdd?: () => void;
  addLabel?: string;
  children: ReactNode;
}) {
  return (
    <section className="chat-category" aria-labelledby={`${id}-heading`}>
      <div className="chat-category-head">
        <button
          type="button"
          id={`${id}-heading`}
          className="chat-category-toggle"
          aria-expanded={open}
          onClick={onToggle}>
          <span className="chat-category-caret" aria-hidden>
            {open ? '▾' : '▸'}
          </span>
          {title}
        </button>
        {onAdd ? (
          <button
            type="button"
            className="chat-channel-add"
            title={addLabel}
            aria-label={addLabel}
            onClick={onAdd}>
            +
          </button>
        ) : null}
      </div>
      {open ? <div className="chat-category-body">{children}</div> : null}
    </section>
  );
}

function ChannelRow({
  channel,
  selected,
  voice = false,
  connected = false,
  unread = 0,
  occupancy = 0,
  onSelect,
}: {
  channel: ChatChannelItem;
  selected: boolean;
  voice?: boolean;
  connected?: boolean;
  unread?: number;
  occupancy?: number;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      className={`chat-channel-item${selected ? ' active' : ''}${
        voice ? ' chat-channel-item--voice' : ''
      }${connected ? ' chat-channel-item--connected' : ''}`}
      onClick={onSelect}>
      <span className="chat-channel-prefix" aria-hidden>
        {voice ? (
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z" />
          </svg>
        ) : (
          '#'
        )}
      </span>
      <span className="chat-channel-name">{channel.name}</span>
      {voice ? (
        <span className="chat-channel-occupancy" aria-label={`${occupancy} in channel`}>
          {occupancy}
        </span>
      ) : unread > 0 ? (
        <span className="chat-room-unread-badge">{unread}</span>
      ) : null}
    </button>
  );
}
