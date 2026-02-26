import type { AxiosResponse } from 'axios'
import axios from 'axios'

import { logger } from '@/utils/logger'

export interface ModelOption {
  value: string
  label: string
}

export interface AvailableModelsResponse {
  options: ModelOption[]
  total: number
}

export class ConfigApiService {
  /**
   * Get available model list
   */
  public static async getAvailableModels(): Promise<AvailableModelsResponse> {
    try {
      const response: AxiosResponse<AvailableModelsResponse> = await axios({
        url: '/api/models/available-models',
        method: 'GET',
        baseURL: '', // Override the default /api/v1 baseURL
      })
      return response.data
    } catch (error) {
      logger.error('Failed to fetch available models:', error)
      return { options: [], total: 0 }
    }
  }

  /**
   * Get version information (GET /api/version)
   */
  public static async getVersion(): Promise<{
    version: string
    buildTime: string
    timestamp: string
  }> {
    try {
      const response = await fetch('/api/version')
      if (!response.ok) throw new Error(`Failed to get version: ${response.status}`)
      return await response.json()
    } catch (error: unknown) {
      logger.error('[ConfigApiService] Failed to get version:', error)
      return {
        version: 'unknown',
        buildTime: 'unknown',
        timestamp: new Date().toISOString(),
      }
    }
  }
}
