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

import { DirectApiService } from '@/api/direct-api-service'
import { useTaskStore } from '@/stores/task'
import { computed, ref } from 'vue'

/**
 * Composable for handling task stop functionality
 * Provides reusable stop logic for components that need to stop running tasks
 */
export function useTaskStop() {
  const taskStore = useTaskStore()
  const isStopping = ref(false)

  /**
   * Check if there's a running task that can be stopped
   */
  const canStop = computed(() => {
    return taskStore.hasRunningTask() && !!taskStore.currentTask?.planId
  })

  /**
   * Stop a running task by plan ID
   * Checks execution status before and after stopping to handle backend restart scenarios
   * @param planId Plan ID to stop. If not provided, uses planId from currentTask
   * @param updateTaskState Whether to update taskStore state after stopping (default: true)
   * @returns Promise<boolean> - true if stop was successful or task was already stopped, false otherwise
   */
  const stopTask = async (planId?: string, updateTaskState: boolean = true): Promise<boolean> => {
    // Determine which planId to use
    const targetPlanId = planId || taskStore.currentTask?.planId

    if (!targetPlanId) {
      console.warn('[useTaskStop] No planId available to stop')
      return false
    }

    if (isStopping.value) {
      console.log('[useTaskStop] Stop already in progress, skipping')
      return false
    }

    console.log('[useTaskStop] Stopping task for planId:', targetPlanId)
    isStopping.value = true

    try {
      // Check execution status before stopping to handle backend restart scenario
      let taskStatus
      try {
        taskStatus = await DirectApiService.getTaskStatus(targetPlanId)
        console.log('[useTaskStop] Task status before stop:', taskStatus)

        // If task doesn't exist or is not running, reset frontend state
        if (!taskStatus.exists || !taskStatus.isRunning) {
          console.log(
            '[useTaskStop] Task is not actually running (backend may have restarted), resetting frontend state'
          )
          if (
            updateTaskState &&
            taskStore.currentTask &&
            taskStore.currentTask.planId === targetPlanId
          ) {
            taskStore.currentTask.isRunning = false
          }
          // Reset stopping flag before returning
          isStopping.value = false
          return true // Consider this a success since task is already stopped
        }
      } catch (statusError) {
        console.warn(
          '[useTaskStop] Failed to check task status, proceeding with stop:',
          statusError
        )
        // Continue with stop attempt even if status check fails
      }

      // Stop the task
      await DirectApiService.stopTask(targetPlanId)
      console.log('[useTaskStop] Task stop request sent successfully')

      // Verify status after stopping
      try {
        // Wait a bit for the backend to process the stop request
        await new Promise(resolve => setTimeout(resolve, 500))
        taskStatus = await DirectApiService.getTaskStatus(targetPlanId)
        console.log('[useTaskStop] Task status after stop:', taskStatus)

        // Update task state based on actual backend status
        if (
          updateTaskState &&
          taskStore.currentTask &&
          taskStore.currentTask.planId === targetPlanId
        ) {
          // Set isRunning to false if task is not running or doesn't exist
          taskStore.currentTask.isRunning = taskStatus.exists && taskStatus.isRunning
          if (!taskStatus.isRunning) {
            console.log('[useTaskStop] Task confirmed stopped, updated frontend state')
          }
        }
      } catch (statusError) {
        console.warn(
          '[useTaskStop] Failed to verify task status after stop, updating state anyway:',
          statusError
        )
        // Update state optimistically
        if (
          updateTaskState &&
          taskStore.currentTask &&
          taskStore.currentTask.planId === targetPlanId
        ) {
          taskStore.currentTask.isRunning = false
        }
      }

      return true
    } catch (error) {
      console.error('[useTaskStop] Failed to stop task:', error)
      return false
    } finally {
      isStopping.value = false
    }
  }

  return {
    stopTask,
    isStopping,
    canStop,
  }
}
