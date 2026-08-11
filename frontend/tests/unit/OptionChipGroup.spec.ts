import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import OptionChipGroup from '@/components/base/OptionChipGroup.vue'

const options = [
  { label: '悬疑', value: 'MYSTERY' },
  { label: '科幻', value: 'SCIENCE_FICTION' },
]

describe('OptionChipGroup', () => {
  it('uses radio semantics and keyboard selection', async () => {
    const wrapper = mount(OptionChipGroup, { props: { modelValue: undefined, label: '小说题材', options } })
    const radios = wrapper.findAll('[role="radio"]')

    expect(radios).toHaveLength(2)
    expect(radios[0]!.attributes('aria-checked')).toBe('false')
    await radios[0]!.trigger('keydown', { key: 'ArrowRight' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['SCIENCE_FICTION']])
  })
})
