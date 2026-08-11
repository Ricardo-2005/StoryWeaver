export const forgeLimits = Object.freeze({
  maxFiles: Number(import.meta.env.VITE_SKILL_FORGE_MAX_FILES ?? 20),
  maxFileBytes: Number(import.meta.env.VITE_SKILL_FORGE_MAX_FILE_BYTES ?? 10 * 1024 * 1024),
  maxTotalBytes: Number(import.meta.env.VITE_SKILL_FORGE_MAX_TOTAL_BYTES ?? 20 * 1024 * 1024),
  minManualCharacters: Number(import.meta.env.VITE_SKILL_FORGE_MIN_MANUAL_CHARACTERS ?? 200),
  sampleWarningCharacters: Number(import.meta.env.VITE_SKILL_FORGE_SAMPLE_WARNING_CHARACTERS ?? 1000),
  maxManualCharacters: Number(import.meta.env.VITE_SKILL_FORGE_MAX_MANUAL_CHARACTERS ?? 50_000),
})
