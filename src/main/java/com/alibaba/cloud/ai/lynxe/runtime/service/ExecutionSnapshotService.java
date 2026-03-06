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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.lynxe.tool.ExecutionSnapshotTool;

/**
 * Stores execution snapshot (agent messages + context) when ExecutionSnapshotTool holds
 * the thread, and the tool reference for resume. Used by DynamicAgent and the simulate
 * API.
 */
@Service
public class ExecutionSnapshotService {

	private static final Logger log = LoggerFactory.getLogger(ExecutionSnapshotService.class);

	/** stepId -> snapshot data (for simulate API) */
	private final Map<String, SnapshotData> snapshotByStepId = new ConcurrentHashMap<>();

	/** rootPlanId -> tool instance (for resume) */
	private final Map<String, ExecutionSnapshotTool> toolByRootPlanId = new ConcurrentHashMap<>();

	/** rootPlanId -> stepId (for getWaitState so frontend knows which stepId to load) */
	private final Map<String, String> stepIdByRootPlanId = new ConcurrentHashMap<>();

	public static class SnapshotData {

		private String stepId;

		private String rootPlanId;

		private String currentPlanId;

		private List<Message> agentMessages;

		private Message systemMessage;

		private Message currentStepEnvMessage;

		/**
		 * Assistant message that contains the ExecutionSnapshotTool call (for simulate).
		 */
		private Message assistantMessageWithToolCalls;

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

		public String getCurrentPlanId() {
			return currentPlanId;
		}

		public void setCurrentPlanId(String currentPlanId) {
			this.currentPlanId = currentPlanId;
		}

		public List<Message> getAgentMessages() {
			return agentMessages;
		}

		public void setAgentMessages(List<Message> agentMessages) {
			this.agentMessages = agentMessages;
		}

		public Message getSystemMessage() {
			return systemMessage;
		}

		public void setSystemMessage(Message systemMessage) {
			this.systemMessage = systemMessage;
		}

		public Message getCurrentStepEnvMessage() {
			return currentStepEnvMessage;
		}

		public void setCurrentStepEnvMessage(Message currentStepEnvMessage) {
			this.currentStepEnvMessage = currentStepEnvMessage;
		}

		public Message getAssistantMessageWithToolCalls() {
			return assistantMessageWithToolCalls;
		}

		public void setAssistantMessageWithToolCalls(Message assistantMessageWithToolCalls) {
			this.assistantMessageWithToolCalls = assistantMessageWithToolCalls;
		}

	}

	/**
	 * Store snapshot data (called by agent before running ExecutionSnapshotTool) and
	 * register the tool for the given root plan.
	 */
	public void storeSnapshot(String stepId, String rootPlanId, String currentPlanId, List<Message> agentMessages,
			Message systemMessage, Message currentStepEnvMessage, ExecutionSnapshotTool tool) {
		storeSnapshot(stepId, rootPlanId, currentPlanId, agentMessages, systemMessage, currentStepEnvMessage, tool,
				null);
	}

	/**
	 * Store snapshot data with optional assistant message (for simulate to continue after
	 * the snapshot tool call).
	 */
	public void storeSnapshot(String stepId, String rootPlanId, String currentPlanId, List<Message> agentMessages,
			Message systemMessage, Message currentStepEnvMessage, ExecutionSnapshotTool tool,
			Message assistantMessageWithToolCalls) {
		SnapshotData data = new SnapshotData();
		data.setStepId(stepId);
		data.setRootPlanId(rootPlanId);
		data.setCurrentPlanId(currentPlanId);
		data.setAgentMessages(agentMessages);
		data.setSystemMessage(systemMessage);
		data.setCurrentStepEnvMessage(currentStepEnvMessage);
		data.setAssistantMessageWithToolCalls(assistantMessageWithToolCalls);
		snapshotByStepId.put(stepId, data);
		toolByRootPlanId.put(rootPlanId, tool);
		stepIdByRootPlanId.put(rootPlanId, stepId);
		log.info("Stored execution snapshot for stepId={}, rootPlanId={}", stepId, rootPlanId);
	}

	public SnapshotData getSnapshot(String stepId) {
		return snapshotByStepId.get(stepId);
	}

	/**
	 * Return stepId for which the given root plan is waiting (snapshot hold), or null.
	 */
	public String getWaitingStepId(String rootPlanId) {
		return stepIdByRootPlanId.get(rootPlanId);
	}

	/**
	 * Mark snapshot as resumed: mark the tool as INPUT_RECEIVED and clear stored refs so
	 * the waiting agent continues.
	 */
	public boolean markResumed(String rootPlanId) {
		ExecutionSnapshotTool tool = toolByRootPlanId.remove(rootPlanId);
		String stepId = stepIdByRootPlanId.remove(rootPlanId);
		if (stepId != null) {
			snapshotByStepId.remove(stepId);
		}
		if (tool != null) {
			tool.markInputReceived();
			log.info("Marked execution snapshot resumed for rootPlanId={}", rootPlanId);
			return true;
		}
		return false;
	}

	public void clearByStepId(String stepId) {
		SnapshotData data = snapshotByStepId.remove(stepId);
		if (data != null && data.getRootPlanId() != null) {
			toolByRootPlanId.remove(data.getRootPlanId());
			stepIdByRootPlanId.remove(data.getRootPlanId());
		}
	}

}
