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

import { PlanTemplateApiService } from '@/api/plan-template-service'
import type {
  InputSchemaParam,
  PlanTemplateConfigVO,
  StepConfig,
  ToolConfigVO,
} from '@/types/plan-template'
import { logger } from '@/utils/logger'
import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'

export const usePlanTemplateConfigStore = defineStore('planTemplateConfig', () => {
  // Reactive state for PlanTemplateConfigVO
  const config = reactive<PlanTemplateConfigVO>({
    title: '',
    steps: [],
    planType: 'dynamic_agent',
    planTemplateId: '',
    accessLevel: 'editable',
    serviceGroup: '',
  })

  // Loading state
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const isUserUpdating = ref(false)
  const needsFullRefresh = ref(false)

  // Version control state
  const planVersions = ref<string[]>([])
  const currentVersionIndex = ref(-1)

  // Template list and selection state
  const planTemplateList = ref<PlanTemplateConfigVO[]>([])
  const selectedTemplate = ref<PlanTemplateConfigVO | null>(null)
  const currentPlanTemplateId = ref<string | null>(null)

  // Actions for templateStore integration
  function setPlanTemplateList(list: PlanTemplateConfigVO[]) {
    planTemplateList.value = list
  }

  function setSelectedTemplate(template: PlanTemplateConfigVO | null) {
    selectedTemplate.value = template
  }

  function setCurrentPlanTemplateId(id: string | null) {
    currentPlanTemplateId.value = id
  }

  function clearSelection() {
    currentPlanTemplateId.value = null
    selectedTemplate.value = null
  }

  // Getters
  function getTitle() {
    return config.title
  }

  function getPlanType() {
    return config.planType || 'dynamic_agent'
  }

  function getServiceGroup() {
    return config.serviceGroup || ''
  }

  function getConfig(): PlanTemplateConfigVO {
    return { ...config }
  }

  // Setters
  function setTitle(title: string) {
    config.title = title
  }

  function setSteps(steps: StepConfig[]) {
    config.steps = steps || []
  }

  function setPlanType(planType: string) {
    config.planType = planType
  }

  function setPlanTemplateId(planTemplateId: string) {
    config.planTemplateId = planTemplateId
  }

  function setServiceGroup(serviceGroup: string) {
    config.serviceGroup = serviceGroup
  }

  function setMaxSteps(maxSteps: number | undefined) {
    if (maxSteps === undefined || maxSteps === null) {
      delete config.maxSteps
    } else {
      config.maxSteps = maxSteps
    }
  }

  function setToolConfig(toolConfig: ToolConfigVO | undefined) {
    if (toolConfig === undefined) {
      delete config.toolConfig
    } else {
      config.toolConfig = toolConfig
    }
  }

  function setToolDescription(toolDescription: string) {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.toolDescription = toolDescription
  }

  function setEnableInternalToolcall(enable: boolean) {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.enableInternalToolcall = enable
  }

  function setEnableHttpService(enable: boolean) {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.enableHttpService = enable
  }

  function setEnableInConversation(enable: boolean) {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.enableInConversation = enable
  }

  function setInputSchema(inputSchema: InputSchemaParam[]) {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.inputSchema = inputSchema || []
  }

  function setConfig(newConfig: PlanTemplateConfigVO) {
    needsFullRefresh.value = true
    const accessLevel = newConfig.accessLevel || (newConfig.readOnly ? 'readOnly' : 'editable')
    const updatedConfig: PlanTemplateConfigVO = {
      title: newConfig.title || '',
      steps: (newConfig.steps || []).map(step => ({
        ...step,
        selectedToolKeys: step.selectedToolKeys ?? [],
      })),
      planType: newConfig.planType || 'dynamic_agent',
      planTemplateId: newConfig.planTemplateId || '',
      accessLevel: accessLevel,
      serviceGroup: newConfig.serviceGroup || '',
    }
    if (newConfig.maxSteps !== undefined) {
      updatedConfig.maxSteps = newConfig.maxSteps
    }
    if (newConfig.version !== undefined) {
      updatedConfig.version = newConfig.version
    }
    if (newConfig.toolConfig) {
      updatedConfig.toolConfig = { ...newConfig.toolConfig }
    }
    Object.assign(config, updatedConfig)
    setTimeout(() => {
      needsFullRefresh.value = false
    }, 50)
  }

  function reset() {
    needsFullRefresh.value = true
    Object.assign(config, {
      title: '',
      steps: [],
      planType: 'dynamic_agent',
      planTemplateId: '',
      accessLevel: 'editable',
      serviceGroup: '',
    })
    if ('version' in config) {
      delete config.version
    }
    if ('toolConfig' in config) {
      delete config.toolConfig
    }
    planVersions.value = []
    currentVersionIndex.value = -1
    error.value = null
    setTimeout(() => {
      needsFullRefresh.value = false
    }, 0)
  }

  function generateJsonString(): string {
    const accessLevel = config.accessLevel || (config.readOnly ? 'readOnly' : 'editable')
    const jsonConfig: PlanTemplateConfigVO = {
      title: config.title || '',
      steps: (config.steps || []).map(step => ({
        ...step,
        selectedToolKeys: step.selectedToolKeys ?? [],
      })),
      planType: config.planType || 'dynamic_agent',
      planTemplateId: config.planTemplateId || '',
      accessLevel: accessLevel,
      serviceGroup: config.serviceGroup || '',
    }
    if (config.maxSteps !== undefined) {
      jsonConfig.maxSteps = config.maxSteps
    }
    if (config.version !== undefined) {
      jsonConfig.version = config.version
    }
    if (config.toolConfig) {
      jsonConfig.toolConfig = { ...config.toolConfig }
    }
    return JSON.stringify(jsonConfig, null, 2)
  }

  function fromJsonString(jsonString: string): boolean {
    try {
      const parsed = JSON.parse(jsonString)
      setConfig(parsed)
      return true
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Invalid JSON format'
      return false
    }
  }

  const canRollback = computed(() => planVersions.value.length > 1 && currentVersionIndex.value > 0)

  const canRestore = computed(
    () => planVersions.value.length > 1 && currentVersionIndex.value < planVersions.value.length - 1
  )

  function rollbackVersion() {
    if (canRollback.value && currentVersionIndex.value > 0) {
      currentVersionIndex.value--
      const versionContent = planVersions.value[currentVersionIndex.value] || ''
      fromJsonString(versionContent)
    }
  }

  function restoreVersion() {
    if (canRestore.value && currentVersionIndex.value < planVersions.value.length - 1) {
      currentVersionIndex.value++
      const versionContent = planVersions.value[currentVersionIndex.value] || ''
      fromJsonString(versionContent)
    }
  }

  function updateVersionsAfterSave(content: string) {
    if (currentVersionIndex.value < planVersions.value.length - 1) {
      planVersions.value = planVersions.value.slice(0, currentVersionIndex.value + 1)
    }
    planVersions.value.push(content)
    currentVersionIndex.value = planVersions.value.length - 1
  }

  async function load(planTemplateId: string): Promise<boolean> {
    if (!planTemplateId) {
      error.value = 'Plan template ID is required'
      return false
    }

    try {
      isLoading.value = true
      error.value = null

      const loadedConfig = await PlanTemplateApiService.getPlanTemplateConfigVO(planTemplateId)
      const isSameTemplate = currentPlanTemplateId.value === planTemplateId

      if (isSameTemplate) {
        currentPlanTemplateId.value = null
        await new Promise(resolve => setTimeout(resolve, 0))
      }

      isUserUpdating.value = false
      setConfig(loadedConfig)
      currentPlanTemplateId.value = planTemplateId

      await new Promise(resolve => setTimeout(resolve, 10))

      if (!selectedTemplate.value || selectedTemplate.value?.planTemplateId === planTemplateId) {
        selectedTemplate.value = {
          ...(selectedTemplate.value || {}),
          ...loadedConfig,
        }
      }

      try {
        const versionsResponse = await PlanTemplateApiService.getPlanVersions(planTemplateId)
        planVersions.value = (versionsResponse as { versions?: string[] }).versions || []
        if (planVersions.value.length > 0) {
          currentVersionIndex.value = planVersions.value.length - 1
        } else {
          currentVersionIndex.value = -1
        }
      } catch (versionError) {
        logger.warn('Failed to load plan versions:', versionError)
        planVersions.value = []
        currentVersionIndex.value = -1
      }

      return true
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load plan template config'
      logger.error('Failed to load plan template config:', err)
      return false
    } finally {
      isLoading.value = false
    }
  }

  async function save(): Promise<boolean> {
    if (!config.planTemplateId) {
      error.value = 'Plan template ID is required'
      return false
    }

    try {
      isLoading.value = true
      error.value = null

      const result = await PlanTemplateApiService.createOrUpdatePlanTemplateWithTool(getConfig())

      if (result.success) {
        const actualPlanTemplateId = result.planTemplateId || config.planTemplateId

        if (actualPlanTemplateId && actualPlanTemplateId !== config.planTemplateId) {
          logger.debug(
            '[planTemplateConfigStore] PlanTemplateId replaced by backend:',
            config.planTemplateId,
            '->',
            actualPlanTemplateId
          )
          config.planTemplateId = actualPlanTemplateId
        }

        if (actualPlanTemplateId) {
          await load(actualPlanTemplateId)

          const oldPlanTemplateId = selectedTemplate.value?.planTemplateId
          if (
            oldPlanTemplateId === actualPlanTemplateId ||
            oldPlanTemplateId === config.planTemplateId
          ) {
            const loadedConfig = getConfig()
            selectedTemplate.value = {
              ...selectedTemplate.value,
              ...loadedConfig,
              planTemplateId: actualPlanTemplateId,
            }
          }

          const templateIndex = planTemplateList.value.findIndex(
            t => t.planTemplateId === actualPlanTemplateId || t.planTemplateId === oldPlanTemplateId
          )
          if (templateIndex >= 0) {
            const loadedConfig = getConfig()
            planTemplateList.value[templateIndex] = {
              ...planTemplateList.value[templateIndex],
              ...loadedConfig,
              planTemplateId: actualPlanTemplateId,
            }
          } else if (oldPlanTemplateId) {
            const oldTemplateIndex = planTemplateList.value.findIndex(
              t => t.planTemplateId === oldPlanTemplateId
            )
            if (oldTemplateIndex >= 0) {
              const loadedConfig = getConfig()
              planTemplateList.value[oldTemplateIndex] = {
                ...planTemplateList.value[oldTemplateIndex],
                ...loadedConfig,
                planTemplateId: actualPlanTemplateId,
              }
            }
          }
        }
      }

      return result.success
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to save plan template config'
      logger.error('Failed to save plan template config:', err)
      return false
    } finally {
      isLoading.value = false
    }
  }

  function validate(): { isValid: boolean; errors: string[] } {
    const errors: string[] = []

    if (!config.planTemplateId?.trim()) {
      errors.push('Plan template ID is required')
    }
    if (!config.title?.trim()) {
      errors.push('Title is required')
    }
    if (!config.steps || config.steps.length === 0) {
      errors.push('At least one step is required')
    }

    return {
      isValid: errors.length === 0,
      errors,
    }
  }

  function parseDateTime(dateValue: unknown): Date {
    if (!dateValue) {
      return new Date()
    }
    if (Array.isArray(dateValue) && dateValue.length >= 6) {
      return new Date(
        dateValue[0],
        dateValue[1] - 1,
        dateValue[2],
        dateValue[3],
        dateValue[4],
        dateValue[5],
        Math.floor(dateValue[6] / 1000000)
      )
    }
    if (typeof dateValue === 'string') {
      return new Date(dateValue)
    }
    return new Date()
  }

  function getAllCoordinatorToolsFromTemplates() {
    const tools: Array<{
      toolName: string
      toolDescription: string
      planTemplateId: string
      inputSchema: string
      enableInternalToolcall: boolean
      enableHttpService: boolean
      enableInConversation: boolean
      serviceGroup?: string
    }> = []

    for (const template of planTemplateList.value) {
      if (!template.toolConfig || !template.toolConfig.enableInternalToolcall) {
        continue
      }
      const toolConfig = template.toolConfig
      let inputSchemaJson = '[]'
      if (toolConfig.inputSchema && Array.isArray(toolConfig.inputSchema)) {
        inputSchemaJson = JSON.stringify(toolConfig.inputSchema)
      }
      const tool = {
        toolName: template.title || '',
        toolDescription: toolConfig.toolDescription || '',
        planTemplateId: template.planTemplateId || '',
        inputSchema: inputSchemaJson,
        enableInternalToolcall: toolConfig.enableInternalToolcall ?? true,
        enableHttpService: toolConfig.enableHttpService ?? false,
        enableInConversation: toolConfig.enableInConversation ?? false,
        serviceGroup: template.serviceGroup || '',
      }
      tools.push(tool)
    }
    return tools
  }

  function getCoordinatorToolConfig(): boolean {
    return planTemplateList.value.some(template => template.toolConfig !== undefined)
  }

  function withUpdateGuard<T>(callback: () => T): T {
    isUserUpdating.value = true
    try {
      return callback()
    } finally {
      setTimeout(() => {
        isUserUpdating.value = false
      }, 0)
    }
  }

  function setStepsWithGuard(steps: StepConfig[]) {
    return withUpdateGuard(() => {
      setSteps(steps)
    })
  }

  function setToolConfigWithGuard(toolConfig: ToolConfigVO | undefined) {
    return withUpdateGuard(() => {
      setToolConfig(toolConfig)
    })
  }

  function setToolDescriptionWithGuard(toolDescription: string) {
    return withUpdateGuard(() => {
      setToolDescription(toolDescription)
    })
  }

  function setEnableInternalToolcallWithGuard(enable: boolean) {
    return withUpdateGuard(() => {
      setEnableInternalToolcall(enable)
    })
  }

  function setEnableHttpServiceWithGuard(enable: boolean) {
    return withUpdateGuard(() => {
      setEnableHttpService(enable)
    })
  }

  function setEnableInConversationWithGuard(enable: boolean) {
    return withUpdateGuard(() => {
      setEnableInConversation(enable)
    })
  }

  function setInputSchemaWithGuard(inputSchema: InputSchemaParam[]) {
    return withUpdateGuard(() => {
      setInputSchema(inputSchema)
    })
  }

  return {
    config,
    isLoading,
    error,
    isUserUpdating,
    needsFullRefresh,
    planVersions,
    currentVersionIndex,
    planTemplateList,
    selectedTemplate,
    currentPlanTemplateId,

    setPlanTemplateList,
    setSelectedTemplate,
    setCurrentPlanTemplateId,
    clearSelection,

    getTitle,
    getPlanType,
    getServiceGroup,
    getConfig,

    setTitle,
    setSteps,
    setPlanType,
    setPlanTemplateId,
    setServiceGroup,
    setMaxSteps,
    setToolConfig,
    setToolDescription,
    setEnableInternalToolcall,
    setEnableHttpService,
    setEnableInConversation,
    setInputSchema,
    setConfig,

    setStepsWithGuard,
    setToolConfigWithGuard,
    setToolDescriptionWithGuard,
    setEnableInternalToolcallWithGuard,
    setEnableHttpServiceWithGuard,
    setEnableInConversationWithGuard,
    setInputSchemaWithGuard,
    withUpdateGuard,

    reset,
    load,
    save,
    validate,
    generateJsonString,
    fromJsonString,
    rollbackVersion,
    restoreVersion,
    updateVersionsAfterSave,

    canRollback,
    canRestore,

    parseDateTime,
    getAllCoordinatorToolsFromTemplates,
    getCoordinatorToolConfig,
  }
})
