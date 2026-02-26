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

import { useAppStore } from '@/stores/new/app'
import { logger } from '@/utils/logger'

/**
 * LLM configuration check utility class
 * Check if model is configured before performing LLM-related operations.
 * Delegates to app store for init status (single source of truth).
 */
export class LlmCheckService {
  /**
   * Check if LLM is configured (uses app store init status)
   */
  public static async checkLlmConfiguration(): Promise<{ initialized: boolean; message?: string }> {
    try {
      const appStore = useAppStore()
      await appStore.ensureInitStatusChecked()
      const status = appStore.initStatus
      const initialized = !!(status?.success && status.initialized)

      if (!initialized) {
        return {
          initialized: false,
          message:
            'System has not configured LLM model yet, please configure API key through initialization page first.',
        }
      }

      return { initialized: true }
    } catch (error) {
      logger.error('[LlmCheckService] Failed to check LLM configuration:', error)
      return {
        initialized: false,
        message:
          'Unable to check LLM configuration status, please ensure system is running normally.',
      }
    }
  }

  /**
   * Ensure LLM is configured, throw error or redirect to initialization page if not configured
   */
  public static async ensureLlmConfigured(options?: {
    showAlert?: boolean
    redirectToInit?: boolean
  }): Promise<void> {
    const { showAlert = true, redirectToInit = true } = options ?? {}

    const checkResult = await this.checkLlmConfiguration()

    if (!checkResult.initialized) {
      const message = checkResult.message ?? 'Please configure LLM model first'

      if (showAlert) {
        alert(message)
      }

      if (redirectToInit) {
        // Clear initialization status, force redirect to initialization page
        localStorage.removeItem('hasInitialized')
        window.location.href = '/ui/#/init'
        throw new Error('Redirecting to initialization page')
      }

      throw new Error(message)
    }
  }

  /**
   * Clear init status cache so next check refetches (delegates to app store)
   */
  public static clearCache(): void {
    useAppStore().clearInitStatusCache()
  }

  /**
   * Wrap API calls, automatically check LLM configuration before calling
   */
  public static async withLlmCheck<T>(
    apiCall: () => Promise<T>,
    options?: {
      showAlert?: boolean
      redirectToInit?: boolean
    }
  ): Promise<T> {
    await this.ensureLlmConfigured(options)
    return apiCall()
  }
}
