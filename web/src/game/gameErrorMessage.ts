import { ApiClientError } from '../api/client';

export function gameErrorMessage(cause: unknown): string {
  if (cause instanceof ApiClientError) {
    return cause.message;
  }
  if (cause instanceof Error && cause.message.trim().length > 0) {
    return cause.message;
  }
  return '请求失败，请稍后重试。';
}
