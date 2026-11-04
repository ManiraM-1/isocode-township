const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export type FieldError = { field: string; message: string };

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly fieldErrors: FieldError[];
  readonly body: Record<string, unknown>;

  constructor(status: number, title: string, detail: string, fieldErrors: FieldError[], body: Record<string, unknown>) {
    super(detail);
    this.status = status;
    this.title = title;
    this.fieldErrors = fieldErrors;
    this.body = body;
  }

  fieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((error) => error.field === field)?.message;
  }
}

let onUnauthenticated: () => void = () => {};

export function setUnauthenticatedHandler(handler: () => void) {
  onUnauthenticated = handler;
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split("; ")
    .find((part) => part.startsWith(name + "="))
    ?.slice(name.length + 1);
}

async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) {
    return decodeURIComponent(existing);
  }
  await fetch("/api/health", { credentials: "same-origin" });
  const issued = readCookie(CSRF_COOKIE);
  return issued ? decodeURIComponent(issued) : undefined;
}

type RequestOptions = {
  method?: string;
  json?: unknown;
  form?: FormData;
};

async function send(path: string, options: RequestOptions = {}): Promise<Response> {
  const method = options.method ?? (options.json !== undefined || options.form ? "POST" : "GET");
  const headers: Record<string, string> = { Accept: "application/json" };
  let body: BodyInit | undefined;
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.json);
  } else if (options.form) {
    body = options.form;
  }
  if (MUTATING.has(method)) {
    const token = await csrfToken();
    if (token) {
      headers[CSRF_HEADER] = token;
    }
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body, credentials: "same-origin" });
  } catch {
    throw new ApiError(0, "Offline", "We could not reach the server. Check your connection and try again.", [], {});
  }
  if (response.ok) {
    return response;
  }
  throw await toApiError(response, path);
}

async function toApiError(response: Response, path: string): Promise<ApiError> {
  let problem: Record<string, unknown> = {};
  try {
    problem = await response.json();
  } catch {
    problem = {};
  }
  if (response.status === 401 && !path.startsWith("/api/auth/") && path !== "/api/me") {
    onUnauthenticated();
  }
  const detail =
    typeof problem.detail === "string" ? problem.detail : fallbackMessage(response.status);
  const title = typeof problem.title === "string" ? problem.title : "Something went wrong";
  const fieldErrors = Array.isArray(problem.errors) ? (problem.errors as FieldError[]) : [];
  return new ApiError(response.status, title, detail, fieldErrors, problem);
}

function fallbackMessage(status: number): string {
  if (status === 401) return "Please sign in again.";
  if (status === 403) return "You do not have access to this.";
  if (status === 404) return "We could not find that.";
  if (status >= 500) return "Something went wrong on our side. Please try again.";
  return "That did not work. Please try again.";
}

export const api = {
  async get<T>(path: string): Promise<T> {
    return (await send(path)).json() as Promise<T>;
  },
  async post<T>(path: string, json: unknown = {}): Promise<T> {
    const response = await send(path, { method: "POST", json });
    return (response.status === 204 ? undefined : await response.json()) as T;
  },
  async patch<T>(path: string, json: unknown): Promise<T> {
    return (await send(path, { method: "PATCH", json })).json() as Promise<T>;
  },
  async upload<T>(path: string, form: FormData): Promise<T> {
    return (await send(path, { method: "POST", form })).json() as Promise<T>;
  },
  async blob(path: string): Promise<{ blob: Blob; fileName: string | null }> {
    const response = await send(path);
    const disposition = response.headers.get("Content-Disposition") ?? "";
    const match = /filename="?([^";]+)"?/.exec(disposition);
    return { blob: await response.blob(), fileName: match ? match[1] : null };
  },
};

export async function downloadFile(path: string, fallbackName: string) {
  const { blob, fileName } = await api.blob(path);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName ?? fallbackName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
