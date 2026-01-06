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

import { computed, ref } from 'vue'
import { useTaskStore } from '@/stores/task'
import { useMessageDialogSingleton } from '@/composables/useMessageDialog'

/**
 * Unified composable for task execution state management
 * Single Source of Truth for all components checking execution state
 *
 * This composable provides a consistent interface for checking task execution state
 * across all components (InputArea, ExecutionController, etc.)
 */
export function useTaskExecutionState() {
  const taskStore = useTaskStore()
  const messageDialog = useMessageDialogSingleton()

  // Local execution flag (for preventing concurrent execution during API call)
  // This prevents race conditions when API request is in flight but taskStore hasn't updated yet
  const isExecutingLocally = ref(false)

  /**
   * Check if a task is currently running
   * Primary state source: taskStore.currentTask.isRunning
   * This is the authoritative source of truth
   */
  const isTaskRunning = computed(() => {
    return taskStore.hasRunningTask()
  })

  /**
   * Check if execution is in progress (including local execution state)
   * Used to prevent concurrent execution during API calls
   */
  const isExecutionInProgress = computed(() => {
    return isTaskRunning.value || isExecutingLocally.value || messageDialog.isLoading.value
  })

  /**
   * Check if execution can be started
   * Validates all conditions before allowing execution
   */
  const canExecute = computed(() => {
    // Block if execution is already in progress (includes task running, local execution, and messageDialog loading)
    return !isExecutionInProgress.value
  })

  /**
   * Start local execution (call before API request)
   * Sets local flag to prevent concurrent execution
   */
  const startLocalExecution = () => {
    isExecutingLocally.value = true
  }

  /**
   * Stop local execution (call after API request completes or fails)
   * Clears local flag
   */
  const stopLocalExecution = () => {
    isExecutingLocally.value = false
  }

  /**
   * Get current running plan ID
   */
  const getCurrentPlanId = computed(() => {
    return taskStore.currentTask?.planId || null
  })

  return {
    // State
    isTaskRunning,
    isExecutionInProgress,
    canExecute,
    isExecutingLocally: computed(() => isExecutingLocally.value),
    currentPlanId: getCurrentPlanId,

    // Methods
    startLocalExecution,
    stopLocalExecution,
  }
}

// Singleton instance for global use
let singletonInstance: ReturnType<typeof useTaskExecutionState> | null = null

/**
 * Get or create singleton instance of useTaskExecutionState
 * This ensures all components use the same state instance
 */
export function useTaskExecutionStateSingleton() {
  if (!singletonInstance) {
    singletonInstance = useTaskExecutionState()
  }
  return singletonInstance
}
