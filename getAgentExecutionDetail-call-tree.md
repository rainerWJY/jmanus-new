# GET /api/executor/agent-execution/{stepId} - Call Tree and Data Flow

This document describes how `getAgentExecutionDetail` reads data and how data is written to the database, using the provided JSON example.

## Example JSON Response

```json
{
  "id": 1905,
  "stepId": "step-353d351a-ce60-4142-8ce2-79beba213208",
  "agentName": "ConfigurableDynaAgent",
  "thinkActSteps": [
    {
      "id": 2368,
      "parentExecutionId": 1905,
      "thinkInput": "[UserMessage{content='...'}]",
      "thinkOutput": "",
      "inputCharCount": 1466,
      "outputCharCount": 1679,
      "actToolInfoList": [
        {
          "name": "file-service-group_write-file-operator",
          "parameters": "{\"file_path\": \"article.md\", \"contents\": \"...\"}",
          "result": "{\"output\":\"File written successfully (created): article.md\"}",
          "id": "toolcall-1767061562532_7902_107"
        }
      ]
    }
  ]
}
```

## Call Tree: How Data is WRITTEN to Database

```
[Agent Execution Flow]
  │
  └─ DynamicAgent::executeStep()
      │
      └─ [When LLM returns tool calls]
          │
          └─ DynamicAgent::processToolCalls()
              │
              ├─ [Step 1] Generate IDs
              │   ├─ stepId = "step-353d351a-ce60-4142-8ce2-79beba213208"
              │   ├─ thinkActId = planIdDispatcher.generateThinkActId()
              │   └─ toolCallId = "toolcall-1767061562532_7902_107"
              │
              ├─ [Step 2] Create ActToolParam list
              │   └─ For each ToolCall:
              │       └─ ActToolParam actToolInfo = new ActToolParam(
              │               toolCall.name(),                    → "file-service-group_write-file-operator"
              │               toolCall.arguments(),               → "{\"file_path\": \"article.md\", ...}"
              │               toolCallIdForTool                    → "toolcall-1767061562532_7902_107"
              │           )
              │
              ├─ [Step 3] Create ThinkActRecordParams
              │   └─ ThinkActRecordParams paramsN = new ThinkActRecordParams(
              │           thinkActId,                            → generated ID
              │           stepId,                                 → "step-353d351a-ce60-4142-8ce2-79beba213208"
              │           thinkInput,                             → "[UserMessage{content='...'}]"
              │           responseByLLm,                          → LLM response text
              │           null,                                   → errorMessage
              │           finalInputCharCount,                    → 1466
              │           finalOutputCharCount,                   → 1679
              │           actToolInfoList                         → List<ActToolParam>
              │       )
              │
              └─ [Step 4] Write to Database
                  └─ NewRepoPlanExecutionRecorder::recordThinkingAndAction(step, paramsN)
                      │
                      ├─ [4.1] Find AgentExecutionRecordEntity
                      │   └─ AgentExecutionRecordRepository::findByStepId(stepId)
                      │       └─ SQL: SELECT * FROM agent_execution_record WHERE step_id = 'step-353d351a-ce60-4142-8ce2-79beba213208'
                      │           └─ Returns: AgentExecutionRecordEntity(id=1905, stepId="step-353d351a-ce60-4142-8ce2-79beba213208", ...)
                      │
                      ├─ [4.2] Create ThinkActRecordEntity
                      │   └─ ThinkActRecordEntity thinkActRecord = new ThinkActRecordEntity()
                      │       ├─ thinkActRecord.setParentExecutionId(agentRecord.getId())     → 1905
                      │       ├─ thinkActRecord.setThinkActId(params.getThinkActId())          → generated ID
                      │       ├─ thinkActRecord.setThinkInput(params.getThinkInput())          → "[UserMessage{content='...'}]"
                      │       ├─ thinkActRecord.setThinkOutput(params.getThinkOutput())        → ""
                      │       ├─ thinkActRecord.setErrorMessage(params.getErrorMessage())      → null
                      │       ├─ thinkActRecord.setInputCharCount(params.getInputCharCount())  → 1466
                      │       └─ thinkActRecord.setOutputCharCount(params.getOutputCharCount()) → 1679
                      │
                      ├─ [4.3] Convert ActToolParam to ActToolInfoEntity
                      │   └─ For each ActToolParam in params.getActToolInfoList():
                      │       └─ convertToActToolInfoEntity(actToolParam)
                      │           └─ ActToolInfoEntity entity = new ActToolInfoEntity(
                      │                   actToolParam.getName(),           → "file-service-group_write-file-operator"
                      │                   actToolParam.getParameters(),     → "{\"file_path\": \"article.md\", ...}"
                      │                   actToolParam.getToolCallId()      → "toolcall-1767061562532_7902_107"
                      │               )
                      │           └─ entity.setResult(actToolParam.getResult()) → null (not set yet)
                      │
                      ├─ [4.4] Set ActToolInfoList to ThinkActRecordEntity
                      │   └─ thinkActRecord.setActToolInfoList(actToolInfoEntities)
                      │       └─ List<ActToolInfoEntity> with tool info
                      │
                      ├─ [4.5] Save ThinkActRecordEntity (CASCADE saves ActToolInfoEntity)
                      │   └─ ThinkActRecordRepository::save(thinkActRecord)
                      │       └─ SQL: INSERT INTO think_act_record (
                      │               parent_execution_id,       → 1905
                      │               think_act_id,              → generated ID
                      │               think_input,               → "[UserMessage{content='...'}]"
                      │               think_output,              → ""
                      │               input_char_count,          → 1466
                      │               output_char_count,         → 1679
                      │               ...
                      │           )
                      │       └─ SQL: INSERT INTO act_tool_info (
                      │               name,                     → "file-service-group_write-file-operator"
                      │               parameters,                → "{\"file_path\": \"article.md\", ...}"
                      │               tool_call_id,              → "toolcall-1767061562532_7902_107"
                      │               think_act_record_id,       → 2368 (auto-set by JPA)
                      │               result                     → null
                      │           )
                      │
                      └─ [4.6] Update AgentExecutionRecordEntity relationship
                          └─ agentRecord.addThinkActStep(savedThinkActRecord)
                              └─ AgentExecutionRecordRepository::save(agentRecord)
                                  └─ Updates relationship (no SQL needed, already linked)

[Later: When tool execution completes]
  │
  └─ NewRepoPlanExecutionRecorder::recordActionResult(actToolParamList)
      │
      └─ For each ActToolParam:
          │
          ├─ [Find existing ActToolInfoEntity]
          │   └─ ActToolInfoRepository::findByToolCallId(toolCallId)
          │       └─ SQL: SELECT * FROM act_tool_info WHERE tool_call_id = 'toolcall-1767061562532_7902_107'
          │
          └─ [Update result]
              └─ existingEntity.setResult(actToolParam.getResult())
                  └─ ActToolInfoRepository::save(existingEntity)
                      └─ SQL: UPDATE act_tool_info 
                          SET result = '{"output":"File written successfully (created): article.md"}'
                          WHERE tool_call_id = 'toolcall-1767061562532_7902_107'
```

## Call Tree: How Data is READ from Database

```
GET /api/executor/agent-execution/{stepId}
  │
  └─ [Controller] LynxeController::getAgentExecutionDetail(stepId="step-353d351a-ce60-4142-8ce2-79beba213208")
      │
      └─ [Service] NewRepoPlanExecutionRecorder::getAgentExecutionDetail(stepId)
          │
          ├─ [Step 1] Find AgentExecutionRecordEntity
          │   └─ AgentExecutionRecordRepository::findByStepId(stepId)
          │       └─ SQL: SELECT * FROM agent_execution_record WHERE step_id = 'step-353d351a-ce60-4142-8ce2-79beba213208'
          │           └─ Returns: AgentExecutionRecordEntity
          │               ├─ id = 1905
          │               ├─ stepId = "step-353d351a-ce60-4142-8ce2-79beba213208"
          │               ├─ agentName = "ConfigurableDynaAgent"
          │               ├─ startTime = [2025,12,30,10,26,2,529626000]
          │               ├─ endTime = [2025,12,30,10,26,9,263264000]
          │               └─ status = FINISHED
          │
          ├─ [Step 2] Convert to AgentExecutionRecord VO
          │   └─ AgentExecutionRecord detail = new AgentExecutionRecord(
          │           agentRecord.getStepId(),        → "step-353d351a-ce60-4142-8ce2-79beba213208"
          │           agentRecord.getAgentName(),     → "ConfigurableDynaAgent"
          │           agentRecord.getAgentDescription() → "A configurable dynamic agent"
          │       )
          │   ├─ detail.setId(agentRecord.getId())                    → id = 1905
          │   ├─ detail.setStartTime(agentRecord.getStartTime())      → startTime = [2025,12,30,10,26,2,529626000]
          │   ├─ detail.setEndTime(agentRecord.getEndTime())          → endTime = [2025,12,30,10,26,9,263264000]
          │   ├─ detail.setStatus(convertToExecutionStatus(...))      → status = "FINISHED"
          │   ├─ detail.setAgentRequest(agentRecord.getAgentRequest()) → agentRequest = "..."
          │   ├─ detail.setResult(agentRecord.getResult())             → result = null
          │   └─ detail.setErrorMessage(agentRecord.getErrorMessage()) → errorMessage = null
          │
          ├─ [Step 3] Fetch ThinkActRecords with ActToolInfo
          │   └─ NewRepoPlanExecutionRecorder::fetchThinkActRecords(agentExecutionId=1905)
          │       │
          │       ├─ [3.1] Query ThinkActRecordEntity with ActToolInfo (eager fetch)
          │       │   └─ ThinkActRecordRepository::findByParentExecutionIdWithActToolInfo(1905)
          │       │       └─ SQL: SELECT t FROM ThinkActRecordEntity t 
          │       │           LEFT JOIN FETCH t.actToolInfoList 
          │       │           WHERE t.parentExecutionId = 1905
          │       │       └─ Returns: List<ThinkActRecordEntity>
          │       │           └─ ThinkActRecordEntity(id=2368, parentExecutionId=1905, ...)
          │       │               └─ actToolInfoList = [
          │       │                   ActToolInfoEntity(
          │       │                       name="file-service-group_write-file-operator",
          │       │                       parameters="{\"file_path\": \"article.md\", ...}",
          │       │                       toolCallId="toolcall-1767061562532_7902_107",
          │       │                       result="{\"output\":\"File written successfully...\"}"
          │       │                   )
          │       │               ]
          │       │
          │       └─ [3.2] Convert ThinkActRecordEntity to ThinkActRecord VO
          │           └─ For each ThinkActRecordEntity:
          │               ├─ ThinkActRecord record = new ThinkActRecord(entity.getParentExecutionId())
          │               │   └─ parentExecutionId = 1905
          │               │
          │               ├─ record.setId(entity.getId())                    → id = 2368
          │               ├─ record.setThinkInput(entity.getThinkInput())    → thinkInput = "[UserMessage{content='...'}]"
          │               ├─ record.setThinkOutput(entity.getThinkOutput())  → thinkOutput = ""
          │               ├─ record.setInputCharCount(...)                   → inputCharCount = 1466
          │               └─ record.setOutputCharCount(...)                  → outputCharCount = 1679
          │               │
          │               └─ [3.2.1] Convert ActToolInfoEntity to ActToolInfo VO
          │                   └─ For each ActToolInfoEntity in entity.getActToolInfoList():
          │                       └─ ActToolInfo actToolInfo = new ActToolInfo(
          │                               toolInfoEntity.getName(),           → "file-service-group_write-file-operator"
          │                               toolInfoEntity.getParameters(),    → "{\"file_path\": \"article.md\", ...}"
          │                               toolInfoEntity.getToolCallId()      → "toolcall-1767061562532_7902_107"
          │                           )
          │                       └─ actToolInfo.setResult(toolInfoEntity.getResult())
          │                           → result = "{\"output\":\"File written successfully (created): article.md\"}"
          │                       └─ record.setActToolInfoList([actToolInfo])
          │                       └─ record.setActionNeeded(true)
          │
          └─ [Step 4] Set ThinkActSteps to AgentExecutionRecord
              └─ detail.setThinkActSteps(thinkActSteps)
                  └─ Returns: AgentExecutionRecord with full thinkActSteps
```

## Value Mapping: Write Flow

### Input Values → Database Entities

| Input Source | Value | Method Call | Database Column | Entity Field |
|--------------|-------|-------------|-----------------|--------------|
| `step.getStepId()` | `"step-353d351a-ce60-4142-8ce2-79beba213208"` | `findByStepId(stepId)` | `step_id` | `AgentExecutionRecordEntity.stepId` |
| `params.getThinkActId()` | Generated ID | `thinkActRecord.setThinkActId(...)` | `think_act_id` | `ThinkActRecordEntity.thinkActId` |
| `agentRecord.getId()` | `1905` | `thinkActRecord.setParentExecutionId(...)` | `parent_execution_id` | `ThinkActRecordEntity.parentExecutionId` |
| `params.getThinkInput()` | `"[UserMessage{content='...'}]"` | `thinkActRecord.setThinkInput(...)` | `think_input` | `ThinkActRecordEntity.thinkInput` |
| `params.getThinkOutput()` | `""` | `thinkActRecord.setThinkOutput(...)` | `think_output` | `ThinkActRecordEntity.thinkOutput` |
| `params.getInputCharCount()` | `1466` | `thinkActRecord.setInputCharCount(...)` | `input_char_count` | `ThinkActRecordEntity.inputCharCount` |
| `params.getOutputCharCount()` | `1679` | `thinkActRecord.setOutputCharCount(...)` | `output_char_count` | `ThinkActRecordEntity.outputCharCount` |
| `actToolParam.getName()` | `"file-service-group_write-file-operator"` | `new ActToolInfoEntity(name, ...)` | `name` | `ActToolInfoEntity.name` |
| `actToolParam.getParameters()` | `"{\"file_path\": \"article.md\", ...}"` | `new ActToolInfoEntity(..., parameters, ...)` | `parameters` | `ActToolInfoEntity.parameters` |
| `actToolParam.getToolCallId()` | `"toolcall-1767061562532_7902_107"` | `new ActToolInfoEntity(..., toolCallId)` | `tool_call_id` | `ActToolInfoEntity.toolCallId` |
| `actToolParam.getResult()` | `"{\"output\":\"File written successfully...\"}"` | `existingEntity.setResult(...)` | `result` | `ActToolInfoEntity.result` |

## Value Mapping: Read Flow

### Database Entities → JSON Response

| Database Column | Entity Field | Method Call | JSON Field | Value |
|-----------------|--------------|-------------|------------|-------|
| `id` | `AgentExecutionRecordEntity.id` | `detail.setId(...)` | `id` | `1905` |
| `step_id` | `AgentExecutionRecordEntity.stepId` | Constructor parameter | `stepId` | `"step-353d351a-ce60-4142-8ce2-79beba213208"` |
| `agent_name` | `AgentExecutionRecordEntity.agentName` | Constructor parameter | `agentName` | `"ConfigurableDynaAgent"` |
| `start_time` | `AgentExecutionRecordEntity.startTime` | `detail.setStartTime(...)` | `startTime` | `[2025,12,30,10,26,2,529626000]` |
| `end_time` | `AgentExecutionRecordEntity.endTime` | `detail.setEndTime(...)` | `endTime` | `[2025,12,30,10,26,9,263264000]` |
| `status` | `AgentExecutionRecordEntity.status` | `detail.setStatus(...)` | `status` | `"FINISHED"` |
| `id` | `ThinkActRecordEntity.id` | `record.setId(...)` | `thinkActSteps[].id` | `2368` |
| `parent_execution_id` | `ThinkActRecordEntity.parentExecutionId` | Constructor parameter | `thinkActSteps[].parentExecutionId` | `1905` |
| `think_input` | `ThinkActRecordEntity.thinkInput` | `record.setThinkInput(...)` | `thinkActSteps[].thinkInput` | `"[UserMessage{content='...'}]"` |
| `think_output` | `ThinkActRecordEntity.thinkOutput` | `record.setThinkOutput(...)` | `thinkActSteps[].thinkOutput` | `""` |
| `input_char_count` | `ThinkActRecordEntity.inputCharCount` | `record.setInputCharCount(...)` | `thinkActSteps[].inputCharCount` | `1466` |
| `output_char_count` | `ThinkActRecordEntity.outputCharCount` | `record.setOutputCharCount(...)` | `thinkActSteps[].outputCharCount` | `1679` |
| `name` | `ActToolInfoEntity.name` | `new ActToolInfo(name, ...)` | `thinkActSteps[].actToolInfoList[].name` | `"file-service-group_write-file-operator"` |
| `parameters` | `ActToolInfoEntity.parameters` | `new ActToolInfo(..., parameters, ...)` | `thinkActSteps[].actToolInfoList[].parameters` | `"{\"file_path\": \"article.md\", ...}"` |
| `tool_call_id` | `ActToolInfoEntity.toolCallId` | `new ActToolInfo(..., toolCallId)` | `thinkActSteps[].actToolInfoList[].id` | `"toolcall-1767061562532_7902_107"` |
| `result` | `ActToolInfoEntity.result` | `actToolInfo.setResult(...)` | `thinkActSteps[].actToolInfoList[].result` | `"{\"output\":\"File written successfully...\"}"` |

## Key Methods Summary

### Write Methods

1. **`DynamicAgent.processToolCalls()`**
   - Creates `ActToolParam` objects from `ToolCall` objects
   - Creates `ThinkActRecordParams` with all think-act cycle data
   - Calls `recordThinkingAndAction()` to persist data

2. **`NewRepoPlanExecutionRecorder.recordThinkingAndAction()`**
   - Finds `AgentExecutionRecordEntity` by `stepId`
   - Creates `ThinkActRecordEntity` with think phase data
   - Converts `ActToolParam` to `ActToolInfoEntity` (without result)
   - Saves `ThinkActRecordEntity` (cascade saves `ActToolInfoEntity`)
   - Updates `AgentExecutionRecordEntity` relationship

3. **`NewRepoPlanExecutionRecorder.recordActionResult()`**
   - Finds `ActToolInfoEntity` by `toolCallId`
   - Updates `result` field with tool execution result
   - Saves updated entity

### Read Methods

1. **`LynxeController.getAgentExecutionDetail()`**
   - Entry point for GET request
   - Calls service method and serializes to JSON

2. **`NewRepoPlanExecutionRecorder.getAgentExecutionDetail()`**
   - Finds `AgentExecutionRecordEntity` by `stepId`
   - Converts to `AgentExecutionRecord` VO
   - Fetches `ThinkActRecord` details with `ActToolInfo`
   - Returns complete `AgentExecutionRecord` with `thinkActSteps`

3. **`NewRepoPlanExecutionRecorder.fetchThinkActRecords()`**
   - Queries `ThinkActRecordEntity` with eager fetch of `ActToolInfoList`
   - Converts entities to VO objects
   - Returns list of `ThinkActRecord` with `ActToolInfo` details

## Database Schema Relationships

```
agent_execution_record (id=1905)
    │
    └─ think_act_record (parent_execution_id=1905, id=2368)
            │
            └─ act_tool_info (think_act_record_id=2368, tool_call_id="toolcall-1767061562532_7902_107")
```

## Notes

- **Cascade Save**: When `ThinkActRecordEntity` is saved, `ActToolInfoEntity` objects in `actToolInfoList` are automatically saved due to `@OneToMany(cascade = CascadeType.ALL)`
- **Two-Step Write**: Tool info is written twice:
  1. First: `recordThinkingAndAction()` writes name and parameters (result is null)
  2. Second: `recordActionResult()` updates result after tool execution completes
- **Eager Fetch**: `findByParentExecutionIdWithActToolInfo()` uses `LEFT JOIN FETCH` to avoid N+1 query problem
- **VO Conversion**: Database entities (PO) are converted to Value Objects (VO) for API response, separating persistence layer from API layer












