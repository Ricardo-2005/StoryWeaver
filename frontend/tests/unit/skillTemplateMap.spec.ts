import { describe, expect, it } from 'vitest'

import { getSkillMeltTemplate, skillTemplateMap } from '@/config/skillTemplateMap'

describe('skillTemplateMap', () => {
  it('contains an independent template for every material and Skill type combination', () => {
    const materials = Object.keys(skillTemplateMap)
    expect(materials).toHaveLength(7)
    const combinations = materials.flatMap(material => Object.values(skillTemplateMap[material as keyof typeof skillTemplateMap]))
    expect(combinations).toHaveLength(28)
    expect(new Set(combinations.map(value => `${value.focus}\n${value.description}`)).size).toBe(28)

    for (const templates of Object.values(skillTemplateMap)) {
      expect(Object.keys(templates)).toEqual(expect.arrayContaining(['FOUNDATION', 'GENRE', 'TECHNIQUE', 'REVIEW']))
      for (const value of Object.values(templates)) {
        expect(value.focus.trim().length).toBeGreaterThan(30)
        expect(value.description.trim().length).toBeGreaterThan(20)
        expect(value.focus.length).toBeLessThanOrEqual(1000)
        expect(value.description.length).toBeLessThanOrEqual(1000)
      }
    }
  })

  it('distinguishes prose foundation, technique and review goals', () => {
    const foundation = getSkillMeltTemplate('PROSE', 'FOUNDATION')
    const technique = getSkillMeltTemplate('PROSE', 'TECHNIQUE')
    const review = getSkillMeltTemplate('PROSE', 'REVIEW')

    expect(foundation.focus).toContain('章节结构')
    expect(foundation.focus).toContain('场景衔接')
    expect(technique.focus).toContain('信息差')
    expect(technique.focus).toContain('章末悬念')
    expect(review.focus).toContain('检测条件')
    expect(new Set([foundation.focus, technique.focus, review.focus]).size).toBe(3)
  })

  it('injects a concrete genre into genre templates', () => {
    const value = getSkillMeltTemplate('DIALOGUE', 'GENRE', '仙侠')
    expect(value.focus).toContain('重点学习仙侠题材中的人物说话方式')
    expect(value.focus).not.toContain('当前题材')
  })

  it('covers character review acceptance terms', () => {
    const value = getSkillMeltTemplate('CHARACTER', 'REVIEW')
    expect(value.focus).toContain('人物行为一致性')
    expect(value.focus).toContain('人物动机')
    expect(value.focus).toContain('知识边界')
    expect(value.focus).toContain('人物关系')
    expect(value.focus).toContain('能力变化')
    expect(value.focus).toContain('称呼')
  })
})
