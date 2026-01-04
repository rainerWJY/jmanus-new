# Execution Details Call Tree

This document describes the call trees for execution details endpoints.

## GET /api/executor/details/{planId}

### Call Tree

```
GET /api/executor/details/{planId}
  │
  └─ [Backend] LynxeController::getExecutionDetails(planId)
      ├─ Check exceptionCache for planId
      │   └─ If exception found: throw PlanException
      │
      ├─ PlanHierarchyReaderService::readPlanTreeByRootId(planId)
      │   ├─ PlanExecutionRecordRepository::findByCurrentPlanId(planId)
      │   ├─ PlanExecutionRecordRepository::findByRootPlanId(planId)
      │   ├─ Convert PlanExecutionRecordEntity to PlanExecutionRecord (VO)
      │   ├─ Convert AgentExecutionRecordEntity to AgentExecutionRecord (simplified, no ThinkActRecord details)
      │   └─ buildHierarchyRelationships(planRecords)
      │       └─ For each plan's agent execution sequence:
      │           └─ findSubPlansForAgent(agentRecord, planMap)
      │               ├─ Query ActToolInfo by toolCallId
      │               ├─ Query ThinkActRecord by ActToolInfo relationship
      │               └─ Match parentExecutionId with AgentExecutionRecord.id
      │
      ├─ UserInputService::getWaitState(rootPlanId)
      │   └─ Get FormInputTool from formInputToolMap
      │       └─ createUserInputWaitState(planId, title, formInputTool)
      │
      ├─ extractLastToolCallResult(planRecord) [if planRecord is completed]
      │   ├─ Get last AgentExecutionRecord from agentExecutionSequence
      │   ├─ Get stepId from last AgentExecutionRecord
      │   ├─ NewRepoPlanExecutionRecorder::getAgentExecutionDetail(stepId)
      │   │   └─ (See GET /api/executor/agent-execution/{stepId} call tree below)
      │   ├─ Get last ThinkActRecord from thinkActSteps
      │   ├─ Get last ActToolInfo from actToolInfoList
      │   └─ Extract result from last ActToolInfo
      │
      └─ ObjectMapper::writeValueAsString(planRecord)
      └─ Return JSON response
```

### Data Objects

#### PlanExecutionRecord (VO)
- **Fields**: id, currentPlanId, rootPlanId, parentPlanId, toolCallId, title, userRequest, startTime, endTime, currentStepIndex, completed, summary, agentExecutionSequence, userInputWaitState, modelName, parentActToolCall, structureResult
- **Update Timing**: 
  - Created when plan execution starts (via `recordPlanExecutionStart`)
  - Updated during plan execution (status, currentStepIndex, summary)
  - Completed when plan execution finishes (completed=true, endTime set)
- **Update Data**: 
  - Basic info set at creation time
  - Execution data updated during agent execution
  - Result data set when plan completes

#### AgentExecutionRecord (Simplified in PlanExecutionRecord)
- **Fields**: id, stepId, conversationId, agentName, agentDescription, startTime, endTime, maxSteps, currentStep, status, agentRequest, result, errorMessage, modelName
- **Note**: In PlanExecutionRecord, this is simplified version without ThinkActRecord details
- **Update Timing**: 
  - Created when agent execution starts
  - Updated during agent execution (currentStep, status)
  - Completed when agent execution finishes
- **Update Data**: 
  - Basic info set at creation
  - Execution status updated during think-act cycles
  - Result set when agent completes

#### UserInputWaitState
- **Fields**: planId, title, waiting, formInputs
- **Update Timing**: 
  - Created when FormInputTool is stored and waiting for user input
  - Retrieved when checking plan execution details
  - Cleared when user submits input or timeout occurs
- **Update Data**: 
  - Set when form input tool is created
  - Retrieved from formInputToolMap by rootPlanId

## GET /api/executor/agent-execution/{stepId}

### Call Tree

```
GET /api/executor/agent-execution/{stepId}
  │
  └─ [Backend] LynxeController::getAgentExecutionDetail(stepId)
      └─ NewRepoPlanExecutionRecorder::getAgentExecutionDetail(stepId)
          ├─ Validate stepId (not null/empty)
          │
          ├─ AgentExecutionRecordRepository::findByStepId(stepId)
          │   └─ Query database: SELECT * FROM agent_execution_record WHERE step_id = ?
          │
          ├─ Convert AgentExecutionRecordEntity to AgentExecutionRecord (VO)
          │   ├─ Set basic properties: id, stepId, agentName, agentDescription
          │   ├─ Set execution properties: startTime, endTime, status
          │   ├─ Set request/result: agentRequest, result, errorMessage
          │   └─ Set model: modelName
          │
          └─ fetchThinkActRecords(agentRecord.getId())
              ├─ ThinkActRecordRepository::findByParentExecutionIdWithActToolInfo(agentExecutionId)
              │   └─ Query database with JOIN FETCH:
              │       SELECT t FROM ThinkActRecordEntity t 
              │       LEFT JOIN FETCH t.actToolInfoList 
              │       WHERE t.parentExecutionId = ?
              │
              └─ Convert ThinkActRecordEntity to ThinkActRecord (VO)
                  ├─ Set basic properties: id, parentExecutionId
                  ├─ Set think phase: thinkInput, thinkOutput, errorMessage
                  ├─ Set character counts: inputCharCount, outputCharCount
                  │
                  └─ Convert ActToolInfoEntity to ActToolInfo (for each tool)
                      ├─ Create ActToolInfo(name, parameters, toolCallId)
                      └─ Set result from ActToolInfoEntity
                  │
                  └─ Set actToolInfoList and actionNeeded=true (if tools exist)
          │
          └─ Set thinkActSteps to AgentExecutionRecord
      │
      └─ Return ResponseEntity<AgentExecutionRecord>
```

### Data Objects

#### AgentExecutionRecord (Detailed)
- **Fields**: id, stepId, conversationId, agentName, agentDescription, startTime, endTime, maxSteps, currentStep, status, agentRequest, result, errorMessage, modelName, **thinkActSteps** (with full details)
- **Update Timing**: 
  - Created when agent execution starts (via `createOrUpdateAgentExecutionRecord`)
  - Updated during agent execution (currentStep, status)
  - Completed when agent execution finishes (status=FINISHED, endTime set)
- **Update Data**: 
  - Basic info set at creation
  - Execution status updated during think-act cycles
  - Result set when agent completes
  - **thinkActSteps populated when fetching detail** (not in simplified version)

#### ThinkActRecord
- **Fields**: id, parentExecutionId, thinkStartTime, thinkEndTime, actStartTime, actEndTime, thinkInput, thinkOutput, actionNeeded, actionDescription, actionResult, status, errorMessage, inputCharCount, outputCharCount, **actToolInfoList**
- **Update Timing**: 
  - Created when agent starts a think-act cycle (via `recordThinkingAndAction`)
  - Updated during think phase (thinkInput, thinkOutput, thinkEndTime)
  - Updated during act phase (actStartTime, actionDescription, actionResult, actEndTime)
  - Completed when think-act cycle finishes
- **Update Data**: 
  - Think phase data set during thinking
  - Act phase data set during action execution
  - Tool info added when tools are called
  - Character counts tracked during LLM calls

#### ActToolInfo
- **Fields**: name, parameters, result, id (toolCallId)
- **Update Timing**: 
  - Created when tool is called during act phase
  - Result updated when tool execution completes
- **Update Data**: 
  - Name and parameters set when tool is called
  - Result set when tool execution finishes
  - ToolCallId used to link with sub-plans

### Key Differences

- **GET /api/executor/details/{planId}**: Returns PlanExecutionRecord with simplified AgentExecutionRecord (no ThinkActRecord details)
- **GET /api/executor/agent-execution/{stepId}**: Returns detailed AgentExecutionRecord with full ThinkActRecord details including actToolInfoList

### Database Queries

1. **AgentExecutionRecordRepository::findByStepId(stepId)**
   - Query: `SELECT * FROM agent_execution_record WHERE step_id = ?`

2. **ThinkActRecordRepository::findByParentExecutionIdWithActToolInfo(agentExecutionId)**
   - Query: `SELECT t FROM ThinkActRecordEntity t LEFT JOIN FETCH t.actToolInfoList WHERE t.parentExecutionId = ?`
   - Uses JOIN FETCH to eagerly load actToolInfoList to avoid N+1 query problem

### Key Components

- **Controller**: `LynxeController.java` - REST endpoint handler
- **Service**: `NewRepoPlanExecutionRecorder.java` - Agent execution detail retrieval logic
- **Repository**: 
  - `AgentExecutionRecordRepository.java` - Agent execution record queries
  - `ThinkActRecordRepository.java` - Think-act record queries with eager fetching
- **VO Objects**: 
  - `AgentExecutionRecord.java` - Detailed agent execution record
  - `ThinkActRecord.java` - Think-act cycle record
  - `ActToolInfo.java` - Tool call information
- **Entity Objects**: 
  - `AgentExecutionRecordEntity.java` - Database entity
  - `ThinkActRecordEntity.java` - Database entity
  - `ActToolInfoEntity.java` - Database entity

