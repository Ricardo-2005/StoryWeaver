import { defineConfig } from '@playwright/test'

import baseConfig from './playwright.config'

export default defineConfig({
  ...baseConfig,
  testMatch: 'demo.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  outputDir: 'artifacts/demo',
  reporter: 'list',
  use: {
    ...baseConfig.use,
    trace: 'on',
    video: 'on',
  },
})
