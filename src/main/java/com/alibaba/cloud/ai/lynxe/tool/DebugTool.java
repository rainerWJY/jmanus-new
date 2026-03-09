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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;

/**
 * Step summary tool: allows the LLM to output a message and return it directly at the end
 * of each step (what was done, next plan, why). Keeps the execution trace clear.
 */
public class DebugTool extends AbstractBaseTool<Map<String, Object>> {

	private static final Logger log = LoggerFactory.getLogger(DebugTool.class);

	public static final String name = "step-summary";

	private final ToolI18nService toolI18nService;

	public DebugTool(ToolI18nService toolI18nService) {
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(Map<String, Object> input) {
		log.info("Step summary tool called with input: {}", input);

		// Extract message from input
		Object messageObj = input != null ? input.get("message") : null;
		String message;

		if (messageObj == null) {
			message = "No message provided";
		}
		else if (messageObj instanceof String) {
			message = (String) messageObj;
		}
		else {
			// Convert to string if not already a string
			message = messageObj.toString();
		}

		log.debug("Step summary message: {}", message);

		return new ToolExecuteResult(message);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("step-summary-tool");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("step-summary-tool");
	}

	@Override
	public Class<Map<String, Object>> getInputType() {
		@SuppressWarnings("unchecked")
		Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class<?>) Map.class;
		return clazz;
	}

	@Override
	public boolean isReturnDirect() {
		return true;
	}

	@Override
	public void cleanup(String planId) {
		// do nothing
	}

	@Override
	public String getServiceGroup() {
		return "default";
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		String stateString = String.format("""
				Step Summary Tool Status:
				- Tool Name: %s
				- Plan ID: %s
				- Status: Active
				""", name, currentPlanId != null ? currentPlanId : "N/A");
		return new ToolStateInfo(null, stateString);
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
