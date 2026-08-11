import { describe, expect, it } from 'vitest'

import { TXT_IMPORT_MAX_BYTES, validateTxtImportFile } from '@/api/endpoints/txtImports'

describe('TXT import file validation', () => {
  it('accepts a non-empty TXT at the exact 20 MB boundary', () => {
    expect(validateTxtImportFile({ name: 'BOOK.TXT', size: TXT_IMPORT_MAX_BYTES })).toBeNull()
  })

  it('rejects unsupported, empty and oversized files before upload', () => {
    expect(validateTxtImportFile({ name: 'book.md', size: 10 })).toContain('.txt')
    expect(validateTxtImportFile({ name: 'book.txt', size: 0 })).toContain('不能为空')
    expect(validateTxtImportFile({ name: 'book.txt', size: TXT_IMPORT_MAX_BYTES + 1 })).toContain('20 MB')
  })
})
