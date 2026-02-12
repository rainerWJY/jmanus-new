/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use it except in compliance with the License.
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

import { logger } from '@/utils/logger'
import { defineStore } from 'pinia'
import { ref } from 'vue'

const PARAMETER_HISTORY_KEY = 'parameterHistory'
const TOOL_HISTORY_INDICES_KEY = 'toolHistoryIndices'
const MAX_HISTORY_SIZE = 5

function loadFromLocalStorage<T>(key: string, parse: (raw: string) => T): T | null {
  try {
    const saved = localStorage.getItem(key)
    return saved ? parse(saved) : null
  } catch {
    return null
  }
}

function saveToLocalStorage(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    logger.warn('[ParameterHistoryStore] Failed to save to localStorage:', e)
  }
}

function areParameterSetsEqual(
  set1: Record<string, string>,
  set2: Record<string, string>
): boolean {
  const keys1 = Object.keys(set1).sort()
  const keys2 = Object.keys(set2).sort()
  if (keys1.length !== keys2.length) return false
  return keys1.every(key => set1[key] === set2[key])
}

export const useParameterHistoryStore = defineStore('parameterHistory', () => {
  // parameterHistory: planTemplateId -> array of parameter sets (up to MAX_HISTORY_SIZE)
  const parameterHistory = ref<Record<string, Record<string, string>[]>>({})
  // toolHistoryIndices: planTemplateId -> current history index (-1 = viewing current, not history)
  const toolHistoryIndices = ref<Record<string, number>>({})

  // Hydrate from localStorage on init
  const historyFromStorage = loadFromLocalStorage(PARAMETER_HISTORY_KEY, raw => {
    const parsed = JSON.parse(raw) as Record<string, Record<string, string>[]>
    return parsed && typeof parsed === 'object' ? parsed : {}
  })
  if (historyFromStorage) {
    parameterHistory.value = historyFromStorage
  }
  const indicesFromStorage = loadFromLocalStorage(TOOL_HISTORY_INDICES_KEY, raw => {
    const parsed = JSON.parse(raw) as Record<string, number>
    return parsed && typeof parsed === 'object' ? parsed : {}
  })
  if (indicesFromStorage) {
    toolHistoryIndices.value = indicesFromStorage
  }

  function persist(): void {
    saveToLocalStorage(PARAMETER_HISTORY_KEY, parameterHistory.value)
    saveToLocalStorage(TOOL_HISTORY_INDICES_KEY, toolHistoryIndices.value)
  }

  function getHistory(planTemplateId: string): Record<string, string>[] | undefined {
    return parameterHistory.value[planTemplateId]
  }

  function hasParameterHistory(planTemplateId: string): boolean {
    const history = parameterHistory.value[planTemplateId]
    return Array.isArray(history) && history.length > 0
  }

  function isDuplicate(planTemplateId: string, paramSet: Record<string, string>): boolean {
    const history = parameterHistory.value[planTemplateId]
    if (!history || history.length === 0) return false
    return history.some(existing => areParameterSetsEqual(existing, paramSet))
  }

  function saveParameterSet(planTemplateId: string, paramSet: Record<string, string>): void {
    if (!planTemplateId || Object.keys(paramSet).length === 0) return
    if (isDuplicate(planTemplateId, paramSet)) {
      logger.debug('[ParameterHistoryStore] Parameter set is duplicate, not saving')
      return
    }

    const history = parameterHistory.value[planTemplateId]
      ? [...parameterHistory.value[planTemplateId]]
      : []
    history.unshift(paramSet)
    if (history.length > MAX_HISTORY_SIZE) {
      history.splice(MAX_HISTORY_SIZE)
    }
    parameterHistory.value = {
      ...parameterHistory.value,
      [planTemplateId]: history,
    }
    persist()
    logger.debug('[ParameterHistoryStore] Saved parameter set to history for', planTemplateId)
  }

  function getToolHistoryIndex(planTemplateId: string): number {
    const idx = toolHistoryIndices.value[planTemplateId]
    return idx !== undefined ? idx : -1
  }

  function setToolHistoryIndex(planTemplateId: string, index: number): void {
    toolHistoryIndices.value = {
      ...toolHistoryIndices.value,
      [planTemplateId]: index,
    }
    persist()
  }

  function resetParamHistoryNavigation(planTemplateId?: string): void {
    if (planTemplateId) {
      const { [planTemplateId]: _, ...rest } = toolHistoryIndices.value
      toolHistoryIndices.value = rest
      logger.debug('[ParameterHistoryStore] Reset history navigation index for', planTemplateId)
    } else {
      toolHistoryIndices.value = {}
      logger.debug('[ParameterHistoryStore] Reset all tool history navigation indices')
    }
    persist()
  }

  function getParameterSetFromHistory(
    planTemplateId: string,
    historyIndex: number
  ): Record<string, string> | undefined {
    const history = parameterHistory.value[planTemplateId]
    if (!history || historyIndex < 0 || historyIndex >= history.length) return undefined
    return history[historyIndex]
  }

  function clearHistory(planTemplateId: string): void {
    const { [planTemplateId]: _, ...rest } = parameterHistory.value
    parameterHistory.value = rest
    resetParamHistoryNavigation(planTemplateId)
    logger.debug('[ParameterHistoryStore] Cleared history for', planTemplateId)
  }

  function clearAllHistory(): void {
    parameterHistory.value = {}
    toolHistoryIndices.value = {}
    persist()
    logger.debug('[ParameterHistoryStore] Cleared all history')
  }

  return {
    MAX_HISTORY_SIZE,
    getHistory,
    hasParameterHistory,
    saveParameterSet,
    getToolHistoryIndex,
    setToolHistoryIndex,
    resetParamHistoryNavigation,
    getParameterSetFromHistory,
    clearHistory,
    clearAllHistory,
  }
})
