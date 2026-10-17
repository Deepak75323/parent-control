import {useCallback, useEffect, useMemo, useState} from 'react';
import {Link} from 'react-router-dom';
import {chatApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {Avatar} from '../components/Avatar';
import {userIdFromAccessToken} from '../lib/jwtSubject';
import {formatPartnerPresenceLabel} from '../lib/parentPresence';
import {
  isTwoPersonNotebook,
  partnerDisplayName,
  partnerFromMembers,
} from '../lib/parentNotebookPartners';
import {useRealtimeStore} from '../realtime/realtimeStore';
import type {ChatMemberItem, ChatRoomSummary} from '../types/api';

type PartnerRow = {
  room: ChatRoomSummary;
  partner: ChatMemberItem | null;
  online: boolean;
  lastSeenAt?: string | null;
  hidden: boolean;
};

export function ParentsPage() {
  const accessToken = useAuthStore(s => s.accessToken);
  const profile = useAuthStore(s => s.profile);
  const myUserId = userIdFromAccessToken(accessToken) ?? profile?.userId ?? null;
  const livePresence = useRealtimeStore(s => s.partnerPresenceByRoom);

  const [rows, setRows] = useState<PartnerRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    const id = window.setInterval(() => setNowMs(Date.now()), 30_000);
    return () => window.clearInterval(id);
  }, []);

  const load = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const {data} = await chatApi.listRooms();
      const paired = (data.data ?? []).filter(isTwoPersonNotebook);
      const detailed = await Promise.all(
        paired.map(async room => {
          const [membersRes, presenceRes] = await Promise.all([
            chatApi.listMembers(room.id).catch(() => null),
            chatApi.partnerPresence(room.id).catch(() => null),
          ]);
          const members = membersRes?.data?.data ?? [];
          const presence = presenceRes?.data?.data;
          return {
            room,
            partner: partnerFromMembers(members, myUserId),
            online: presence?.online ?? false,
            lastSeenAt: presence?.lastSeenAt ?? null,
            hidden: presence?.hidden ?? false,
          } satisfies PartnerRow;
        }),
      );
      setRows(detailed);
    } catch {
      setError('Could not load shared notebooks.');
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [myUserId]);

  useEffect(() => {
    void load();
  }, [load]);

  const displayRows = useMemo(() => {
    return rows.map(row => {
      const live = livePresence[row.room.id];
      if (!live) {
        return row;
      }
      return {
        ...row,
        online: live.online,
        lastSeenAt: live.lastSeenAt ?? row.lastSeenAt,
        hidden: live.hidden,
      };
    });
  }, [rows, livePresence]);

  return (
    <div className="page parents-page">
      <div className="page-header">
        <div>
          <h1>Parents</h1>
          <p className="muted">
            Other parents in your 2-person notebooks — online status and last seen.
          </p>
        </div>
        <button type="button" className="btn ghost" onClick={() => void load()}>
          Refresh
        </button>
      </div>

      {error ? <div className="error">{error}</div> : null}

      {loading ? (
        <div className="card">
          <p className="muted">Loading shared notebooks…</p>
        </div>
      ) : displayRows.length === 0 ? (
        <div className="card parents-empty">
          <h2>No shared notebooks yet</h2>
          <p className="muted">
            Invite another parent into a notebook from Notes. When the notebook has exactly two
            members, they appear here with active / last-seen status.
          </p>
          <Link className="btn primary" to="/chat">
            Open Notes
          </Link>
        </div>
      ) : (
        <ul className="parents-list">
          {displayRows.map(row => {
            const name = partnerDisplayName(row.partner);
            const statusLabel = formatPartnerPresenceLabel(
              {online: row.online, lastSeenAt: row.lastSeenAt, hidden: row.hidden},
              nowMs,
            );
            const statusText = row.hidden
              ? 'Presence hidden'
              : statusLabel ?? 'Offline';
            const online = !row.hidden && row.online;

            return (
              <li key={row.room.id} className="parents-card card">
                <div className="parents-card-main">
                  <Avatar name={name} seed={row.partner?.userId ?? row.room.id} size="md" />
                  <div className="parents-card-copy">
                    <div className="parents-card-title-row">
                      <strong>{name}</strong>
                      <span
                        className={`parents-status${online ? ' parents-status--online' : ''}`}>
                        <span className="parents-status-dot" aria-hidden />
                        {statusText}
                      </span>
                    </div>
                    <p className="muted tiny parents-card-meta">
                      Notebook: {row.room.name}
                      {row.room.directMessage ? ' · Direct' : ''}
                      {' · '}2 members
                    </p>
                  </div>
                </div>
                <Link className="btn primary sm" to={`/chat?room=${encodeURIComponent(row.room.id)}`}>
                  Open notebook
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
