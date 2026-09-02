import type {FormEvent} from 'react';
import {Avatar} from '../Avatar';
import type {ChatRoomSummary} from '../../types/api';

type Props = {
  rooms: ChatRoomSummary[];
  selectedRoomId: string | null;
  newRoomName: string;
  busy: boolean;
  onSelectRoom: (roomId: string) => void;
  onDeleteRoom: (roomId: string) => void;
  onNewRoomNameChange: (value: string) => void;
  onCreateRoom: (event: FormEvent) => void;
};

export function ChatRoomsPanel({
  rooms,
  selectedRoomId,
  newRoomName,
  busy,
  onSelectRoom,
  onDeleteRoom,
  onNewRoomNameChange,
  onCreateRoom,
}: Props) {
  return (
    <aside className="chat-rooms-panel">
      <div className="chat-panel-head">
        <h2>Rooms</h2>
        {rooms.length > 0 ? <span className="chat-panel-count">{rooms.length}</span> : null}
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
                onClick={() => onSelectRoom(room.id)}>
                <Avatar name={room.name} seed={room.id} size="md" />
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
                  aria-label={`Delete ${room.name}`}
                  disabled={busy}
                  onClick={() => onDeleteRoom(room.id)}>
                  ×
                </button>
              ) : null}
            </div>
          ))
        )}
      </div>
      <form className="chat-create" onSubmit={onCreateRoom}>
        <input
          type="text"
          placeholder="New room name"
          value={newRoomName}
          maxLength={64}
          onChange={event => onNewRoomNameChange(event.target.value)}
        />
        <button type="submit" className="btn primary" disabled={busy || !newRoomName.trim()}>
          +
        </button>
      </form>
    </aside>
  );
}
