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

import type { Tool } from '@/types/tool'
import { logger } from '@/utils/logger'

/**
 * Tool API service class
 * Provides basic tool-related functionality without agent dependencies
 */
export class ToolApiService {
  /**
   * Handle HTTP response. Uses text() for error bodies to avoid SyntaxError when
   * the body is empty or non-JSON (e.g. 404/500 with no body or HTML).
   */
  private static async handleResponse(response: Response) {
    if (!response.ok) {
      const text = await response.text()
      if (text) {
        try {
          const errorData = JSON.parse(text) as { error?: string; message?: string }
          const msg =
            errorData.error || errorData.message || `API request failed: ${response.status}`
          throw new Error(msg)
        } catch (err) {
          if (err instanceof Error) {
            throw err
          }
        }
      }
      throw new Error(`API request failed: ${response.status} ${response.statusText}`)
    }
    return response
  }

  /**
   * Get available tools from backend API.
   * Returns [] when the response body is empty or invalid (e.g. client disconnected / broken pipe)
   * so the sidebar and tool list do not crash.
   */
  static async getAvailableTools(): Promise<Tool[]> {
    try {
      const response = await fetch('/api/tools')
      const result = await this.handleResponse(response)
      const text = await result.text()
      if (!text || !text.trim()) {
        logger.warn('Get available tools: empty response body, returning empty list')
        return []
      }
      try {
        return JSON.parse(text) as Tool[]
      } catch {
        logger.warn('Get available tools: invalid JSON in response, returning empty list')
        return []
      }
    } catch (error) {
      logger.error('Failed to get available tools:', error)
      throw error
    }
  }
}
