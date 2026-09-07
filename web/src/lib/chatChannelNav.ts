import type {ChatChannelItem, ChatRoomSummary} from '../types/api';

export function channelsOf(room: ChatRoomSummary | null | undefined): ChatChannelItem[] {
  return room?.channels ?? [];
}

export function textChannels(room: ChatRoomSummary | null | undefined): ChatChannelItem[] {
  return channelsOf(room).filter(channel => channel.channelType === 'TEXT');
}

export function voiceChannels(room: ChatRoomSummary | null | undefined): ChatChannelItem[] {
  return channelsOf(room).filter(channel => channel.channelType === 'VOICE');
}

export function defaultTextChannel(
  room: ChatRoomSummary | null | undefined,
): ChatChannelItem | null {
  const text = textChannels(room);
  return text.find(channel => channel.name.toLowerCase() === 'general') ?? text[0] ?? null;
}

export function defaultVoiceChannel(
  room: ChatRoomSummary | null | undefined,
): ChatChannelItem | null {
  return voiceChannels(room)[0] ?? null;
}
