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
package com.alibaba.cloud.ai.lynxe.tool.bash;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.alibaba.cloud.ai.lynxe.tool.innerStorage.SmartContentSavingService;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Bash extends AbstractBaseTool<BashRequestVO> {

	private final ObjectMapper objectMapper;

	private final ToolI18nService toolI18nService;

	private final ShellExecutorService shellExecutorService;

	private static final Logger log = LoggerFactory.getLogger(Bash.class);

	/**
	 * Unified directory manager for directory operations
	 */
	private final UnifiedDirectoryManager unifiedDirectoryManager;

	/**
	 * Configuration properties for bash security settings
	 */
	private final LynxeProperties lynxeProperties;

	/**
	 * Smart content saving service for handling long outputs
	 */
	private final SmartContentSavingService smartContentSavingService;

	// Add operating system information
	private static final String osName = System.getProperty("os.name");

	private final String name = "bash";

	// Track if run method has been called at least once
	private volatile boolean hasRunAtLeastOnce = false;

	private String lastCommand = "";

	private String lastResult = "";

	// Execution log to track full terminal session history
	private final StringBuilder executionLog = new StringBuilder();

	/**
	 * Clean ANSI escape codes and terminal control sequences from output
	 * @param text The text to clean
	 * @return Cleaned text without ANSI codes and control sequences
	 */
	private static String cleanAnsiCodes(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		// Remove ANSI escape sequences: \u001B[ or \033[ followed by parameters and command letter
		// Pattern matches: ESC[ followed by optional parameters (digits, semicolons) and a command letter
		text = text.replaceAll("\u001B\\[[\\d;]*[a-zA-Z]", "");
		text = text.replaceAll("\033\\[[\\d;]*[a-zA-Z]", "");
		// Remove terminal control sequences like [?2004h, [?2004l, [J, [K, [H, etc.
		text = text.replaceAll("\\[\\?[\\d;]*[a-zA-Z]", "");
		text = text.replaceAll("\\[[\\d;]*[HJKl]", "");
		// Remove other common control characters
		text = text.replaceAll("\\[\\d+[;\\d]*[mH]", "");
		// Remove carriage returns that might interfere
		text = text.replace("\r", "");
		// Remove backspace characters
		text = text.replace("\b", "");
		// Remove bell character
		text = text.replace("\u0007", "");
		return text;
	}


	public Bash(UnifiedDirectoryManager unifiedDirectoryManager, ObjectMapper objectMapper,
			ToolI18nService toolI18nService, ShellExecutorService shellExecutorService,
			LynxeProperties lynxeProperties, SmartContentSavingService smartContentSavingService) {
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.objectMapper = objectMapper;
		this.toolI18nService = toolI18nService;
		this.shellExecutorService = shellExecutorService;
		this.lynxeProperties = lynxeProperties;
		this.smartContentSavingService = smartContentSavingService;
	}

	/**
	 * Get the working directory for bash execution Uses rootPlanId directory if
	 * available, otherwise falls back to base working directory
	 * @return Working directory path as string (absolute path for internal use)
	 */
	private String getWorkingDirectory() {
		if (rootPlanId != null && !rootPlanId.trim().isEmpty()) {
			try {
				java.nio.file.Path rootPlanDir = unifiedDirectoryManager.getRootPlanDirectory(rootPlanId);
				return rootPlanDir.toString();
			}
			catch (Exception e) {
				log.warn(
						"Failed to get root plan directory for rootPlanId: {}, falling back to base working directory. Error: {}",
						rootPlanId, e.getMessage());
			}
		}
		// Fallback to base working directory
		return unifiedDirectoryManager.getWorkingDirectoryPath();
	}

	/**
	 * Get the display working directory (relative to task root)
	 * Shows the current directory relative to the task root directory
	 * @return Display working directory path (relative path, e.g., "." or "subdir")
	 */
	private String getDisplayWorkingDirectory() {
		if (rootPlanId == null || rootPlanId.trim().isEmpty()) {
			// No root plan ID, show base working directory name
			String workingDir = getWorkingDirectory();
			if (workingDir != null && !workingDir.isEmpty()) {
				// Extract just the directory name
				int lastSlash = workingDir.lastIndexOf('/');
				if (lastSlash >= 0 && lastSlash < workingDir.length() - 1) {
					return workingDir.substring(lastSlash + 1);
				}
				return workingDir;
			}
			return ".";
		}

		try {
			java.nio.file.Path rootPlanDir = unifiedDirectoryManager.getRootPlanDirectory(rootPlanId);
			java.nio.file.Path workingDirPath = Paths.get(getWorkingDirectory()).normalize();
			
			// If working directory is the root plan directory, show as root
			if (workingDirPath.equals(rootPlanDir)) {
				return "/";
			}
			
			// Get relative path from root plan directory
			if (workingDirPath.startsWith(rootPlanDir)) {
				java.nio.file.Path relativePath = rootPlanDir.relativize(workingDirPath);
				if (relativePath.toString().isEmpty()) {
					return "/";
				}
				return "/" + relativePath.toString().replace("\\", "/");
			}
			
			// If path doesn't start with root plan dir, return just the directory name
			String dirName = workingDirPath.getFileName() != null 
					? workingDirPath.getFileName().toString() 
					: workingDirPath.toString();
			return dirName;
		}
		catch (Exception e) {
			log.debug("Failed to get display working directory: {}", e.getMessage());
			// Fallback: return just the directory name
			String workingDir = getWorkingDirectory();
			if (workingDir != null && !workingDir.isEmpty()) {
				int lastSlash = workingDir.lastIndexOf('/');
				if (lastSlash >= 0 && lastSlash < workingDir.length() - 1) {
					return workingDir.substring(lastSlash + 1);
				}
			}
			return ".";
		}
	}

	/**
	 * Validate that all absolute paths in the command are within the allowed working
	 * directory Similar to UnifiedDirectoryManager.isPathAllowed() and
	 * GlobalFileReadOperator.validateGlobalPath()
	 * @param command The command to validate
	 * @param workingDir The allowed working directory
	 * @return Error message if validation fails, null if validation passes
	 */
	private String validateCommandPaths(String command, String workingDir) {
		if (command == null || command.trim().isEmpty() || workingDir == null) {
			return null; // Skip validation if command or workingDir is null
		}

		try {
			Path workingDirPath = Paths.get(workingDir).toAbsolutePath().normalize();

			// Pattern to match absolute paths in the command
			// Matches paths like: /tmp/file, /usr/bin, /home/user/file.txt, etc.
			// Also matches paths in quotes: "/tmp/file", '/tmp/file'
			Pattern absolutePathPattern = Pattern.compile(
					// Match absolute paths (starting with / on Unix, or C:\ on Windows)
					"(?:^|\\s|['\"]|>|>>|<|\\|)" + // Start of line, whitespace, quotes,
													// or redirection
							"([/\\\\]|[A-Za-z]:[/\\\\])" + // Absolute path start (/ or
															// C:\)
							"([^\\s'\"<>|;`$]+)" + // Path characters (not whitespace,
													// quotes, operators)
							"(?:['\"]|\\s|$|>|>>|<|\\|)" // End with quote, whitespace, or
															// end of line
			);

			Matcher matcher = absolutePathPattern.matcher(command);
			while (matcher.find()) {
				String pathStr = matcher.group(1) + matcher.group(2);

				// Skip common system paths that are safe (like /bin, /usr/bin, etc.)
				// These are typically used in commands like "which", "ls", etc.
				if (isSystemPath(pathStr)) {
					continue;
				}

				try {
					Path absolutePath = Paths.get(pathStr).toAbsolutePath().normalize();

					// Check if path is within working directory
					if (!absolutePath.startsWith(workingDirPath)) {
						// Check if it's a linked_external path (allowed)
						if (rootPlanId != null && !rootPlanId.trim().isEmpty()) {
							try {
								Path rootPlanDir = unifiedDirectoryManager.getRootPlanDirectory(rootPlanId);
								Path linkedExternalDir = rootPlanDir.resolve("linked_external");
								if (absolutePath.startsWith(linkedExternalDir)) {
									// Allow linked_external directory access
									continue;
								}
							}
							catch (Exception e) {
								// If we can't check linked_external, continue with
								// validation
							}
						}

						log.warn("Command contains path outside working directory: {} (working dir: {})", absolutePath,
								workingDirPath);
						return "Access denied: Path '" + pathStr + "' is outside the allowed working directory. "
								+ "Only paths within the working directory are allowed for security reasons.";
					}
				}
				catch (Exception e) {
					// If path parsing fails, log and continue (might be part of a complex
					// command)
					log.debug("Failed to parse path in command: {}", pathStr, e);
				}
			}
		}
		catch (Exception e) {
			log.error("Error validating command paths: {}", command, e);
			// Don't block command if validation fails due to error
		}

		return null; // Validation passed
	}

	/**
	 * Check if a path is a system path that should be allowed
	 * @param path The path to check
	 * @return true if it's a system path, false otherwise
	 */
	private boolean isSystemPath(String path) {
		if (path == null || path.isEmpty()) {
			return false;
		}

		// Common system paths that are safe to access
		String[] allowedSystemPaths = { "/bin", "/usr/bin", "/usr/local/bin", "/sbin", "/usr/sbin", "/opt", "/etc",
				"/var", "/tmp", "/dev", "/proc", "/sys", "/System", "/Library", "/Applications", // macOS
				"C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)" // Windows
		};

		// Check if path starts with any allowed system path
		// But only if it's a read-only operation (like which, ls, cat, etc.)
		// For now, we'll be conservative and only allow common system binaries
		for (String allowedPath : allowedSystemPaths) {
			if (path.startsWith(allowedPath)) {
				// Only allow if it looks like a system binary path
				// (contains /bin/ or ends with common binary extensions)
				if (path.contains("/bin/") || path.endsWith(".exe") || path.endsWith(".dll")) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Get executor for current planId
	 */
	private ShellCommandExecutor getExecutor() {
		try {
			ShellCommandExecutor executor = shellExecutorService.getExecutor(currentPlanId);
			if (executor == null) {
				throw new RuntimeException("Failed to get executor for planId: " + currentPlanId);
			}
			return executor;
		}
		catch (Exception e) {
			log.error("Error getting executor for planId {}: {}", currentPlanId, e.getMessage(), e);
			throw new RuntimeException("Failed to get executor for planId: " + currentPlanId, e);
		}
	}

	@Override
	public ToolExecuteResult run(BashRequestVO requestVO) {
		String action = null;
		try {
			log.info("Bash tool requestVO: action={}", requestVO.getAction());

			// Check for thread interrupt before starting
			if (Thread.currentThread().isInterrupted()) {
				log.warn("Thread was interrupted before execution");
				return new ToolExecuteResult("Operation cancelled: thread was interrupted");
			}

			// Mark that run has been called at least once
			hasRunAtLeastOnce = true;

			// Get parameters from RequestVO
			action = requestVO.getAction();
			if (action == null || action.trim().isEmpty()) {
				// Backward compatibility: if no action specified, treat as 'command'
				// action
				action = "command";
			}

			ToolExecuteResult result;
			try {
				switch (action) {
					case "command": {
						// Check for interrupt
						if (Thread.currentThread().isInterrupted()) {
							return new ToolExecuteResult("Operation cancelled: thread was interrupted");
						}

						String command = requestVO.getCommand();
						if (command == null || command.trim().isEmpty()) {
							return new ToolExecuteResult("Command parameter is required for 'command' action");
						}


						// Validate paths in command to ensure they are within allowed
						// working directory
						String workingDir = getWorkingDirectory();
						String pathValidationError = validateCommandPaths(command, workingDir);
						if (pathValidationError != null) {
							log.warn("Command blocked due to path validation: {}", command);
							return new ToolExecuteResult(pathValidationError);
						}

						// Check bash security protection
						if (lynxeProperties != null && lynxeProperties.getBashSecurityProtection() != null
								&& lynxeProperties.getBashSecurityProtection()) {
							String commandLower = command.toLowerCase().trim();
							boolean isWindows = osName.toLowerCase().contains("windows");

							// Check for dangerous commands
							boolean isDangerous = false;
							String dangerousCommand = null;

							if (isWindows) {
								// Windows: check for del command
								// Use word boundary to avoid false positives (e.g.,
								// "delete" should not be blocked)
								if (commandLower.matches(".*\\bdel\\b.*") || commandLower.startsWith("del ")) {
									isDangerous = true;
									dangerousCommand = "del";
								}
							}
							else {
								// Unix/Linux/Mac: check for rm and rmdir commands
								// Use word boundary to avoid false positives (e.g.,
								// "grep" or "remove" should not be blocked)
								if (commandLower.matches(".*\\brm\\b.*") || commandLower.startsWith("rm ")
										|| commandLower.matches(".*\\brmdir\\b.*")
										|| commandLower.startsWith("rmdir ")) {
									isDangerous = true;
									dangerousCommand = "rm/rmdir";
								}
							}

							if (isDangerous) {
								log.warn("Command blocked by bash security protection: {}", command);
								return new ToolExecuteResult("Command blocked by security protection: "
										+ dangerousCommand
										+ " commands are not allowed. Set bashSecurityProtection to false to disable this protection.");
							}
						}

						log.info("Executing bash command: {}", command);
						log.info("Current operating system: {}", osName);
						this.lastCommand = command;

						// Add command to execution log with prompt-like format
						// Use display working directory (relative to task root) for prompt
						String username = System.getProperty("user.name");
						String hostname = System.getenv("HOSTNAME");
						if (hostname == null || hostname.isEmpty()) {
							try {
								hostname = java.net.InetAddress.getLocalHost().getHostName();
							}
							catch (Exception e) {
								hostname = "localhost";
							}
						}
						// Use display working directory (relative path) for prompt
						String displayDir = getDisplayWorkingDirectory();
						String prompt = String.format("%s@%s %s %% ", username, hostname, displayDir);
						executionLog.append(prompt).append(command).append("\n");

						// Check for interrupt before executing
						if (Thread.currentThread().isInterrupted()) {
							return new ToolExecuteResult(
									"Operation cancelled: thread was interrupted before command execution");
						}

						ShellCommandExecutor executor = getExecutor();
						List<String> commandList = new ArrayList<>();
						commandList.add(command);

						// Execute command (executors handle interrupts internally)
						List<String> executionResult = executor.execute(commandList, workingDir);

						// Check if result indicates interruption
						if (executionResult.size() == 1 && executionResult.get(0).contains("interrupted")) {
							log.warn("Command execution was interrupted");
						}

						this.lastResult = String.join("\n", executionResult);

						// Add result to execution log (clean ANSI codes first)
						if (!this.lastResult.isEmpty()) {
							String cleanedResult = cleanAnsiCodes(this.lastResult);
							executionLog.append(cleanedResult);
							if (!cleanedResult.endsWith("\n")) {
								executionLog.append("\n");
							}
						}

						// Use SmartContentSavingService to process the result
						String resultContent = this.lastResult;
						if (smartContentSavingService != null && rootPlanId != null) {
							SmartContentSavingService.SmartProcessResult smartResult = 
								smartContentSavingService.processContent(rootPlanId, resultContent, "bash");
							resultContent = smartResult.getComprehensiveResult();
						}

						// Create result with processed content
						List<String> processedResult = new ArrayList<>();
						processedResult.add(resultContent);
						result = new ToolExecuteResult(objectMapper.writeValueAsString(processedResult));
						break;
					}
					case "send_input": {
						String input = requestVO.getInput();
						if (input == null || input.trim().isEmpty()) {
							return new ToolExecuteResult("Input parameter is required for 'send_input' action");
						}
						log.info("Sending input to process: {}", input);

						ShellCommandExecutor executor = getExecutor();
						executor.sendInput(input);
						// Get updated state after sending input
						String state = executor.getCurrentState();
						this.lastResult = state;

						// Add input and response to execution log (clean ANSI codes first)
						executionLog.append(input).append("\n");
						if (!state.isEmpty()) {
							String cleanedState = cleanAnsiCodes(state);
							executionLog.append(cleanedState);
							if (!cleanedState.endsWith("\n")) {
								executionLog.append("\n");
							}
						}

						result = new ToolExecuteResult("Input sent successfully. Current state:\n" + state);
						break;
					}
					case "terminate": {
						log.info("Terminating current process");

						ShellCommandExecutor executor = getExecutor();
						executor.terminate();
						
						// Clear all state after termination
						this.lastCommand = "";
						this.lastResult = "";
						synchronized (executionLog) {
							executionLog.setLength(0);
						}
						
						result = new ToolExecuteResult("Process terminated successfully");
						break;
					}
					default:
						return new ToolExecuteResult("Unknown action: " + action
								+ ". Supported actions: command, send_input, terminate");
				}
			}
			catch (IllegalStateException e) {
				log.error("Illegal state error executing action '{}': {}", action, e.getMessage(), e);
				return new ToolExecuteResult("Action '" + action + "' failed: " + e.getMessage());
			}
			catch (Exception e) {
				// Check if exception was caused by thread interrupt
				if (Thread.currentThread().isInterrupted()) {
					log.warn("Thread interrupted, exception: {}", e.getMessage());
					Thread.currentThread().interrupt();
					// Try to clean up by sending Ctrl+C
					try {
						ShellCommandExecutor executor = getExecutor();
						if (executor != null && executor.isProcessAlive()) {
							executor.sendInput("\u0003"); // Ctrl+C
						}
					}
					catch (Exception ex) {
						log.debug("Failed to send Ctrl+C after interrupt: {}", ex.getMessage());
					}
					return new ToolExecuteResult("Operation cancelled: thread was interrupted");
				}
				log.error("Unexpected error executing action '{}': {}", action, e.getMessage(), e);
				return new ToolExecuteResult("Action '" + action + "' failed: " + e.getMessage());
			}

			// Check for interrupt before returning
			if (Thread.currentThread().isInterrupted()) {
				log.warn("Thread interrupted after action '{}' completed", action);
				Thread.currentThread().interrupt();
			}

			return result;
		}
		catch (Exception e) {
			// Check if exception was caused by thread interrupt
			if (Thread.currentThread().isInterrupted()) {
				log.warn("Thread interrupted, exception: {}", e.getMessage());
				Thread.currentThread().interrupt();
				return new ToolExecuteResult("Operation cancelled: thread was interrupted");
			}
			log.error("Unexpected error in bash tool for action '{}': {}", action, e.getMessage(), e);
			return new ToolExecuteResult("Bash operation failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return String.format(toolI18nService.getDescription("bash"), osName);
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("bash");
	}

	@Override
	public Class<BashRequestVO> getInputType() {
		return BashRequestVO.class;
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public String getCurrentToolStateString() {
		// Only show state if run method has been called at least once
		if (!hasRunAtLeastOnce) {
			return "";
		}

		try {
			ShellCommandExecutor executor = getExecutor();
			boolean isAlive = executor.isProcessAlive();

			StringBuilder stateBuilder = new StringBuilder();

			// Only add sections with actual data
			// Display working directory as relative path (task root as /)
			String displayWorkingDir = getDisplayWorkingDirectory();
			if (displayWorkingDir != null && !displayWorkingDir.isEmpty()) {
				stateBuilder.append("- Working Directory:\n");
				stateBuilder.append(displayWorkingDir);
				stateBuilder.append("\n\n");
			}

			if (!lastCommand.isEmpty()) {
				stateBuilder.append("- Last Command Executed:\n");
				stateBuilder.append(lastCommand);
				stateBuilder.append("\n\n");
			}

			// Get current state from executor (replaces executionLog and lastResult)
			try {
				if (isAlive) {
					stateBuilder.append("- Process Status: Running (waiting for input)\n\n");
				}
				else
				{
					stateBuilder.append("- Process Status: Terminated\n\n");
				}
			}
			catch (Exception e) {
				log.warn("Error getting current state: {}", e.getMessage());
				// Fallback to lastResult if getCurrentState fails
				if (!lastResult.isEmpty() && !lastResult.equals("Process terminated")) {
					stateBuilder.append("- Last Command Output:\n");
					stateBuilder.append(lastResult);
					if (!lastResult.endsWith("\n")) {
						stateBuilder.append("\n");
					}
					stateBuilder.append("\n");
				}
			}

			return stateBuilder.toString();
		}
		catch (Exception e) {
			// Handle any unexpected errors gracefully
			log.warn("Error getting bash tool state string (non-fatal): {}", e.getMessage(), e);
			StringBuilder errorBuilder = new StringBuilder();
			String displayWorkingDir = getDisplayWorkingDirectory();
			if (displayWorkingDir != null && !displayWorkingDir.isEmpty()) {
				errorBuilder.append("- Working Directory: ").append(displayWorkingDir).append("\n");
			}
			if (!lastCommand.isEmpty()) {
				errorBuilder.append("- Last Command: ").append(lastCommand).append("\n");
			}
			// Try to get current state from executor as fallback
			try {
				ShellCommandExecutor executor = getExecutor();
				String currentState = executor.getCurrentState();
				if (currentState != null && !currentState.isEmpty() 
						&& !currentState.equals("No active shell process")
						&& !currentState.equals("Shell process has terminated")) {
					currentState = cleanAnsiCodes(currentState);
					errorBuilder.append("- Current State:\n").append(currentState);
					if (!currentState.endsWith("\n")) {
						errorBuilder.append("\n");
					}
				}
				else if (!lastResult.isEmpty()) {
					errorBuilder.append("- Last Result: ").append(lastResult).append("\n");
				}
			}
			catch (Exception ex) {
				log.debug("Failed to get current state in error handler: {}", ex.getMessage());
				if (!lastResult.isEmpty()) {
					errorBuilder.append("- Last Result: ").append(lastResult).append("\n");
				}
			}
			return errorBuilder.toString();
		}
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up shell executor resources for plan: {}", planId);
			this.shellExecutorService.closeExecutorForPlan(planId);
			// Reset execution log and state for the plan
			synchronized (executionLog) {
				executionLog.setLength(0);
			}
			lastCommand = "";
			lastResult = "";
			hasRunAtLeastOnce = false;
		}
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
