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

import { useConversationStore } from '@/stores/new/conversation'
import type { AgentExecutionRecordDetail } from '@/types/agent-execution-detail'
import type { InputMessage } from '@/types/message-dialog'
import type { PlanExecutionRecordResponse } from '@/types/plan-execution-record'
import { logger } from '@/utils/logger'
import { LlmCheckService } from '@/utils/llm-check'

export class DirectApiService {
  private static readonly BASE_URL = '/api/executor'

  // Send task directly (direct execution mode)
  public static async sendMessage(query: InputMessage): Promise<unknown> {
    return LlmCheckService.withLlmCheck(async () => {
      // Add requestSource to distinguish from HTTP requests
      const requestBody = {
        ...query,
        requestSource: 'VUE_DIALOG',
      }

      const response = await fetch(`${this.BASE_URL}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
      })
      if (!response.ok) throw new Error(`API request failed: ${response.status}`)
      return await response.json()
    })
  }

  // Send simple chat message with SSE streaming (no plan execution, just LLM chat)
  public static async sendChatMessage(
    query: InputMessage,
    requestSource: 'VUE_DIALOG' | 'VUE_SIDEBAR' = 'VUE_DIALOG',
    onChunk?: (chunk: {
      type: string
      content?: string
      conversationId?: string
      message?: string
    }) => void,
    abortSignal?: AbortSignal
  ): Promise<{ conversationId?: string; message?: string }> {
    return LlmCheckService.withLlmCheck(async () => {
      logger.debug('[DirectApiService] sendChatMessage called with:', {
        input: query.input,
        uploadedFiles: query.uploadedFiles,
        uploadKey: query.uploadKey,
        requestSource,
      })

      const requestBody: Record<string, unknown> = {
        input: query.input,
        requestSource: requestSource,
      }

      // Include conversationId from conversation store if available
      const conversationStore = useConversationStore()
      if (conversationStore.selectedConversationId) {
        requestBody.conversationId = conversationStore.selectedConversationId
        logger.debug(
          '[DirectApiService] Including conversationId from conversation store:',
          conversationStore.selectedConversationId
        )
      }

      // Include uploaded files if present
      if (query.uploadedFiles && query.uploadedFiles.length > 0) {
        requestBody.uploadedFiles = query.uploadedFiles
        logger.debug('[DirectApiService] Including uploaded files:', query.uploadedFiles.length)
      }

      // Include uploadKey if present
      if (query.uploadKey) {
        requestBody.uploadKey = query.uploadKey
        logger.debug('[DirectApiService] Including uploadKey:', query.uploadKey)
      }

      logger.debug('[DirectApiService] Making SSE request to:', `${this.BASE_URL}/chat`)
      logger.debug('[DirectApiService] Request body:', requestBody)

      const fetchOptions: RequestInit = {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(requestBody),
      }

      // Only include signal if provided (to satisfy TypeScript exactOptionalPropertyTypes)
      if (abortSignal !== undefined) {
        fetchOptions.signal = abortSignal
      }

      let response: Response
      try {
        response = await fetch(`${this.BASE_URL}/chat`, fetchOptions)
      } catch (fetchError) {
        // Handle abort errors gracefully
        if (fetchError instanceof Error && fetchError.name === 'AbortError') {
          logger.debug('[DirectApiService] Fetch request was aborted')
          throw fetchError
        }
        throw fetchError
      }

      logger.debug('[DirectApiService] Response status:', response.status, response.ok)
      logger.debug('[DirectApiService] Response headers:', {
        contentType: response.headers.get('Content-Type'),
        transferEncoding: response.headers.get('Transfer-Encoding'),
      })

      if (!response.ok) {
        const errorText = await response.text()
        logger.error('[DirectApiService] Request failed:', errorText)
        throw new Error(`Failed to send chat message: ${response.status}`)
      }

      // Handle SSE streaming
      if (!response.body) {
        throw new Error('Response body is null')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let conversationId: string | undefined
      let accumulatedMessage = ''

      logger.debug('[DirectApiService] Starting to read SSE stream...')

      try {
        while (true) {
          // Check if aborted before reading
          if (abortSignal?.aborted) {
            logger.debug('[DirectApiService] Stream aborted, stopping read')
            reader.releaseLock()
            throw new DOMException('The operation was aborted.', 'AbortError')
          }

          const { done, value } = await reader.read()
          logger.debug('[DirectApiService] Read chunk:', { done, valueLength: value?.length })
          if (done) break

          // Check if aborted after reading
          if (abortSignal?.aborted) {
            logger.debug('[DirectApiService] Stream aborted after read, stopping processing')
            reader.releaseLock()
            throw new DOMException('The operation was aborted.', 'AbortError')
          }

          buffer += decoder.decode(value, { stream: true })
          logger.debug(
            '[DirectApiService] Buffer length:',
            buffer.length,
            'content:',
            buffer.substring(0, 200)
          )
          const lines = buffer.split('\n\n')
          buffer = lines.pop() || '' // Keep incomplete line in buffer
          logger.debug(
            '[DirectApiService] Split into',
            lines.length,
            'lines, buffer remaining:',
            buffer.length
          )

          for (const line of lines) {
            logger.debug('[DirectApiService] Processing line:', line)
            if (line.startsWith('data:')) {
              // Handle both 'data:' and 'data: ' formats
              const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
              logger.debug('[DirectApiService] Extracted data:', data)
              try {
                const parsed = JSON.parse(data) as {
                  type: string
                  content?: string
                  conversationId?: string
                  message?: string
                }
                logger.debug('[DirectApiService] Parsed SSE event:', parsed)

                if (parsed.type === 'start' && parsed.conversationId) {
                  conversationId = parsed.conversationId
                  logger.debug(
                    '[DirectApiService] Got start event with conversationId:',
                    conversationId
                  )
                  if (onChunk) {
                    onChunk({ type: 'start', conversationId: parsed.conversationId })
                  }
                } else if (parsed.type === 'chunk' && parsed.content) {
                  accumulatedMessage += parsed.content
                  logger.debug(
                    '[DirectApiService] Got chunk, accumulated length:',
                    accumulatedMessage.length
                  )
                  if (onChunk) {
                    onChunk({ type: 'chunk', content: parsed.content })
                  }
                } else if (parsed.type === 'done') {
                  logger.debug('[DirectApiService] Got done event')
                  if (onChunk) {
                    onChunk({ type: 'done' })
                  }
                } else if (parsed.type === 'error') {
                  logger.error('[DirectApiService] Got error event:', parsed.message)
                  if (onChunk) {
                    onChunk({
                      type: 'error',
                      message: parsed.message || 'Streaming error occurred',
                    })
                  }
                  // Break the loop to stop processing
                  reader.releaseLock()
                  throw new Error(parsed.message || 'Streaming error occurred')
                }
              } catch (parseError) {
                logger.error(
                  '[DirectApiService] Error parsing SSE data:',
                  parseError,
                  'Data:',
                  data
                )
              }
            }
          }
        }
      } finally {
        reader.releaseLock()
      }

      const result: { conversationId?: string; message?: string } = {}
      if (conversationId) {
        result.conversationId = conversationId
      }
      result.message = accumulatedMessage || 'No response received'
      logger.debug('[DirectApiService] sendChatMessage completed:', result)
      return result
    })
  }

  // Unified method to execute by tool name (used by useMessageDialog for plan execution)
  public static async executeByToolName(
    toolName: string,
    replacementParams?: Record<string, string>,
    uploadedFiles?: string[],
    uploadKey?: string,
    requestSource: 'VUE_DIALOG' | 'VUE_SIDEBAR' = 'VUE_DIALOG',
    serviceGroup?: string
  ): Promise<unknown> {
    return LlmCheckService.withLlmCheck(async () => {
      logger.debug('[DirectApiService] executeByToolName called with:', {
        toolName,
        replacementParams,
        uploadedFiles,
        uploadKey,
        requestSource,
        serviceGroup,
      })

      const requestBody: Record<string, unknown> = {
        toolName: toolName,
        requestSource: requestSource,
      }

      // Include conversationId from conversation store if available
      const conversationStore = useConversationStore()
      if (conversationStore.selectedConversationId) {
        requestBody.conversationId = conversationStore.selectedConversationId
        logger.debug(
          '[DirectApiService] Including conversationId from conversation store:',
          conversationStore.selectedConversationId
        )
      }

      // Include serviceGroup if provided
      if (serviceGroup) {
        requestBody.serviceGroup = serviceGroup
        logger.debug('[DirectApiService] Including serviceGroup:', serviceGroup)
      }

      // Include replacement parameters if present
      if (replacementParams && Object.keys(replacementParams).length > 0) {
        requestBody.replacementParams = replacementParams
        logger.debug('[DirectApiService] Including replacement params:', replacementParams)
      }

      // Include uploaded files if present
      if (uploadedFiles && uploadedFiles.length > 0) {
        requestBody.uploadedFiles = uploadedFiles
        logger.debug('[DirectApiService] Including uploaded files:', uploadedFiles.length)
      }

      // Include uploadKey if present
      if (uploadKey) {
        requestBody.uploadKey = uploadKey
        logger.debug('[DirectApiService] Including uploadKey:', uploadKey)
      }

      logger.debug(
        '[DirectApiService] Making request to:',
        `${this.BASE_URL}/executeByToolNameAsync`
      )
      logger.debug('[DirectApiService] Request body:', requestBody)

      const response = await fetch(`${this.BASE_URL}/executeByToolNameAsync`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
      })

      logger.debug('[DirectApiService] Response status:', response.status, response.ok)

      if (!response.ok) {
        const errorText = await response.text()
        logger.error('[DirectApiService] Request failed:', errorText)
        throw new Error(`Failed to execute: ${response.status}`)
      }

      const result = await response.json()
      logger.debug('[DirectApiService] executeByToolName response:', result)
      return result
    })
  }

  // Get task status by plan ID
  public static async getTaskStatus(planId: string): Promise<{
    planId: string
    isRunning: boolean
    exists: boolean
    desiredState?: string
    startTime?: string
    endTime?: string
    lastUpdated?: string
    taskResult?: string
  }> {
    return LlmCheckService.withLlmCheck(async () => {
      logger.debug('[DirectApiService] Getting task status for planId:', planId)

      const response = await fetch(`${this.BASE_URL}/taskStatus/${planId}`, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || `Failed to get task status: ${response.status}`)
      }

      return await response.json()
    })
  }

  // Stop a running task by plan ID
  public static async stopTask(planId: string): Promise<unknown> {
    return LlmCheckService.withLlmCheck(async () => {
      logger.debug('[DirectApiService] Stopping task for planId:', planId)

      const response = await fetch(`${this.BASE_URL}/stopTask/${planId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || `Failed to stop task: ${response.status}`)
      }

      return await response.json()
    })
  }

  // Cancel a chat stream by conversationId and streamId
  public static async cancelChatStream(
    conversationId: string,
    streamId: string
  ): Promise<{ status: string; message: string }> {
    return LlmCheckService.withLlmCheck(async () => {
      logger.debug('[DirectApiService] Cancelling chat stream:', { conversationId, streamId })

      const response = await fetch(`${this.BASE_URL}/chat/${conversationId}/${streamId}/cancel`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || `Failed to cancel chat stream: ${response.status}`)
      }

      return await response.json()
    })
  }

  // --- Merged from CommonApiService (LynxeController /api/executor) ---

  /** Get detailed execution records (GET /details/{planId}) */
  public static async getDetails(planId: string): Promise<PlanExecutionRecordResponse | null> {
    try {
      const response = await fetch(`${this.BASE_URL}/details/${planId}`)
      if (response.status === 404) return null
      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`Failed to get detailed information: ${response.status} - ${errorText}`)
      }
      const rawText = await response.text()
      const data = JSON.parse(rawText)
      if (data && typeof data === 'object' && !data.currentPlanId) {
        data.currentPlanId = planId
      }
      return data
    } catch (error: unknown) {
      logger.error('[DirectApiService] Failed to get plan details:', error)
      return null
    }
  }

  /** Delete execution details (DELETE /details/{planId}) */
  public static async deleteExecutionDetails(planId: string): Promise<Record<string, string>> {
    try {
      const response = await fetch(`${this.BASE_URL}/details/${planId}`, { method: 'DELETE' })
      if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`Failed to delete execution details: ${response.status} - ${errorText}`)
      }
      return await response.json()
    } catch (error: unknown) {
      logger.error('[DirectApiService] Failed to delete execution details:', error)
      throw error
    }
  }

  /** Submit user form input (POST /submit-input/{planId}) */
  public static async submitFormInput(
    planId: string,
    formData: Record<string, unknown>
  ): Promise<Record<string, unknown>> {
    const response = await fetch(`${this.BASE_URL}/submit-input/${planId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(formData),
    })
    if (!response.ok) {
      let errorData: { message?: string }
      try {
        errorData = await response.json()
      } catch {
        errorData = { message: `Failed to submit form input: ${response.status}` }
      }
      throw new Error(errorData.message || `Failed to submit form input: ${response.status}`)
    }
    const contentType = response.headers.get('content-type')
    if (contentType && contentType.indexOf('application/json') !== -1) {
      return await response.json()
    }
    return { success: true }
  }

  /** Get all Prompt list (GET /api/executor) */
  public static async getAllPrompts(): Promise<unknown[]> {
    const response = await fetch(this.BASE_URL)
    if (!response.ok) {
      try {
        const errorData = await response.json()
        throw new Error(errorData.message || `API request failed: ${response.status}`)
      } catch {
        throw new Error(`API request failed: ${response.status} ${response.statusText}`)
      }
    }
    return await response.json()
  }

  /** Get agent execution detail by stepId (GET /agent-execution/{stepId}) */
  public static async getAgentExecutionDetail(
    stepId: string
  ): Promise<AgentExecutionRecordDetail | null> {
    try {
      const response = await fetch(`${this.BASE_URL}/agent-execution/${stepId}`)
      if (!response.ok) {
        if (response.status === 404) {
          logger.warn(`[DirectApiService] Agent execution detail not found for stepId: ${stepId}`)
          return null
        }
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      const data = await response.json()
      return data as AgentExecutionRecordDetail
    } catch (error) {
      logger.error(
        `[DirectApiService] Error fetching agent execution detail for stepId: ${stepId}:`,
        error
      )
      return null
    }
  }

  /** Refresh agent execution detail (alias for getAgentExecutionDetail) */
  public static async refreshAgentExecutionDetail(
    stepId: string
  ): Promise<AgentExecutionRecordDetail | null> {
    return this.getAgentExecutionDetail(stepId)
  }
}
