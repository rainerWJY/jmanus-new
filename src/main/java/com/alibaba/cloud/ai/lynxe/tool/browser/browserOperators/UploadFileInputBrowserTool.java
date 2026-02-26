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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.alibaba.cloud.ai.lynxe.tool.browser.service.BrowserUseCommonService;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.TextFileService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Upload file via standard file input browser tool. Sets file(s) on an
 * {@code <input type="file">} using a regular file path (root plan directory). Agent can
 * then call click-browser to submit (e.g. "Upload with Standard Input").
 */
public class UploadFileInputBrowserTool extends AbstractBrowserTool<UploadFileInputBrowserTool.UploadFileInputInput> {

	private static final Logger log = LoggerFactory.getLogger(UploadFileInputBrowserTool.class);

	private static final String TOOL_NAME = "upload-file-via-input";

	private final ToolI18nService toolI18nService;

	private final TextFileService textFileService;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	/**
	 * Input class for upload-file-via-input operations (regular file path).
	 */
	public static class UploadFileInputInput {

		private Integer index;

		private String filePath;

		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

	}

	public UploadFileInputBrowserTool(BrowserUseCommonService browserUseTool, TextFileService textFileService,
			UnifiedDirectoryManager unifiedDirectoryManager, ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.textFileService = textFileService;
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(UploadFileInputInput input) {
		log.info("UploadFileInputBrowserTool request: index={}, filePath={}", input.getIndex(), input.getFilePath());
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			Integer index = input.getIndex();
			String filePath = input.getFilePath();

			if (index == null || filePath == null || filePath.trim().isEmpty()) {
				return new ToolExecuteResult("Error: index and filePath parameters are required");
			}

			return executeActionWithRetry(() -> {
				Path resolvedPath;
				try {
					resolvedPath = validateAndResolveRegularPath(filePath);
				}
				catch (IOException e) {
					log.error("Path resolution error: {}", e.getMessage(), e);
					return new ToolExecuteResult("Path resolution failed: " + e.getMessage());
				}
				if (!Files.exists(resolvedPath)) {
					return new ToolExecuteResult("Error: File does not exist: " + filePath);
				}
				if (Files.isDirectory(resolvedPath)) {
					return new ToolExecuteResult("Error: Path is a directory, not a file: " + filePath);
				}

				Locator locator = getLocatorByIdx(index);
				if (locator == null) {
					return new ToolExecuteResult("Failed to create locator for element with index " + index);
				}

				Integer timeoutMs = getElementTimeoutMs();
				locator.setInputFiles(resolvedPath, new Locator.SetInputFilesOptions().setTimeout(timeoutMs));

				log.info("Successfully uploaded file input at index {} with file: {}", index, resolvedPath);
				return new ToolExecuteResult("Successfully set file input at index " + index + " with file: " + filePath
						+ ". You can now click the submit button (e.g. Upload with Standard Input).");
			}, "upload_file_input");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing upload_file_input: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser upload_file_input timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing upload_file_input: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser upload_file_input failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing upload_file_input: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser upload_file_input failed: " + e.getMessage());
		}
	}

	/**
	 * Validate and resolve file path within root plan (and optionally subplan) directory,
	 * same pattern as ReadFileOperator.validateGlobalPath. Does not restrict to text file
	 * types (any file can be uploaded).
	 */
	private Path validateAndResolveRegularPath(String filePath) throws IOException {
		String rootPlanId = getRootPlanId();
		if (rootPlanId == null || rootPlanId.isEmpty()) {
			throw new IOException("Error: rootPlanId is required for file operations but is null or empty");
		}

		String normalizedPath = normalizeFilePath(filePath);
		if (normalizedPath.isEmpty()) {
			throw new IOException("Error: filePath cannot be empty after normalization");
		}

		Path rootPlanDirectory = textFileService.getRootPlanDirectory(rootPlanId);
		Path rootPlanPath = unifiedDirectoryManager.resolveAndValidatePath(rootPlanDirectory, normalizedPath);

		if (Files.exists(rootPlanPath)) {
			return rootPlanPath;
		}

		String currentPlanId = getCurrentPlanId();
		if (currentPlanId != null && !currentPlanId.isEmpty() && !currentPlanId.equals(rootPlanId)) {
			Path subplanDirectory = rootPlanDirectory.resolve(currentPlanId);
			Path subplanPath = subplanDirectory.resolve(normalizedPath).normalize();
			if (subplanPath.startsWith(subplanDirectory) && Files.exists(subplanPath)) {
				return subplanPath;
			}
		}

		return rootPlanPath;
	}

	private String normalizeFilePath(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return filePath;
		}
		String normalized = filePath.trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		if (normalized.startsWith("./")) {
			normalized = normalized.substring(2);
		}
		if (normalized.matches("^plan-[^/]+/.*")) {
			normalized = normalized.replaceFirst("^plan-[^/]+/", "");
		}
		return normalized;
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription(TOOL_NAME);
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters(TOOL_NAME);
	}

	@Override
	public Class<UploadFileInputInput> getInputType() {
		return UploadFileInputInput.class;
	}

	@Override
	public String getServiceGroup() {
		return "bw";
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		String stateString = browserUseTool.getCurrentToolStateString(getCurrentPlanId(), getRootPlanId());
		return new ToolStateInfo("bw", stateString);
	}

}
