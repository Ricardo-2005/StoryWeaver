import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { chaptersApi } from '@/api/endpoints/assets'
import type { CreateChapterVersionRequest, RestoreChapterVersionRequest } from '@/api/types'
import { queryKeys } from '@/queries/keys'

export function useChapterQuery(chapterId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.chapter(toValue(chapterId))),
    queryFn: () => chaptersApi.get(toValue(chapterId)),
    enabled: () => toValue(chapterId).length > 0,
  })
}

export function useChapterVersionsQuery(chapterId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.chapterVersions(toValue(chapterId))),
    queryFn: () => chaptersApi.versions(toValue(chapterId)),
    enabled: () => toValue(chapterId).length > 0,
  })
}

export function useCreateChapterVersionMutation(
  projectId: MaybeRefOrGetter<string>,
  chapterId: MaybeRefOrGetter<string>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateChapterVersionRequest) =>
      chaptersApi.addVersion(toValue(chapterId), request),
    onSuccess: async (chapter) => {
      queryClient.setQueryData(queryKeys.chapter(chapter.id), chapter)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.chapters(toValue(projectId)) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.chapterVersions(chapter.id) }),
      ])
    },
  })
}

export function useRestoreChapterVersionMutation(
  projectId: MaybeRefOrGetter<string>,
  chapterId: MaybeRefOrGetter<string>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ versionNo, request }: { versionNo: number; request: RestoreChapterVersionRequest }) =>
      chaptersApi.restore(toValue(chapterId), versionNo, request),
    onSuccess: async (chapter) => {
      queryClient.setQueryData(queryKeys.chapter(chapter.id), chapter)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.chapters(toValue(projectId)) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.chapterVersions(chapter.id) }),
      ])
    },
  })
}
