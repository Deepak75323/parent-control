import {describe, expect, it} from 'vitest';
import {
  defaultTextChannel,
  defaultVoiceChannel,
  textChannels,
  voiceChannels,
} from './chatChannelNav';
import type {ChatRoomSummary} from '../types/api';

const room: ChatRoomSummary = {
  id: 'r1',
  name: 'Notes',
  ownerUserId: 'u1',
  owner: true,
  memberCount: 2,
  createdAt: '',
  updatedAt: '',
  channels: [
    {
      id: 'c-voice',
      roomId: 'r1',
      name: 'Lounge',
      channelType: 'VOICE',
      position: 1,
      createdAt: '',
    },
    {
      id: 'c-general',
      roomId: 'r1',
      name: 'general',
      channelType: 'TEXT',
      position: 0,
      createdAt: '',
    },
    {
      id: 'c-school',
      roomId: 'r1',
      name: 'school',
      channelType: 'TEXT',
      position: 2,
      createdAt: '',
    },
  ],
};

describe('chatChannelNav', () => {
  it('splits text and voice channels', () => {
    expect(textChannels(room).map(c => c.name)).toEqual(['general', 'school']);
    expect(voiceChannels(room).map(c => c.name)).toEqual(['Lounge']);
  });

  it('defaults to general text and first voice', () => {
    expect(defaultTextChannel(room)?.id).toBe('c-general');
    expect(defaultVoiceChannel(room)?.id).toBe('c-voice');
  });
});
