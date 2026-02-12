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
import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.BoundingBox;

/**
 * Upload file via chooser browser tool. Triggers the file chooser by clicking an element
 * (e.g. "Select File" or drop zone), then sets the selected file(s). Agent can then call
 * click-browser on the upload button if needed (e.g. "Upload Dragged/Dropped File").
 */
public class UploadFileViaChooserBrowserTool
		extends AbstractBrowserTool<UploadFileViaChooserBrowserTool.UploadFileViaChooserInput> {

	private static final Logger log = LoggerFactory.getLogger(UploadFileViaChooserBrowserTool.class);

	private static final String TOOL_NAME = "upload-file-via-chooser-browser";

	private final ToolI18nService toolI18nService;

	private final TextFileService textFileService;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	/**
	 * Input class for upload-file-via-chooser operations.
	 */
	public static class UploadFileViaChooserInput {

		private Integer triggerIndex;

		private String filePath;

		public Integer getTriggerIndex() {
			return triggerIndex;
		}

		public void setTriggerIndex(Integer triggerIndex) {
			this.triggerIndex = triggerIndex;
		}

		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

	}

	public UploadFileViaChooserBrowserTool(BrowserUseCommonService browserUseTool, TextFileService textFileService,
			UnifiedDirectoryManager unifiedDirectoryManager, ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.textFileService = textFileService;
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(UploadFileViaChooserInput input) {
		log.info("UploadFileViaChooserBrowserTool request: triggerIndex={}, filePath={}", input.getTriggerIndex(),
				input.getFilePath());
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			Integer triggerIndex = input.getTriggerIndex();
			String filePath = input.getFilePath();

			if (triggerIndex == null || filePath == null || filePath.trim().isEmpty()) {
				return new ToolExecuteResult("Error: triggerIndex and filePath parameters are required");
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

				Page page = getCurrentPage();
				if (page == null) {
					return new ToolExecuteResult("No active page available");
				}

				Locator triggerLocator = getLocatorByIdx(triggerIndex);
				if (triggerLocator == null) {
					return new ToolExecuteResult(
							"Failed to create locator for trigger element with index " + triggerIndex);
				}

				Integer timeoutMs = getBrowserTimeoutMs();
				FileChooser fileChooser = page.waitForFileChooser(
						new Page.WaitForFileChooserOptions().setTimeout(timeoutMs),
						() -> clickTriggerWithMouseSimulation(page, triggerLocator, triggerIndex));

				fileChooser.setFiles(resolvedPath, new FileChooser.SetFilesOptions().setTimeout(timeoutMs));

				log.info("Successfully set file via chooser (trigger index {}): {}", triggerIndex, resolvedPath);
				return new ToolExecuteResult("Successfully set file via chooser at trigger index " + triggerIndex
						+ " with file: " + filePath
						+ ". You can now click the upload button (e.g. Upload Dragged/Dropped File) if needed.");
			}, "upload_file_via_chooser");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing upload_file_via_chooser: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser upload_file_via_chooser timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing upload_file_via_chooser: {}", e.getMessage(), e);
			return new ToolExecuteResult(
					"Browser upload_file_via_chooser failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing upload_file_via_chooser: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser upload_file_via_chooser failed: " + e.getMessage());
		}
	}

	/**
	 * Click the trigger element using mouse simulation so the file chooser opens. Same
	 * pattern as ClickBrowserTool.clickWithMouseSimulation.
	 */
	private void clickTriggerWithMouseSimulation(Page page, Locator locator, Integer index) {
		try {
			try {
				locator.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000));
			}
			catch (TimeoutError scrollError) {
				log.warn("Failed to scroll element into view: {}", scrollError.getMessage());
			}
			try {
				locator.waitFor(new Locator.WaitForOptions().setTimeout(3000)
					.setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
			}
			catch (TimeoutError waitError) {
				log.warn("Element may not be visible: {}", waitError.getMessage());
			}

			BoundingBox box = locator.boundingBox(new Locator.BoundingBoxOptions().setTimeout(5000));
			if (box == null) {
				String visibilityInfo = "unknown";
				try {
					visibilityInfo = locator.isVisible() ? "visible but no bounding box" : "not visible";
				}
				catch (Exception e) {
					log.debug("Could not check element visibility: {}", e.getMessage());
				}
				throw new RuntimeException(String.format(
						"Element not found or not visible (index: %d, visibility: %s). Please check the page.", index,
						visibilityInfo));
			}

			double centerX = box.x + box.width / 2.0;
			double centerY = box.y + box.height / 2.0;

			BoundingBox updatedBox = locator.boundingBox(new Locator.BoundingBoxOptions().setTimeout(3000));
			if (updatedBox != null) {
				centerX = updatedBox.x + updatedBox.width / 2.0;
				centerY = updatedBox.y + updatedBox.height / 2.0;
			}

			page.mouse().move(centerX, centerY);
			Thread.sleep(100);
			page.mouse().click(centerX, centerY);
			Thread.sleep(500);
		}
		catch (TimeoutError e) {
			throw new RuntimeException("Timeout getting element bounding box (index: " + index + "). " + e.getMessage(),
					e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted during mouse simulation", e);
		}
	}

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
	public Class<UploadFileViaChooserInput> getInputType() {
		return UploadFileViaChooserInput.class;
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
