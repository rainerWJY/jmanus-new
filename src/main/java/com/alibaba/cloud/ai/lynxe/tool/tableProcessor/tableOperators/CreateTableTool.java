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
 * Create table tool that creates a new table with headers.
 * Supports Excel (.xlsx, .xls) and CSV (.csv) formats.
 */
public class CreateTableTool extends AbstractBaseTool<CreateTableTool.CreateTableInput> {

	private static final Logger log = LoggerFactory.getLogger(CreateTableTool.class);

	private static final String TOOL_NAME = "create-table";

	/**
	 * Input class for create table operations
	 */
	public static class CreateTableInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		@com.fasterxml.jackson.annotation.JsonProperty("sheet_name")
		private String sheetName;

		private List<String> headers;

		// Getters and setters
		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public String getSheetName() {
			return sheetName;
		}

		public void setSheetName(String sheetName) {
			this.sheetName = sheetName;
		}

		public List<String> getHeaders() {
			return headers;
		}

		public void setHeaders(List<String> headers) {
			this.headers = headers;
		}

	}

	private final ITableProcessingService tableProcessingService;

	private final ToolI18nService toolI18nService;

	public CreateTableTool(ITableProcessingService tableProcessingService, ToolI18nService toolI18nService) {
		this.tableProcessingService = tableProcessingService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(CreateTableInput input) {
		log.info("CreateTableTool input: file_path={}", input.getFilePath());
		try {
			String planId = this.currentPlanId;
			String filePath = input.getFilePath();
			String sheetName = input.getSheetName();
			List<String> headers = input.getHeaders();

			// Basic parameter validation
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}
			if (headers == null) {
				return new ToolExecuteResult("Error: headers parameter is required");
			}

			return createTable(planId, filePath, sheetName, headers);
		}
		catch (Exception e) {
			log.error("CreateTableTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	private ToolExecuteResult createTable(String planId, String filePath, String sheetName, List<String> headers) {
		try {
			// Check file type
			if (!tableProcessingService.isSupportedFileType(filePath)) {
				tableProcessingService.updateFileState(planId, filePath, "Error: Unsupported file type");
				return new ToolExecuteResult(
						"Unsupported file type. Only Excel (.xlsx, .xls) and CSV (.csv) files are supported.");
			}

			tableProcessingService.createTable(planId, filePath, sheetName, headers);
			return new ToolExecuteResult("Table created successfully: " + filePath);
		}
		catch (IOException e) {
			tableProcessingService.updateFileState(planId, filePath, "Error: " + e.getMessage());
			return new ToolExecuteResult("Failed to create table: " + e.getMessage());
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
		return toolI18nService.getDescription("create-table");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("create-table");
	}

	@Override
	public Class<CreateTableInput> getInputType() {
		return CreateTableInput.class;
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

