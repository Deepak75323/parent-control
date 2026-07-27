import {classifyRefreshFailure} from '../services/refreshResult';

describe('classifyRefreshFailure', () => {
  it('treats 401/403 as invalid session', () => {
    expect(classifyRefreshFailure({response: {status: 401}})).toBe('invalid');
    expect(classifyRefreshFailure({response: {status: 403}})).toBe('invalid');
  });

  it('treats network and server errors as transient', () => {
    expect(classifyRefreshFailure({message: 'Network Error'})).toBe('transient');
    expect(classifyRefreshFailure({response: {status: 500}})).toBe('transient');
    expect(classifyRefreshFailure({response: {status: 429}})).toBe('transient');
  });
});
