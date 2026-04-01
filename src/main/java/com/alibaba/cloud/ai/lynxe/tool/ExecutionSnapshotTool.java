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
package com.alibaba.cloud.ai.lynxe.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tool that holds the current thread mid-execution (like FormInputTool). When invoked,
 * sets state to AWAITING_USER_INPUT and returns stepId/rootPlanId so the frontend can
 * show execution history and support simulated next-step runs. The agent stores the
 * snapshot (agentMessages) in ExecutionSnapshotService before calling this tool.
 */
public class ExecutionSnapshotTool extends AbstractBaseTool<ExecutionSnapshotTool.ExecutionSnapshotInput> {

	private static final Logger log = LoggerFactory.getLogger(ExecutionSnapshotTool.class);

	public static final String name = "pause-and-replay-execution";

	private final ObjectMapper objectMapper;

	private final ToolI18nService toolI18nService;

	public enum InputState {

		AWAITING_USER_INPUT, INPUT_RECEIVED, INPUT_TIMEOUT

	}

	private InputState inputState = InputState.INPUT_RECEIVED;

	/**
	 * Set by agent before run() so result contains the correct stepId for the frontend.
	 */
	private String currentStepId;

	public ExecutionSnapshotTool(ObjectMapper objectMapper, ToolI18nService toolI18nService) {
		this.objectMapper = objectMapper;
		this.toolI18nService = toolI18nService;
	}

	public InputState getInputState() {
		return inputState;
	}

	public void setInputState(InputState inputState) {
		this.inputState = inputState;
	}

	public void setCurrentStepId(String currentStepId) {
		this.currentStepId = currentStepId;
	}

	@Override
	public ToolExecuteResult run(ExecutionSnapshotInput input) {
		log.info(
				"ExecutionSnapshotTool invoked, holding thread for stepId/planId (agent stores snapshot before calling)");
		setInputState(InputState.AWAITING_USER_INPUT);
		// Result carries stepId/rootPlanId for frontend (agent sets currentStepId before
		// run)
		try {
			SnapshotResult result = new SnapshotResult();
			result.setStepId(
					currentStepId != null ? currentStepId : (getCurrentPlanId() != null ? getCurrentPlanId() : ""));
			result.setRootPlanId(getRootPlanId() != null ? getRootPlanId() : "");
			result.setMessage(input != null && input.getMessage() != null ? input.getMessage()
					: "Execution paused for review and optional simulated run.");
			return new ToolExecuteResult(objectMapper.writeValueAsString(result));
		}
		catch (Exception e) {
			log.error("Error building snapshot result", e);
			return new ToolExecuteResult("{\"error\": \"Failed to build snapshot result: " + e.getMessage() + "\"}");
		}
	}

	public void markInputReceived() {
		setInputState(InputState.INPUT_RECEIVED);
	}

	public void handleInputTimeout() {
		log.warn("Execution snapshot input timeout. Resuming without user action.");
		setInputState(InputState.INPUT_TIMEOUT);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("pause-and-replay-execution-tool");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("pause-and-replay-execution-tool");
	}

	@Override
	public Class<ExecutionSnapshotInput> getInputType() {
		return ExecutionSnapshotInput.class;
	}

	@Override
	public boolean isReturnDirect() {
		return true;
	}

	@Override
	public void cleanup(String planId) {
		// no-op
	}

	@Override
	public String getServiceGroup() {
		return "default";
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		String state = String.format("Pause-and-replay-execution tool status: %s", inputState.toString());
		return new ToolStateInfo(null, state);
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	public static class ExecutionSnapshotInput {

		private String message;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

	public static class SnapshotResult {

		private String stepId;

		private String rootPlanId;

		private String message;

		public String getStepId() {
			return stepId;
		}

		public void setStepId(String stepId) {
			this.stepId = stepId;
		}

		public String getRootPlanId() {
			return rootPlanId;
		}

		public void setRootPlanId(String rootPlanId) {
			this.rootPlanId = rootPlanId;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

}
