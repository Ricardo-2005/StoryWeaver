import type { JSONContent } from '@tiptap/core'

const DATABASE_NAME = 'storyweaver-editor'
const DATABASE_VERSION = 1
const STORE_NAME = 'chapter-drafts'

export interface ChapterDraft {
  key: string
  projectId: string
  chapterId: string
  baseVersion: number
  title: string
  contentText: string
  editorDocument: JSONContent
  updatedAt: string
}

export function chapterDraftKey(projectId: string, chapterId: string, baseVersion: number): string {
  return `storyweaver:draft:${projectId}:${chapterId}:${baseVersion}`
}

type DraftDatabase = ReturnType<typeof globalThis.indexedDB.open>['result']

function openDatabase(): Promise<DraftDatabase> {
  return new Promise((resolve, reject) => {
    const request = globalThis.indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE_NAME)) {
        request.result.createObjectStore(STORE_NAME, { keyPath: 'key' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('无法打开本地草稿数据库'))
  })
}

export async function readChapterDraft(key: string): Promise<ChapterDraft | undefined> {
  const database = await openDatabase()
  try {
    return await new Promise((resolve, reject) => {
      const request = database.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(key)
      request.onsuccess = () => resolve(request.result as ChapterDraft | undefined)
      request.onerror = () => reject(request.error ?? new Error('无法读取本地草稿'))
    })
  } finally {
    database.close()
  }
}

export async function writeChapterDraft(draft: ChapterDraft): Promise<void> {
  const database = await openDatabase()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, 'readwrite')
      transaction.objectStore(STORE_NAME).put(draft)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error ?? new Error('无法保存本地草稿'))
      transaction.onabort = () => reject(transaction.error ?? new Error('本地草稿保存已中止'))
    })
  } finally {
    database.close()
  }
}

export async function deleteChapterDraft(key: string): Promise<void> {
  const database = await openDatabase()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = database.transaction(STORE_NAME, 'readwrite')
      transaction.objectStore(STORE_NAME).delete(key)
      transaction.oncomplete = () => resolve()
      transaction.onerror = () => reject(transaction.error ?? new Error('无法清理本地草稿'))
    })
  } finally {
    database.close()
  }
}
