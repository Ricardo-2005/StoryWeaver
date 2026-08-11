import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import FoundationView from '@/pages/FoundationView.vue'

describe('FoundationView', () => {
  it('states the Phase 0 boundary and exposes the theme control', () => {
    const wrapper = mount(FoundationView, {
      global: {
        plugins: [createPinia()],
      },
    })

    expect(wrapper.get('h1').text()).toBe('前端基础设施已就绪')
    expect(wrapper.text()).toContain('当前严格停留在 Phase 0')
    expect(wrapper.find('[aria-labelledby="theme-label"]').exists()).toBe(true)
  })
})
