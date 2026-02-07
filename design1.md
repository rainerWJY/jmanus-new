Here’s the three-part answer: (1) all API calls, (2) their return values, (3) Pinia store objects that mirror backend data first.

---

# 1) All API calls in the frontend

Grouped by service:

| Service                        | Method                                                        | Called from                                                       |
| ------------------------------ | ------------------------------------------------------------- | ----------------------------------------------------------------- |
| **NamespaceApiService**        | `getAllNamespaces()`                                          | namespaceConfig.vue, namespaceSwitch.vue                          |
|                                | `getNamespaceById(id)`                                        | (defined, usage not in grep)                                      |
|                                | `createNamespace(config)`                                     | namespaceConfig.vue                                               |
|                                | `updateNamespace(id, config)`                                 | namespaceConfig.vue                                               |
|                                | `deleteNamespace(id)`                                         | namespaceConfig.vue                                               |
| **MemoryApiService**           | `getMemories()`                                               | Memory.vue                                                        |
|                                | `getMemory(conversationId)`                                   | (defined)                                                         |
|                                | `createMemory(conversationId, memoryName)`                    | (defined)                                                         |
|                                | `updateMemory(conversationId, memoryName)`                    | Memory.vue                                                        |
|                                | `deleteMemory(conversationId)`                                | Memory.vue                                                        |
|                                | `generateConversationId()`                                    | (defined)                                                         |
|                                | `getConversationHistory(conversationId)`                      | useConversationHistory.ts                                         |
| **PlanTemplateApiService**     | `createOrUpdatePlanTemplateWithTool(data)`                    | usePlanTemplateConfig, JsonEditorV2                               |
|                                | `getPlanTemplateConfigVO(planTemplateId)`                     | usePlanTemplateConfig                                             |
|                                | `getAllPlanTemplateConfigVOs()`                               | templateStore, TemplateList, planTemplateConfig.vue, JsonEditorV2 |
|                                | `deletePlanTemplate(planTemplateId)`                          | templateStore                                                     |
|                                | `exportAllPlanTemplates()`                                    | planTemplateConfig.vue                                            |
|                                | `importPlanTemplates(templates)`                              | usePlanTemplateImport                                             |
|                                | `generatePlanTemplateId()`                                    | templateStore, JsonEditorV2                                       |
| **PlanActApiService**          | `executePlan(...)`                                            | useMessageDialog (via DirectApiService)                           |
|                                | `getPlanVersions(planId)`                                     | usePlanTemplateConfig                                             |
|                                | `getAllPlanTemplates()`                                       | TaskDetailModal.vue                                               |
| **CommonApiService**           | `getDetails(planId)`                                          | usePlanExecution                                                  |
|                                | `deleteExecutionDetails(planId)`                              | usePlanExecution                                                  |
|                                | `submitFormInput(planId, formData)`                           | UserInputForm.vue                                                 |
|                                | `getAllPrompts()`                                             | (defined)                                                         |
|                                | `getVersion()`                                                | basicConfig.vue                                                   |
| **DirectApiService**           | `sendMessage(query)`                                          | (legacy)                                                          |
|                                | `sendChatMessage(query, requestSource, onChunk, abortSignal)` | useMessageDialog                                                  |
|                                | `executeByToolName(...)`                                      | useMessageDialog, PlanActApiService.executePlan                   |
|                                | `getTaskStatus(planId)`                                       | useTaskStop                                                       |
|                                | `stopTask(planId)`                                            | useTaskStop                                                       |
|                                | `cancelChatStream(conversationId, streamId)`                  | useMessageDialog                                                  |
| **ToolApiService**             | `getAvailableTools()`                                         | useAvailableTools, JsonEditorV2                                   |
| **PlanParameterApiService**    | `getParameterRequirements(planTemplateId)`                    | ExecutionController, PublishServiceModal                          |
| **agent-execution**            | `fetchAgentExecutionDetail(stepId)`                           | useRightPanel                                                     |
|                                | `refreshAgentExecutionDetail(stepId)`                         | useRightPanel                                                     |
| **ConfigApiService**           | `getAvailableModels()`                                        | JsonEditorV2                                                      |
| **ModelApiService**            | `getAllModels()`, `getAllTypes()`                             | modelConfig.vue                                                   |
|                                | `getModelById(id)`                                            | modelConfig.vue                                                   |
|                                | `validateConfig(request)`                                     | modelConfig.vue                                                   |
|                                | `createModel(config)`                                         | modelConfig.vue                                                   |
|                                | `updateModel(id, config)`                                     | modelConfig.vue                                                   |
|                                | `deleteModel(id)`                                             | modelConfig.vue                                                   |
|                                | `setDefaultModel(id)`                                         | modelConfig.vue                                                   |
| **AdminApiService**            | `getConfigsByGroup(groupName)`                                | basicConfig.vue                                                   |
|                                | `batchUpdateConfigs(configs)`                                 | basicConfig.vue                                                   |
|                                | `getConfigById(id)`                                           | (defined)                                                         |
|                                | `updateConfig(config)`                                        | (defined)                                                         |
|                                | `resetAllConfigsToDefaults()`                                 | basicConfig.vue                                                   |
| **McpApiService**              | `getAllMcpServers()`                                          | mcpConfig.vue                                                     |
|                                | `addMcpServer(config)`                                        | (defined)                                                         |
|                                | `importMcpServers(jsonData)`                                  | mcpConfig.vue                                                     |
|                                | `removeMcpServer(id)`                                         | mcpConfig.vue                                                     |
|                                | `saveMcpServer(request)`                                      | mcpConfig.vue                                                     |
|                                | `getMcpServer(id)`                                            | (defined)                                                         |
|                                | `enableMcpServer(id)`                                         | mcpConfig.vue                                                     |
|                                | `disableMcpServer(id)`                                        | mcpConfig.vue                                                     |
| **DatasourceConfigApiService** | `getAllConfigs()`                                             | databaseConfig.vue                                                |
|                                | `getConfigById(id)`                                           | databaseConfig.vue                                                |
|                                | `createConfig(config)`                                        | databaseConfig.vue                                                |
|                                | `updateConfig(id, config)`                                    | databaseConfig.vue                                                |
|                                | `deleteConfig(id)`                                            | databaseConfig.vue                                                |
|                                | `testConnection(config)`                                      | DatasourceConfigForm.vue                                          |
| **FileBrowserApiService**      | `getFileTree(planId)`                                         | file-browser/index.vue                                            |
|                                | `getFileContent(planId, path)`                                | file-browser/index.vue                                            |
|                                | `downloadFile(planId, path, name)`                            | file-browser/index.vue                                            |
| **FileUploadApiService**       | `uploadFiles(files)`                                          | FileUploadComponent.vue                                           |
|                                | `getUploadedFiles(uploadKey)`                                 | (defined)                                                         |
|                                | `deleteFile(uploadKey, fileName)`                             | FileUploadComponent.vue                                           |
|                                | `getUploadConfig()`                                           | (defined)                                                         |
| **CronApiService**             | `getAllCronTasks()`                                           | CronTaskModal.vue                                                 |
|                                | `getCronTaskById(id)`                                         | (defined)                                                         |
|                                | `createCronTask(config)`                                      | cron-task-utils                                                   |
|                                | `updateCronTask(id, config)`                                  | cron-task-utils                                                   |
|                                | `deleteCronTask(id)`                                          | cron-task-utils                                                   |
| **DatabaseCleanupApiService**  | `getTableCounts()`                                            | databaseCleanupConfig.vue                                         |
|                                | `clearAllTables()`                                            | databaseCleanupConfig.vue                                         |
| **Init** (fetch)               | `POST /api/init/save`                                         | init/index.vue                                                    |
|                                | `GET /api/init/status`                                        | init/index.vue, router, llm-check                                 |
| **Language**                   | `getLanguage()`                                               | (api/language.ts)                                                 |
|                                | `setLanguage(language)`                                       | (api/language.ts)                                                 |

---

# 2) Return value of each API

| API                                        | Return type (or shape)                                                                                               |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **NamespaceApiService**                    |                                                                                                                      |
| `getAllNamespaces()`                       | `Namespace[]` — `{ id, code, name, description?, host? }`                                                            |
| `getNamespaceById(id)`                     | `Namespace`                                                                                                          |
| `createNamespace(config)`                  | `Namespace`                                                                                                          |
| `updateNamespace(id, config)`              | `Namespace`                                                                                                          |
| `deleteNamespace(id)`                      | `void`                                                                                                               |
| **MemoryApiService**                       |                                                                                                                      |
| `getMemories()`                            | `Memory[]` — `{ id, conversation_id, memory_name, create_time, messages? }` (from `MemoryResponse.memories`)         |
| `getMemory(conversationId)`                | `Memory` (from `MemoryResponse.data`)                                                                                |
| `createMemory(...)`                        | `Memory`                                                                                                             |
| `updateMemory(...)`                        | `Memory`                                                                                                             |
| `deleteMemory(...)`                        | `void`                                                                                                               |
| `generateConversationId()`                 | `Memory`                                                                                                             |
| `getConversationHistory(conversationId)`   | `PlanExecutionRecord[]`                                                                                              |
| **PlanTemplateApiService**                 |                                                                                                                      |
| `createOrUpdatePlanTemplateWithTool(data)` | `CreateOrUpdatePlanTemplateWithToolResponse` — `{ success, planTemplateId, toolRegistered }`                         |
| `getPlanTemplateConfigVO(planTemplateId)`  | `PlanTemplateConfigVO`                                                                                               |
| `getAllPlanTemplateConfigVOs()`            | `PlanTemplateConfigVO[]`                                                                                             |
| `deletePlanTemplate(planTemplateId)`       | `unknown`                                                                                                            |
| `exportAllPlanTemplates()`                 | `PlanTemplateConfigVO[]`                                                                                             |
| `importPlanTemplates(templates)`           | `{ success, total, successCount, failureCount, errors: { planTemplateId, message }[] }`                              |
| `generatePlanTemplateId()`                 | `string` (planTemplateId)                                                                                            |
| **PlanActApiService**                      |                                                                                                                      |
| `executePlan(...)`                         | (delegates to DirectApiService) `unknown` — typically `{ planId?, conversationId? }`                                 |
| `getPlanVersions(planId)`                  | `unknown` — used as `{ versions?: string[] }`                                                                        |
| `getAllPlanTemplates()`                    | `unknown` (TaskDetailModal)                                                                                          |
| **CommonApiService**                       |                                                                                                                      |
| `getDetails(planId)`                       | `PlanExecutionRecordResponse` = `PlanExecutionRecord                                                                 | null`                                      |
| `deleteExecutionDetails(planId)`           | `Record<string, string>`                                                                                             |
| `submitFormInput(planId, formData)`        | `Record<string, unknown>` or `{ success: true }`                                                                     |
| `getAllPrompts()`                          | `unknown[]`                                                                                                          |
| `getVersion()`                             | `{ version, buildTime, timestamp }`                                                                                  |
| **DirectApiService**                       |                                                                                                                      |
| `sendMessage(query)`                       | `unknown`                                                                                                            |
| `sendChatMessage(...)`                     | `Promise<{ conversationId?, message? }>` (+ SSE chunks)                                                              |
| `executeByToolName(...)`                   | `unknown` — typically `{ planId?, conversationId? }`                                                                 |
| `getTaskStatus(planId)`                    | `{ planId, isRunning, exists, desiredState?, startTime?, endTime?, lastUpdated?, taskResult? }`                      |
| `stopTask(planId)`                         | `unknown`                                                                                                            |
| `cancelChatStream(...)`                    | `{ status, message }`                                                                                                |
| **ToolApiService**                         |                                                                                                                      |
| `getAvailableTools()`                      | `Tool[]` — `{ key, name, description, enabled, serviceGroup, selectable }`                                           |
| **PlanParameterApiService**                |                                                                                                                      |
| `getParameterRequirements(planTemplateId)` | `ParameterRequirements` — `{ parameters: string[], hasParameters, requirements }`                                    |
| **agent-execution**                        |                                                                                                                      |
| `fetchAgentExecutionDetail(stepId)`        | `AgentExecutionRecordDetail                                                                                          | null` (same shape as AgentExecutionRecord) |
| **ConfigApiService**                       |                                                                                                                      |
| `getAvailableModels()`                     | `AvailableModelsResponse` — `{ options: ModelOption[], total }`, `ModelOption` = `{ value, label }`                  |
| **ModelApiService**                        |                                                                                                                      |
| `getAllModels()`                           | `Model[]`                                                                                                            |
| `getAllTypes()`                            | `string[]`                                                                                                           |
| `getModelById(id)`                         | `Model`                                                                                                              |
| `validateConfig(request)`                  | `ValidationResult` — `{ valid, message?, availableModels? }`                                                         |
| `createModel(config)`                      | `Model`                                                                                                              |
| `updateModel(id, config)`                  | `Model`                                                                                                              |
| `deleteModel(id)`                          | `void`                                                                                                               |
| `setDefaultModel(id)`                      | `{ success, message }`                                                                                               |
| **AdminApiService**                        |                                                                                                                      |
| `getConfigsByGroup(groupName)`             | `ConfigItem[]`                                                                                                       |
| `batchUpdateConfigs(configs)`              | `ApiResponse` — `{ success, message }`                                                                               |
| `getConfigById(id)`                        | `ConfigItem`                                                                                                         |
| `updateConfig(config)`                     | `ApiResponse`                                                                                                        |
| `resetAllConfigsToDefaults()`              | `ApiResponse`                                                                                                        |
| **McpApiService**                          |                                                                                                                      |
| `getAllMcpServers()`                       | `McpServer[]`                                                                                                        |
| `addMcpServer(config)`                     | `ApiResponse`                                                                                                        |
| `importMcpServers(jsonData)`               | `ApiResponse`                                                                                                        |
| `removeMcpServer(id)`                      | `ApiResponse`                                                                                                        |
| `saveMcpServer(request)`                   | `ApiResponse`                                                                                                        |
| `getMcpServer(id)`                         | (returns server)                                                                                                     |
| `enableMcpServer(id)`                      | `ApiResponse`                                                                                                        |
| `disableMcpServer(id)`                     | `ApiResponse`                                                                                                        |
| **DatasourceConfigApiService**             |                                                                                                                      |
| `getAllConfigs()`                          | `DatasourceConfig[]`                                                                                                 |
| `getConfigById(id)`                        | `DatasourceConfig`                                                                                                   |
| `createConfig(config)`                     | `DatasourceConfig`                                                                                                   |
| `updateConfig(id, config)`                 | `DatasourceConfig`                                                                                                   |
| `deleteConfig(id)`                         | `void`                                                                                                               |
| `testConnection(config)`                   | `{ success, message }`                                                                                               |
| **FileBrowserApiService**                  |                                                                                                                      |
| `getFileTree(planId)`                      | `FileNode` — `{ name, path, type, size, lastModified, children? }`                                                   |
| `getFileContent(planId, path)`             | `FileContent` — `{ content, mimeType, size, isBinary?, downloadOnly? }`                                              |
| **FileUploadApiService**                   |                                                                                                                      |
| `uploadFiles(files)`                       | `FileUploadResult` — `{ success, message, uploadKey, uploadedFiles, totalFiles, successfulFiles, failedFiles }`      |
| `getUploadedFiles(uploadKey)`              | `GetUploadedFilesResponse`                                                                                           |
| `deleteFile(...)`                          | `DeleteFileResponse`                                                                                                 |
| `getUploadConfig()`                        | `UploadConfig`                                                                                                       |
| **CronApiService**                         |                                                                                                                      |
| `getAllCronTasks()`                        | `CronConfig[]`                                                                                                       |
| `getCronTaskById(id)`                      | `CronConfig`                                                                                                         |
| `createCronTask(config)`                   | `CronConfig`                                                                                                         |
| `updateCronTask(id, config)`               | `CronConfig`                                                                                                         |
| `deleteCronTask(id)`                       | `void`                                                                                                               |
| **DatabaseCleanupApiService**              |                                                                                                                      |
| `getTableCounts()`                         | `TableCounts` — `{ act_tool_info, think_act_record, plan_execution_record, agent_execution_record, ai_chat_memory }` |
| `clearAllTables()`                         | `TableCounts`                                                                                                        |
| **Init**                                   |                                                                                                                      |
| `POST /api/init/save`                      | `{ success, requiresRestart?, error? }`                                                                              |
| `GET /api/init/status`                     | `{ success, initialized }`                                                                                           |
| **Language**                               |                                                                                                                      |
| `getLanguage()`                            | `'zh'                                                                                                                | 'en'`(from`LanguageResponse.language`)     |
| `setLanguage(language)`                    | `SetLanguageResponse` — `{ success, language, error? }`                                                              |

---

# 3) Pinia store objects that mirror backend data first

Design rule: **each store holds data that comes from (or is sent to) the backend; UI-only state (collapsed, active tab, etc.) lives in components or a separate UI store.**

Suggested stores and their “backend-mirror” state:

---

## A. `namespace` (already exists; align with API)

- **Backend mirror**
  - `namespaces: Ref<Namespace[]>` — from `NamespaceApiService.getAllNamespaces()`.
  - `currentNamespaceId: Ref<string>` — selected id (e.g. `'default'`); used by API calls.
- **Actions:** `setNamespaces(list)`, `setCurrentNamespace(id)`, and optionally `loadNamespaces()` calling the API.
- **Persistence:** Optional: persist `currentNamespaceId` in localStorage.

---

## B. `conversation` (memory backend data only)

- **Backend mirror**
  - `conversations: Ref<Memory[]>` — from `MemoryApiService.getMemories()`; shape `{ id, conversation_id, memory_name, create_time, messages? }`.
  - `selectedConversationId: Ref<string | null>` — used when loading history and for API (e.g. chat).
- **Actions:** `setConversations(list)`, `setSelectedConversationId(id)`, `loadConversations()` (calls `getMemories()`), `loadConversationHistory(conversationId)` (calls `getConversationHistory`) if you want it in store.
- **Persistence:** `selectedConversationId` in localStorage.
- **Do not put here:** `isCollapsed`, `loadMessages` callback, `intervalId` — those stay in the Memory panel component or a small `memoryPanel` UI store.

---

## C. `planTemplate` (plan template list + current config from backend)

- **Backend mirror**
  - `planTemplateList: Ref<PlanTemplateConfigVO[]>` — from `getAllPlanTemplateConfigVOs()`.
  - `currentConfig: Ref<PlanTemplateConfigVO | null>` — from `getPlanTemplateConfigVO(id)` (editor state).
  - `currentPlanTemplateId: Ref<string | null>` — which template is selected/edited.
  - `planVersions: Ref<string[]>` — from `getPlanVersions(planId)`.
  - `currentVersionIndex: Ref<number>` — local index into `planVersions`.
- **Actions:** `loadPlanTemplateList()`, `loadPlanTemplateConfig(id)`, `savePlanTemplateConfig()`, `createNewTemplate(planType)`, `deletePlanTemplate(id)`, `setCurrentPlanTemplateId(id)`, `loadPlanVersions(planId)`, `setCurrentVersionIndex(i)`.
- **Persistence:** None; backend is source of truth.
- **Optional:** Import result from `importPlanTemplates` can be written into `planTemplateList` (and maybe a small `importResult` state) so the UI reflects the backend after import.

---

## D. `planExecution` (execution records from backend)

- **Backend mirror**
  - `recordsByPlanId: Ref<Record<string, PlanExecutionRecord>>` — from `CommonApiService.getDetails(planId)` (polling).
  - `trackedPlanIds: Ref<Set<string>>` — which plans we poll (derived from “started” executions).
- **Actions:** `trackPlan(planId)`, `untrackPlan(planId)`, `fetchDetails(planId)` (calls `getDetails` and updates `recordsByPlanId`), `setCachedPlanRecord(planId, record)` (e.g. after loading history), `deleteExecutionDetails(planId)`.
- **Persistence:** None; cache only.

---

## E. `availableTools` (tools from backend)

- **Backend mirror**
  - `tools: Ref<Tool[]>` — from `ToolApiService.getAvailableTools()`; shape `{ key, name, description, enabled, serviceGroup, selectable }`.
- **Actions:** `setTools(list)`, `loadTools()` (calls API).
- **Persistence:** None.
- **Optional:** Expose a getter like `selectableTools` (filter `selectable !== false`) so the rest of the app uses one list.

---

## F. `parameterRequirements` (optional; backend-only cache)

- **Backend mirror**
  - `requirementsByPlanTemplateId: Ref<Record<string, ParameterRequirements>>` — from `PlanParameterApiService.getParameterRequirements(planTemplateId)`.
- **Actions:** `getOrLoadRequirements(planTemplateId)` (return cached or fetch and store).
- **Persistence:** None; cache only.
- **Note:** Parameter **history** (user’s past parameter sets) is frontend-only; keep that in a separate store (e.g. `parameterHistory`) and do not mix with this backend-mirror store.

---

## G. Config / admin (optional single store or split)

Backend-mirror only; no UI state.

- **Models**
  - `models: Ref<Model[]>` — from `ModelApiService.getAllModels()`.
  - `modelTypes: Ref<string[]>` — from `getAllTypes()`.
  - `defaultModelId: Ref<string | null>` — derived or from current model list.
- **Admin config**
  - `configByGroup: Ref<Record<string, ConfigItem[]>>` — from `AdminApiService.getConfigsByGroup(groupName)` (keyed by group).
- **Actions:** `loadModels()`, `loadModelTypes()`, `loadConfigGroup(groupName)`, `updateConfig(config)`, `batchUpdateConfigs(configs)`, `resetAllConfigsToDefaults()`.
- **Persistence:** None.
- **Note:** If you prefer, split into `modelConfig` and `adminConfig` stores.

---

## H. MCP servers (backend mirror)

- **Backend mirror**
  - `servers: Ref<McpServer[]>` — from `McpApiService.getAllMcpServers()`.
- **Actions:** `setServers(list)`, `loadServers()`, and optionally thin wrappers that call API then `setServers` (e.g. after add/remove/enable/disable).
- **Persistence:** None.

---

## I. Datasource configs (backend mirror)

- **Backend mirror**
  - `configs: Ref<DatasourceConfig[]>` — from `DatasourceConfigApiService.getAllConfigs()`.
- **Actions:** `setConfigs(list)`, `loadConfigs()`, and after create/update/delete reload or update `configs`.
- **Persistence:** None.

---

## J. Cron tasks (backend mirror)

- **Backend mirror**
  - `cronTasks: Ref<CronConfig[]>` — from `CronApiService.getAllCronTasks()`.
- **Actions:** `setCronTasks(list)`, `loadCronTasks()`, and after create/update/delete reload or update list.
- **Persistence:** None.

---

## K. App / runtime (not “list” backend, but still backend-related)

- **Version**
  - `version: Ref<{ version, buildTime, timestamp } | null>` — from `CommonApiService.getVersion()`.
- **Init**
  - `initStatus: Ref<{ success: boolean; initialized: boolean } | null>` — from `GET /api/init/status`.
- **Task status** (for “current running task”)
  - `currentTaskPlanId: Ref<string | null>` — set when a plan is started; cleared when done.
  - Optional: `taskStatusByPlanId: Ref<Record<string, TaskStatus>>` if you want to cache `getTaskStatus(planId)`.
- **Actions:** `loadVersion()`, `checkInitStatus()`, `setCurrentTaskPlanId(id)`, optional `fetchTaskStatus(planId)`.
- **Persistence:** Optional: persist “has visited home” / init flags in localStorage (as today); keep those in this store or a tiny `app` store.

---

## Summary: what to put in Pinia first (backend-mirror)

| Store                                | Backend-mirror state                                                                                | Main API source                                           |
| ------------------------------------ | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| **namespace**                        | `namespaces`, `currentNamespaceId`                                                                  | NamespaceApiService                                       |
| **conversation**                     | `conversations` (Memory[]), `selectedConversationId`                                                | MemoryApiService                                          |
| **planTemplate**                     | `planTemplateList`, `currentConfig`, `currentPlanTemplateId`, `planVersions`, `currentVersionIndex` | PlanTemplateApiService, PlanActApiService.getPlanVersions |
| **planExecution**                    | `recordsByPlanId`, `trackedPlanIds`                                                                 | CommonApiService.getDetails                               |
| **availableTools**                   | `tools` (Tool[])                                                                                    | ToolApiService                                            |
| **parameterRequirements** (optional) | `requirementsByPlanTemplateId`                                                                      | PlanParameterApiService                                   |
| **parameterHistory**                 | (frontend-only: past param sets)                                                                    | —                                                         |
| **modelConfig** (optional)           | `models`, `modelTypes`                                                                              | ModelApiService                                           |
| **adminConfig** (optional)           | `configByGroup`                                                                                     | AdminApiService                                           |
| **mcpServers**                       | `servers` (McpServer[])                                                                             | McpApiService                                             |
| **datasourceConfigs**                | `configs` (DatasourceConfig[])                                                                      | DatasourceConfigApiService                                |
| **cronTasks**                        | `cronTasks` (CronConfig[])                                                                          | CronApiService                                            |
| **app** (or **runtime**)             | `version`, `initStatus`, `currentTaskPlanId`                                                        | CommonApiService, init, DirectApiService.getTaskStatus    |

Implement these **first** as Pinia stores that only hold and load/save the above backend-mirror data. Then add UI state (e.g. sidebar collapsed, right-panel tab) either in components or in a small `ui` / `memoryPanel` store so that “objects that mirror the backend” stay clear and separate from UI.
