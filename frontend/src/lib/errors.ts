import { isAxiosError } from 'axios';
import type { CompetitorSummaryDto } from '@/lib/adminApi';

/**
 * The server's reason for refusing a request (the `detail` of its problem response, or an older `message`),
 * or `fallback` when there is none, such as a network failure. Axios's own message ("Request failed with
 * status code 409") never reaches the user.
 */
export function getApiErrorMessage(err: unknown, fallback: string): string {
  if (!isAxiosError(err)) return fallback;
  const data = err.response?.data as { detail?: unknown; message?: unknown } | undefined;
  if (typeof data?.detail === 'string' && data.detail) return data.detail;
  if (typeof data?.message === 'string' && data.message) return data.message;
  return fallback;
}

/** The fields of a failed request's problem response that pages read beyond the `detail` text. */
export interface ApiErrorBody {
  detail?: string;
  message?: string;
  /** Machine-readable cause, such as `POSSIBLE_DUPLICATE_COMPETITOR`. */
  code?: string;
  /** Why a 403 was refused, such as `disabled`. */
  reason?: string;
  /** Reasons a competitor merge is refused. */
  blockers?: string[];
  /** The competitors a new name may be a duplicate of. */
  matches?: CompetitorSummaryDto[];
}

/** The body of the server's answer to a failed request, or undefined when there was no answer. */
export function getApiErrorData(err: unknown): ApiErrorBody | undefined {
  return isAxiosError(err) ? err.response?.data : undefined;
}

/** The HTTP status the server answered with, or undefined when there was no answer, such as a network failure. */
export function getApiErrorStatus(err: unknown): number | undefined {
  return isAxiosError(err) ? err.response?.status : undefined;
}
