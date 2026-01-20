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
package com.alibaba.cloud.ai.lynxe.tool.mapreduce.parallelOperators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.lynxe.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.AsyncToolCallBiFunctionDef;
import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.FunctionRegistryService;
import com.alibaba.cloud.ai.lynxe.tool.mapreduce.ParallelExecutionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Start async execution tool that executes all registered functions asynchronously
 * without waiting for results (fire-and-forget pattern). This tool immediately returns
 * success status while execution continues in the background.
 */
public class StartAsyncExecutionTool extends AbstractBaseTool<StartAsyncExecutionTool.StartAsyncInput>
		implements AsyncToolCallBiFunctionDef<StartAsyncExecutionTool.StartAsyncInput> {

	private static final Logger logger = LoggerFactory.getLogger(StartAsyncExecutionTool.class);

	private static final String TOOL_NAME = "start-async-execution";

	/**
	 * Input class for start async execution (no parameters needed)
	 */
	public static class StartAsyncInput {

		// No parameters needed

	}

	private final ObjectMapper objectMapper;

	private final Map<String, ToolCallBackContext> toolCallbackMap;

	private final FunctionRegistryService functionRegistryService;

	private final ParallelExecutionService parallelExecutionService;

	private final ToolI18nService toolI18nService;

	public StartAsyncExecutionTool(ObjectMapper objectMapper, Map<String, ToolCallBackContext> toolCallbackMap,
			FunctionRegistryService functionRegistryService, ParallelExecutionService parallelExecutionService,
			ToolI18nService toolI18nService) {
		this.objectMapper = objectMapper;
		this.toolCallbackMap = toolCallbackMap;
		this.functionRegistryService = functionRegistryService;
		this.parallelExecutionService = parallelExecutionService;
		this.toolI18nService = toolI18nService;
	}

	/**
	 * Set the tool callback map (used to look up actual tool implementations)
	 */
	public void setToolCallbackMap(Map<String, ToolCallBackContext> toolCallbackMap) {
		this.toolCallbackMap.putAll(toolCallbackMap);
	}

	@Override
	public ToolExecuteResult apply(StartAsyncInput input, ToolContext toolContext) {
		return applyAsync(input, toolContext).join();
	}

	@Override
	public CompletableFuture<ToolExecuteResult> applyAsync(StartAsyncInput input, ToolContext parentToolContext) {
		try {
			String planId = this.currentPlanId;
			List<FunctionRegistryService.FunctionRegistry> functionRegistries = functionRegistryService
				.getRegistries(planId);

			if (functionRegistries.isEmpty()) {
				return CompletableFuture.completedFuture(new ToolExecuteResult("No functions registered"));
			}

			// Collect pending functions and create execution requests
			List<FunctionRegistryService.FunctionRegistry> pendingFunctions = new ArrayList<>();
			List<ParallelExecutionService.ParallelExecutionRequest> executions = new ArrayList<>();

			for (FunctionRegistryService.FunctionRegistry function : functionRegistries) {
				if (function.getResult() != null) {
					continue; // Skip already executed functions
				}

				String toolName = function.getToolName();
				ToolCallBackContext toolContext = parallelExecutionService.lookupToolContext(toolName, toolCallbackMap);

				if (toolContext == null) {
					logger.warn("Tool not found in callback map: {}", toolName);
					function.setResult(new ToolExecuteResult("Tool not found: " + toolName));
					continue;
				}

				pendingFunctions.add(function);
				executions.add(new ParallelExecutionService.ParallelExecutionRequest(toolName, function.getInput()));
			}

			if (executions.isEmpty()) {
				return CompletableFuture.completedFuture(new ToolExecuteResult("No pending functions to execute"));
			}

			// Start async execution in background without waiting for results
			// Use thenApply to handle results in background without blocking
			parallelExecutionService.executeToolsInParallel(executions, toolCallbackMap, parentToolContext)
				.thenApply(results -> {
					// Map results back to FunctionRegistry objects in background
					for (int i = 0; i < pendingFunctions.size() && i < results.size(); i++) {
						FunctionRegistryService.FunctionRegistry function = pendingFunctions.get(i);
						Map<String, Object> result = results.get(i);

						String status = (String) result.get("status");
						if ("SUCCESS".equals(status)) {
							Object outputObj = result.get("output");
							String output = outputObj != null ? outputObj.toString() : "No output";
							if (output != null) {
								output = output.replace("\\\"", "\"").replace("\\\\", "\\");
							}
							function.setResult(new ToolExecuteResult(output));
						}
						else {
							Object errorObj = result.get("error");
							String error = errorObj != null ? errorObj.toString() : "Unknown error";
							function.setResult(new ToolExecuteResult("Error: " + error));
						}
					}
					logger.info("Async execution completed for {} functions", executions.size());
					return results;
				})
				.exceptionally(ex -> {
					logger.error("Error in async execution: {}", ex.getMessage(), ex);
					// Set error results for all pending functions
					for (FunctionRegistryService.FunctionRegistry function : pendingFunctions) {
						if (function.getResult() == null) {
							function.setResult(new ToolExecuteResult("Error: " + ex.getMessage()));
						}
					}
					return null;
				});

			// Immediately return success without waiting for execution to complete
			Map<String, Object> result = new HashMap<>();
			result.put("message", "Started async execution of " + executions.size() + " functions");
			result.put("count", executions.size());
			result.put("status", "STARTED");
			try {
				return CompletableFuture
					.completedFuture(new ToolExecuteResult(objectMapper.writeValueAsString(result)));
			}
			catch (JsonProcessingException e) {
				logger.error("Error serializing result: {}", e.getMessage(), e);
				return CompletableFuture.completedFuture(
						new ToolExecuteResult("Started async execution of " + executions.size() + " functions"));
			}
		}
		catch (Exception e) {
			logger.error("Error starting async execution: {}", e.getMessage(), e);
			return CompletableFuture
				.completedFuture(new ToolExecuteResult("Error starting async execution: " + e.getMessage()));
		}
	}

	@Override
	public ToolExecuteResult run(StartAsyncInput input) {
		throw new UnsupportedOperationException(
				"StartAsyncExecutionTool must be called using apply() method with ToolContext, not run()");
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		return new ToolStateInfo(null, "");
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("start-async-execution");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("start-async-execution");
	}

	@Override
	public Class<StartAsyncInput> getInputType() {
		return StartAsyncInput.class;
	}

	@Override
	public void cleanup(String planId) {
		// Cleanup is handled by FunctionRegistryService
	}

	@Override
	public String getServiceGroup() {
		return "parallel";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
