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

import { ToolApiService } from '@/api/tool-api-service'
import type { Tool } from '@/types/tool'
import { logger } from '@/utils/logger'
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface AvailableTool {
  key: string
  name: string
  description: string
  enabled: boolean
  serviceGroup: string
  selectable: boolean
}

export const useAvailableToolsStore = defineStore('availableTools', () => {
  const availableTools = ref<AvailableTool[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function loadAvailableTools() {
    if (isLoading.value) {
      return
    }

    isLoading.value = true
    error.value = null

    try {
      logger.debug('[AvailableToolsStore] Loading available tools...')
      const tools = await ToolApiService.getAvailableTools()
      logger.debug('[AvailableToolsStore] Loaded available tools:', tools)
      availableTools.value = tools
        .filter((tool: Tool) => tool.selectable !== false)
        .map((tool: Tool) => ({
          key: tool.key || '',
          name: tool.name || '',
          description: tool.description || '',
          enabled: tool.enabled || false,
          serviceGroup: tool.serviceGroup || 'default',
          selectable: tool.selectable,
        }))
    } catch (err) {
      logger.error('[AvailableToolsStore] Error loading tools:', err)
      error.value = err instanceof Error ? err.message : 'Unknown error'
      availableTools.value = []
    } finally {
      isLoading.value = false
    }
  }

  function reset() {
    availableTools.value = []
    isLoading.value = false
    error.value = null
  }

  return {
    availableTools,
    isLoading,
    error,
    loadAvailableTools,
    reset,
  }
})
