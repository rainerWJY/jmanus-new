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
 * Update row in table tool that updates an existing row in a table based on ID.
 * Supports Excel (.xlsx, .xls) and CSV (.csv) formats.
 * The row data must start with a valid ID that exists in the table.
 */
public class UpdateRowInTableTool extends AbstractBaseTool<UpdateRowInTableTool.UpdateRowInput> {

	private static final Logger log = LoggerFactory.getLogger(UpdateRowInTableTool.class);

	private static final String TOOL_NAME = "update-row";

	/**
	 * Input class for update row operations
	 */
	public static class UpdateRowInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		private List<String> rowData;

		// Getters and setters
		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public List<String> getRowData() {
			return rowData;
		}

		public void setRowData(List<String> rowData) {
			this.rowData = rowData;
		}

	}

	private final ITableProcessingService tableProcessingService;

	private final ToolI18nService toolI18nService;

	public UpdateRowInTableTool(ITableProcessingService tableProcessingService, ToolI18nService toolI18nService) {
		this.tableProcessingService = tableProcessingService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(UpdateRowInput input) {
		log.info("UpdateRowInTableTool input: file_path={}", input.getFilePath());
		try {
			String planId = this.currentPlanId;
			String filePath = input.getFilePath();
			List<String> rowData = input.getRowData();

			// Basic parameter validation
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}
			if (rowData == null || rowData.isEmpty()) {
				return new ToolExecuteResult("Error: row_data parameter is required and must not be empty");
			}

			return updateRowInTable(planId, filePath, rowData);
		}
		catch (Exception e) {
			log.error("UpdateRowInTableTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	private ToolExecuteResult updateRowInTable(String planId, String filePath, List<String> rowData) {
		try {
			// writeDataToTable handles ID-based updates: if data starts with a valid ID that exists,
			// it will update that row
			tableProcessingService.writeDataToTable(planId, filePath, rowData);
			tableProcessingService.updateFileState(planId, filePath, "Success: Row updated in table");
			return new ToolExecuteResult("Row updated successfully in table: " + filePath);
		}
		catch (IOException e) {
			tableProcessingService.updateFileState(planId, filePath, "Error: " + e.getMessage());
			return new ToolExecuteResult("Failed to update row in table: " + e.getMessage());
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
		return toolI18nService.getDescription("update-row");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("update-row");
	}

	@Override
	public Class<UpdateRowInput> getInputType() {
		return UpdateRowInput.class;
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

