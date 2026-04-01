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
package com.alibaba.cloud.ai.lynxe.runtime.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.lynxe.llm.LlmService;
import com.alibaba.cloud.ai.lynxe.planning.PlanningFactory;
import com.alibaba.cloud.ai.lynxe.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.lynxe.runtime.service.ExecutionSnapshotService.SnapshotData;
import com.alibaba.cloud.ai.lynxe.tool.ExecutionSnapshotTool;

/**
 * Runs one LLM round with snapshot messages (and optional modified prompt) without
 * executing tools. Used for "simulated" next-step in the UI.
 */
@Service
public class SimulationService {

	private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

	private final ExecutionSnapshotService executionSnapshotService;

	private final LlmService llmService;

	private final PlanningFactory planningFactory;

	public SimulationService(ExecutionSnapshotService executionSnapshotService, LlmService llmService,
			PlanningFactory planningFactory) {
		this.executionSnapshotService = executionSnapshotService;
		this.llmService = llmService;
		this.planningFactory = planningFactory;
	}

	/**
	 * Result of a simulated next step: think output and tool calls (not executed).
	 */
	public static class SimulateResult {

		private String thinkOutput;

		private List<SimulatedToolCall> toolCalls;

		private boolean simulated = true;

		public String getThinkOutput() {
			return thinkOutput;
		}

		public void setThinkOutput(String thinkOutput) {
			this.thinkOutput = thinkOutput;
		}

		public List<SimulatedToolCall> getToolCalls() {
			return toolCalls;
		}

		public void setToolCalls(List<SimulatedToolCall> toolCalls) {
			this.toolCalls = toolCalls;
		}

		public boolean isSimulated() {
			return simulated;
		}

	}

	public static class SimulatedToolCall {

		private String name;

		private String arguments;

		public SimulatedToolCall(String name, String arguments) {
			this.name = name;
			this.arguments = arguments;
		}

		public String getName() {
			return name;
		}

		public String getArguments() {
			return arguments;
		}

	}

	/**
	 * Run one LLM round with snapshot messages; optionally replace the last user/env
	 * message with modifiedPrompt. Returns think output and tool calls without executing
	 * tools.
	 */
	public SimulateResult simulateNextStep(String stepId, String modifiedPrompt) {
		SnapshotData snapshot = executionSnapshotService.getSnapshot(stepId);
		if (snapshot == null) {
			throw new IllegalArgumentException("No snapshot found for stepId: " + stepId);
		}

		List<Message> messages = new ArrayList<>();
		if (snapshot.getAgentMessages() != null) {
			messages.addAll(snapshot.getAgentMessages());
		}
		if (snapshot.getSystemMessage() != null) {
			messages.add(snapshot.getSystemMessage());
		}
		Message envMessage = snapshot.getCurrentStepEnvMessage();
		if (modifiedPrompt != null && !modifiedPrompt.trim().isEmpty()) {
			envMessage = new UserMessage(modifiedPrompt.trim());
		}
		if (envMessage == null) {
			envMessage = new UserMessage("");
		}
		messages.add(envMessage);

		// If snapshot includes the assistant message (tool call), append it and a
		// synthetic
		// tool result so the LLM continues after the snapshot tool instead of repeating
		// it.
		Message assistantMessage = snapshot.getAssistantMessageWithToolCalls();
		if (assistantMessage instanceof AssistantMessage am && am.getToolCalls() != null
				&& !am.getToolCalls().isEmpty()) {
			messages.add(assistantMessage);
			List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
			for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
				String content = ExecutionSnapshotTool.name.equals(tc.name())
						|| tc.name() != null && tc.name().contains(ExecutionSnapshotTool.name)
								? "Execution paused for review." : "Not executed (simulation).";
				toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), content));
			}
			messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
		}

		Map<String, ToolCallBackContext> contextMap = planningFactory.toolCallbackMap(snapshot.getCurrentPlanId(),
				snapshot.getRootPlanId(), null);
		List<ToolCallback> callbacks = contextMap.values()
			.stream()
			.map(ToolCallBackContext::getToolCallback)
			.collect(Collectors.toList());

		Map<String, Object> toolContextMap = new HashMap<>();
		toolContextMap.put("planDepth", 0);
		ToolCallingChatOptions options = ToolCallingChatOptions.builder()
			.internalToolExecutionEnabled(false)
			.toolContext(toolContextMap)
			.build();
		Prompt prompt = new Prompt(messages, options);

		ChatClient client = llmService.getDefaultDynamicAgentChatClient();
		ChatResponse response = client.prompt(prompt).toolCallbacks(callbacks).call().chatResponse();

		SimulateResult result = new SimulateResult();
		if (response.getResult() != null && response.getResult().getOutput() != null) {
			AssistantMessage output = response.getResult().getOutput();
			result.setThinkOutput(output.getText() != null ? output.getText() : "");
			if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
				List<SimulatedToolCall> simulatedCalls = new ArrayList<>();
				for (AssistantMessage.ToolCall tc : output.getToolCalls()) {
					simulatedCalls.add(new SimulatedToolCall(tc.name(), tc.arguments()));
				}
				result.setToolCalls(simulatedCalls);
			}
			else {
				result.setToolCalls(List.of());
			}
		}
		else {
			result.setToolCalls(List.of());
		}
		log.info("Simulated next step for stepId={}, thinkOutput length={}, toolCalls={}", stepId,
				result.getThinkOutput() != null ? result.getThinkOutput().length() : 0,
				result.getToolCalls() != null ? result.getToolCalls().size() : 0);
		return result;
	}

}
