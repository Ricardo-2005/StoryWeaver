export interface ApiProblem {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  code?: string
  fieldErrors?: Record<string, string[]>
  traceId?: string
}

interface ProblemPayload {
  type?: unknown
  title?: unknown
  status?: unknown
  detail?: unknown
  instance?: unknown
  code?: unknown
  errors?: unknown
  fieldErrors?: unknown
  traceId?: unknown
}

const STATUS_TITLES: Readonly<Record<number, string>> = {
  400: '请求内容有误',
  401: '登录状态已失效',
  403: '没有访问权限',
  404: '内容不存在',
  409: '内容已发生变化',
  422: '请求无法处理',
  429: '请求过于频繁',
  500: '服务器暂时不可用',
}

export class HttpProblemError extends Error {
  readonly problem: ApiProblem

  constructor(problem: ApiProblem) {
    super(problem.detail ?? problem.title)
    this.name = 'HttpProblemError'
    this.problem = problem
  }
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

function normalizeFieldErrors(value: unknown): Record<string, string[]> | undefined {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return undefined
  }

  const normalized: Record<string, string[]> = {}

  for (const [field, messages] of Object.entries(value)) {
    if (typeof messages === 'string') {
      normalized[field] = [messages]
      continue
    }

    if (Array.isArray(messages)) {
      const fieldMessages = messages.filter((message): message is string => typeof message === 'string')
      if (fieldMessages.length > 0) {
        normalized[field] = fieldMessages
      }
    }
  }

  return Object.keys(normalized).length > 0 ? normalized : undefined
}

function fallbackTitle(status: number): string {
  if (status >= 500) {
    return STATUS_TITLES[500] ?? '服务器错误'
  }

  return STATUS_TITLES[status] ?? '请求失败'
}

export function normalizeProblem(payload: unknown, response: Response): ApiProblem {
  const source: ProblemPayload =
    typeof payload === 'object' && payload !== null ? (payload as ProblemPayload) : {}
  const status = typeof source.status === 'number' ? source.status : response.status
  const fieldErrors = normalizeFieldErrors(source.fieldErrors ?? source.errors)
  const problem: ApiProblem = {
    type: optionalString(source.type) ?? 'about:blank',
    title: optionalString(source.title) ?? fallbackTitle(status),
    status,
  }

  const detail = optionalString(source.detail)
  const instance = optionalString(source.instance)
  const code = optionalString(source.code)
  const traceId = optionalString(source.traceId) ?? response.headers.get('x-trace-id') ?? undefined

  if (detail) problem.detail = detail
  if (instance) problem.instance = instance
  if (code) problem.code = code
  if (fieldErrors) problem.fieldErrors = fieldErrors
  if (traceId) problem.traceId = traceId

  return problem
}

export function problemMessage(error: unknown): string {
  if (error instanceof HttpProblemError) {
    return error.problem.detail ?? error.problem.title
  }

  if (error instanceof DOMException && error.name === 'AbortError') {
    return '请求已取消，请重试。'
  }

  return '网络连接异常，请检查连接后重试。'
}
