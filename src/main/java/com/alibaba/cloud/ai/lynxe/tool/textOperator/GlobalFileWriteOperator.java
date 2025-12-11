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
package com.alibaba.cloud.ai.lynxe.tool.textOperator;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.alibaba.cloud.ai.lynxe.tool.innerStorage.SmartContentSavingService;
import com.alibaba.cloud.ai.lynxe.tool.shortUrl.ShortUrlService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Global file write operator that performs write operations on files. This operator provides access
 * to files that can be accessed across all sub-plans within the same execution context.
 *
 * Keywords: global files, root directory, root folder, root plan directory, global file
 * write operations, root file access, cross-plan files.
 *
 * Use this tool for write operations on global files, root directory files, or root folder
 * files.
 */
public class GlobalFileWriteOperator extends AbstractBaseTool<GlobalFileWriteOperator.WriteFileInput> {

	private static final Logger log = LoggerFactory.getLogger(GlobalFileWriteOperator.class);

	private static final String TOOL_NAME = "write_file_operator";

	/**
	 * Set of supported text file extensions
	 */
	private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Set.of(".txt", ".md", ".markdown", // Plain
																												// text
																												// and
																												// Markdown
			".java", ".py", ".js", ".ts", ".jsx", ".tsx", // Common programming languages
			".html", ".htm", ".mhtml", ".css", ".scss", ".sass", ".less", // Web-related
			".xml", ".json", ".yaml", ".yml", ".properties", // Configuration files
			".sql", ".sh", ".bat", ".cmd", // Scripts and database
			".log", ".conf", ".ini", // Logs and configuration
			".gradle", ".pom", ".mvn", // Build tools
			".csv", ".rst", ".adoc", // Documentation and data
			".cpp", ".c", ".h", ".go", ".rs", ".php", ".rb", ".swift", ".kt", ".scala" // Additional
																						// programming
																						// languages
	));

	/**
	 * Input class for write file operations
	 */
	public static class WriteFileInput {

		private String action;

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		private String content;

		@com.fasterxml.jackson.annotation.JsonProperty("source_text")
		private String sourceText;

		@com.fasterxml.jackson.annotation.JsonProperty("target_text")
		private String targetText;


		// Getters and setters
		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public String getSourceText() {
			return sourceText;
		}

		public void setSourceText(String sourceText) {
			this.sourceText = sourceText;
		}

		public String getTargetText() {
			return targetText;
		}

		public void setTargetText(String targetText) {
			this.targetText = targetText;
		}

	}

	private final TextFileService textFileService;

	private final SmartContentSavingService innerStorageService;

	private final ObjectMapper objectMapper;

	private final ShortUrlService shortUrlService;

	private final ToolI18nService toolI18nService;

	public GlobalFileWriteOperator(TextFileService textFileService, SmartContentSavingService innerStorageService,
			ObjectMapper objectMapper, ShortUrlService shortUrlService, ToolI18nService toolI18nService) {
		this.textFileService = textFileService;
		this.innerStorageService = innerStorageService;
		this.objectMapper = objectMapper;
		this.shortUrlService = shortUrlService;
		this.toolI18nService = toolI18nService;
	}

	public ToolExecuteResult run(String toolInput) {
		log.info("WriteOperator toolInput: {}", toolInput);
		try {
			Map<String, Object> toolInputMap = objectMapper.readValue(toolInput,
					new TypeReference<Map<String, Object>>() {
					});

			String action = (String) toolInputMap.get("action");
			String filePath = (String) toolInputMap.get("file_path");

			// Basic parameter validation
			if (action == null) {
				return new ToolExecuteResult("Error: action parameter is required");
			}
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}

			return switch (action) {
				case "replace" -> {
					String sourceText = (String) toolInputMap.get("source_text");
					String targetText = (String) toolInputMap.get("target_text");

					if (sourceText == null || targetText == null) {
						yield new ToolExecuteResult(
								"Error: replace operation requires source_text and target_text parameters");
					}

					// Replace short URLs in sourceText and targetText
					sourceText = replaceShortUrls(sourceText);
					targetText = replaceShortUrls(targetText);

					yield replaceText(filePath, sourceText, targetText);
				}
				case "append" -> {
					String appendContent = (String) toolInputMap.get("content");

					if (appendContent == null) {
						yield new ToolExecuteResult("Error: append operation requires content parameter");
					}

					// Replace short URLs in appendContent
					appendContent = replaceShortUrls(appendContent);

					yield appendToFile(filePath, appendContent);
				}
				case "delete" -> deleteFile(filePath);
				default -> new ToolExecuteResult("Unknown operation: " + action
						+ ". Supported operations: replace, append, delete");
			};
		}
		catch (Exception e) {
			log.error("WriteOperator execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	@Override
	public ToolExecuteResult run(WriteFileInput input) {
		log.info("WriteOperator input: action={}, filePath={}", input.getAction(), input.getFilePath());
		try {
			String action = input.getAction();
			String filePath = input.getFilePath();

			// Basic parameter validation
			if (action == null) {
				return new ToolExecuteResult("Error: action parameter is required");
			}
			if (filePath == null) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}

			// Replace short URLs in filePath
			filePath = replaceShortUrls(filePath);

			return switch (action) {
				case "replace" -> {
					String sourceText = input.getSourceText();
					String targetText = input.getTargetText();

					if (sourceText == null || targetText == null) {
						yield new ToolExecuteResult(
								"Error: replace operation requires source_text and target_text parameters");
					}

					// Replace short URLs in sourceText and targetText
					sourceText = replaceShortUrls(sourceText);
					targetText = replaceShortUrls(targetText);

					yield replaceText(filePath, sourceText, targetText);
				}
				case "append" -> {
					String appendContent = input.getContent();

					if (appendContent == null) {
						yield new ToolExecuteResult("Error: append operation requires content parameter");
					}

					// Replace short URLs in appendContent
					appendContent = replaceShortUrls(appendContent);

					yield appendToFile(filePath, appendContent);
				}
				case "delete" -> deleteFile(filePath);
				default -> new ToolExecuteResult("Unknown operation: " + action
						+ ". Supported operations: replace, append, delete");
			};
		}
		catch (Exception e) {
			log.error("WriteOperator execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Replace short URLs in a string with real URLs
	 * @param text The text that may contain short URLs
	 * @return The text with short URLs replaced by real URLs
	 */
	private String replaceShortUrls(String text) {
		if (text == null || text.isEmpty() || this.rootPlanId == null || this.rootPlanId.isEmpty()
				|| this.shortUrlService == null) {
			return text;
		}

		// Check if short URL feature is enabled
		Boolean enableShortUrl = textFileService.getLynxeProperties().getEnableShortUrl();
		if (enableShortUrl == null || !enableShortUrl) {
			return text; // Skip replacement if disabled
		}

		// Pattern to match short URLs: http://s@Url.a/ followed by digits
		Pattern shortUrlPattern = Pattern.compile(Pattern.quote(ShortUrlService.SHORT_URL_PREFIX) + "\\d+");
		Matcher matcher = shortUrlPattern.matcher(text);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String shortUrl = matcher.group();
			String realUrl = shortUrlService.getRealUrl(this.rootPlanId, shortUrl);
			if (realUrl != null) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(realUrl));
				log.debug("Replaced short URL {} with real URL {}", shortUrl, realUrl);
			}
			else {
				log.warn("Short URL not found in mapping: {}", shortUrl);
				// Keep the short URL if mapping not found
				matcher.appendReplacement(result, Matcher.quoteReplacement(shortUrl));
			}
		}
		matcher.appendTail(result);

		return result.toString();
	}

	/**
	 * Normalize file path by removing plan ID prefixes and relative path indicators
	 */
	private String normalizeFilePath(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return filePath;
		}

		// Remove leading slashes and relative path indicators
		String normalized = filePath.trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}

		// Remove "./" prefix if present
		if (normalized.startsWith("./")) {
			normalized = normalized.substring(2);
		}

		// Remove plan ID prefix (e.g., "plan-1763035234741/")
		if (normalized.matches("^plan-[^/]+/.*")) {
			normalized = normalized.replaceFirst("^plan-[^/]+/", "");
		}

		return normalized;
	}

	/**
	 * Validate and get the absolute path within the plan/subplan directory
	 */
	private Path validateGlobalPath(String filePath) throws IOException {
		if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
			throw new IOException("Error: rootPlanId is required for global file operations but is null or empty");
		}

		// Normalize the file path to remove plan ID prefixes
		String normalizedPath = normalizeFilePath(filePath);

		// Check file type for non-directory operations
		if (!normalizedPath.isEmpty() && !normalizedPath.endsWith("/") && !isSupportedFileType(normalizedPath)) {
			throw new IOException("Unsupported file type. Only text-based files are supported.");
		}

		// Get root plan directory
		Path rootPlanDirectory = textFileService.getRootPlanDirectory(this.rootPlanId);

		// For WriteOperator, check root plan directory first, then subplan directory
		// if applicable
		// This allows accessing files in root plan directory even when in subplan context
		Path rootPlanPath = rootPlanDirectory.resolve(normalizedPath).normalize();

		// Ensure root plan path stays within root plan directory
		if (!rootPlanPath.startsWith(rootPlanDirectory)) {
			throw new IOException("Access denied: Invalid file path");
		}

		// If file exists in root plan directory, use it
		if (Files.exists(rootPlanPath)) {
			return rootPlanPath;
		}

		// If currentPlanId exists and differs from rootPlanId, check subplan directory
		if (this.currentPlanId != null && !this.currentPlanId.isEmpty()
				&& !this.currentPlanId.equals(this.rootPlanId)) {
			Path subplanDirectory = rootPlanDirectory.resolve(this.currentPlanId);
			Path subplanPath = subplanDirectory.resolve(normalizedPath).normalize();

			// Ensure subplan path stays within subplan directory
			if (!subplanPath.startsWith(subplanDirectory)) {
				throw new IOException("Access denied: Invalid file path");
			}

			// If file exists in subplan directory, use it
			if (Files.exists(subplanPath)) {
				return subplanPath;
			}
		}

		// If file doesn't exist in either location, return root plan path for creation
		// (new files are created in root plan directory)
		return rootPlanPath;
	}

	/**
	 * Check if file type is supported
	 */
	private boolean isSupportedFileType(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return false;
		}

		String extension = getFileExtension(filePath);
		return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
	}

	/**
	 * Get file extension
	 */
	private String getFileExtension(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return "";
		}

		int lastDotIndex = filePath.lastIndexOf('.');
		if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
			return "";
		}

		return filePath.substring(lastDotIndex);
	}

	/**
	 * Delete a file
	 */
	private ToolExecuteResult deleteFile(String filePath) {
		try {
			Path absolutePath = validateGlobalPath(filePath);

			if (!Files.exists(absolutePath)) {
				return new ToolExecuteResult("Error: File does not exist: " + filePath);
			}

			Files.delete(absolutePath);

			log.info("Deleted file: {}", absolutePath);
			return new ToolExecuteResult("File deleted successfully: " + filePath);
		}
		catch (IOException e) {
			log.error("Error deleting file: {}", filePath, e);
			return new ToolExecuteResult("Error deleting file: " + e.getMessage());
		}
	}


	/**
	 * Replace text in file
	 */
	private ToolExecuteResult replaceText(String filePath, String sourceText, String targetText) {
		try {
			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
				log.info("Created new file automatically: {}", absolutePath);
			}

			String content = Files.readString(absolutePath);

			// Check if sourceText exists in the file content
			if (!content.contains(sourceText)) {
				log.warn("Source text not found in file: {}", absolutePath);
				return new ToolExecuteResult("Error: Text to be replaced was not found in file: " + filePath);
			}

			// Perform replacement
			String newContent = content.replace(sourceText, targetText);
			Files.writeString(absolutePath, newContent);

			// Force flush to disk
			try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.WRITE)) {
				channel.force(true);
			}

			log.info("Text replaced in file: {}", absolutePath);
			return new ToolExecuteResult("Replacement successful in file: " + filePath);
		}
		catch (IOException e) {
			log.error("Error replacing text in file: {}", filePath, e);
			return new ToolExecuteResult("Error replacing text in file: " + e.getMessage());
		}
	}


	/**
	 * Append content to file
	 */
	private ToolExecuteResult appendToFile(String filePath, String content) {
		try {
			if (content == null || content.isEmpty()) {
				return new ToolExecuteResult("Error: No content to append");
			}

			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
			}

			Files.writeString(absolutePath, "\n" + content, StandardOpenOption.APPEND, StandardOpenOption.CREATE);

			// Force flush to disk
			try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.WRITE)) {
				channel.force(true);
			}

			log.info("Content appended to file: {}", absolutePath);
			return new ToolExecuteResult("Content appended successfully to file: " + filePath);
		}
		catch (IOException e) {
			log.error("Error appending to file: {}", filePath, e);
			return new ToolExecuteResult("Error appending to file: " + e.getMessage());
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
		return toolI18nService.getDescription("write-file-operator");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("write-file-operator");
	}

	@Override
	public Class<WriteFileInput> getInputType() {
		return WriteFileInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up file resources for plan: {}", planId);
			// Cleanup if needed - the TextFileService handles the main cleanup
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
