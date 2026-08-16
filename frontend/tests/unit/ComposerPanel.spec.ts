import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ComposerPanel from '@/components/chat/ComposerPanel.vue'

describe('ComposerPanel', () => {
  it('removes a tool from the chip shown to the right of the mode selector', async () => {
    const wrapper = mount(ComposerPanel, {
      global: { plugins: [createPinia()] },
    })

    await wrapper.get('.composer-tools > button').trigger('click')
    const worldbook = wrapper.findAll('[role="menuitem"]').find((item) => item.text() === '世界书')
    expect(worldbook).toBeDefined()
    await worldbook!.trigger('click')

    expect(wrapper.get('.selected-tool').text()).toContain('世界书')
    await wrapper.get('[aria-label="移除工具 世界书"]').trigger('click')

    expect(wrapper.find('.selected-tool').exists()).toBe(false)
  })
})
