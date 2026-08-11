import { Extension, type Editor } from '@tiptap/core'

import { createParagraphKey } from '@/features/chapters/chapterDocument'

export const ParagraphKey = Extension.create({
  name: 'paragraphKey',
  addGlobalAttributes() {
    return [{
      types: ['paragraph'],
      attributes: {
        paragraphKey: {
          default: null,
          parseHTML: (element) => element.getAttribute('data-paragraph-key'),
          renderHTML: (attributes) => attributes.paragraphKey
            ? { 'data-paragraph-key': attributes.paragraphKey as string }
            : {},
        },
      },
    }]
  },
})

export function ensureParagraphKeys(editor: Editor): boolean {
  const transaction = editor.state.tr
  let changed = false
  editor.state.doc.descendants((node, position) => {
    if (node.type.name === 'paragraph' && !node.attrs.paragraphKey) {
      transaction.setNodeMarkup(position, undefined, {
        ...node.attrs,
        paragraphKey: createParagraphKey(),
      })
      changed = true
    }
  })
  if (changed) editor.view.dispatch(transaction)
  return changed
}
