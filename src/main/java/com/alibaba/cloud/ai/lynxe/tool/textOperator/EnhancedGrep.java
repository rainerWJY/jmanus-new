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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Grep Tool - Powerful text search tool based on ripgrep (rg) for precise text/regex matching
 *
 * This tool provides powerful search capabilities similar to ripgrep, supporting:
 * - Full regular expression syntax (e.g., "log.*Error", "function\\s+\\w+")
 * - Multiple output modes: content (default), files_with_matches, count
 * - File filtering with glob patterns (e.g., "*.js", "*.{ts,tsx}") or type parameter
 * - Case-insensitive search option (-i flag)
 * - Context lines display (-A: after, -B: before, -C: around matches)
 * - Multiline matching support (. matches newlines)
 * - Result limiting (head_limit parameter)
 *
 * Usage Scenarios:
 * - Use Grep for: Precise text search, regex matching, known symbol/variable lookup
 * - Don't use Grep for: Semantic search (use SemanticSearch), file name search (use Glob), 
 *   reading known files (use Read)
 *
 * Output Formats:
 * - content mode: Shows matching lines with ':' separator, context lines with '-' separator
 * - files_with_matches mode: Only shows file paths containing matches
 * - count mode: Shows match counts per file (e.g., "file.java: 5 matches")
 *
 * Note: Literal braces need escaping in patterns (use interface\\{\\} to find interface{} in code)
 *
 * Keywords: grep, search, find text, regex, ripgrep, rg, pattern matching, text search, exact match
 */
public class EnhancedGrep extends AbstractBaseTool<EnhancedGrep.GrepInput> {

	private static final Logger log = LoggerFactory.getLogger(EnhancedGrep.class);

	private static final String TOOL_NAME = "enhanced_grep";

	/**
	 * Maximum number of results to return (to prevent overwhelming output)
	 */
	private static final int DEFAULT_MAX_RESULTS = 1000;

	/**
	 * Predefined file type mappings (similar to ripgrep)
	 */
	private static final Map<String, List<String>> FILE_TYPE_EXTENSIONS = new HashMap<>();
	static {
		FILE_TYPE_EXTENSIONS.put("java", List.of(".java"));
		FILE_TYPE_EXTENSIONS.put("py", List.of(".py"));
		FILE_TYPE_EXTENSIONS.put("js", List.of(".js", ".jsx"));
		FILE_TYPE_EXTENSIONS.put("ts", List.of(".ts", ".tsx"));
		FILE_TYPE_EXTENSIONS.put("rust", List.of(".rs"));
		FILE_TYPE_EXTENSIONS.put("go", List.of(".go"));
		FILE_TYPE_EXTENSIONS.put("cpp", List.of(".cpp", ".cc", ".cxx", ".c", ".h", ".hpp"));
		FILE_TYPE_EXTENSIONS.put("md", List.of(".md", ".markdown"));
		FILE_TYPE_EXTENSIONS.put("json", List.of(".json"));
		FILE_TYPE_EXTENSIONS.put("xml", List.of(".xml"));
		FILE_TYPE_EXTENSIONS.put("yaml", List.of(".yaml", ".yml"));
		FILE_TYPE_EXTENSIONS.put("sql", List.of(".sql"));
		FILE_TYPE_EXTENSIONS.put("sh", List.of(".sh", ".bash"));
		FILE_TYPE_EXTENSIONS.put("css", List.of(".css", ".scss", ".sass", ".less"));
		FILE_TYPE_EXTENSIONS.put("html", List.of(".html", ".htm"));
	}

	/**
	 * Output mode enumeration
	 */
	public enum OutputMode {
		CONTENT, // Show matching lines with content
		FILES_WITH_MATCHES, // Only show file paths that contain matches
		COUNT // Show match counts per file
	}

	/**
	 * Input class for grep operations
	 */
	public static class GrepInput {

		@JsonProperty("pattern")
		private String pattern;

		@JsonProperty("path")
		private String path;

		@JsonProperty("glob")
		private String glob;

		@JsonProperty("type")
		private String type;

		@JsonProperty("-i")
		private Boolean caseInsensitive;

		@JsonProperty("output_mode")
		private String outputMode;

		@JsonProperty("-B")
		private Integer contextBefore;

		@JsonProperty("-A")
		private Integer contextAfter;

		@JsonProperty("-C")
		private Integer context;

		@JsonProperty("multiline")
		private Boolean multiline;

		@JsonProperty("head_limit")
		private Integer headLimit;

		// Getters and setters
		public String getPattern() {
			return pattern;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getGlob() {
			return glob;
		}

		public void setGlob(String glob) {
			this.glob = glob;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public Boolean getCaseInsensitive() {
			return caseInsensitive;
		}

		public void setCaseInsensitive(Boolean caseInsensitive) {
			this.caseInsensitive = caseInsensitive;
		}

		public String getOutputMode() {
			return outputMode;
		}

		public void setOutputMode(String outputMode) {
			this.outputMode = outputMode;
		}

		public Integer getContextBefore() {
			return contextBefore;
		}

		public void setContextBefore(Integer contextBefore) {
			this.contextBefore = contextBefore;
		}

		public Integer getContextAfter() {
			return contextAfter;
		}

		public void setContextAfter(Integer contextAfter) {
			this.contextAfter = contextAfter;
		}

		public Integer getContext() {
			return context;
		}

		public void setContext(Integer context) {
			this.context = context;
		}

		public Boolean getMultiline() {
			return multiline;
		}

		public void setMultiline(Boolean multiline) {
			this.multiline = multiline;
		}

		public Integer getHeadLimit() {
			return headLimit;
		}

		public void setHeadLimit(Integer headLimit) {
			this.headLimit = headLimit;
		}

	}

	/**
	 * Match result for a single line
	 */
	private static class MatchResult {

		String filePath;

		int lineNumber;

		String lineContent;

		boolean isMatchLine; // true for match, false for context

		public MatchResult(String filePath, int lineNumber, String lineContent, boolean isMatchLine) {
			this.filePath = filePath;
			this.lineNumber = lineNumber;
			this.lineContent = lineContent;
			this.isMatchLine = isMatchLine;
		}

	}

	private final TextFileService textFileService;

	private final ObjectMapper objectMapper;

	private final ToolI18nService toolI18nService;

	public EnhancedGrep(TextFileService textFileService, ObjectMapper objectMapper, ToolI18nService toolI18nService) {
		this.textFileService = textFileService;
		this.objectMapper = objectMapper;
		this.toolI18nService = toolI18nService;
	}

	public ToolExecuteResult run(String toolInput) {
		log.info("EnhancedGrep toolInput: {}", toolInput);
		try {
			Map<String, Object> toolInputMap = objectMapper.readValue(toolInput,
					new TypeReference<Map<String, Object>>() {
					});

			// Extract parameters
			String pattern = (String) toolInputMap.get("pattern");
			if (pattern == null || pattern.isEmpty()) {
				return new ToolExecuteResult("Error: pattern parameter is required");
			}

			String path = (String) toolInputMap.get("path");
			String glob = (String) toolInputMap.get("glob");
			String type = (String) toolInputMap.get("type");
			Boolean caseInsensitive = (Boolean) toolInputMap.get("-i");
			String outputMode = (String) toolInputMap.get("output_mode");
			Integer contextBefore = (Integer) toolInputMap.get("-B");
			Integer contextAfter = (Integer) toolInputMap.get("-A");
			Integer context = (Integer) toolInputMap.get("-C");
			Boolean multiline = (Boolean) toolInputMap.get("multiline");
			Integer headLimit = (Integer) toolInputMap.get("head_limit");

			return executeGrep(pattern, path, glob, type, caseInsensitive != null && caseInsensitive,
					outputMode != null ? outputMode : "content", contextBefore, contextAfter, context,
					multiline != null && multiline, headLimit);
		}
		catch (Exception e) {
			log.error("EnhancedGrep execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	@Override
	public ToolExecuteResult run(GrepInput input) {
		log.info("EnhancedGrep input: pattern={}, path={}", input.getPattern(), input.getPath());
		try {
			if (input.getPattern() == null || input.getPattern().isEmpty()) {
				return new ToolExecuteResult("Error: pattern parameter is required");
			}

			return executeGrep(input.getPattern(), input.getPath(), input.getGlob(), input.getType(),
					input.getCaseInsensitive() != null && input.getCaseInsensitive(),
					input.getOutputMode() != null ? input.getOutputMode() : "content", input.getContextBefore(),
					input.getContextAfter(), input.getContext(), input.getMultiline() != null && input.getMultiline(),
					input.getHeadLimit());
		}
		catch (Exception e) {
			log.error("EnhancedGrep execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Execute grep search with all parameters
	 */
	private ToolExecuteResult executeGrep(String pattern, String path, String glob, String type,
			boolean caseInsensitive, String outputMode, Integer contextBefore, Integer contextAfter, Integer context,
			boolean multiline, Integer headLimit) {
		try {
			// Validate and get search root path
			Path searchRoot = getSearchRoot(path);

			// Determine context lines
			int beforeLines = contextBefore != null ? contextBefore : (context != null ? context : 0);
			int afterLines = contextAfter != null ? contextAfter : (context != null ? context : 0);

			// Parse output mode
			OutputMode mode = parseOutputMode(outputMode);

			// Determine result limit
			int maxResults = headLimit != null ? headLimit : DEFAULT_MAX_RESULTS;

			// Compile regex pattern
			Pattern regexPattern = compilePattern(pattern, caseInsensitive, multiline);

			// Search files
			List<Path> filesToSearch = findFilesToSearch(searchRoot, glob, type);

			if (filesToSearch.isEmpty()) {
				return new ToolExecuteResult("No files found matching the criteria");
			}

			// Execute search based on mode
			return switch (mode) {
				case CONTENT -> searchContent(filesToSearch, regexPattern, beforeLines, afterLines, maxResults,
						multiline);
				case FILES_WITH_MATCHES -> searchFilesOnly(filesToSearch, regexPattern, maxResults, multiline);
				case COUNT -> searchCount(filesToSearch, regexPattern, maxResults, multiline);
			};
		}
		catch (Exception e) {
			log.error("Error executing grep", e);
			return new ToolExecuteResult("Error executing grep: " + e.getMessage());
		}
	}

	/**
	 * Get search root path
	 */
	private Path getSearchRoot(String path) throws IOException {
		if (path == null || path.isEmpty()) {
			// Use workspace root if available
			if (this.rootPlanId != null && !this.rootPlanId.isEmpty()) {
				return textFileService.getRootPlanDirectory(this.rootPlanId);
			}
			// Fallback to current directory
			return Paths.get(".");
		}
		return Paths.get(path);
	}

	/**
	 * Compile regex pattern with flags
	 */
	private Pattern compilePattern(String pattern, boolean caseInsensitive, boolean multiline) {
		int flags = 0;
		if (caseInsensitive) {
			flags |= Pattern.CASE_INSENSITIVE;
		}
		if (multiline) {
			flags |= Pattern.MULTILINE | Pattern.DOTALL;
		}
		return Pattern.compile(pattern, flags);
	}

	/**
	 * Parse output mode string
	 */
	private OutputMode parseOutputMode(String mode) {
		if (mode == null) {
			return OutputMode.CONTENT;
		}
		return switch (mode.toLowerCase()) {
			case "files_with_matches" -> OutputMode.FILES_WITH_MATCHES;
			case "count" -> OutputMode.COUNT;
			default -> OutputMode.CONTENT;
		};
	}

	/**
	 * Find files to search based on glob pattern or file type
	 */
	private List<Path> findFilesToSearch(Path root, String glob, String type) throws IOException {
		List<Path> files = new ArrayList<>();

		// Determine file filter
		Set<String> extensions = new HashSet<>();
		if (type != null && FILE_TYPE_EXTENSIONS.containsKey(type.toLowerCase())) {
			extensions.addAll(FILE_TYPE_EXTENSIONS.get(type.toLowerCase()));
		}

		// Convert glob to pattern
		Pattern globPattern = null;
		if (glob != null && !glob.isEmpty()) {
			globPattern = compileGlobPattern(glob);
		}

		Pattern finalGlobPattern = globPattern;

		// Walk directory tree
		try (Stream<Path> paths = Files.walk(root)) {
			paths.filter(Files::isRegularFile).filter(p -> {
				// Skip hidden files and directories
				if (isHidden(p)) {
					return false;
				}

				// Apply type filter
				if (!extensions.isEmpty()) {
					String fileName = p.getFileName().toString();
					return extensions.stream().anyMatch(fileName::endsWith);
				}

				// Apply glob filter
				if (finalGlobPattern != null) {
					return finalGlobPattern.matcher(p.getFileName().toString()).matches();
				}

				// Default: include text files only
				return isTextFile(p);
			}).forEach(files::add);
		}

		return files;
	}

	/**
	 * Check if path is hidden
	 */
	private boolean isHidden(Path path) {
		try {
			return Files.isHidden(path) || path.getFileName().toString().startsWith(".");
		}
		catch (IOException e) {
			return false;
		}
	}

	/**
	 * Check if file is text file (basic heuristic)
	 */
	private boolean isTextFile(Path path) {
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".java")
				|| fileName.endsWith(".py") || fileName.endsWith(".js") || fileName.endsWith(".ts")
				|| fileName.endsWith(".jsx") || fileName.endsWith(".tsx") || fileName.endsWith(".json")
				|| fileName.endsWith(".xml") || fileName.endsWith(".yaml") || fileName.endsWith(".yml")
				|| fileName.endsWith(".properties") || fileName.endsWith(".log") || fileName.endsWith(".conf")
				|| fileName.endsWith(".sh") || fileName.endsWith(".css") || fileName.endsWith(".html");
	}

	/**
	 * Compile glob pattern to regex
	 */
	private Pattern compileGlobPattern(String glob) {
		// Convert glob to regex
		StringBuilder regex = new StringBuilder("^");
		for (char c : glob.toCharArray()) {
			switch (c) {
				case '*':
					regex.append(".*");
					break;
				case '?':
					regex.append(".");
					break;
				case '.':
					regex.append("\\.");
					break;
				default:
					if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
						regex.append(c);
					}
					else {
						regex.append("\\").append(c);
					}
			}
		}
		regex.append("$");
		return Pattern.compile(regex.toString());
	}

	/**
	 * Search and return content with matches
	 */
	private ToolExecuteResult searchContent(List<Path> files, Pattern pattern, int beforeLines, int afterLines,
			int maxResults, boolean multiline) {
		StringBuilder result = new StringBuilder();
		int totalMatches = 0;
		int filesWithMatches = 0;

		for (Path file : files) {
			if (totalMatches >= maxResults) {
				result.append(String.format("\n... (output limited to %d results)\n", maxResults));
				break;
			}

			try {
				List<MatchResult> matches = searchFile(file, pattern, beforeLines, afterLines, multiline);
				if (!matches.isEmpty()) {
					filesWithMatches++;
					result.append(file.toString()).append("\n");

					for (MatchResult match : matches) {
						if (totalMatches >= maxResults)
							break;

						String marker = match.isMatchLine ? ":" : "-";
						result.append(String.format("%d%s%s\n", match.lineNumber, marker, match.lineContent));
						totalMatches++;
					}
					result.append("\n");
				}
			}
			catch (IOException e) {
				log.warn("Error reading file: {}", file, e);
			}
		}

		if (totalMatches == 0) {
			return new ToolExecuteResult("No matches found");
		}

		result.append(String.format("Found %d matches in %d files\n", totalMatches, filesWithMatches));
		return new ToolExecuteResult(result.toString());
	}

	/**
	 * Search single file and return matches with context
	 */
	private List<MatchResult> searchFile(Path file, Pattern pattern, int beforeLines, int afterLines,
			boolean multiline) throws IOException {
		List<MatchResult> results = new ArrayList<>();

		if (multiline) {
			// Read entire file for multiline matching
			String content = Files.readString(file);
			Matcher matcher = pattern.matcher(content);
			if (matcher.find()) {
				// For multiline, return a simple indicator
				results.add(new MatchResult(file.toString(), 1, "(multiline match found)", true));
			}
		}
		else {
			// Line-by-line matching
			List<String> lines = Files.readAllLines(file);
			Set<Integer> printedLines = new HashSet<>();

			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (pattern.matcher(line).find()) {
					// Add context before
					for (int j = Math.max(0, i - beforeLines); j < i; j++) {
						if (!printedLines.contains(j)) {
							results.add(new MatchResult(file.toString(), j + 1, lines.get(j), false));
							printedLines.add(j);
						}
					}

					// Add match line
					if (!printedLines.contains(i)) {
						results.add(new MatchResult(file.toString(), i + 1, line, true));
						printedLines.add(i);
					}

					// Add context after
					for (int j = i + 1; j <= Math.min(lines.size() - 1, i + afterLines); j++) {
						if (!printedLines.contains(j)) {
							results.add(new MatchResult(file.toString(), j + 1, lines.get(j), false));
							printedLines.add(j);
						}
					}
				}
			}
		}

		return results;
	}

	/**
	 * Search and return only file paths with matches
	 */
	private ToolExecuteResult searchFilesOnly(List<Path> files, Pattern pattern, int maxResults, boolean multiline) {
		StringBuilder result = new StringBuilder();
		int count = 0;

		for (Path file : files) {
			if (count >= maxResults) {
				result.append(String.format("... (output limited to %d files)\n", maxResults));
				break;
			}

			try {
				if (fileHasMatch(file, pattern, multiline)) {
					result.append(file.toString()).append("\n");
					count++;
				}
			}
			catch (IOException e) {
				log.warn("Error reading file: {}", file, e);
			}
		}

		if (count == 0) {
			return new ToolExecuteResult("No files with matches found");
		}

		result.append(String.format("\nTotal: %d files\n", count));
		return new ToolExecuteResult(result.toString());
	}

	/**
	 * Check if file has any matches
	 */
	private boolean fileHasMatch(Path file, Pattern pattern, boolean multiline) throws IOException {
		if (multiline) {
			String content = Files.readString(file);
			return pattern.matcher(content).find();
		}
		else {
			try (BufferedReader reader = Files.newBufferedReader(file)) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (pattern.matcher(line).find()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Search and return match counts
	 */
	private ToolExecuteResult searchCount(List<Path> files, Pattern pattern, int maxResults, boolean multiline) {
		StringBuilder result = new StringBuilder();
		int totalMatches = 0;
		int filesProcessed = 0;

		for (Path file : files) {
			if (filesProcessed >= maxResults) {
				result.append(String.format("... (output limited to %d files)\n", maxResults));
				break;
			}

			try {
				int count = countMatches(file, pattern, multiline);
				if (count > 0) {
					result.append(String.format("%s: %d\n", file.toString(), count));
					totalMatches += count;
				}
				filesProcessed++;
			}
			catch (IOException e) {
				log.warn("Error reading file: {}", file, e);
			}
		}

		if (totalMatches == 0) {
			return new ToolExecuteResult("No matches found");
		}

		result.append(String.format("\nTotal: %d matches\n", totalMatches));
		return new ToolExecuteResult(result.toString());
	}

	/**
	 * Count matches in a file
	 */
	private int countMatches(Path file, Pattern pattern, boolean multiline) throws IOException {
		if (multiline) {
			String content = Files.readString(file);
			Matcher matcher = pattern.matcher(content);
			int count = 0;
			while (matcher.find()) {
				count++;
			}
			return count;
		}
		else {
			int count = 0;
			try (BufferedReader reader = Files.newBufferedReader(file)) {
				String line;
				while ((line = reader.readLine()) != null) {
					Matcher matcher = pattern.matcher(line);
					while (matcher.find()) {
						count++;
					}
				}
			}
			return count;
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
		return toolI18nService.getDescription("enhanced-grep");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("enhanced-grep");
	}

	@Override
	public Class<GrepInput> getInputType() {
		return GrepInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up EnhancedGrep resources for plan: {}", planId);
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

