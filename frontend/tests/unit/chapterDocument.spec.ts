import { describe, expect, it } from 'vitest'

import {
  countCharacters,
  documentFromText,
  paragraphMapFromDocument,
} from '@/features/chapters/chapterDocument'

describe('chapter document helpers', () => {
  it('creates stable paragraph references for plain backend content', () => {
    let index = 0
    const document = documentFromText('第一段\n第二段', () => `p_test_${++index}`)

    expect(paragraphMapFromDocument(document)).toEqual([
      { key: 'p_test_1', text: '第一段', index: 0 },
      { key: 'p_test_2', text: '第二段', index: 1 },
    ])
  })

  it('counts non-whitespace characters and handles a 100,000-character draft', () => {
    const longText = '雾'.repeat(100_000)

    expect(countCharacters(`潮 雾\n${longText}`)).toBe(100_002)
    expect(paragraphMapFromDocument(documentFromText(longText))).toHaveLength(1)
  })
})
