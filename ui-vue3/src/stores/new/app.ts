/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ConfigApiService } from '@/api/config-api-service'
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface VersionInfo {
  version: string
  buildTime: string
  timestamp: string
}

export interface InitStatus {
  success: boolean
  initialized: boolean
}

const INIT_STATUS_CACHE_DURATION_MS = 30000

export const useAppStore = defineStore('app', () => {
  const version = ref<VersionInfo | null>(null)
  const initStatus = ref<InitStatus | null>(null)
  const versionLoading = ref(false)
  const versionError = ref<string | null>(null)
  const initStatusLastCheck = ref<number>(0)

  async function loadVersion(): Promise<void> {
    versionLoading.value = true
    versionError.value = null
    try {
      const info = await ConfigApiService.getVersion()
      version.value = info
    } catch (error: unknown) {
      versionError.value = error instanceof Error ? error.message : 'Unknown error'
      console.error('[AppStore] Failed to load version:', error)
    } finally {
      versionLoading.value = false
    }
  }

  async function ensureInitStatusChecked(): Promise<void> {
    const now = Date.now()
    if (
      initStatus.value !== null &&
      now - initStatusLastCheck.value < INIT_STATUS_CACHE_DURATION_MS
    ) {
      return
    }
    try {
      const response = await fetch('/api/init/status')
      if (!response.ok) {
        throw new Error(`Check failed: ${response.status}`)
      }
      const result = (await response.json()) as InitStatus
      initStatus.value = result
      initStatusLastCheck.value = now
    } catch (error: unknown) {
      console.error('[AppStore] Failed to check init status:', error)
      initStatus.value = { success: false, initialized: false }
      initStatusLastCheck.value = now
    }
  }

  function clearInitStatusCache(): void {
    initStatus.value = null
    initStatusLastCheck.value = 0
  }

  return {
    version,
    initStatus,
    versionLoading,
    versionError,
    loadVersion,
    ensureInitStatusChecked,
    clearInitStatusCache,
  }
})
