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

import { DirectApiService } from '@/api/lynxe-service'
import { useTaskStore } from '@/stores/new/task'
import type { PlanExecutionRecord } from '@/types/plan-execution-record'
import { logger } from '@/utils/logger'
import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'

const POLL_INTERVAL = 5000
const MAX_RETRY_ATTEMPTS = 10
const POST_COMPLETION_POLL_COUNT = 10

export const usePlanExecutionStore = defineStore('planExecution', () => {
  const recordsByPlanId = ref<Record<string, PlanExecutionRecord>>({})
  const trackedPlanIds = ref<Set<string>>(new Set())

  const completedPlansPollCount = reactive(new Map<string, number>())
  const planPollAttempts = reactive(new Map<string, number>())
  const planRetryAttempts = reactive(new Map<string, number>())
  const isPolling = ref(false)
  const pollTimer = ref<number | null>(null)

  function getPlanExecutionRecord(planId: string): PlanExecutionRecord | undefined {
    return recordsByPlanId.value[planId]
  }

  function setCachedPlanRecord(planId: string, record: PlanExecutionRecord): void {
    if (!planId) {
      logger.warn('[PlanExecutionStore] Cannot cache plan record with empty planId')
      return
    }
    recordsByPlanId.value = {
      ...recordsByPlanId.value,
      [planId]: record,
    }
    logger.debug('[PlanExecutionStore] Cached plan record:', planId)
  }

  function getTrackedPlanIds(): string[] {
    return Array.from(trackedPlanIds.value)
  }

  function deleteExecutionDetails(planId: string): void {
    const { [planId]: _removed, ...rest } = recordsByPlanId.value
    recordsByPlanId.value = rest
  }

  async function pollPlanStatus(planId: string): Promise<void> {
    if (!planId) return

    try {
      const details = await DirectApiService.getDetails(planId)
      planRetryAttempts.delete(planId)

      if (!details) {
        const retryCount = planRetryAttempts.get(planId) || 0
        if (retryCount < MAX_RETRY_ATTEMPTS) {
          planRetryAttempts.set(planId, retryCount + 1)
          logger.debug(
            `[PlanExecutionStore] Plan ${planId} not found yet, retrying (${retryCount + 1}/${MAX_RETRY_ATTEMPTS})`
          )
          setTimeout(() => {
            if (trackedPlanIds.value.has(planId)) {
              pollPlanStatus(planId).catch(error => {
                logger.error(`[PlanExecutionStore] Retry poll failed for ${planId}:`, error)
              })
            }
          }, POLL_INTERVAL)
        } else {
          logger.warn(
            `[PlanExecutionStore] Plan ${planId} not found after ${MAX_RETRY_ATTEMPTS} attempts, giving up`
          )
          untrackPlan(planId)
        }
        return
      }

      const currentAttempts = planPollAttempts.get(planId) || 0
      planPollAttempts.set(planId, currentAttempts + 1)

      const recordKey = details.rootPlanId || details.currentPlanId
      if (!recordKey) {
        logger.warn('[PlanExecutionStore] Plan record has no rootPlanId or currentPlanId:', details)
        return
      }

      recordsByPlanId.value = {
        ...recordsByPlanId.value,
        [recordKey]: details,
      }
      if (planId !== recordKey) {
        recordsByPlanId.value = {
          ...recordsByPlanId.value,
          [planId]: details,
        }
        logger.debug('[PlanExecutionStore] Stored record with both keys:', { planId, recordKey })
      }

      if (details.completed) {
        logger.debug(`[PlanExecutionStore] Plan ${recordKey} completed, checking for summary...`)
        const hasSummary = details.summary || details.result || details.message
        const currentPollCount = completedPlansPollCount.get(recordKey) || 0

        if (!hasSummary && currentPollCount < POST_COMPLETION_POLL_COUNT) {
          completedPlansPollCount.set(recordKey, currentPollCount + 1)
          logger.debug(
            `[PlanExecutionStore] Plan ${recordKey} completed but no summary yet, continuing to poll (${currentPollCount + 1}/${POST_COMPLETION_POLL_COUNT})`
          )
        } else {
          logger.debug(`[PlanExecutionStore] Plan ${recordKey} completed, cleaning up...`, {
            hasSummary: !!hasSummary,
            pollCount: currentPollCount,
          })
          try {
            await DirectApiService.deleteExecutionDetails(recordKey)
            logger.debug(`[PlanExecutionStore] Deleted execution details for plan: ${recordKey}`)
          } catch (error: unknown) {
            const message = error instanceof Error ? error.message : 'Unknown error'
            logger.error(`[PlanExecutionStore] Failed to delete execution details: ${message}`)
          }
          untrackPlan(planId)
          completedPlansPollCount.delete(recordKey)
          setTimeout(() => {
            const { [recordKey]: _r, ...rest } = recordsByPlanId.value
            recordsByPlanId.value = rest
            if (planId !== recordKey && recordsByPlanId.value[planId]) {
              const { [planId]: _p, ...rest2 } = recordsByPlanId.value
              recordsByPlanId.value = rest2
            }
          }, 5000)
        }
      }

      if (
        details.status === 'failed' &&
        details.message &&
        !details.message.includes('Failed to get detailed information')
      ) {
        logger.error(`[PlanExecutionStore] Plan ${recordKey} failed:`, details.message)
      }
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error'
      logger.error(`[PlanExecutionStore] Failed to poll plan status for ${planId}:`, errorMessage)
      const retryCount = planRetryAttempts.get(planId) || 0
      if (retryCount < MAX_RETRY_ATTEMPTS) {
        planRetryAttempts.set(planId, retryCount + 1)
        logger.debug(
          `[PlanExecutionStore] Network error for ${planId}, retrying (${retryCount + 1}/${MAX_RETRY_ATTEMPTS})`
        )
        setTimeout(
          () => {
            if (trackedPlanIds.value.has(planId)) {
              pollPlanStatus(planId).catch(err => {
                logger.error(`[PlanExecutionStore] Retry poll failed for ${planId}:`, err)
              })
            }
          },
          POLL_INTERVAL * (retryCount + 1)
        )
      }
    }
  }

  async function pollAllTrackedPlans(): Promise<void> {
    if (isPolling.value) {
      logger.debug('[PlanExecutionStore] Previous polling still in progress, skipping')
      return
    }
    const plansToPoll = new Set(trackedPlanIds.value)
    for (const [planId] of completedPlansPollCount.entries()) {
      plansToPoll.add(planId)
    }
    if (plansToPoll.size === 0) return

    try {
      isPolling.value = true
      const pollPromises = Array.from(plansToPoll).map(planId => pollPlanStatus(planId))
      await Promise.all(pollPromises)
    } catch (error: unknown) {
      logger.error('[PlanExecutionStore] Failed to poll tracked plans:', error)
    } finally {
      isPolling.value = false
    }
  }

  function startPolling(): void {
    if (pollTimer.value) {
      clearInterval(pollTimer.value)
    }
    pollTimer.value = window.setInterval(() => {
      pollAllTrackedPlans()
    }, POLL_INTERVAL)
    logger.debug('[PlanExecutionStore] Started adaptive polling')
  }

  function stopPolling(): void {
    if (completedPlansPollCount.size > 0) {
      logger.debug(
        `[PlanExecutionStore] Not stopping polling - ${completedPlansPollCount.size} completed plans still waiting for summary`
      )
      return
    }
    if (pollTimer.value) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
    }
    logger.debug('[PlanExecutionStore] Stopped polling')
  }

  function trackPlan(planId: string): void {
    if (!planId) {
      logger.warn('[PlanExecutionStore] Cannot track empty planId')
      return
    }
    trackedPlanIds.value.add(planId)
    planPollAttempts.set(planId, 0)
    planRetryAttempts.set(planId, 0)
    logger.debug('[PlanExecutionStore] Tracking plan:', planId)

    if (!pollTimer.value) {
      startPolling()
    }
    pollPlanStatus(planId).catch(error => {
      logger.error(`[PlanExecutionStore] Initial poll failed for ${planId}:`, error)
    })
  }

  function untrackPlan(planId: string): void {
    trackedPlanIds.value.delete(planId)
    planPollAttempts.delete(planId)
    planRetryAttempts.delete(planId)
    logger.debug('[PlanExecutionStore] Untracking plan:', planId)

    if (trackedPlanIds.value.size === 0 && completedPlansPollCount.size === 0) {
      stopPolling()
    }
  }

  async function pollPlanStatusImmediately(planId: string): Promise<void> {
    logger.debug(`[PlanExecutionStore] Polling plan status immediately for: ${planId}`)
    await pollPlanStatus(planId)
  }

  function handlePlanExecutionRequested(planId: string): void {
    logger.debug('[PlanExecutionStore] Received plan execution request:', { planId })
    if (!planId) {
      logger.error('[PlanExecutionStore] Invalid plan execution request: missing planId')
      return
    }
    const taskStore = useTaskStore()
    taskStore.setTaskRunning(planId)
    trackPlan(planId)
  }

  function cleanup(): void {
    stopPolling()
    trackedPlanIds.value.clear()
    completedPlansPollCount.clear()
    planPollAttempts.clear()
    planRetryAttempts.clear()
    recordsByPlanId.value = {}
    isPolling.value = false
  }

  return {
    recordsByPlanId,
    trackedPlanIds,
    isPolling,
    getPlanExecutionRecord,
    setCachedPlanRecord,
    getTrackedPlanIds,
    deleteExecutionDetails,
    trackPlan,
    untrackPlan,
    handlePlanExecutionRequested,
    pollPlanStatusImmediately,
    startPolling,
    stopPolling,
    cleanup,
  }
})
