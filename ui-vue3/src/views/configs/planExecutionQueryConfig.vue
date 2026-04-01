<!--
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
-->
<template>
  <div class="plan-execution-query-config">
    <div class="query-form">
      <label class="form-label">{{ t('config.planExecutionQuery.planIdLabel') }}</label>
      <div class="form-row">
        <input
          v-model="planIdInput"
          type="text"
          class="plan-id-input"
          :placeholder="t('config.planExecutionQuery.planIdPlaceholder')"
          @keydown.enter="handleLoad"
        />
        <button
          type="button"
          class="load-btn"
          :disabled="loading || !planIdInput.trim()"
          @click="handleLoad"
        >
          {{
            loading ? t('config.planExecutionQuery.loading') : t('config.planExecutionQuery.load')
          }}
        </button>
      </div>
      <p v-if="loadError" class="error-message">{{ loadError }}</p>
    </div>

    <div v-if="!planRecord && !loading" class="empty-hint">
      <Icon icon="carbon:search" class="empty-icon" />
      <p>{{ t('config.planExecutionQuery.noPlanLoaded') }}</p>
    </div>

    <template v-else-if="planRecord">
      <div class="content-layout">
        <div class="summary-section">
          <h3 class="section-title">{{ t('config.planExecutionQuery.agentSummary') }}</h3>
          <div class="summary-scroll">
            <ExecutionDetails
              :plan-execution="planRecord as CompatiblePlanExecutionRecord"
              @step-selected="handleStepSelected"
            />
          </div>
        </div>
        <div class="detail-section">
          <h3 class="section-title">{{ t('config.planExecutionQuery.executionDetails') }}</h3>
          <div v-if="selectedStepId && selectedAgentDetail" class="detail-scroll">
            <div class="step-info">
              <h4 class="step-title">
                {{
                  selectedAgentDetail.agentName ||
                  selectedAgentDetail.agentDescription ||
                  selectedStepId
                }}
              </h4>
              <div class="agent-info" v-if="selectedAgentDetail.modelName">
                <span class="label">{{ t('rightPanel.callingModel') }}:</span>
                <span class="value">{{ selectedAgentDetail.modelName }}</span>
              </div>
              <div class="execution-status">
                <Icon
                  icon="carbon:checkmark-filled"
                  v-if="selectedAgentDetail.status === 'FINISHED'"
                  class="status-icon success"
                />
                <Icon
                  icon="carbon:in-progress"
                  v-else-if="selectedAgentDetail.status === 'RUNNING'"
                  class="status-icon progress"
                />
                <Icon icon="carbon:time" v-else class="status-icon pending" />
                <span class="status-text">
                  {{
                    selectedAgentDetail.status === 'FINISHED'
                      ? t('rightPanel.status.completed')
                      : selectedAgentDetail.status === 'RUNNING'
                        ? t('rightPanel.status.executing')
                        : t('rightPanel.status.pending')
                  }}
                </span>
              </div>
            </div>
            <div
              v-if="
                selectedAgentDetail.thinkActSteps && selectedAgentDetail.thinkActSteps.length > 0
              "
              class="think-act-steps"
            >
              <h4 class="steps-heading">{{ t('rightPanel.thinkAndActionSteps') }}</h4>
              <div class="steps-container">
                <div
                  v-for="(tas, index) in selectedAgentDetail.thinkActSteps"
                  :key="index"
                  class="think-act-step"
                >
                  <div class="step-header">
                    <span class="step-number">#{{ index + 1 }}</span>
                  </div>
                  <div class="think-section">
                    <h5>
                      <Icon icon="carbon:thinking" />
                      {{ t('rightPanel.thinking') }}
                    </h5>
                    <div class="think-content">
                      <div class="input">
                        <div class="label-row">
                          <span class="label">{{ t('rightPanel.input') }}:</span>
                          <div class="label-actions">
                            <button
                              class="copy-btn"
                              @click="copyToClipboard(tas.thinkInput)"
                              :title="t('rightPanel.copyToClipboard')"
                            >
                              <Icon icon="carbon:copy" />
                            </button>
                            <span class="char-count-badge">
                              {{ tas.inputCharCount ?? 0 }} tokens
                              <span
                                v-if="
                                  calculateContextUsagePercentage(
                                    tas.inputCharCount,
                                    tas.modelContextLimit
                                  ) !== null
                                "
                              >
                                {{
                                  calculateContextUsagePercentage(
                                    tas.inputCharCount,
                                    tas.modelContextLimit
                                  )
                                }}
                                context used
                              </span>
                            </span>
                          </div>
                        </div>
                        <div class="pre-container">
                          <pre>{{ formatJson(tas.thinkInput) }}</pre>
                        </div>
                      </div>
                      <div class="output">
                        <div class="label-row">
                          <span class="label">{{ t('rightPanel.output') }}:</span>
                          <div class="label-actions">
                            <button
                              class="copy-btn"
                              @click="copyToClipboard(tas.thinkOutput)"
                              :title="t('rightPanel.copyToClipboard')"
                            >
                              <Icon icon="carbon:copy" />
                            </button>
                            <span class="char-count-badge"
                              >{{ tas.outputCharCount ?? 0 }} tokens</span
                            >
                          </div>
                        </div>
                        <div class="pre-container">
                          <pre>{{ formatJson(tas.thinkOutput) }}</pre>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div v-if="tas.actionNeeded" class="action-section">
                    <h5>
                      <Icon icon="carbon:play" />
                      {{ t('rightPanel.action') }}
                    </h5>
                    <div class="action-content">
                      <div
                        v-for="(actToolInfo, ti) in tas.actToolInfoList || []"
                        :key="`tool-${ti}-${actToolInfo?.id || actToolInfo?.name || ti}`"
                        class="tool-execution-item"
                      >
                        <div class="tool-info">
                          <span class="label">{{ t('rightPanel.tool') }}:</span>
                          <span class="value">{{ actToolInfo?.name || 'N/A' }}</span>
                        </div>
                        <div class="input">
                          <span class="label">{{ t('rightPanel.toolParameters') }}:</span>
                          <pre>{{ formatJson(actToolInfo?.parameters) }}</pre>
                        </div>
                        <div class="output">
                          <span class="label">{{ t('rightPanel.executionResult') }}:</span>
                          <pre>{{ formatJson(actToolInfo?.result) }}</pre>
                        </div>
                      </div>
                      <div
                        v-if="!tas.actToolInfoList || tas.actToolInfoList.length === 0"
                        class="no-tools"
                      >
                        <p>{{ t('rightPanel.noToolsExecuted') }}</p>
                      </div>
                    </div>
                    <div v-if="tas.subPlanExecutionRecord" class="sub-plan-section">
                      <h5>
                        <Icon icon="carbon:tree-view" />
                        {{ t('rightPanel.subPlan') }}
                      </h5>
                      <div class="sub-plan-content">
                        <div class="sub-plan-header">
                          <div class="sub-plan-info">
                            <span class="label">{{ t('rightPanel.subPlanId') }}:</span>
                            <span class="value">{{
                              tas.subPlanExecutionRecord.currentPlanId
                            }}</span>
                          </div>
                          <div class="sub-plan-info" v-if="tas.subPlanExecutionRecord.title">
                            <span class="label">{{ t('rightPanel.title') }}:</span>
                            <span class="value">{{ tas.subPlanExecutionRecord.title }}</span>
                          </div>
                          <div class="sub-plan-status">
                            <Icon
                              icon="carbon:checkmark-filled"
                              v-if="tas.subPlanExecutionRecord.completed"
                              class="status-icon success"
                            />
                            <Icon icon="carbon:in-progress" v-else class="status-icon progress" />
                            <span class="status-text">
                              {{
                                tas.subPlanExecutionRecord.completed
                                  ? t('rightPanel.status.completed')
                                  : t('rightPanel.status.executing')
                              }}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div
              v-else-if="selectedAgentDetail && !selectedAgentDetail.thinkActSteps?.length"
              class="no-steps-message"
            >
              <p>{{ t('rightPanel.noStepDetails') }}</p>
            </div>
          </div>
          <div v-else class="select-hint">
            <Icon icon="carbon:touch-interaction" class="hint-icon" />
            <p>{{ t('config.planExecutionQuery.selectAgentHint') }}</p>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { DirectApiService } from '@/api/lynxe-service'
import ExecutionDetails from '@/components/chat/ExecutionDetails.vue'
import { useToast } from '@/plugins/useToast'
import type { AgentExecutionRecordDetail } from '@/types/agent-execution-detail'
import type { CompatiblePlanExecutionRecord } from '@/types/message-dialog'
import type { PlanExecutionRecord } from '@/types/plan-execution-record'
import { logger } from '@/utils/logger'
import { Icon } from '@iconify/vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

defineOptions({
  name: 'PlanExecutionQueryConfig',
})

const { t } = useI18n()
const toast = useToast()

const planIdInput = ref('')
const loading = ref(false)
const loadError = ref('')
const planRecord = ref<PlanExecutionRecord | null>(null)
const selectedStepId = ref<string | null>(null)
const selectedAgentDetail = ref<AgentExecutionRecordDetail | null>(null)

async function handleLoad() {
  const planId = planIdInput.value.trim()
  if (!planId) return
  loading.value = true
  loadError.value = ''
  planRecord.value = null
  selectedStepId.value = null
  selectedAgentDetail.value = null
  try {
    const record = await DirectApiService.getDetails(planId)
    if (record) {
      planRecord.value = record
    } else {
      loadError.value = t('config.planExecutionQuery.planNotFound')
    }
  } catch (e) {
    logger.error('[PlanExecutionQueryConfig] Load failed:', e)
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function handleStepSelected(stepId: string) {
  if (!stepId) return
  selectedStepId.value = stepId
  selectedAgentDetail.value = null
  try {
    const detail = await DirectApiService.getAgentExecutionDetail(stepId)
    selectedAgentDetail.value = detail ?? null
  } catch (e) {
    logger.error('[PlanExecutionQueryConfig] Fetch agent detail failed:', e)
  }
}

function formatJson(jsonData: unknown): string {
  if (jsonData === null || typeof jsonData === 'undefined' || jsonData === '') {
    return 'N/A'
  }
  if (typeof jsonData === 'object' && jsonData !== null) {
    try {
      return JSON.stringify(jsonData, null, 2)
    } catch {
      return String(jsonData)
    }
  }
  if (typeof jsonData === 'string') {
    const trimmed = jsonData.trim()
    if (trimmed === '') return 'N/A'
    try {
      return JSON.stringify(JSON.parse(trimmed), null, 2)
    } catch {
      return trimmed
    }
  }
  return String(jsonData)
}

function calculateContextUsagePercentage(
  inputTokenCount: number | undefined | null,
  modelContextLimit: number | undefined | null
): string | null {
  if (!inputTokenCount || !modelContextLimit || modelContextLimit <= 0) return null
  const percentage = (inputTokenCount / modelContextLimit) * 100
  return `${percentage.toFixed(1)}%`
}

async function copyToClipboard(text: string | null | undefined) {
  if (!text) {
    toast.error(t('rightPanel.copyFailed') || 'Failed to copy')
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    toast.success(t('rightPanel.copySuccess') || 'Copied to clipboard')
  } catch {
    toast.error(t('rightPanel.copyFailed') || 'Failed to copy')
  }
}
</script>

<style lang="less" scoped>
.plan-execution-query-config {
  max-width: 1200px;
}

.query-form {
  margin-bottom: 24px;

  .form-label {
    display: block;
    margin-bottom: 8px;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.9);
    font-size: 14px;
  }

  .form-row {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  .plan-id-input {
    flex: 1;
    max-width: 400px;
    padding: 10px 14px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.15);
    border-radius: 6px;
    color: #fff;
    font-size: 14px;

    &::placeholder {
      color: rgba(255, 255, 255, 0.4);
    }

    &:focus {
      outline: none;
      border-color: #667eea;
    }
  }

  .load-btn {
    padding: 10px 20px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border: none;
    border-radius: 6px;
    color: #fff;
    font-weight: 500;
    cursor: pointer;
    transition: opacity 0.2s;

    &:hover:not(:disabled) {
      opacity: 0.9;
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  .error-message {
    margin-top: 8px;
    color: #f87171;
    font-size: 13px;
  }
}

.empty-hint {
  text-align: center;
  padding: 48px 24px;
  color: rgba(255, 255, 255, 0.5);

  .empty-icon {
    font-size: 48px;
    margin-bottom: 16px;
    opacity: 0.5;
  }

  p {
    margin: 0;
    font-size: 14px;
  }
}

.content-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  min-height: 400px;
}

@media (max-width: 900px) {
  .content-layout {
    grid-template-columns: 1fr;
  }
}

.section-title {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
}

.summary-section,
.detail-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.summary-scroll,
.detail-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: rgba(255, 255, 255, 0.05);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.2);
    border-radius: 3px;
  }
}

.step-info {
  padding: 12px 0;
  margin-bottom: 16px;

  .step-title {
    color: #fff;
    margin: 0 0 12px 0;
    font-size: 14px;
    font-weight: 600;
  }

  .agent-info {
    margin-bottom: 8px;
    font-size: 12px;
    .label {
      color: #888;
      margin-right: 8px;
    }
    .value {
      color: #ccc;
    }
  }

  .execution-status {
    display: flex;
    align-items: center;
    gap: 8px;
    .status-icon {
      font-size: 14px;
      &.success {
        color: #22c55e;
      }
      &.progress {
        color: #667eea;
      }
      &.pending {
        color: #9ca3af;
      }
    }
    .status-text {
      color: #ccc;
      font-size: 12px;
    }
  }
}

.think-act-steps {
  margin-top: 12px;

  .steps-heading {
    color: #fff;
    margin: 0 0 12px 0;
    font-size: 14px;
    font-weight: 600;
  }
}

.steps-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.think-act-step {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 16px;

  .step-header .step-number {
    font-weight: 600;
    color: #667eea;
    font-size: 14px;
  }

  .think-section,
  .action-section,
  .sub-plan-section {
    margin-bottom: 16px;
    &:last-child {
      margin-bottom: 0;
    }
    h5 {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0 0 12px 0;
      font-size: 14px;
      font-weight: 600;
      color: #fff;
    }
  }

  .think-content,
  .action-content {
    .tool-execution-item {
      margin-bottom: 16px;
      padding: 12px;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 6px;
      border: 1px solid rgba(255, 255, 255, 0.05);
    }
    .no-tools {
      padding: 12px;
      text-align: center;
      color: rgba(255, 255, 255, 0.5);
      font-size: 13px;
    }
    .input,
    .output,
    .tool-info {
      margin-bottom: 12px;
      .label-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 4px;
      }
      .label {
        font-weight: 600;
        color: #888;
        font-size: 12px;
      }
      .label-actions {
        display: flex;
        align-items: center;
        gap: 6px;
      }
      .copy-btn {
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(0, 0, 0, 0.3);
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 4px;
        padding: 4px 6px;
        color: rgba(255, 255, 255, 0.7);
        cursor: pointer;
        font-size: 12px;
        &:hover {
          background: rgba(0, 0, 0, 0.5);
          color: rgba(255, 255, 255, 0.9);
        }
      }
      .value {
        color: #ccc;
        font-size: 14px;
      }
      pre {
        background: rgba(0, 0, 0, 0.3);
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 4px;
        padding: 12px;
        color: #ccc;
        font-size: 12px;
        overflow-x: auto;
        white-space: pre-wrap;
        margin: 0;
        max-height: 200px;
        overflow-y: auto;
      }
      .char-count-badge {
        background: rgba(0, 0, 0, 0.6);
        border-radius: 4px;
        padding: 2px 6px;
        font-size: 10px;
        color: rgba(255, 255, 255, 0.7);
      }
    }
  }

  .sub-plan-content .sub-plan-header {
    background: rgba(102, 126, 234, 0.1);
    border: 1px solid rgba(102, 126, 234, 0.3);
    border-radius: 6px;
    padding: 12px;
    margin-top: 12px;
    .sub-plan-info {
      display: flex;
      margin-bottom: 8px;
      font-size: 12px;
      .label {
        min-width: 80px;
        color: #888;
      }
      .value {
        color: #ccc;
        word-break: break-word;
      }
    }
    .sub-plan-status {
      display: flex;
      align-items: center;
      gap: 6px;
      padding-top: 8px;
      border-top: 1px solid rgba(255, 255, 255, 0.1);
      .status-icon.success {
        color: #22c55e;
      }
      .status-icon.progress {
        color: #667eea;
      }
      .status-text {
        color: #ccc;
        font-size: 12px;
      }
    }
  }
}

.no-steps-message {
  padding: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
}

.select-hint {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: rgba(255, 255, 255, 0.45);
  text-align: center;

  .hint-icon {
    font-size: 40px;
    margin-bottom: 12px;
    opacity: 0.6;
  }
  p {
    margin: 0;
    font-size: 14px;
  }
}
</style>
