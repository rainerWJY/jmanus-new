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
package com.alibaba.cloud.ai.lynxe.tool.message;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;
import com.alibaba.cloud.ai.lynxe.llm.LlmService;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.AsyncToolCallBiFunctionDef;
import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;

/**
 * Tool that sends an assistant message to the user and adds it to conversation memory.
 * Only supported in plan execution (executeByToolName) flow. The message is persisted via
 * LlmService and returned in the tool result so the frontend can display it in the
 * assistant bubble when polling plan details.
 */
public class SendAssistantMessageTool extends AbstractBaseTool<SendAssistantMessageInput>
		implements AsyncToolCallBiFunctionDef<SendAssistantMessageInput> {

	private static final Logger log = LoggerFactory.getLogger(SendAssistantMessageTool.class);

	public static final String NAME = "send-assistant-message";

	private final ToolI18nService toolI18nService;

	private final LlmService llmService;

	private final LynxeProperties lynxeProperties;

	public SendAssistantMessageTool(ToolI18nService toolI18nService, LlmService llmService,
			LynxeProperties lynxeProperties) {
		this.toolI18nService = toolI18nService;
		this.llmService = llmService;
		this.lynxeProperties = lynxeProperties;
	}

	@Override
	public CompletableFuture<ToolExecuteResult> applyAsync(SendAssistantMessageInput input, ToolContext toolContext) {
		String conversationId = null;
		if (toolContext != null && toolContext.getContext() != null) {
			Object cid = toolContext.getContext().get("conversationId");
			if (cid instanceof String) {
				conversationId = (String) cid;
			}
		}
		if (conversationId == null || conversationId.trim().isEmpty()) {
			log.warn("SendAssistantMessageTool: conversationId not available in ToolContext, skipping memory save");
			String msg = input != null && input.getMessage() != null ? input.getMessage() : "";
			return CompletableFuture.completedFuture(new ToolExecuteResult(msg));
		}
		String messageText = input != null && input.getMessage() != null ? input.getMessage() : "";
		try {
			Integer maxMemory = lynxeProperties != null ? lynxeProperties.getMaxMemory() : null;
			if (maxMemory == null) {
				maxMemory = 1000;
			}
			AssistantMessage assistantMessage = new AssistantMessage(messageText);
			llmService.addToConversationMemoryWithLimit(maxMemory, conversationId, assistantMessage);
			log.debug("SendAssistantMessageTool: added assistant message to conversation memory for conversationId: {}",
					conversationId);
		}
		catch (Exception e) {
			log.warn("SendAssistantMessageTool: failed to add to conversation memory: {}", e.getMessage());
		}
		return CompletableFuture.completedFuture(new ToolExecuteResult(messageText));
	}

	@Override
	public ToolExecuteResult run(SendAssistantMessageInput input) {
		String msg = input != null && input.getMessage() != null ? input.getMessage() : "";
		return new ToolExecuteResult(msg);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("send-assistant-message");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("send-assistant-message");
	}

	@Override
	public Class<SendAssistantMessageInput> getInputType() {
		return SendAssistantMessageInput.class;
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
		String stateString = String.format("""
				SendAssistantMessage Tool Status:
				- Tool Name: %s
				- Plan ID: %s
				- Status: Active
				""", NAME, currentPlanId != null ? currentPlanId : "N/A");
		return new ToolStateInfo(null, stateString);
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
