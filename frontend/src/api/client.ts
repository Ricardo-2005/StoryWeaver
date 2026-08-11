import { HttpProblemError, normalizeProblem } from './errors'
import { getAccessToken } from './tokenMemory'

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  timeoutMs?: number
}

type UnauthorizedHandler = (problem: HttpProblemError) => void

const DEFAULT_TIMEOUT_MS = 15_000
let unauthorizedHandler: UnauthorizedHandler | undefined

export function setUnauthorizedHandler(handler: UnauthorizedHandler | undefined): void {
  unauthorizedHandler = handler
}

export function requestUrl(path: string): string {
  if (!path.startsWith('/api/')) {
    throw new Error('API paths must start with /api/')
  }

  const baseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''
  return `${baseUrl}${path}`
}

export async function apiStream(path: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers)
  const token = getAccessToken()

  headers.set('Accept', 'text/event-stream')
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(requestUrl(path), {
    ...options,
    credentials: 'same-origin',
    headers,
  })
  if (!response.ok) {
    const payload = await responsePayload(response)
    const error = new HttpProblemError(normalizeProblem(payload, response))
    if (response.status === 401) {
      unauthorizedHandler?.(error)
    }
    throw error
  }
  return response
}

function createRequestSignal(externalSignal: AbortSignal | null | undefined, timeoutMs: number) {
  const controller = new AbortController()
  const abortFromExternal = () => controller.abort(externalSignal?.reason)
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)

  if (externalSignal?.aborted) {
    abortFromExternal()
  } else {
    externalSignal?.addEventListener('abort', abortFromExternal, { once: true })
  }

  return {
    signal: controller.signal,
    cleanup: () => {
      window.clearTimeout(timeoutId)
      externalSignal?.removeEventListener('abort', abortFromExternal)
    },
  }
}

async function responsePayload(response: Response): Promise<unknown> {
  if (response.status === 204) {
    return undefined
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('json')) {
    return response.json()
  }

  const text = await response.text()
  return text.length > 0 ? text : undefined
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, headers: providedHeaders, timeoutMs = DEFAULT_TIMEOUT_MS, ...requestInit } = options
  const headers = new Headers(providedHeaders)
  const token = getAccessToken()

  headers.set('Accept', 'application/json, application/problem+json')
  if (body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const { signal, cleanup } = createRequestSignal(options.signal, timeoutMs)

  try {
    const fetchOptions: RequestInit = {
      ...requestInit,
      credentials: 'same-origin',
      headers,
      signal,
    }
    if (body !== undefined) {
      fetchOptions.body = JSON.stringify(body)
    }

    const response = await fetch(requestUrl(path), fetchOptions)
    const payload = await responsePayload(response)

    if (!response.ok) {
      const error = new HttpProblemError(normalizeProblem(payload, response))
      if (response.status === 401) {
        unauthorizedHandler?.(error)
      }
      throw error
    }

    return payload as T
  } finally {
    cleanup()
  }
}

export async function apiFormData<T>(path: string, body: FormData, method = 'POST'): Promise<T> {
  const headers = new Headers()
  const token = getAccessToken()
  headers.set('Accept', 'application/json, application/problem+json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(requestUrl(path), { method, body, headers, credentials: 'same-origin' })
  const payload = await responsePayload(response)
  if (!response.ok) {
    const error = new HttpProblemError(normalizeProblem(payload, response))
    if (response.status === 401) unauthorizedHandler?.(error)
    throw error
  }
  return payload as T
}

export async function apiDownload(path: string): Promise<Blob> {
  const headers = new Headers()
  const token = getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(requestUrl(path), { headers, credentials: 'same-origin' })
  if (!response.ok) {
    const payload = await responsePayload(response)
    throw new HttpProblemError(normalizeProblem(payload, response))
  }
  return response.blob()
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body: unknown, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions) =>
    apiRequest<T>(path, { ...options, method: 'DELETE' }),
}
