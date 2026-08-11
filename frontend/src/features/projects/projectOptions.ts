export type ProjectGenre =
  | 'ROMANCE'
  | 'REALISTIC_EMOTION'
  | 'MYSTERY'
  | 'THRILLER'
  | 'SCIENCE_FICTION'
  | 'WUXIA'
  | 'HIGH_CONCEPT'
  | 'SPACE_OPERA'
  | 'CYBERPUNK'
  | 'GAME'
  | 'XIANXIA'
  | 'HISTORY'
  | 'FANTASY'
  | 'FANTASY_GENERAL'
  | 'URBAN'
  | 'CAMPUS'
  | 'YOUTH'
  | 'FAMILY'
  | 'WORKPLACE'
  | 'BUSINESS'
  | 'MILITARY'
  | 'WAR'
  | 'APOCALYPSE'
  | 'INFINITE_FLOW'
  | 'CTHULHU'
  | 'DETECTIVE'
  | 'WESTERN_FANTASY'
  | 'LIGHT_NOVEL'
  | 'FAN_FICTION'
  | 'CUSTOM'

export type TargetAudience = 'MALE' | 'FEMALE' | 'GENERAL'
export type NarrativePerspective = 'FIRST_PERSON' | 'THIRD_PERSON'
export type LengthType = 'SHORT_NOVEL' | 'LONG_NOVEL'

export interface ProjectOption<T extends string> {
  label: string
  value: T
}

export const primaryGenreOptions: ProjectOption<ProjectGenre>[] = [
  ['言情', 'ROMANCE'], ['现实情感', 'REALISTIC_EMOTION'], ['悬疑', 'MYSTERY'], ['惊悚', 'THRILLER'],
  ['科幻', 'SCIENCE_FICTION'], ['武侠', 'WUXIA'], ['脑洞', 'HIGH_CONCEPT'], ['太空歌剧', 'SPACE_OPERA'],
  ['赛博朋克', 'CYBERPUNK'], ['游戏', 'GAME'], ['仙侠', 'XIANXIA'], ['历史', 'HISTORY'], ['玄幻', 'FANTASY'],
].map(([label, value]) => ({ label: label!, value: value as ProjectGenre }))

export const moreGenreOptions: ProjectOption<ProjectGenre>[] = [
  ['都市', 'URBAN'], ['校园', 'CAMPUS'], ['青春', 'YOUTH'], ['家庭', 'FAMILY'], ['职场', 'WORKPLACE'],
  ['商战', 'BUSINESS'], ['军事', 'MILITARY'], ['战争', 'WAR'], ['末世', 'APOCALYPSE'], ['无限流', 'INFINITE_FLOW'],
  ['克苏鲁', 'CTHULHU'], ['推理', 'DETECTIVE'], ['奇幻', 'FANTASY_GENERAL'], ['西幻', 'WESTERN_FANTASY'],
  ['轻小说', 'LIGHT_NOVEL'], ['同人', 'FAN_FICTION'], ['自定义', 'CUSTOM'],
].map(([label, value]) => ({ label: label!, value: value as ProjectGenre }))

export const audienceOptions: ProjectOption<TargetAudience>[] = [
  { label: '男频', value: 'MALE' }, { label: '女频', value: 'FEMALE' }, { label: '全频', value: 'GENERAL' },
]

export const perspectiveOptions: ProjectOption<NarrativePerspective>[] = [
  { label: '第一人称', value: 'FIRST_PERSON' }, { label: '第三人称', value: 'THIRD_PERSON' },
]

export const lengthOptions: ProjectOption<LengthType>[] = [
  { label: '短篇小说', value: 'SHORT_NOVEL' }, { label: '长篇小说', value: 'LONG_NOVEL' },
]

export function genreLabel(value: string | null): string {
  return [...primaryGenreOptions, ...moreGenreOptions].find((option) => option.value === value)?.label ?? value ?? '未设置题材'
}
