import { describe, it, expect } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { getApiErrorData, getApiErrorMessage } from './errors';

function axiosError(status: number, data: unknown) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError('Request failed with status code ' + status, 'ERR_BAD_REQUEST', config, null, {
    status,
    statusText: '',
    headers: {},
    config,
    data,
  });
}

describe('getApiErrorMessage', () => {
  it("uses the server's detail", () => {
    expect(getApiErrorMessage(axiosError(409, { detail: 'The race is already running' }), 'fallback'))
      .toBe('The race is already running');
  });

  it('uses an older message field when there is no detail', () => {
    expect(getApiErrorMessage(axiosError(400, { message: 'Logo too large' }), 'fallback')).toBe('Logo too large');
  });

  it('falls back when the server gave no reason, never showing the axios message', () => {
    expect(getApiErrorMessage(axiosError(500, ''), 'fallback')).toBe('fallback');
    expect(getApiErrorMessage(new AxiosError('Network Error'), 'fallback')).toBe('fallback');
  });

  it('falls back for errors that are not from a request', () => {
    expect(getApiErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
  });
});

describe('getApiErrorData', () => {
  it('gives the body of the answer', () => {
    expect(getApiErrorData(axiosError(409, { code: 'POSSIBLE_DUPLICATE_COMPETITOR', matches: [] })))
      .toEqual({ code: 'POSSIBLE_DUPLICATE_COMPETITOR', matches: [] });
  });

  it('gives nothing when there was no answer', () => {
    expect(getApiErrorData(new AxiosError('Network Error'))).toBeUndefined();
    expect(getApiErrorData(new Error('x'))).toBeUndefined();
  });
});
