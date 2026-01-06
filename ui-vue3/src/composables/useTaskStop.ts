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
import { usePlanExecutionSingleton } from '@/composables/usePlanExecution'
import { useTaskStore } from '@/stores/task'
import { computed, ref } from 'vue'

/**
 * Composable for handling task stop functionality
 * Provides reusable stop logic for components that need to stop running tasks
 */
export function useTaskStop() {
  const taskStore = useTaskStore()
  const planExecution = usePlanExecutionSingleton()
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
   * @returns Promise<boolean> - true if stop was successful or task was already stopped, false otherwise
   */
  const stopTask = async (planId?: string): Promise<boolean> => {
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
      // Optimistic update: immediately update state for instant UI feedback
      if (taskStore.currentTask) {
        taskStore.currentTask.isRunning = false
      }

      // Untrack plan immediately
      if (planExecution.trackedPlanIds.value.has(targetPlanId)) {
        planExecution.untrackPlan(targetPlanId)
        console.log('[useTaskStop] Untracked plan:', targetPlanId)
      }

      // Update plan execution record to mark as stopped
      const record = planExecution.getPlanExecutionRecord(targetPlanId)
      if (record && (!record.completed || record.status !== 'failed')) {
        planExecution.setCachedPlanRecord(targetPlanId, {
          ...record,
          completed: true,
          status: 'failed',
          summary: record.summary || 'Task stopped by user',
        })
        console.log('[useTaskStop] Marked plan execution record as stopped:', targetPlanId)
      }

      // Check execution status before stopping to handle backend restart scenario
      let taskStatus
      try {
        taskStatus = await DirectApiService.getTaskStatus(targetPlanId)
        console.log('[useTaskStop] Task status before stop:', taskStatus)

        // If task doesn't exist or is not running, state already updated optimistically
        if (!taskStatus.exists || !taskStatus.isRunning) {
          console.log(
            '[useTaskStop] Task is not actually running (backend may have restarted), state already updated'
          )
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

      // Verify status after stopping (optional, for confirmation)
      try {
        // Wait a bit for the backend to process the stop request
        await new Promise(resolve => setTimeout(resolve, 500))
        taskStatus = await DirectApiService.getTaskStatus(targetPlanId)
        console.log('[useTaskStop] Task status after stop:', taskStatus)

        // Update state based on actual backend status (if different from optimistic update)
        if (taskStore.currentTask && taskStore.currentTask.planId === targetPlanId) {
          taskStore.currentTask.isRunning = taskStatus.exists && taskStatus.isRunning
          if (!taskStatus.isRunning) {
            console.log('[useTaskStop] Task confirmed stopped, updated frontend state')
          }
        }
      } catch (statusError) {
        console.warn('[useTaskStop] Failed to verify task status after stop:', statusError)
        // State already updated optimistically, so this is fine
      }

      return true
    } catch (error) {
      console.error('[useTaskStop] Failed to stop task:', error)
      // Keep state updated (user clicked stop, so state should reflect that)
      if (taskStore.currentTask) {
        taskStore.currentTask.isRunning = false
      }
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
