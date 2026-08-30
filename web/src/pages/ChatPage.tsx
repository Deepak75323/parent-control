import {type FormEvent, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {useLocation} from 'react-router-dom';
import axios from 'axios';
import {chatApi} from '../api/services';
import {useRealtimeStore} from '../realtime/realtimeStore';
import type {ChatInviteItem, ChatMessageItem, ChatRoomSummary} from '../types/api';

function roomInitial(name: string) {
  return (name.trim()[0] ?? '?').toUpperCase();
}

export function ChatPage() {
  const location = useLocation();
  const [rooms, setRooms] = useState<ChatRoomSummary[]>([]);
  const [invites, setInvites] = useState<ChatInviteItem[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [draft, setDraft] = useState('');
  const [newRoomName, setNewRoomName] = useState('');
  const [inviteIdentifier, setInviteIdentifier] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const initialRoomLoadRef = useRef(true);

  const chatRoomsEpoch = useRealtimeStore(s => s.chatRoomsEpoch);
  const markChatRoomRemoved = useRealtimeStore(s => s.markChatRoomRemoved);
  const unmarkChatRoomRemoved = useRealtimeStore(s => s.unmarkChatRoomRemoved);
  const pruneLiveChatMessages = useRealtimeStore(s => s.pruneLiveChatMessages);
  const chatInvitesEpoch = useRealtimeStore(s => s.chatInvitesEpoch);
  const liveChatMessages = useRealtimeStore(s => s.liveChatMessages);

  const selectedRoom = useMemo(
    () => rooms.find(room => room.id === selectedRoomId) ?? null,
    [rooms, selectedRoomId],
  );

  const loadRooms = useCallback(async () => {
    try {
      const {data} = await chatApi.listRooms();
      const apiRooms = data.data ?? [];
      const removed = useRealtimeStore.getState().removedChatRoomIds;
      const next = apiRooms.filter(room => !removed[room.id]);
      for (const id of Object.keys(removed)) {
        if (!apiRooms.some(room => room.id === id)) {
          unmarkChatRoomRemoved(id);
        }
      }
      pruneLiveChatMessages(next.map(room => room.id));
      setRooms(next);
      setSelectedRoomId(prev => {
        if (prev && next.some(room => room.id === prev)) {
          return prev;
        }
        if (prev && !next.some(room => room.id === prev)) {
          return next[0]?.id ?? null;
        }
        if (!prev && initialRoomLoadRef.current && next.length > 0) {
          initialRoomLoadRef.current = false;
          return next[0]?.id ?? null;
        }
        return prev;
      });
      if (initialRoomLoadRef.current && next.length === 0) {
        initialRoomLoadRef.current = false;
      }
      setMessages(prev => {
        if (prev.length === 0) {
          return prev;
        }
        const allowed = new Set(next.map(room => room.id));
        return prev.filter(message => allowed.has(message.roomId));
      });
    } catch {
      setError('Failed to load chat rooms');
    }
  }, [pruneLiveChatMessages, unmarkChatRoomRemoved]);

  const loadInvites = useCallback(async () => {
    try {
      const {data} = await chatApi.listPendingInvites();
      setInvites(data.data ?? []);
    } catch {
      setError('Failed to load invites');
    }
  }, []);

  const loadMessages = useCallback(async (roomId: string) => {
    try {
      const {data} = await chatApi.listMessages(roomId);
      setMessages(data.data ?? []);
    } catch {
      setError('Failed to load messages');
    }
  }, []);

  useEffect(() => {
    initialRoomLoadRef.current = true;
    void loadRooms();
    void loadInvites();
  }, [loadRooms, loadInvites, chatRoomsEpoch, location.pathname]);

  useEffect(() => {
    void loadInvites();
  }, [loadInvites, chatInvitesEpoch]);

  useEffect(() => {
    if (!selectedRoomId) {
      return;
    }
    void loadMessages(selectedRoomId);
  }, [selectedRoomId, loadMessages]);

  useEffect(() => {
    if (!selectedRoomId) {
      return;
    }
    const live = liveChatMessages.filter(message => message.roomId === selectedRoomId);
    if (live.length === 0) {
      return;
    }
    setMessages(prev => {
      const merged = [...prev];
      for (const message of live) {
        if (!merged.some(existing => existing.id === message.id)) {
          merged.push(message);
        }
      }
      return merged;
    });
  }, [liveChatMessages, selectedRoomId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
  }, [messages]);

  async function handleCreateRoom(event: FormEvent) {
    event.preventDefault();
    const name = newRoomName.trim();
    if (!name) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const {data} = await chatApi.createRoom(name);
      const room = data.data;
      if (room) {
        setRooms(prev => [room, ...prev]);
        setSelectedRoomId(room.id);
        setNewRoomName('');
      }
    } catch {
      setError('Could not create room');
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteRoom(roomId: string) {
    const room = rooms.find(entry => entry.id === roomId);
    if (!room?.owner) {
      return;
    }
    if (!window.confirm(`Delete "${room.name}"? This removes the room for all members.`)) {
      return;
    }
    setBusy(true);
    setError(null);
    const wasSelected = selectedRoomId === roomId;
    setRooms(prev => prev.filter(entry => entry.id !== roomId));
    if (wasSelected) {
      setSelectedRoomId(null);
      setMessages([]);
    }
    markChatRoomRemoved(roomId);
    try {
      await chatApi.deleteRoom(roomId);
    } catch (err) {
      unmarkChatRoomRemoved(roomId);
      await loadRooms();
      const apiMessage =
        axios.isAxiosError(err) &&
        typeof err.response?.data === 'object' &&
        err.response.data !== null &&
        'message' in err.response.data &&
        typeof (err.response.data as {message?: string}).message === 'string'
          ? (err.response.data as {message: string}).message
          : null;
      setError(apiMessage ?? 'Could not delete room');
    } finally {
      setBusy(false);
    }
  }

  async function handleInvite(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId) {
      return;
    }
    const identifier = inviteIdentifier.trim();
    if (!identifier) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await chatApi.invite(selectedRoomId, identifier);
      setInviteIdentifier('');
    } catch {
      setError('Could not send invite — check handle or email');
    } finally {
      setBusy(false);
    }
  }

  async function respondInvite(inviteId: string, accept: boolean) {
    setBusy(true);
    setError(null);
    try {
      const {data} = await chatApi.respondInvite(inviteId, accept);
      setInvites(prev => prev.filter(invite => invite.id !== inviteId));
      if (accept && data.data?.roomId) {
        setSelectedRoomId(data.data.roomId);
        void loadRooms();
      }
    } catch {
      setError('Could not respond to invite');
    } finally {
      setBusy(false);
    }
  }

  async function handleSendMessage(event: FormEvent) {
    event.preventDefault();
    if (!selectedRoomId) {
      return;
    }
    const body = draft.trim();
    if (!body) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const {data} = await chatApi.sendMessage(selectedRoomId, body);
      if (data.data) {
        setMessages(prev =>
          prev.some(message => message.id === data.data!.id) ? prev : [...prev, data.data!],
        );
      }
      setDraft('');
    } catch {
      setError('Could not send message');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="chat-page">
      {invites.length > 0 ? (
        <div className="chat-invites-banner">
          <div className="chat-invites-banner-head">
            <strong>{invites.length} pending invite{invites.length === 1 ? '' : 's'}</strong>
          </div>
          <ul className="chat-invites-list">
            {invites.map(invite => (
              <li key={invite.id} className="chat-invite-row-item">
                <div>
                  <span className="chat-invite-room">{invite.roomName}</span>
                  <span className="muted tiny"> from {invite.inviterLabel}</span>
                </div>
                <div className="chat-invite-actions">
                  <button
                    type="button"
                    className="btn primary sm"
                    disabled={busy}
                    onClick={() => void respondInvite(invite.id, true)}>
                    Accept
                  </button>
                  <button
                    type="button"
                    className="btn ghost sm"
                    disabled={busy}
                    onClick={() => void respondInvite(invite.id, false)}>
                    Decline
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {error ? <div className="chat-error">{error}</div> : null}

      <div className="chat-layout">
        <aside className="chat-rooms-panel">
          <div className="chat-panel-head">
            <h2>Your rooms</h2>
          </div>
          <div className="chat-room-list">
            {rooms.length === 0 ? (
              <p className="chat-room-empty">No rooms yet</p>
            ) : (
              rooms.map(room => (
                <div
                  key={room.id}
                  className={`chat-room-item${room.id === selectedRoomId ? ' active' : ''}`}>
                  <button
                    type="button"
                    className="chat-room-select"
                    onClick={() => setSelectedRoomId(room.id)}>
                    <span className="chat-room-icon">{roomInitial(room.name)}</span>
                    <span className="chat-room-meta">
                      <span className="chat-room-name">{room.name}</span>
                      <span className="chat-room-sub">
                        {room.memberCount} member{room.memberCount === 1 ? '' : 's'}
                        {room.owner ? ' · owner' : ''}
                      </span>
                    </span>
                  </button>
                  {room.owner ? (
                    <button
                      type="button"
                      className="chat-room-delete"
                      title={`Delete ${room.name}`}
                      disabled={busy}
                      onClick={() => void handleDeleteRoom(room.id)}>
                      🗑
                    </button>
                  ) : null}
                </div>
              ))
            )}
          </div>
          <form className="chat-create" onSubmit={handleCreateRoom}>
            <input
              type="text"
              placeholder="New room name"
              value={newRoomName}
              maxLength={64}
              onChange={event => setNewRoomName(event.target.value)}
            />
            <button type="submit" className="btn primary" disabled={busy || !newRoomName.trim()}>
              +
            </button>
          </form>
        </aside>

        <section className="chat-thread-panel">
          {!selectedRoom ? (
            <div className="chat-empty">
              <div className="chat-empty-icon">💬</div>
              <h2>Select a room</h2>
              <p className="muted">
                Click a room on the left to open it. Owners can delete with the trash icon or the
                Delete room button.
              </p>
            </div>
          ) : (
            <>
              <header className="chat-thread-head">
                <div className="chat-thread-title">
                  <span className="chat-room-icon lg">{roomInitial(selectedRoom.name)}</span>
                  <div>
                    <h2>{selectedRoom.name}</h2>
                    <p className="muted tiny">
                      {selectedRoom.memberCount} members
                      {selectedRoom.owner ? ' · you own this room' : ''}
                    </p>
                  </div>
                </div>
                {selectedRoom.owner ? (
                  <button
                    type="button"
                    className="btn ghost sm"
                    disabled={busy}
                    onClick={() => void handleDeleteRoom(selectedRoom.id)}>
                    Delete room
                  </button>
                ) : null}
              </header>

              <form className="chat-invite-row" onSubmit={handleInvite}>
                <input
                  type="text"
                  placeholder="Invite parent — @handle or email"
                  value={inviteIdentifier}
                  onChange={event => setInviteIdentifier(event.target.value)}
                />
                <button type="submit" className="btn ghost sm" disabled={busy || !inviteIdentifier.trim()}>
                  Invite
                </button>
              </form>

              <div className="chat-messages">
                {messages.length === 0 ? (
                  <p className="chat-messages-empty muted">No messages yet — say hello.</p>
                ) : (
                  messages.map(message => (
                    <article key={message.id} className="chat-message">
                      <div className="chat-message-head">
                        <span className="chat-message-author">{message.senderLabel}</span>
                        <time className="chat-message-time">
                          {new Date(message.createdAt).toLocaleString()}
                        </time>
                      </div>
                      <p className="chat-message-body">{message.body}</p>
                    </article>
                  ))
                )}
                <div ref={messagesEndRef} />
              </div>

              <form className="chat-compose" onSubmit={handleSendMessage}>
                <input
                  type="text"
                  placeholder={`Message #${selectedRoom.name}`}
                  value={draft}
                  maxLength={2000}
                  onChange={event => setDraft(event.target.value)}
                />
                <button type="submit" className="btn primary" disabled={busy || !draft.trim()}>
                  Send
                </button>
              </form>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
