import {type FormEvent} from 'react';
import {Avatar} from '../Avatar';
import {useRoomSwipeAction} from '../../hooks/useRoomSwipeAction';
import type {ChatRoomSummary} from '../../types/api';

type RoomRowProps = {
  room: ChatRoomSummary;
  selected: boolean;
  busy: boolean;
  swipeEnabled: boolean;
  onSelect: () => void;
  onRemove: () => void;
};

function ChatRoomRow({room, selected, busy, swipeEnabled, onSelect, onRemove}: RoomRowProps) {
  const swipe = useRoomSwipeAction({
    enabled: swipeEnabled && !busy,
    onAction: onRemove,
  });

  return (
    <div
      className={`chat-room-item${selected ? ' active' : ''}${swipe.swiping ? ' chat-room-item--swiping' : ''}`}>
      <div
        className={`chat-room-swipe-remove${swipe.offset < 0 ? ' chat-room-swipe-remove--visible' : ''}`}
        aria-hidden>
        {room.owner ? 'Delete' : 'Leave'}
      </div>
      <div
        className="chat-room-swipe-content"
        style={swipe.offset !== 0 ? {transform: `translateX(${swipe.offset}px)`} : undefined}
        onTouchStart={swipe.onTouchStart}
        onTouchMove={swipe.onTouchMove}
        onTouchEnd={swipe.onTouchEnd}
        onTouchCancel={swipe.onTouchCancel}>
        <button type="button" className="chat-room-select" onClick={onSelect}>
          <Avatar name={room.name} seed={room.id} size="md" />
          <span className="chat-room-meta">
            <span className="chat-room-name">{room.name}</span>
            <span className="chat-room-sub">
              {room.memberCount} member{room.memberCount === 1 ? '' : 's'}
              {room.owner ? ' · owner' : ''}
            </span>
          </span>
        </button>
        <button
          type="button"
          className="chat-room-delete"
          title={room.owner ? `Delete ${room.name}` : `Leave ${room.name}`}
          aria-label={room.owner ? `Delete ${room.name}` : `Leave ${room.name}`}
          disabled={busy}
          onClick={onRemove}>
          ×
        </button>
      </div>
    </div>
  );
}

type Props = {
  rooms: ChatRoomSummary[];
  selectedRoomId: string | null;
  newRoomName: string;
  busy: boolean;
  swipeEnabled?: boolean;
  showClose?: boolean;
  onSelectRoom: (roomId: string) => void;
  onRemoveRoom: (roomId: string) => void;
  onClose?: () => void;
  onNewRoomNameChange: (value: string) => void;
  onCreateRoom: (event: FormEvent) => void;
};

export function ChatRoomsPanel({
  rooms,
  selectedRoomId,
  newRoomName,
  busy,
  swipeEnabled = false,
  showClose = false,
  onSelectRoom,
  onRemoveRoom,
  onClose,
  onNewRoomNameChange,
  onCreateRoom,
}: Props) {
  return (
    <aside className="chat-rooms-panel">
      <div className="chat-panel-head">
        <h2>Rooms</h2>
        <div className="chat-panel-head-actions">
          {rooms.length > 0 ? <span className="chat-panel-count">{rooms.length}</span> : null}
          {showClose && onClose ? (
            <button
              type="button"
              className="chat-panel-close"
              aria-label="Hide rooms panel"
              title="Hide rooms"
              onClick={onClose}>
              ×
            </button>
          ) : null}
        </div>
      </div>
      <div className="chat-room-list">
        {rooms.length === 0 ? (
          <p className="chat-room-empty">No rooms yet</p>
        ) : (
          rooms.map(room => (
            <ChatRoomRow
              key={room.id}
              room={room}
              selected={room.id === selectedRoomId}
              busy={busy}
              swipeEnabled={swipeEnabled}
              onSelect={() => onSelectRoom(room.id)}
              onRemove={() => onRemoveRoom(room.id)}
            />
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
