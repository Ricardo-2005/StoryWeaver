import type { JSONContent } from '@tiptap/core'

export interface ParagraphReference {
  key: string
  text: string
  index: number
}

export function createParagraphKey(): string {
  return `p_${globalThis.crypto.randomUUID().replaceAll('-', '').slice(0, 10)}`
}

export function documentFromText(
  content: string,
  keyFactory: () => string = createParagraphKey,
): JSONContent {
  const paragraphs = content.length > 0 ? content.split('\n') : ['']
  return {
    type: 'doc',
    content: paragraphs.map((text) => ({
      type: 'paragraph',
      attrs: { paragraphKey: keyFactory() },
      ...(text ? { content: [{ type: 'text', text }] } : {}),
    })),
  }
}

function nodeText(node: JSONContent): string {
  if (typeof node.text === 'string') return node.text
  return node.content?.map(nodeText).join('') ?? ''
}

export function paragraphMapFromDocument(document: JSONContent): ParagraphReference[] {
  const result: ParagraphReference[] = []
  const visit = (node: JSONContent) => {
    if (node.type === 'paragraph') {
      result.push({
        key: typeof node.attrs?.paragraphKey === 'string' ? node.attrs.paragraphKey : createParagraphKey(),
        text: nodeText(node),
        index: result.length,
      })
    }
    node.content?.forEach(visit)
  }
  visit(document)
  return result
}

export function countCharacters(content: string): number {
  return content.replace(/\s/gu, '').length
}
