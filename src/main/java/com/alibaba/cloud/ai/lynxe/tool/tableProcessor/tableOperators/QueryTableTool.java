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
 * Query table tool that searches for rows matching keywords in a table.
 * Supports Excel (.xlsx, .xls) and CSV (.csv) formats.
 * Returns all rows that contain any of the specified keywords in any cell.
 */
public class QueryTableTool extends AbstractBaseTool<QueryTableTool.QueryTableInput> {

	private static final Logger log = LoggerFactory.getLogger(QueryTableTool.class);

	private static final String TOOL_NAME = "query-table";

	/**
	 * Input class for query table operations
	 */
	public static class QueryTableInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		private List<String> keywords;

		// Getters and setters
		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public List<String> getKeywords() {
			return keywords;
		}

		public void setKeywords(List<String> keywords) {
			this.keywords = keywords;
		}

	}

	private final ITableProcessingService tableProcessingService;

	private final ToolI18nService toolI18nService;

	public QueryTableTool(ITableProcessingService tableProcessingService, ToolI18nService toolI18nService) {
		this.tableProcessingService = tableProcessingService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(QueryTableInput input) {
		log.info("QueryTableTool input: file_path={}", input.getFilePath());
		try {
			String planId = this.currentPlanId;
			String filePath = input.getFilePath();
			List<String> keywords = input.getKeywords();

			// Basic parameter validation
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}
			if (keywords == null || keywords.isEmpty()) {
				return new ToolExecuteResult("Error: keywords parameter is required and must not be empty");
			}

			return queryTable(planId, filePath, keywords);
		}
		catch (Exception e) {
			log.error("QueryTableTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	private ToolExecuteResult queryTable(String planId, String filePath, List<String> keywords) {
		try {
			List<List<String>> matchingRows = tableProcessingService.searchRows(planId, filePath, keywords);
			tableProcessingService.updateFileState(planId, filePath, "Success: Rows queried");
			if (matchingRows.isEmpty()) {
				return new ToolExecuteResult("No matching rows found");
			}
			else {
				StringBuilder result = new StringBuilder("Found matching rows:\n");
				for (int i = 0; i < matchingRows.size(); i++) {
					result.append(String.format("Row %d: %s\n", i + 1, matchingRows.get(i).toString()));
				}
				return new ToolExecuteResult(result.toString());
			}
		}
		catch (IOException e) {
			tableProcessingService.updateFileState(planId, filePath, "Error: " + e.getMessage());
			return new ToolExecuteResult("Failed to query table: " + e.getMessage());
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
		return toolI18nService.getDescription("query-table");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("query-table");
	}

	@Override
	public Class<QueryTableInput> getInputType() {
		return QueryTableInput.class;
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

