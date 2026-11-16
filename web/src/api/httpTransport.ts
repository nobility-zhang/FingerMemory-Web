interface CsrfView {
  headerName: string;
  parameterName: string;
  token: string;
}

interface ApiErrorBody {
  code?: string;
  message?: string;
}

type FetchImplementation = typeof fetch;

export class ApiClientError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiClientError';
  }
}

export class HttpApiTransport {
  private csrfToken: string | undefined;
  private csrfHeaderName = 'X-CSRF-TOKEN';
  private readonly fetchImplementation: FetchImplementation;

  constructor(
    private readonly baseUrl = '/api/v1',
    fetchImplementation?: FetchImplementation,
  ) {
    this.fetchImplementation =
      fetchImplementation ?? ((input, init) => globalThis.fetch(input, init));
  }

  clearCsrfToken(): void {
    this.csrfToken = undefined;
  }

  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const method = (init.method ?? 'GET').toUpperCase();
    const headers = new Headers(init.headers);
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      await this.ensureCsrfToken();
      if (this.csrfToken !== undefined) {
        headers.set(this.csrfHeaderName, this.csrfToken);
      }
    }

    const response = await this.fetchImplementation(`${this.baseUrl}${path}`, {
      ...init,
      method,
      headers,
      credentials: 'same-origin',
    });

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        this.clearCsrfToken();
      }
      let body: ApiErrorBody = {};
      try {
        body = (await response.json()) as ApiErrorBody;
      } catch {
        body = {};
      }
      throw new ApiClientError(
        response.status,
        body.code ?? 'REQUEST_FAILED',
        body.message ?? '请求失败',
      );
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  private async ensureCsrfToken(): Promise<void> {
    if (this.csrfToken !== undefined) {
      return;
    }
    const response = await this.fetchImplementation(
      `${this.baseUrl}/auth/csrf`,
      {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      },
    );
    if (!response.ok) {
      throw new ApiClientError(
        response.status,
        'CSRF_UNAVAILABLE',
        '无法建立安全会话',
      );
    }
    const csrf = (await response.json()) as CsrfView;
    this.csrfToken = csrf.token;
    this.csrfHeaderName = csrf.headerName;
  }
}
