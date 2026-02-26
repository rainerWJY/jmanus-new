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
import { i18n } from '@/base/i18n'
import { usePlanTemplateConfigStore } from '@/stores/new/planTemplateConfig'
import type { PlanTemplateConfigVO } from '@/types/plan-template'
import { logger } from '@/utils/logger'
import { computed, reactive } from 'vue'

export class TemplateStore {
  // Loading and error state
  isLoading = false
  errorMessage = ''
  // Track task requirement modifications
  hasTaskRequirementModified = false

  // Organization method: 'by_group_time' | 'by_group_abc'
  organizationMethod: 'by_group_time' | 'by_group_abc' = 'by_group_time'

  // Template service group mapping (templateId -> serviceGroup)
  templateServiceGroups: Map<string, string> = new Map()

  // Group collapse state (groupName -> isCollapsed)
  // Using plain object instead of Map for Vue reactivity
  groupCollapseState: Record<string, boolean> = {}

  constructor() {
    // Load organization method from localStorage
    const savedMethod = localStorage.getItem('sidebarOrganizationMethod')
    if (savedMethod && ['by_group_time', 'by_group_abc'].includes(savedMethod)) {
      this.organizationMethod = savedMethod as 'by_group_time' | 'by_group_abc'
    }
    // Load group collapse state from localStorage
    this.loadGroupCollapseState()
  }

  // Load group collapse state from localStorage
  loadGroupCollapseState() {
    try {
      const saved = localStorage.getItem('sidebarGroupCollapseState')
      if (saved) {
        const parsed = JSON.parse(saved)
        this.groupCollapseState = parsed || {}
      }
    } catch (error) {
      logger.warn('[TemplateStore] Failed to load group collapse state:', error)
    }
  }

  // Save group collapse state to localStorage
  saveGroupCollapseState() {
    try {
      localStorage.setItem('sidebarGroupCollapseState', JSON.stringify(this.groupCollapseState))
    } catch (error) {
      logger.warn('[TemplateStore] Failed to save group collapse state:', error)
    }
  }

  // Toggle group collapse state
  toggleGroupCollapse(groupName: string | null) {
    // Convert null to string key for object property access
    const key = groupName ?? 'null'
    const currentState = this.groupCollapseState[key] ?? false
    this.groupCollapseState[key] = !currentState
    this.saveGroupCollapseState()
  }

  // Check if group is collapsed
  isGroupCollapsed(groupName: string | null): boolean {
    // Convert null to string key for object property access
    const key = groupName ?? 'null'
    return this.groupCollapseState[key] ?? false
  }

  // Helper function to parse date from different formats
  parseDateTime(dateValue: unknown): Date {
    if (!dateValue) {
      return new Date()
    }

    // If array format [year, month, day, hour, minute, second, nanosecond]
    if (Array.isArray(dateValue) && dateValue.length >= 6) {
      // JavaScript Date constructor months start from 0, so subtract 1
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

    // If string format, parse directly
    if (typeof dateValue === 'string') {
      return new Date(dateValue)
    }

    // Return current time for other cases
    return new Date()
  }

  // Set organization method
  setOrganizationMethod(method: 'by_group_time' | 'by_group_abc') {
    this.organizationMethod = method
    localStorage.setItem('sidebarOrganizationMethod', method)
  }

  // Actions
  async loadPlanTemplateList() {
    const planTemplateConfigStore = usePlanTemplateConfigStore()
    this.isLoading = true
    this.errorMessage = ''
    try {
      logger.debug('[TemplateStore] Starting to load plan template list...')
      const configVOs = await PlanTemplateApiService.getAllPlanTemplateConfigVOs()

      planTemplateConfigStore.setPlanTemplateList(configVOs)

      // Build service group mapping
      this.templateServiceGroups.clear()
      const list = planTemplateConfigStore.planTemplateList
      for (const config of list) {
        const planTemplateId = config.planTemplateId
        if (planTemplateId) {
          const serviceGroup = config.serviceGroup || ''
          if (serviceGroup) {
            this.templateServiceGroups.set(planTemplateId, serviceGroup)
          }
        }
      }

      logger.debug(`[TemplateStore] Successfully loaded ${list.length} plan templates`)
    } catch (error: unknown) {
      logger.error('[TemplateStore] Failed to load plan template list:', error)
      planTemplateConfigStore.setPlanTemplateList([])
      const message = error instanceof Error ? error.message : 'Unknown error'
      this.errorMessage = `Load failed: ${message}`
    } finally {
      this.isLoading = false
    }
  }

  async selectTemplate(template: PlanTemplateConfigVO) {
    const planTemplateConfigStore = usePlanTemplateConfigStore()
    planTemplateConfigStore.setCurrentPlanTemplateId(template.planTemplateId || null)
    planTemplateConfigStore.setSelectedTemplate(template)
    this.hasTaskRequirementModified = false

    logger.debug(`[TemplateStore] Selected plan template: ${template.planTemplateId}`)
  }

  async createNewTemplate(planType: string) {
    const planTemplateConfigStore = usePlanTemplateConfigStore()
    try {
      const planTemplateId = await PlanTemplateApiService.generatePlanTemplateId()
      logger.debug('[TemplateStore] Generated plan template ID from backend:', planTemplateId)

      const emptyTemplate: PlanTemplateConfigVO = {
        planTemplateId: planTemplateId,
        title: i18n.global.t('sidebar.newTemplateName'),
        planType: planType,
        createTime: new Date().toISOString(),
        updateTime: new Date().toISOString(),
      }
      planTemplateConfigStore.setSelectedTemplate(emptyTemplate)
      planTemplateConfigStore.setCurrentPlanTemplateId(null)
      this.hasTaskRequirementModified = false

      logger.debug('[TemplateStore] Created new empty plan template')
    } catch (error) {
      logger.error('[TemplateStore] Failed to generate plan template ID:', error)
      const fallbackId = `planTemplate-${Date.now()}`
      logger.warn('[TemplateStore] Using fallback plan template ID:', fallbackId)
      const emptyTemplate: PlanTemplateConfigVO = {
        planTemplateId: fallbackId,
        title: i18n.global.t('sidebar.newTemplateName'),
        planType: planType,
        createTime: new Date().toISOString(),
        updateTime: new Date().toISOString(),
      }
      planTemplateConfigStore.setSelectedTemplate(emptyTemplate)
      planTemplateConfigStore.setCurrentPlanTemplateId(null)
      this.hasTaskRequirementModified = false
    }
  }

  async deleteTemplate(template: PlanTemplateConfigVO) {
    const planTemplateConfigStore = usePlanTemplateConfigStore()
    const planTemplateId = template.planTemplateId
    if (!planTemplateId) {
      logger.warn('[TemplateStore] deleteTemplate: Invalid template object or ID')
      return
    }
    try {
      await PlanTemplateApiService.deletePlanTemplate(planTemplateId)
      if (planTemplateConfigStore.currentPlanTemplateId === planTemplateId) {
        this.clearSelection()
      }
      await this.loadPlanTemplateList()
      logger.debug(`[TemplateStore] Plan template ${planTemplateId} has been deleted`)
    } catch (error: unknown) {
      logger.error('Failed to delete plan template:', error)
      await this.loadPlanTemplateList()
      throw error
    }
  }

  clearSelection() {
    const planTemplateConfigStore = usePlanTemplateConfigStore()
    planTemplateConfigStore.clearSelection()
    this.hasTaskRequirementModified = false
  }
}

// Create store instance
const storeInstance = new TemplateStore()

// Create reactive store first
export const templateStore = reactive({
  // State properties
  isLoading: storeInstance.isLoading,
  errorMessage: storeInstance.errorMessage,
  hasTaskRequirementModified: storeInstance.hasTaskRequirementModified,
  organizationMethod: storeInstance.organizationMethod,
  templateServiceGroups: storeInstance.templateServiceGroups,
  groupCollapseState: storeInstance.groupCollapseState,

  // Methods
  loadGroupCollapseState: () => storeInstance.loadGroupCollapseState(),
  saveGroupCollapseState: () => storeInstance.saveGroupCollapseState(),
  toggleGroupCollapse: (groupName: string | null) => {
    storeInstance.toggleGroupCollapse(groupName)
    // Update reactive object - Vue can track object property changes directly
    const key = groupName ?? 'null'
    templateStore.groupCollapseState[key] = storeInstance.groupCollapseState[key]
    // Force reactivity by creating new object reference
    templateStore.groupCollapseState = { ...templateStore.groupCollapseState }
  },
  isGroupCollapsed: (groupName: string | null) => storeInstance.isGroupCollapsed(groupName),
  parseDateTime: (dateValue: unknown) => storeInstance.parseDateTime(dateValue),
  setOrganizationMethod: (method: 'by_group_time' | 'by_group_abc') => {
    storeInstance.setOrganizationMethod(method)
    templateStore.organizationMethod = method
  },
  loadPlanTemplateList: async () => {
    await storeInstance.loadPlanTemplateList()
    // Update reactive properties after loading
    templateStore.isLoading = storeInstance.isLoading
    templateStore.errorMessage = storeInstance.errorMessage
    templateStore.templateServiceGroups = storeInstance.templateServiceGroups
  },
  selectTemplate: (template: PlanTemplateConfigVO) => storeInstance.selectTemplate(template),
  createNewTemplate: (planType: string) => storeInstance.createNewTemplate(planType),
  deleteTemplate: (template: PlanTemplateConfigVO) => storeInstance.deleteTemplate(template),
  clearSelection: () => storeInstance.clearSelection(),
})

// Create computed properties for reactive template lists
// These must be defined after templateStore to access reactive properties
const sortedTemplatesComputed = computed(() => {
  const planTemplateConfigStore = usePlanTemplateConfigStore()
  const templates = [...planTemplateConfigStore.planTemplateList].filter(template => {
    const accessLevel = template.accessLevel || (template.readOnly ? 'readOnly' : 'editable')
    return accessLevel !== 'readOnly'
  })

  switch (templateStore.organizationMethod) {
    case 'by_group_time':
    case 'by_group_abc': {
      const groups = new Map<string, PlanTemplateConfigVO[]>()
      const ungrouped: PlanTemplateConfigVO[] = []

      templates.forEach(template => {
        const planTemplateId = template.planTemplateId || ''
        const serviceGroup = templateStore.templateServiceGroups.get(planTemplateId) ?? ''
        if (!serviceGroup || serviceGroup === 'default' || serviceGroup === '') {
          ungrouped.push(template)
        } else {
          if (!groups.has(serviceGroup)) {
            groups.set(serviceGroup, [])
          }
          groups.get(serviceGroup)!.push(template)
        }
      })

      const sortedGroups = new Map<string, PlanTemplateConfigVO[]>()
      groups.forEach((templatesInGroup, groupName) => {
        const sorted = [...templatesInGroup]
        if (templateStore.organizationMethod === 'by_group_time') {
          sorted.sort((a, b) => {
            const timeA = templateStore.parseDateTime(a.updateTime ?? a.createTime)
            const timeB = templateStore.parseDateTime(b.updateTime ?? b.createTime)
            return timeB.getTime() - timeA.getTime()
          })
        } else {
          sorted.sort((a, b) => {
            const titleA = (a.title ?? '').toLowerCase()
            const titleB = (b.title ?? '').toLowerCase()
            return titleA.localeCompare(titleB)
          })
        }
        sortedGroups.set(groupName, sorted)
      })

      if (templateStore.organizationMethod === 'by_group_time') {
        ungrouped.sort((a, b) => {
          const timeA = templateStore.parseDateTime(a.updateTime ?? a.createTime)
          const timeB = templateStore.parseDateTime(b.updateTime ?? b.createTime)
          return timeB.getTime() - timeA.getTime()
        })
      } else {
        ungrouped.sort((a, b) => {
          const titleA = (a.title ?? '').toLowerCase()
          const titleB = (b.title ?? '').toLowerCase()
          return titleA.localeCompare(titleB)
        })
      }

      const result: PlanTemplateConfigVO[] = []
      result.push(...ungrouped)
      const sortedGroupNames = Array.from(sortedGroups.keys()).sort()
      sortedGroupNames.forEach(groupName => {
        result.push(...sortedGroups.get(groupName)!)
      })
      return result
    }
    default:
      return templates.sort((a, b) => {
        const timeA = templateStore.parseDateTime(a.updateTime || a.createTime || '')
        const timeB = templateStore.parseDateTime(b.updateTime || b.createTime || '')
        return timeB.getTime() - timeA.getTime()
      })
  }
})

const groupedTemplatesComputed = computed(() => {
  if (
    templateStore.organizationMethod !== 'by_group_time' &&
    templateStore.organizationMethod !== 'by_group_abc'
  ) {
    return new Map([[null, sortedTemplatesComputed.value]])
  }

  const groups = new Map<string | null, PlanTemplateConfigVO[]>()
  const ungrouped: PlanTemplateConfigVO[] = []
  const sorted = sortedTemplatesComputed.value

  sorted.forEach(template => {
    const planTemplateId = template.planTemplateId || ''
    const serviceGroup = templateStore.templateServiceGroups.get(planTemplateId) ?? ''
    if (!serviceGroup || serviceGroup === 'default' || serviceGroup === '') {
      ungrouped.push(template)
    } else {
      if (!groups.has(serviceGroup)) {
        groups.set(serviceGroup, [])
      }
      groups.get(serviceGroup)!.push(template)
    }
  })

  const result = new Map<string | null, PlanTemplateConfigVO[]>()
  if (ungrouped.length > 0) {
    result.set(null, ungrouped)
  }
  const sortedGroupNames = Array.from(groups.keys()).sort()
  sortedGroupNames.forEach(groupName => {
    result.set(groupName, groups.get(groupName)!)
  })

  return result
})

// Add computed properties to reactive store using Object.defineProperty
Object.defineProperty(templateStore, 'sortedTemplates', {
  get: () => sortedTemplatesComputed.value,
  enumerable: true,
  configurable: true,
})

Object.defineProperty(templateStore, 'groupedTemplates', {
  get: () => groupedTemplatesComputed.value,
  enumerable: true,
  configurable: true,
})

// Type augmentation to include computed properties
export interface TemplateStoreWithComputed {
  sortedTemplates: PlanTemplateConfigVO[]
  groupedTemplates: Map<string | null, PlanTemplateConfigVO[]>
}

// Type assertion to help TypeScript understand the computed properties
export type TemplateStoreType = typeof templateStore & TemplateStoreWithComputed
