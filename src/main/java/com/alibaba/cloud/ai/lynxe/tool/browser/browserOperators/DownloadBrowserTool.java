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
package com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.BrowserRequestVO;
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.DownloadFileAction;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Download browser tool that clicks download link and saves file to specified directory.
 */
public class DownloadBrowserTool extends AbstractBrowserTool<DownloadBrowserTool.DownloadInput> {

	private static final Logger log = LoggerFactory.getLogger(DownloadBrowserTool.class);

	private static final String TOOL_NAME = "download-browser";

	private final ToolI18nService toolI18nService;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	/**
	 * Input class for download operations
	 */
	public static class DownloadInput {

		private Integer index;

		// Getters and setters
		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

	}

	public DownloadBrowserTool(BrowserUseTool browserUseTool, UnifiedDirectoryManager unifiedDirectoryManager,
			ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.toolI18nService = toolI18nService;
	}

	@Override
	protected BrowserRequestVO toBrowserRequestVO(DownloadInput input) {
		BrowserRequestVO request = new BrowserRequestVO();
		request.setAction("download");
		request.setIndex(input.getIndex());
		return request;
	}

	@Override
	public ToolExecuteResult run(DownloadInput input) {
		log.info("DownloadBrowserTool request: index={}", input.getIndex());
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			if (input.getIndex() == null) {
				return new ToolExecuteResult("Error: index parameter is required");
			}

			// Get download directory for current plan
			Path downloadDir = unifiedDirectoryManager.getRootPlanDirectory(rootPlanId).resolve("downloads");
			try {
				unifiedDirectoryManager.ensureDirectoryExists(downloadDir);
			}
			catch (java.io.IOException e) {
				log.error("Failed to create download directory: {}", e.getMessage());
				return new ToolExecuteResult("Failed to create download directory: " + e.getMessage());
			}

			return executeActionWithRetry(
					() -> new DownloadFileAction(browserUseTool, downloadDir).execute(toBrowserRequestVO(input)),
					"download");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing download: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser download timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing download: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser download failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing download: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser download failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("download-browser");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("download-browser");
	}

	@Override
	public Class<DownloadInput> getInputType() {
		return DownloadInput.class;
	}

	@Override
	public String getServiceGroup() {
		return "browser-service-group";
	}

	@Override
	public String getCurrentToolStateString() {
		return "";
	}

}
