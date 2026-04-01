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
package com.alibaba.cloud.ai.lynxe.tool.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.lynxe.agent.model.Tool;
import com.alibaba.cloud.ai.lynxe.planning.PlanningFactory;
import com.alibaba.cloud.ai.lynxe.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.lynxe.tool.ToolCallBiFunctionDef;

/**
 * Tool Controller - Provides API endpoints for tool management
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

	private static final Logger log = LoggerFactory.getLogger(ToolController.class);

	@Autowired
	private PlanningFactory planningFactory;

	/**
	 * Get all available tools. On success returns 200 with list of tools; on error
	 * returns 500 with JSON body {@code { "error": "...", "message": "..." }} so the
	 * frontend can show the cause.
	 * @return List of available tools, or error map on 500
	 */
	@GetMapping
	public ResponseEntity<?> getAvailableTools() {
		try {
			log.debug("Getting available tools using PlanningFactory");
			Map<String, ToolCallBackContext> toolCallbackContext = planningFactory.toolCallbackMap(
					PlanningFactory.TOOLS_LISTING_CACHE_KEY, PlanningFactory.TOOLS_LISTING_CACHE_KEY, null);

			List<Tool> tools = toolCallbackContext.entrySet().stream().map(entry -> {
				Tool tool = new Tool();
				ToolCallBiFunctionDef<?> functionInstance = entry.getValue().getFunctionInstance();
				String serviceGroup = functionInstance.getServiceGroup();
				String toolName = functionInstance.getName();

				// Construct key in serviceGroup.toolName format for frontend
				// Backend will convert this to serviceGroup-toolName format during
				// execution
				String toolKey;
				if (serviceGroup != null && !serviceGroup.isEmpty()) {
					toolKey = serviceGroup + "-" + toolName;
				}
				else {
					toolKey = toolName;
				}
				tool.setKey(toolKey);
				tool.setName(toolName); // Keep just the tool name for display
				tool.setDescription(functionInstance.getDescriptionWithServiceGroup());
				tool.setEnabled(true);
				tool.setServiceGroup(serviceGroup);
				tool.setSelectable(functionInstance.isSelectable());
				return tool;
			}).collect(Collectors.toList());

			log.info("Retrieved {} available tools", tools.size());
			return ResponseEntity.ok(tools);

		}
		catch (Exception e) {
			log.error("Error getting available tools: {}", e.getMessage(), e);
			String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", message, "message", message));
		}
	}

	/**
	 * Legacy endpoint: tool callback maps are built per request with no server-side
	 * cache. Kept for clients that still call refresh after tool changes.
	 */
	@PostMapping("/refresh-cache")
	public ResponseEntity<Void> refreshToolCache() {
		planningFactory.invalidateToolCallbackMapCache();
		log.debug("Tool refresh-cache invoked (no-op; maps are not cached)");
		return ResponseEntity.ok().build();
	}

}
