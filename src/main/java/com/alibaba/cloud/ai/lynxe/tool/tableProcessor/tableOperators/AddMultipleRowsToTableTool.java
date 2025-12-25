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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.alibaba.cloud.ai.lynxe.tool.tableProcessor.ITableProcessingService;

/**
 * Add multiple rows to table tool that adds multiple rows of data to a table.
 * Supports Excel (.xlsx, .xls) and CSV (.csv) formats.
 * Each row data must match the number of headers (excluding ID column).
 */
public class AddMultipleRowsToTableTool
		extends AbstractBaseTool<AddMultipleRowsToTableTool.AddMultipleRowsInput> {

	private static final Logger log = LoggerFactory.getLogger(AddMultipleRowsToTableTool.class);

	private static final String TOOL_NAME = "add-multiple-rows";

	/**
	 * Input class for add multiple rows operations
	 */
	public static class AddMultipleRowsInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		@com.fasterxml.jackson.annotation.JsonProperty("multiple_rows_data")
		private List<List<String>> multipleRowsData;

		// Getters and setters
		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public List<List<String>> getMultipleRowsData() {
			return multipleRowsData;
		}

		public void setMultipleRowsData(List<List<String>> multipleRowsData) {
			this.multipleRowsData = multipleRowsData;
		}

	}

	private final ITableProcessingService tableProcessingService;

	private final ToolI18nService toolI18nService;

	public AddMultipleRowsToTableTool(ITableProcessingService tableProcessingService,
			ToolI18nService toolI18nService) {
		this.tableProcessingService = tableProcessingService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(AddMultipleRowsInput input) {
		log.info("AddMultipleRowsToTableTool input: file_path={}", input.getFilePath());
		try {
			String planId = this.currentPlanId;
			String filePath = input.getFilePath();
			List<List<String>> multipleRowsData = input.getMultipleRowsData();

			// Basic parameter validation
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}
			if (multipleRowsData == null) {
				return new ToolExecuteResult("Error: multiple_rows_data parameter is required");
			}

			return addMultipleRowsToTable(planId, filePath, multipleRowsData);
		}
		catch (Exception e) {
			log.error("AddMultipleRowsToTableTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	private ToolExecuteResult addMultipleRowsToTable(String planId, String filePath,
			List<List<String>> multipleRowsData) {
		try {
			tableProcessingService.writeMultipleRowsToTable(planId, filePath, multipleRowsData);
			tableProcessingService.updateFileState(planId, filePath, "Success: Multiple rows added to table");
			return new ToolExecuteResult("Multiple rows data written successfully to table: " + filePath);
		}
		catch (IOException e) {
			tableProcessingService.updateFileState(planId, filePath, "Error: " + e.getMessage());
			return new ToolExecuteResult("Failed to write multiple rows data: " + e.getMessage());
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
		return toolI18nService.getDescription("add-multiple-rows");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("add-multiple-rows");
	}

	@Override
	public Class<AddMultipleRowsInput> getInputType() {
		return AddMultipleRowsInput.class;
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

