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
package com.alibaba.cloud.ai.lynxe.tool.textOperator.fileOperators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.TextFileService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;

/**
 * Split file tool that splits text files (markdown, code, HTML, etc.) into smaller pieces.
 * Splits files by lines to ensure content completeness and adds index numbers to split file names.
 */
public class SplitFileTool extends AbstractBaseTool<SplitFileTool.SplitFileInput> {

	private static final Logger log = LoggerFactory.getLogger(SplitFileTool.class);

	private static final String TOOL_NAME = "split-file";

	/**
	 * Number of pieces to split file into
	 */
	private static final int SPLIT_COUNT = 10;

	/**
	 * Input class for split file operations
	 */
	public static class SplitFileInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		private String header;

		// Getters and setters
		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public String getHeader() {
			return header;
		}

		public void setHeader(String header) {
			this.header = header;
		}

	}

	private final TextFileService textFileService;

	private final ToolI18nService toolI18nService;

	public SplitFileTool(TextFileService textFileService, ToolI18nService toolI18nService) {
		this.textFileService = textFileService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(SplitFileInput input) {
		log.info("SplitFileTool input: filePath={}", input.getFilePath());
		try {
			String filePath = input.getFilePath();
			String header = input.getHeader();

			// Basic parameter validation
			if (filePath == null || filePath.trim().isEmpty()) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}

			return splitFile(filePath, header);
		}
		catch (Exception e) {
			log.error("SplitFileTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Validate and get the absolute path for the file. Files are read from rootPlanId/
	 * directory, same as GlobalFileOperator and MarkdownConverterTool.
	 */
	private Path validateFilePath(String filePath) throws IOException {
		if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
			throw new IOException("Error: rootPlanId is required for file splitter operations but is null or empty");
		}

		// Check file type
		if (!isSupportedFileType(filePath)) {
			throw new IOException("Unsupported file type. Only text-based files are supported.");
		}

		// Normalize the file path (remove leading slashes, similar to GlobalFileOperator)
		String normalizedPath = normalizeFilePath(filePath);

		// Get the root plan directory
		Path rootPlanDirectory = textFileService.getRootPlanDirectory(this.rootPlanId);

		// Resolve file path within the root plan directory
		Path absolutePath = rootPlanDirectory.resolve(normalizedPath).normalize();

		// Ensure the path stays within the root plan directory
		if (!absolutePath.startsWith(rootPlanDirectory)) {
			throw new IOException("Access denied: File path must be within the root plan directory");
		}

		if (!Files.exists(absolutePath)) {
			throw new IOException("File does not exist: " + filePath
					+ ". Please ensure the file exists in the root plan directory (rootPlanId/).");
		}

		if (!Files.isRegularFile(absolutePath)) {
			throw new IOException("Path is not a regular file: " + filePath);
		}

		return absolutePath;
	}

	/**
	 * Normalize file path by removing leading slashes (similar to GlobalFileOperator)
	 */
	private String normalizeFilePath(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return filePath;
		}

		// Remove leading slashes
		String normalized = filePath.trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}

		// Remove "shared/" prefix if present
		if (normalized.startsWith("shared/")) {
			normalized = normalized.substring("shared/".length());
		}

		// Remove any remaining "shared/" in the path
		normalized = normalized.replaceAll("^shared/", "");

		return normalized;
	}

	/**
	 * Check if file type is supported
	 */
	private boolean isSupportedFileType(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return false;
		}

		String extension = getFileExtension(filePath);
		return UnifiedDirectoryManager.SUPPORTED_TEXT_FILE_EXTENSIONS.contains(extension.toLowerCase());
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
	 * Split file into multiple pieces
	 */
	private ToolExecuteResult splitFile(String filePath, String header) {
		try {
			Path sourceFile = validateFilePath(filePath);

			// Read all lines from the source file
			List<String> allLines = Files.readAllLines(sourceFile);
			if (allLines.isEmpty()) {
				return new ToolExecuteResult("Error: File is empty, cannot split");
			}

			int totalLines = allLines.size();
			int linesPerPiece = totalLines / SPLIT_COUNT;
			int remainder = totalLines % SPLIT_COUNT;

			// Prepare header (add newline if not empty)
			String headerContent = (header != null && !header.trim().isEmpty()) ? header.trim() + "\n" : "";

			// Get file name and extension
			String fileName = sourceFile.getFileName().toString();
			int lastDotIndex = fileName.lastIndexOf('.');
			String baseName = (lastDotIndex > 0) ? fileName.substring(0, lastDotIndex) : fileName;
			String extension = (lastDotIndex > 0) ? fileName.substring(lastDotIndex) : "";

			// Get parent directory for output files
			Path parentDir = sourceFile.getParent();

			List<String> createdFiles = new ArrayList<>();
			int currentLineIndex = 0;

			// Split into SPLIT_COUNT pieces
			for (int i = 0; i < SPLIT_COUNT; i++) {
				// Calculate lines for this piece (distribute remainder evenly)
				int pieceSize = linesPerPiece + (i < remainder ? 1 : 0);

				if (pieceSize == 0) {
					// Skip empty pieces if file is too small
					continue;
				}

				// Create output file name with index prefix
				String outputFileName = String.format("%d-%s%s", i, baseName, extension);
				Path outputFile = parentDir.resolve(outputFileName);

				// Prepare content for this piece
				StringBuilder content = new StringBuilder();
				if (!headerContent.isEmpty()) {
					content.append(headerContent);
				}

				// Add lines for this piece
				for (int j = 0; j < pieceSize && currentLineIndex < totalLines; j++) {
					content.append(allLines.get(currentLineIndex));
					if (currentLineIndex < totalLines - 1 || j < pieceSize - 1) {
						content.append("\n");
					}
					currentLineIndex++;
				}

				// Write the split file
				Files.writeString(outputFile, content.toString());

				createdFiles.add(outputFileName);
				log.info("Created split file {} with {} lines", outputFileName, pieceSize);
			}

			// Build result message
			StringBuilder result = new StringBuilder();
			result.append(String.format("Successfully split file '%s' into %d pieces:\n", fileName, createdFiles.size()));
			result.append("=".repeat(60)).append("\n");
			for (String createdFile : createdFiles) {
				result.append(String.format("  - %s\n", createdFile));
			}
			result.append(String.format("\nTotal lines in original file: %d\n", totalLines));
			result.append(String.format("Lines per piece: approximately %d\n", linesPerPiece));
			if (!headerContent.isEmpty()) {
				result.append("Header added to each split file\n");
			}

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error splitting file: {}", filePath, e);
			return new ToolExecuteResult("Error splitting file: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error splitting file: {}", filePath, e);
			return new ToolExecuteResult("Unexpected error splitting file: " + e.getMessage());
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
		return toolI18nService.getDescription("split-file");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("split-file");
	}

	@Override
	public Class<SplitFileInput> getInputType() {
		return SplitFileInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up split file resources for plan: {}", planId);
		}
	}

	@Override
	public String getServiceGroup() {
		return "parallel-execution";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}

