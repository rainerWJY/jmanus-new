import type { ApiResponse } from '@/types/mcp'
import { logger } from '@/utils/logger'
import { ref } from 'vue'

export function useRequest() {
  const loading = ref(false)

  const executeRequest = async <T>(
    requestFn: () => Promise<ApiResponse<T>>,
    successMessage?: string,
    errorMessage?: string
  ): Promise<ApiResponse<T> | null> => {
    try {
      loading.value = true
      const result = await requestFn()

      if (result.success && successMessage) {
        // Need to pass showMessage function from outside to avoid circular dependencies
        logger.debug(successMessage)
      } else if (!result.success && errorMessage) {
        logger.error(errorMessage)
      }

      return result
    } catch (error) {
      logger.error('Request execution failed:', error)
      if (errorMessage) {
        logger.error(errorMessage)
      }
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    executeRequest,
  }
}
