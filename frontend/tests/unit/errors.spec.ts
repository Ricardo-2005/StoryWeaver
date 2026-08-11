import { describe, expect, it } from 'vitest'

import { HttpProblemError, normalizeProblem, problemMessage } from '@/api/errors'

function response(status: number, traceId?: string): Response {
  const headers = new Headers()
  if (traceId) headers.set('x-trace-id', traceId)
  return new Response(null, { status, headers })
}

describe('normalizeProblem', () => {
  it('normalizes the backend validation errors map', () => {
    const problem = normalizeProblem(
      {
        type: 'urn:storyweaver:error:validation_failed',
        title: 'Bad Request',
        status: 400,
        detail: 'Request validation failed',
        code: 'validation_failed',
        errors: { name: '不能为空' },
      },
      response(400, 'trace-123'),
    )

    expect(problem).toEqual({
      type: 'urn:storyweaver:error:validation_failed',
      title: 'Bad Request',
      status: 400,
      detail: 'Request validation failed',
      code: 'validation_failed',
      fieldErrors: { name: ['不能为空'] },
      traceId: 'trace-123',
    })
  })

  it.each([
    [401, '登录状态已失效'],
    [403, '没有访问权限'],
    [409, '内容已发生变化'],
    [422, '请求无法处理'],
    [429, '请求过于频繁'],
    [503, '服务器暂时不可用'],
  ])('provides a readable title for HTTP %s', (status, title) => {
    expect(normalizeProblem(undefined, response(status)).title).toBe(title)
  })
})

describe('problemMessage', () => {
  it('prefers the server detail', () => {
    const error = new HttpProblemError({
      type: 'about:blank',
      title: 'Conflict',
      status: 409,
      detail: 'The resource changed',
    })

    expect(problemMessage(error)).toBe('The resource changed')
  })
})
