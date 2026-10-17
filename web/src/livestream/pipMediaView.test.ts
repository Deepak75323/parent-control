import {describe, expect, it} from 'vitest';
import {pipMediaView} from './pipMediaView';

describe('pipMediaView', () => {
  it('ignores a text note', () => {
    expect(
      pipMediaView({messageType: 'TEXT', canViewAttachment: true, unlocked: true, showing: false}),
    ).toEqual({kind: 'hidden'});
  });

  it('keeps a deleted photo closed', () => {
    expect(
      pipMediaView({
        messageType: 'IMAGE',
        deleted: true,
        canViewAttachment: true,
        unlocked: true,
        showing: false,
      }),
    ).toEqual({kind: 'hidden'});
  });

  it('does not let the sender open their own photo', () => {
    expect(
      pipMediaView({messageType: 'IMAGE', ownMessage: true, unlocked: true, showing: false}),
    ).toEqual({kind: 'own', label: 'View once photo · Locked until recipient unlocks'});
  });

  it('asks to unlock before a photo can be opened', () => {
    expect(
      pipMediaView({
        messageType: 'IMAGE',
        canViewAttachment: true,
        unlocked: false,
        showing: false,
      }),
    ).toEqual({kind: 'unlock', label: 'Unlock decrypt to view photo once'});
  });

  it('offers a tap to open an unlocked photo', () => {
    expect(
      pipMediaView({
        messageType: 'IMAGE',
        canViewAttachment: true,
        unlocked: true,
        showing: false,
      }),
    ).toEqual({kind: 'view', label: 'Tap to view photo once'});
  });

  it('shows the photo after it has been opened', () => {
    expect(
      pipMediaView({
        messageType: 'IMAGE',
        canViewAttachment: false,
        unlocked: true,
        showing: true,
      }),
    ).toEqual({kind: 'show'});
  });

  it('marks a photo opened once it has been consumed', () => {
    expect(
      pipMediaView({
        messageType: 'VIDEO',
        attachmentConsumed: true,
        unlocked: true,
        showing: false,
      }),
    ).toEqual({kind: 'opened', label: 'View once video · Opened'});
  });
});
