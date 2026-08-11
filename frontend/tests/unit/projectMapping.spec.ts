import { describe, expect, it } from 'vitest'

import type { ProjectResponse } from '@/api/types'
import { toUpdateProjectRequest } from '@/queries/projects'

const project: ProjectResponse = {
  id: 'project-1',
  name: '雾港来信',
  genre: 'MYSTERY',
  customGenre: null,
  targetAudience: 'GENERAL',
  narrativePerspective: 'THIRD_PERSON',
  lengthType: 'LONG_NOVEL',
  premise: '一封来信揭开雾港失踪案。',
  description: null,
  authorIntent: '保持克制',
  currentFocus: null,
  worldRules: [],
  targetWordCount: null,
  chapterWordTarget: null,
  archived: false,
  version: 7,
  createdAt: '2026-08-03T00:00:00Z',
  updatedAt: '2026-08-03T01:00:00Z',
}

describe('toUpdateProjectRequest', () => {
  it('preserves the full PUT contract and optimistic-lock version', () => {
    expect(toUpdateProjectRequest(project, { archived: true })).toEqual({
      name: '雾港来信',
      genre: 'MYSTERY',
      customGenre: null,
      targetAudience: 'GENERAL',
      narrativePerspective: 'THIRD_PERSON',
      lengthType: 'LONG_NOVEL',
      premise: '一封来信揭开雾港失踪案。',
      description: null,
      authorIntent: '保持克制',
      currentFocus: null,
      worldRules: [],
      targetWordCount: null,
      chapterWordTarget: null,
      archived: true,
      expectedVersion: 7,
    })
  })
})
