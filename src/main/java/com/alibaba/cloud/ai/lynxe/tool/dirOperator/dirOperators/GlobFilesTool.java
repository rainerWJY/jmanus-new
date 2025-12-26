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
package com.alibaba.cloud.ai.lynxe.tool.dirOperator.dirOperators;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.ToolStateInfo;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.SymbolicLinkDetector;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Glob files tool that finds files matching a glob pattern. Results are sorted by
 * modification time (most recently modified first).
 */
public class GlobFilesTool extends AbstractBaseTool<GlobFilesTool.GlobFilesInput> {

	private static final Logger log = LoggerFactory.getLogger(GlobFilesTool.class);

	private static final String TOOL_NAME = "glob-files";

	/**
	 * Input class for glob files operations
	 */
	public static class GlobFilesInput {

		@JsonProperty("glob_pattern")
		private String globPattern;

		@JsonProperty("target_directory")
		private String targetDirectory;

		// Getters and setters
		public String getGlobPattern() {
			return globPattern;
		}

		public void setGlobPattern(String globPattern) {
			this.globPattern = globPattern;
		}

		public String getTargetDirectory() {
			return targetDirectory;
		}

		public void setTargetDirectory(String targetDirectory) {
			this.targetDirectory = targetDirectory;
		}

	}

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	private final SymbolicLinkDetector symlinkDetector;

	private final ToolI18nService toolI18nService;

	public GlobFilesTool(UnifiedDirectoryManager unifiedDirectoryManager, SymbolicLinkDetector symlinkDetector,
			ToolI18nService toolI18nService) {
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.symlinkDetector = symlinkDetector;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public ToolExecuteResult run(GlobFilesInput input) {
		log.info("GlobFilesTool input: glob_pattern={}, target_directory={}", input.getGlobPattern(),
				input.getTargetDirectory());
		try {
			String globPattern = input.getGlobPattern();
			String targetDirectory = input.getTargetDirectory();

			if (globPattern == null || globPattern.isEmpty()) {
				return new ToolExecuteResult("Error: glob_pattern parameter is required");
			}

			return globFiles(globPattern, targetDirectory);
		}
		catch (Exception e) {
			log.error("GlobFilesTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Normalize directory path by removing plan ID prefixes and relative path indicators
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
	 * Find files matching a glob pattern. This method searches for files matching the
	 * specified glob pattern, with results sorted by modification time (most recently
	 * modified first).
	 */
	private ToolExecuteResult globFiles(String globPattern, String targetDirectory) {
		try {
			if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
				return new ToolExecuteResult("Error: rootPlanId is required for glob operations");
			}

			// Normalize glob pattern: auto-prefix with **/ if not starting with **/
			String normalizedPattern = normalizeGlobPattern(globPattern);

			// Determine search root directory
			Path searchRoot;
			if (targetDirectory != null && !targetDirectory.isEmpty()) {
				// Normalize target directory path
				String normalizedTargetDir = normalizeFilePath(targetDirectory);
				Path rootPlanDirectory = unifiedDirectoryManager.getRootPlanDirectory(this.rootPlanId);

				// Use the centralized method from UnifiedDirectoryManager
				searchRoot = unifiedDirectoryManager.resolveAndValidatePath(rootPlanDirectory, normalizedTargetDir);

				// Check if target directory exists
				if (!Files.exists(searchRoot)) {
					return new ToolExecuteResult("Error: Target directory does not exist: " + normalizedTargetDir);
				}

				if (!Files.isDirectory(searchRoot)) {
					return new ToolExecuteResult("Error: Target path is not a directory: " + normalizedTargetDir);
				}
			}
			else {
				// Default to root plan directory
				searchRoot = unifiedDirectoryManager.getRootPlanDirectory(this.rootPlanId);
			}

			// Create PathMatcher for glob pattern
			FileSystem fileSystem = FileSystems.getDefault();
			PathMatcher matcher = fileSystem.getPathMatcher("glob:" + normalizedPattern);

			// Find all matching files using safe traversal
			List<Path> matchingFiles = new ArrayList<>();
			Files.walkFileTree(searchRoot, symlinkDetector.createSafeFileVisitor(searchRoot, (file, attrs) -> {
				// Check if file matches the pattern
				Path relativePath = searchRoot.relativize(file);
				if (matcher.matches(relativePath)) {
					matchingFiles.add(file);
				}
			}, null // No special directory handling needed
			));

			// Sort by modification time (most recently modified first)
			matchingFiles.sort(Comparator.comparing((Path path) -> {
				try {
					FileTime lastModified = Files.getLastModifiedTime(path);
					return lastModified.toInstant();
				}
				catch (IOException e) {
					log.warn("Error getting modification time for file: {}", path, e);
					return java.time.Instant.EPOCH;
				}
			}).reversed());

			// Build result
			StringBuilder result = new StringBuilder();
			result.append(String.format("Glob results for pattern '%s':\n", globPattern));
			if (targetDirectory != null && !targetDirectory.isEmpty()) {
				result.append(String.format("Search directory: %s\n", normalizeFilePath(targetDirectory)));
			}
			result.append("=".repeat(60)).append("\n");

			if (matchingFiles.isEmpty()) {
				result.append("No files found matching the pattern.\n");
			}
			else {
				result.append(String.format("Found %d file(s):\n\n", matchingFiles.size()));
				for (Path path : matchingFiles) {
					try {
						Path relativePath = searchRoot.relativize(path);
						String relativePathStr = relativePath.toString().replace('\\', '/');
						long size = Files.size(path);
						String sizeStr = formatFileSize(size);
						FileTime lastModified = Files.getLastModifiedTime(path);
						result.append(String.format("%s (%s, modified: %s)\n", relativePathStr, sizeStr,
								lastModified.toString()));
					}
					catch (IOException e) {
						log.warn("Error reading file info: {}", path, e);
						Path relativePath = searchRoot.relativize(path);
						String relativePathStr = relativePath.toString().replace('\\', '/');
						result.append(String.format("%s (error reading file info)\n", relativePathStr));
					}
				}
			}

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error performing glob search: pattern={}, targetDirectory={}", globPattern, targetDirectory, e);
			return new ToolExecuteResult("Error performing glob search: " + e.getMessage());
		}
	}

	/**
	 * Normalize glob pattern by auto-prefixing with recursive pattern if needed. Patterns
	 * are automatically prefixed for recursive search.
	 */
	private String normalizeGlobPattern(String globPattern) {
		if (globPattern == null || globPattern.isEmpty()) {
			return globPattern;
		}

		String trimmed = globPattern.trim();

		// If pattern doesn't start with **/, prepend it for recursive search
		if (!trimmed.startsWith("**/")) {
			// Handle patterns that start with / (absolute-like patterns)
			if (trimmed.startsWith("/")) {
				trimmed = trimmed.substring(1);
			}
			return "**/" + trimmed;
		}

		return trimmed;
	}

	/**
	 * Format file size in human-readable format
	 */
	private String formatFileSize(long size) {
		if (size < 1024)
			return size + " B";
		if (size < 1024 * 1024)
			return String.format("%.1f KB", size / 1024.0);
		if (size < 1024 * 1024 * 1024)
			return String.format("%.1f MB", size / (1024.0 * 1024));
		return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
	}

	@Override
	public ToolStateInfo getCurrentToolStateString() {
		return new ToolStateInfo(null, "");
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("glob-files");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("glob-files");
	}

	@Override
	public Class<GlobFilesInput> getInputType() {
		return GlobFilesInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up glob files resources for plan: {}", planId);
		}
	}

	@Override
	public String getServiceGroup() {
		return "file-service-group";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
