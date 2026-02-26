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

import { usePlanExecutionStore } from '@/stores/new/planExecution'
import { storeToRefs } from 'pinia'
import { readonly } from 'vue'

/**
 * Composable for plan execution state and actions.
 * Delegates to the planExecution Pinia store (single source of truth).
 * Use in components; for reactivity use store properties directly or storeToRefs(usePlanExecutionStore()).
 */
export function usePlanExecution() {
  const store = usePlanExecutionStore()
  const { recordsByPlanId, trackedPlanIds, isPolling } = storeToRefs(store)
  return {
    planExecutionRecords: readonly(recordsByPlanId),
    recordsByPlanId: readonly(recordsByPlanId),
    trackedPlanIds: readonly(trackedPlanIds),
    isPolling: readonly(isPolling),
    getPlanExecutionRecord: store.getPlanExecutionRecord,
    setCachedPlanRecord: store.setCachedPlanRecord,
    getTrackedPlanIds: store.getTrackedPlanIds,
    trackPlan: store.trackPlan,
    untrackPlan: store.untrackPlan,
    handlePlanExecutionRequested: store.handlePlanExecutionRequested,
    pollPlanStatusImmediately: store.pollPlanStatusImmediately,
    startPolling: store.startPolling,
    stopPolling: store.stopPolling,
    cleanup: store.cleanup,
  }
}

/**
 * Singleton-style access to the plan execution store.
 * Prefer usePlanExecutionStore() directly for clearer single source of truth.
 */
export function usePlanExecutionSingleton() {
  return usePlanExecution()
}
