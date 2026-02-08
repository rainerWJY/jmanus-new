# Design: API calls, return values, and Pinia stores

Three parts: (1) all API calls in the frontend, (2) their return values, (3) Pinia store objects that mirror backend data first.

---

# 1) All API calls in the frontend

Grouped by service (file).

**NamespaceApiService** (`namespace-api-service.ts`)

- `getAllNamespaces()` — namespaceConfig.vue, namespaceSwitch.vue
- `getNamespaceById(id)` — defined
- `createNamespace(config)` — namespaceConfig.vue
- `updateNamespace(id, config)` — namespaceConfig.vue
- `deleteNamespace(id)` — namespaceConfig.vue

**MemoryApiService** (`memory-api-service.ts`)

- `getMemories()` — Memory.vue
- `getMemory(conversationId)` — defined
- `createMemory(conversationId, memoryName)` — defined
- `updateMemory(conversationId, memoryName)` — Memory.vue
- `deleteMemory(conversationId)` — Memory.vue
- `generateConversationId()` — defined
- `getConversationHistory(conversationId)` — useConversationHistory.ts

**PlanTemplateApiService** (`plan-template-service.ts`)

- `createOrUpdatePlanTemplateWithTool(data)` — planTemplateConfig store, JsonEditorV2
- `getPlanTemplateConfigVO(planTemplateId)` — planTemplateConfig store
- `getAllPlanTemplateConfigVOs()` — templateStore, TemplateList, planTemplateConfig store, JsonEditorV2
- `deletePlanTemplate(planTemplateId)` — templateStore
- `exportAllPlanTemplates()` — planTemplateConfig.vue (or equivalent)
- `importPlanTemplates(templates)` — usePlanTemplateImport
- `generatePlanTemplateId()` — templateStore, JsonEditorV2
- `getParameterRequirements(planTemplateId)` — ExecutionController, PublishServiceModal

**DirectApiService** (`lynxe-service.ts`, mirrors LynxeController `/api/executor`)

- `sendMessage(query)` — legacy
- `sendChatMessage(query, requestSource, onChunk, abortSignal)` — useMessageDialog
- `executeByToolName(...)` — useMessageDialog (dialog tool run + plan execution flow)
- `getTaskStatus(planId)` — useTaskStop
- `stopTask(planId)` — useTaskStop
- `cancelChatStream(conversationId, streamId)` — useMessageDialog
- `getDetails(planId)` — usePlanExecution
- `deleteExecutionDetails(planId)` — usePlanExecution
- `submitFormInput(planId, formData)` — UserInputForm.vue
- `getAllPrompts()` — defined
- `getAgentExecutionDetail(stepId)` — useRightPanel
- `refreshAgentExecutionDetail(stepId)` — useRightPanel

**ToolApiService** (`tool-api-service.ts`)

- `getAvailableTools()` — availableTools store, JsonEditorV2

**ConfigApiService** (`config-api-service.ts`)

- `getAvailableModels()` — JsonEditorV2
- `getVersion()` — basicConfig.vue

**ModelApiService** (`model-api-service.ts`)

- `getAllModels()`, `getAllTypes()` — modelConfig.vue
- `getModelById(id)` — modelConfig.vue
- `validateConfig(request)` — modelConfig.vue
- `createModel(config)` — modelConfig.vue
- `updateModel(id, config)` — modelConfig.vue
- `deleteModel(id)` — modelConfig.vue
- `setDefaultModel(id)` — modelConfig.vue

**AdminApiService** (`admin-api-service.ts`)

- `getConfigsByGroup(groupName)` — basicConfig.vue
- `batchUpdateConfigs(configs)` — basicConfig.vue
- `getConfigById(id)` — defined
- `updateConfig(config)` — defined
- `resetAllConfigsToDefaults()` — basicConfig.vue

**McpApiService** (`mcp-api-service.ts`)

- `getAllMcpServers()` — mcpConfig.vue
- `addMcpServer(config)` — defined
- `importMcpServers(jsonData)` — mcpConfig.vue
- `removeMcpServer(id)` — mcpConfig.vue
- `saveMcpServer(request)` — mcpConfig.vue
- `getMcpServer(id)` — defined
- `enableMcpServer(id)` — defined
- `disableMcpServer(id)` — defined

**DatasourceConfigApiService** (`datasource-config-api-service.ts`)

- `getAllConfigs()` — databaseConfig.vue
- `getConfigById(id)` — databaseConfig.vue
- `createConfig(config)` — databaseConfig.vue
- `updateConfig(id, config)` — databaseConfig.vue
- `deleteConfig(id)` — databaseConfig.vue
- `testConnection(config)` — DatasourceConfigForm.vue

**FileBrowserApiService** (`file-browser-api-service.ts`)

- `getFileTree(planId)` — file-browser/index.vue
- `getFileContent(planId, path)` — file-browser/index.vue
- `downloadFile(planId, path, name)` — file-browser/index.vue

**FileUploadApiService** (`file-upload-api-service.ts`)

- `uploadFiles(files)` — FileUploadComponent.vue
- `getUploadedFiles(uploadKey)` — defined
- `deleteFile(uploadKey, fileName)` — FileUploadComponent.vue
- `getUploadConfig()` — defined

**CronApiService** (`cron-api-service.ts`)

- `getAllCronTasks()` — CronTaskModal.vue
- `getCronTaskById(id)` — defined
- `createCronTask(config)` — cron-task-utils
- `updateCronTask(id, config)` — cron-task-utils
- `deleteCronTask(id)` — cron-task-utils

**DatabaseCleanupApiService** (`database-cleanup-api-service.ts`)

- `getTableCounts()` — databaseCleanupConfig.vue
- `clearAllTables()` — databaseCleanupConfig.vue

**Init** (fetch)

- `POST /api/init/save` — init/index.vue
- `GET /api/init/status` — init/index.vue, router, llm-check

**Language** (`language.ts`)

- `getLanguage()` — api/language.ts
- `setLanguage(language)` — api/language.ts

---

# 2) Return value of each API

**NamespaceApiService**

- `getAllNamespaces()` → `Namespace[]` — `{ id, code, name, description?, host? }`
- `getNamespaceById(id)` → `Namespace`
- `createNamespace(config)` → `Namespace`
- `updateNamespace(id, config)` → `Namespace`
- `deleteNamespace(id)` → `void`

**MemoryApiService**

- `getMemories()` → `Memory[]` — from `MemoryResponse.memories`; shape `{ id, conversation_id, memory_name, create_time, messages? }`
- `getMemory(conversationId)` → `Memory` (from `MemoryResponse.data`)
- `createMemory(...)` → `Memory`
- `updateMemory(...)` → `Memory`
- `deleteMemory(...)` → `void`
- `generateConversationId()` → `Memory`
- `getConversationHistory(conversationId)` → `PlanExecutionRecord[]`

**PlanTemplateApiService**

- `createOrUpdatePlanTemplateWithTool(data)` → `CreateOrUpdatePlanTemplateWithToolResponse` — `{ success, planTemplateId, toolRegistered }`
- `getPlanTemplateConfigVO(planTemplateId)` → `PlanTemplateConfigVO`
- `getAllPlanTemplateConfigVOs()` → `PlanTemplateConfigVO[]`
- `deletePlanTemplate(planTemplateId)` → `unknown`
- `exportAllPlanTemplates()` → `PlanTemplateConfigVO[]`
- `importPlanTemplates(templates)` → `{ success, total, successCount, failureCount, errors: { planTemplateId, message }[] }`
- `generatePlanTemplateId()` → `string` (planTemplateId)
- `getParameterRequirements(planTemplateId)` → `ParameterRequirements` — `{ parameters, hasParameters, requirements }`

**DirectApiService** (lynxe-service.ts)

- `sendMessage(query)` → `unknown`
- `sendChatMessage(...)` → `Promise<{ conversationId?, message? }>` plus SSE chunks
- `executeByToolName(...)` → `unknown` — typically `{ planId?, conversationId? }`
- `getTaskStatus(planId)` → `{ planId, isRunning, exists, desiredState?, startTime?, endTime?, lastUpdated?, taskResult? }`
- `stopTask(planId)` → `unknown`
- `cancelChatStream(...)` → `{ status, message }`
- `getDetails(planId)` → `PlanExecutionRecordResponse | null`
- `deleteExecutionDetails(planId)` → `Record<string, string>`
- `submitFormInput(planId, formData)` → `Record<string, unknown>` or `{ success: true }`
- `getAllPrompts()` → `unknown[]`
- `getAgentExecutionDetail(stepId)` → `AgentExecutionRecordDetail | null`
- `refreshAgentExecutionDetail(stepId)` → `AgentExecutionRecordDetail | null`

**ToolApiService**

- `getAvailableTools()` → `Tool[]` — `{ key, name, description, enabled, serviceGroup, selectable }`

**ConfigApiService**

- `getAvailableModels()` → `AvailableModelsResponse` — `{ options: ModelOption[], total }`, `ModelOption` = `{ value, label }`
- `getVersion()` → `{ version, buildTime, timestamp }`

**ModelApiService**

- `getAllModels()` → `Model[]`
- `getAllTypes()` → `string[]`
- `getModelById(id)` → `Model`
- `validateConfig(request)` → `ValidationResult` — `{ valid, message?, availableModels? }`
- `createModel(config)` → `Model`
- `updateModel(id, config)` → `Model`
- `deleteModel(id)` → `void`
- `setDefaultModel(id)` → `{ success, message }`

**AdminApiService**

- `getConfigsByGroup(groupName)` → `ConfigItem[]`
- `batchUpdateConfigs(configs)` → `ApiResponse` — `{ success, message }`
- `getConfigById(id)` → `ConfigItem`
- `updateConfig(config)` → `ApiResponse`
- `resetAllConfigsToDefaults()` → `ApiResponse`

**McpApiService**

- `getAllMcpServers()` → `McpServer[]`
- `addMcpServer(config)` → `ApiResponse`
- `importMcpServers(jsonData)` → `ApiResponse`
- `removeMcpServer(id)` → `ApiResponse`
- `saveMcpServer(request)` → `ApiResponse`
- `getMcpServer(id)` → server
- `enableMcpServer(id)` → `ApiResponse`
- `disableMcpServer(id)` → `ApiResponse`

**DatasourceConfigApiService**

- `getAllConfigs()` → `DatasourceConfig[]`
- `getConfigById(id)` → `DatasourceConfig`
- `createConfig(config)` → `DatasourceConfig`
- `updateConfig(id, config)` → `DatasourceConfig`
- `deleteConfig(id)` → `void`
- `testConnection(config)` → `{ success, message }`

**FileBrowserApiService**

- `getFileTree(planId)` → `FileNode` — `{ name, path, type, size, lastModified, children? }`
- `getFileContent(planId, path)` → `FileContent` — `{ content, mimeType, size, isBinary?, downloadOnly? }`

**FileUploadApiService**

- `uploadFiles(files)` → `FileUploadResult` — `{ success, message, uploadKey, uploadedFiles, totalFiles, successfulFiles, failedFiles }`
- `getUploadedFiles(uploadKey)` → `GetUploadedFilesResponse`
- `deleteFile(...)` → `DeleteFileResponse`
- `getUploadConfig()` → `UploadConfig`

**CronApiService**

- `getAllCronTasks()` → `CronConfig[]`
- `getCronTaskById(id)` → `CronConfig`
- `createCronTask(config)` → `CronConfig`
- `updateCronTask(id, config)` → `CronConfig`
- `deleteCronTask(id)` → `void`

**DatabaseCleanupApiService**

- `getTableCounts()` → `TableCounts` — `{ act_tool_info, think_act_record, plan_execution_record, agent_execution_record, ai_chat_memory }`
- `clearAllTables()` → `TableCounts`

**Init**

- `POST /api/init/save` → `{ success, requiresRestart?, error? }`
- `GET /api/init/status` → `{ success, initialized }`

**Language**

- `getLanguage()` → `'zh' | 'en'` (from `LanguageResponse.language`)
- `setLanguage(language)` → `SetLanguageResponse` — `{ success, language, error? }`

---

# 3) Pinia store objects that mirror backend data first

Design rule: **each store holds data that comes from (or is sent to) the backend; UI-only state (collapsed, active tab, etc.) lives in components or a separate UI store.**

Suggested stores and their backend-mirror state:

**A. namespace** (align with API)

- Backend mirror: `namespaces: Ref<Namespace[]>` from `NamespaceApiService.getAllNamespaces()`; `currentNamespaceId: Ref<string>` (e.g. `'default'`).
- Actions: `setNamespaces(list)`, `setCurrentNamespace(id)`, optional `loadNamespaces()`.
- Persistence: optional `currentNamespaceId` in localStorage.

**B. conversation** (memory backend data only)

- Backend mirror: `conversations: Ref<Memory[]>` from `MemoryApiService.getMemories()`; `selectedConversationId: Ref<string | null>`.
- Actions: `setConversations(list)`, `setSelectedConversationId(id)`, `loadConversations()`, optional `loadConversationHistory(conversationId)`.
- Persistence: `selectedConversationId` in localStorage.
- Do not put here: `isCollapsed`, `loadMessages` callback, `intervalId` — keep in Memory panel or a small `memoryPanel` UI store.

**C. planTemplate** (plan template list + current config from backend)

- Backend mirror: `planTemplateList: Ref<PlanTemplateConfigVO[]>` from `getAllPlanTemplateConfigVOs()`; `currentConfig: Ref<PlanTemplateConfigVO | null>` from `getPlanTemplateConfigVO(id)`; `currentPlanTemplateId: Ref<string | null>`; `planVersions: Ref<string[]>`; `currentVersionIndex: Ref<number>`.
- Actions: `loadPlanTemplateList()`, `loadPlanTemplateConfig(id)`, `savePlanTemplateConfig()`, `createNewTemplate(planType)`, `deletePlanTemplate(id)`, `setCurrentPlanTemplateId(id)`, `loadPlanVersions(planId)`, `setCurrentVersionIndex(i)`.
- Persistence: none; backend is source of truth.
- Optional: after `importPlanTemplates`, write into `planTemplateList` (and maybe `importResult`).

**D. planExecution** (execution records from backend)

- Backend mirror: `recordsByPlanId: Ref<Record<string, PlanExecutionRecord>>` from `DirectApiService.getDetails(planId)` (polling); `trackedPlanIds: Ref<Set<string>>`.
- Actions: `trackPlan(planId)`, `untrackPlan(planId)`, `fetchDetails(planId)`, `setCachedPlanRecord(planId, record)`, `deleteExecutionDetails(planId)`.
- Persistence: none; cache only.

**E. availableTools** (tools from backend)

- Backend mirror: `tools: Ref<Tool[]>` from `ToolApiService.getAvailableTools()` — `{ key, name, description, enabled, serviceGroup, selectable }`.
- Actions: `setTools(list)`, `loadTools()`.
- Persistence: none.
- Optional getter: `selectableTools` (filter `selectable !== false`).

**F. parameterRequirements** (optional; backend-only cache)

- Backend mirror: `requirementsByPlanTemplateId: Ref<Record<string, ParameterRequirements>>` from `PlanTemplateApiService.getParameterRequirements(planTemplateId)`.
- Actions: `getOrLoadRequirements(planTemplateId)`.
- Persistence: none.
- Note: parameter **history** (user’s past parameter sets) is frontend-only; keep in a separate store (e.g. `parameterHistory`).

**G. Config / admin** (optional single store or split)

- Models: `models: Ref<Model[]>`, `modelTypes: Ref<string[]>`, `defaultModelId: Ref<string | null>` — from ModelApiService.
- Admin config: `configByGroup: Ref<Record<string, ConfigItem[]>>` from `AdminApiService.getConfigsByGroup(groupName)`.
- Actions: `loadModels()`, `loadModelTypes()`, `loadConfigGroup(groupName)`, `updateConfig(config)`, `batchUpdateConfigs(configs)`, `resetAllConfigsToDefaults()`.
- Persistence: none.
- Can split into `modelConfig` and `adminConfig`.

**H. mcpServers** (backend mirror)

- Backend mirror: `servers: Ref<McpServer[]>` from `McpApiService.getAllMcpServers()`.
- Actions: `setServers(list)`, `loadServers()`, thin wrappers after add/remove/enable/disable.
- Persistence: none.

**I. datasourceConfigs** (backend mirror)

- Backend mirror: `configs: Ref<DatasourceConfig[]>` from `DatasourceConfigApiService.getAllConfigs()`.
- Actions: `setConfigs(list)`, `loadConfigs()`, reload/update after create/update/delete.
- Persistence: none.

**J. cronTasks** (backend mirror)

- Backend mirror: `cronTasks: Ref<CronConfig[]>` from `CronApiService.getAllCronTasks()`.
- Actions: `setCronTasks(list)`, `loadCronTasks()`, reload/update after create/update/delete.
- Persistence: none.

**K. app / runtime** (backend-related, not list backend)

- Version: `version: Ref<{ version, buildTime, timestamp } | null>` from `ConfigApiService.getVersion()`.
- Init: `initStatus: Ref<{ success, initialized } | null>` from `GET /api/init/status`.
- Task: `currentTaskPlanId: Ref<string | null>`; optional `taskStatusByPlanId: Ref<Record<string, TaskStatus>>` from `DirectApiService.getTaskStatus(planId)`.
- Actions: `loadVersion()`, `checkInitStatus()`, `setCurrentTaskPlanId(id)`, optional `fetchTaskStatus(planId)`.
- Persistence: optional “has visited home” / init flags in localStorage.

---

## Summary: what to put in Pinia first (backend-mirror)

- **namespace** — namespaces, currentNamespaceId — NamespaceApiService
- **conversation** — conversations (Memory[]), selectedConversationId — MemoryApiService
- **planTemplate** — planTemplateList, currentConfig, currentPlanTemplateId, planVersions, currentVersionIndex — PlanTemplateApiService
- **planExecution** — recordsByPlanId, trackedPlanIds — DirectApiService.getDetails
- **availableTools** — tools (Tool[]) — ToolApiService
- **parameterRequirements** (optional) — requirementsByPlanTemplateId — PlanTemplateApiService.getParameterRequirements
- **parameterHistory** — frontend-only (past param sets)
- **modelConfig** (optional) — models, modelTypes — ModelApiService
- **adminConfig** (optional) — configByGroup — AdminApiService
- **mcpServers** — servers (McpServer[]) — McpApiService
- **datasourceConfigs** — configs (DatasourceConfig[]) — DatasourceConfigApiService
- **cronTasks** — cronTasks (CronConfig[]) — CronApiService
- **app** (or **runtime**) — version, initStatus, currentTaskPlanId — ConfigApiService.getVersion, init, DirectApiService.getTaskStatus

Implement these **first** as Pinia stores that only hold and load/save the above backend-mirror data. Then add UI state (e.g. sidebar collapsed, right-panel tab) in components or a small `ui` / `memoryPanel` store so that “objects that mirror the backend” stay clear and separate from UI.
