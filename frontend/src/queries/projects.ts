import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import { projectsApi } from '@/api/endpoints/projects'
import type { CreateProjectRequest, ProjectResponse, UpdateProjectRequest } from '@/api/types'
import { queryKeys } from '@/queries/keys'

export function useProjectsQuery(includeArchived = false) {
  return useQuery({
    queryKey: queryKeys.projects(includeArchived),
    queryFn: () => projectsApi.list(includeArchived),
  })
}

export function useProjectQuery(projectId: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: computed(() => queryKeys.project(toValue(projectId))),
    queryFn: () => projectsApi.get(toValue(projectId)),
    enabled: () => toValue(projectId).length > 0,
  })
}

export function useCreateProjectMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateProjectRequest) => projectsApi.create(request),
    onSuccess: async (project) => {
      queryClient.setQueryData(queryKeys.project(project.id), project)
      await queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
  })
}

export function useUpdateProjectMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ projectId, request }: { projectId: string; request: UpdateProjectRequest }) =>
      projectsApi.update(projectId, request),
    onSuccess: async (project) => {
      queryClient.setQueryData(queryKeys.project(project.id), project)
      await queryClient.invalidateQueries({ queryKey: ['projects'] })
    },
  })
}

export function toUpdateProjectRequest(
  project: ProjectResponse,
  overrides: Partial<Omit<UpdateProjectRequest, 'expectedVersion'>> = {},
): UpdateProjectRequest {
  return {
    name: project.name,
    genre: project.genre ?? '',
    customGenre: project.customGenre,
    targetAudience: project.targetAudience,
    narrativePerspective: project.narrativePerspective,
    lengthType: project.lengthType,
    premise: project.premise ?? '',
    description: project.description,
    authorIntent: project.authorIntent,
    currentFocus: project.currentFocus,
    worldRules: project.worldRules,
    targetWordCount: project.targetWordCount,
    chapterWordTarget: project.chapterWordTarget,
    archived: project.archived,
    ...overrides,
    expectedVersion: project.version,
  }
}
