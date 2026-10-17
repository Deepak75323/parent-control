import {useEffect} from 'react';
import {useLocation, useNavigate} from 'react-router-dom';
import {ChatCallOverlay} from '../components/chat/ChatCallOverlay';
import {useChatCallSessionStore} from './chatCallSessionStore';

/**
 * Keeps an active chat call alive across route changes (e.g. Chat → Devices).
 * Renders full workspace chrome only on /chat; otherwise stays as floating PiP.
 */
export function GlobalChatCallHost() {
  const location = useLocation();
  const navigate = useNavigate();
  const joined = useChatCallSessionStore(s => s.joined);
  const call = useChatCallSessionStore(s => s.call);
  const roomId = useChatCallSessionStore(s => s.roomId);
  const roomName = useChatCallSessionStore(s => s.roomName);
  const channelName = useChatCallSessionStore(s => s.channelName);
  const myUserId = useChatCallSessionStore(s => s.myUserId);
  const members = useChatCallSessionStore(s => s.members);
  const audioOnly = useChatCallSessionStore(s => s.audioOnly);
  const minimized = useChatCallSessionStore(s => s.minimized);
  const canEnd = useChatCallSessionStore(s => s.canEnd);
  const setMinimized = useChatCallSessionStore(s => s.setMinimized);
  const leaveLocal = useChatCallSessionStore(s => s.leaveLocal);

  const onChatRoute = location.pathname === '/chat';
  const showMinimized = minimized || !onChatRoute;

  useEffect(() => {
    if (!joined) {
      return;
    }
    if (!onChatRoute) {
      setMinimized(true);
    }
  }, [joined, onChatRoute, setMinimized]);

  if (!joined || !call || !roomId || !myUserId) {
    return null;
  }

  return (
    <div
      className={`chat-call-global${showMinimized ? ' chat-call-global--pip' : ' chat-call-global--workspace'}`}
      aria-live="polite">
      <ChatCallOverlay
        layout="workspace"
        roomId={roomId}
        call={call}
        myUserId={myUserId}
        audioOnly={audioOnly}
        roomName={roomName ?? undefined}
        channelName={channelName ?? undefined}
        members={members}
        minimized={showMinimized}
        pipOnly={showMinimized}
        canEnd={canEnd}
        onMinimizedChange={next => {
          setMinimized(next);
          if (!next && !onChatRoute) {
            navigate('/chat');
          }
        }}
        onOpenParticipants={
          onChatRoute
            ? undefined
            : () => {
                navigate('/chat');
              }
        }
        onLeft={() => {
          leaveLocal();
        }}
        onEnded={() => {
          leaveLocal();
        }}
        onError={message => {
          console.warn('Chat call error', message);
        }}
      />
    </div>
  );
}
