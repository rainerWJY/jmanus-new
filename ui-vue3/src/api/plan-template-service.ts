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

import type {
  CreateOrUpdatePlanTemplateWithToolResponse,
  PlanTemplateConfigVO,
} from '../types/plan-template'

import { logger } from '@/utils/logger'

export interface ParameterRequirements {
  parameters: string[]
  hasParameters: boolean
  requirements: string
}

/**
 * Plan template API service class
 * Provides plan template-related functionality (config, versions, list, parameters)
 */
export class PlanTemplateApiService {
  /**
   * Handle HTTP response
   */
  private static async handleResponse(response: Response) {
    if (!response.ok) {
      try {
        const errorData = await response.json()
        // Create error with errorCode if available
        // Backend returns "error" field, but we use "message" for consistency
        const errorMessage =
          errorData.error || errorData.message || `API request failed: ${response.status}`
        const error = new Error(errorMessage) as Error & {
          errorCode?: string
        }
        if (errorData.errorCode) {
          error.errorCode = errorData.errorCode
        }
        throw error
      } catch (err) {
        if (err instanceof Error) {
          throw err
        }
        throw new Error(`API request failed: ${response.status} ${response.statusText}`)
      }
    }
    return response
  }

  /**
   * Create or update plan template and register as coordinator tool
   * This method combines the functionality of both "Save Plan Template" and
   * "Register Plan Templates as Toolcalls" by using PlanTemplateConfigVO
   */
  static async createOrUpdatePlanTemplateWithTool(
    data: PlanTemplateConfigVO
  ): Promise<CreateOrUpdatePlanTemplateWithToolResponse> {
    try {
      const response = await fetch('/api/plan-template/create-or-update-with-tool', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to create or update plan template with tool:', error)
      throw error
    }
  }

  /**
   * Get plan template configuration VO by plan template ID
   */
  static async getPlanTemplateConfigVO(planTemplateId: string): Promise<PlanTemplateConfigVO> {
    try {
      const response = await fetch(`/api/plan-template/${planTemplateId}/config`)
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to get plan template config VO:', error)
      throw error
    }
  }

  /**
   * Get all plan template configuration VOs
   */
  static async getAllPlanTemplateConfigVOs(): Promise<PlanTemplateConfigVO[]> {
    try {
      const response = await fetch('/api/plan-template/list-config')
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to get all plan template config VOs:', error)
      throw error
    }
  }

  /**
   * Delete plan template
   */
  static async deletePlanTemplate(planTemplateId: string): Promise<unknown> {
    try {
      const response = await fetch('/api/plan-template/delete', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ planId: planTemplateId }),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to delete plan template:', error)
      throw error
    }
  }

  /**
   * Export all plan templates as JSON array
   */
  static async exportAllPlanTemplates(): Promise<PlanTemplateConfigVO[]> {
    try {
      const response = await fetch('/api/plan-template/export-all')
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to export all plan templates:', error)
      throw error
    }
  }

  /**
   * Import plan templates from JSON array
   */
  static async importPlanTemplates(templates: PlanTemplateConfigVO[]): Promise<{
    success: boolean
    total: number
    successCount: number
    failureCount: number
    errors: Array<{ planTemplateId: string; message: string }>
  }> {
    try {
      const response = await fetch('/api/plan-template/import-all', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(templates),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to import plan templates:', error)
      throw error
    }
  }

  /**
   * Generate a new plan template ID from backend
   * @returns Generated plan template ID
   */
  static async generatePlanTemplateId(): Promise<string> {
    try {
      const response = await fetch('/api/plan-template/generate-plan-template-id', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      })
      const result = await this.handleResponse(response)
      const data = await result.json()
      return data.planTemplateId
    } catch (error) {
      logger.error('Failed to generate plan template ID:', error)
      throw error
    }
  }

  /**
   * Get all versions of a plan
   */
  static async getPlanVersions(planId: string): Promise<unknown> {
    try {
      const response = await fetch('/api/plan-template/versions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ planId }),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to get plan versions:', error)
      throw error
    }
  }

  /**
   * Get all plan template list (legacy list endpoint)
   */
  static async getAllPlanTemplates(): Promise<unknown> {
    try {
      const response = await fetch('/api/plan-template/list')
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Failed to get plan template list:', error)
      throw error
    }
  }

  /**
   * Get parameter requirements for a plan template
   */
  static async getParameterRequirements(planTemplateId: string): Promise<ParameterRequirements> {
    try {
      const response = await fetch(`/api/plan-template/${planTemplateId}/parameters`, {
        method: 'GET',
        headers: { 'Content-Type': 'application/json' },
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      logger.error('Error getting parameter requirements:', error)
      throw error
    }
  }
}
