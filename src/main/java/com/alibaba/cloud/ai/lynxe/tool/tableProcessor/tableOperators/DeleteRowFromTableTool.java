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
 * Delete row from table tool that deletes specified rows from a table based on row indices.
 * Supports Excel (.xlsx, .xls) and CSV (.csv) formats.
 * Row indices are 0-based, excluding the header row.
 */
public class DeleteRowFromTableTool extends AbstractBaseTool<DeleteRowFromTableTool.DeleteRowInput> {

	private static final Logger log = LoggerFactory.getLogger(DeleteRowFromTableTool.class);

	private static final String TOOL_NAME = "delete-row";

	/**
	 * Input class for delete row operations
	 */
	public static class DeleteRowInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		@com.fasterxml.jackson.annotation.JsonProperty("row_indices")
		private List<Integer> rowIndices;

		// Getters and setters
		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public List<Integer> getRowIndices() {
			return rowIndices;
		}

		public void setRowIndices(List<Integer> rowIndices) {
			this.rowIndices = rowIndices;
		}

	}

	private final ITableProcessingService tableProcessingService;

	private final ToolI18nService toolI18nService;

	public DeleteRowFromTableTool(ITableProcessingService tableProcessingService, ToolI18nService toolI18nService) {
		this.tableProcessingService = tableProcessingService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(DeleteRowInput input) {
		log.info("DeleteRowFromTableTool input: file_path={}", input.getFilePath());
		try {
			String planId = this.currentPlanId;
			String filePath = input.getFilePath();
			List<Integer> rowIndices = input.getRowIndices();

			// Basic parameter validation
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}
			if (rowIndices == null || rowIndices.isEmpty()) {
				return new ToolExecuteResult("Error: row_indices parameter is required and must not be empty");
			}

			return deleteRowsFromTable(planId, filePath, rowIndices);
		}
		catch (Exception e) {
			log.error("DeleteRowFromTableTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	private ToolExecuteResult deleteRowsFromTable(String planId, String filePath, List<Integer> rowIndices) {
		try {
			tableProcessingService.deleteRowsByList(planId, filePath, rowIndices);
			tableProcessingService.updateFileState(planId, filePath, "Success: Rows deleted");
			return new ToolExecuteResult("Deletion successful, " + rowIndices.size() + " rows deleted");
		}
		catch (IOException e) {
			tableProcessingService.updateFileState(planId, filePath, "Error: " + e.getMessage());
			return new ToolExecuteResult("Failed to delete rows: " + e.getMessage());
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
		return toolI18nService.getDescription("delete-row");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("delete-row");
	}

	@Override
	public Class<DeleteRowInput> getInputType() {
		return DeleteRowInput.class;
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

