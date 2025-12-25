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
package com.alibaba.cloud.ai.lynxe.tool.tableProcessor.tableOperators;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.alibaba.cloud.ai.lynxe.tool.tableProcessor.ITableProcessingService;

/**
 * Get table structure tool that retrieves table headers (structure information).
 * Supports Excel (.xlsx, .xls) and CSV (.csv) formats.
 */
public class GetTableStructureTool extends AbstractBaseTool<GetTableStructureTool.GetTableStructureInput> {

	private static final Logger log = LoggerFactory.getLogger(GetTableStructureTool.class);

	private static final String TOOL_NAME = "get-table-structure";

	/**
	 * Input class for get table structure operations
	 */
	public static class GetTableStructureInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		// Getters and setters
		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

	}

	private final ITableProcessingService tableProcessingService;

	private final ToolI18nService toolI18nService;

	public GetTableStructureTool(ITableProcessingService tableProcessingService, ToolI18nService toolI18nService) {
		this.tableProcessingService = tableProcessingService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(GetTableStructureInput input) {
		log.info("GetTableStructureTool input: file_path={}", input.getFilePath());
		try {
			String planId = this.currentPlanId;
			String filePath = input.getFilePath();

			// Basic parameter validation
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}

			return getTableStructure(planId, filePath);
		}
		catch (Exception e) {
			log.error("GetTableStructureTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	private ToolExecuteResult getTableStructure(String planId, String filePath) {
		try {
			java.util.List<String> headers = tableProcessingService.getTableStructure(planId, filePath);
			tableProcessingService.updateFileState(planId, filePath, "Success: Retrieved table structure");
			return new ToolExecuteResult("Header information: " + headers.toString());
		}
		catch (IOException e) {
			tableProcessingService.updateFileState(planId, filePath, "Error: " + e.getMessage());
			return new ToolExecuteResult("Failed to get table structure: " + e.getMessage());
		}
	}

	@Override
	public String getCurrentToolStateString() {
		return "";
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("get-table-structure");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("get-table-structure");
	}

	@Override
	public Class<GetTableStructureInput> getInputType() {
		return GetTableStructureInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up table processing resources for plan: {}", planId);
			// Cleanup is handled by TableProcessingService
		}
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}

