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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
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

	// Add operating system information
	private static final String osName = System.getProperty("os.name");

	private final String name = "bash";

	// Track if run method has been called at least once
	private volatile boolean hasRunAtLeastOnce = false;

	private String lastCommand = "";

	private String lastResult = "";

	// Execution log to track full terminal session history
	private final StringBuilder executionLog = new StringBuilder();

	public Bash(UnifiedDirectoryManager unifiedDirectoryManager, ObjectMapper objectMapper,
			ToolI18nService toolI18nService, ShellExecutorService shellExecutorService,
			LynxeProperties lynxeProperties) {
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.objectMapper = objectMapper;
		this.toolI18nService = toolI18nService;
		this.shellExecutorService = shellExecutorService;
		this.lynxeProperties = lynxeProperties;
	}

	/**
	 * Get the working directory for bash execution Uses rootPlanId directory if
	 * available, otherwise falls back to base working directory
	 * @return Working directory path as string
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
						String workingDir = getWorkingDirectory();
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
						String dirName = workingDir.substring(workingDir.lastIndexOf('/') + 1);
						if (dirName.isEmpty()) {
							dirName = workingDir;
						}
						String prompt = String.format("%s@%s %s %% ", username, hostname, dirName);
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

						// Add result to execution log
						if (!this.lastResult.isEmpty()) {
							executionLog.append(this.lastResult);
							if (!this.lastResult.endsWith("\n")) {
								executionLog.append("\n");
							}
						}

						result = new ToolExecuteResult(objectMapper.writeValueAsString(executionResult));
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

						// Add input and response to execution log
						executionLog.append(input).append("\n");
						if (!state.isEmpty()) {
							executionLog.append(state);
							if (!state.endsWith("\n")) {
								executionLog.append("\n");
							}
						}

						result = new ToolExecuteResult("Input sent successfully. Current state:\n" + state);
						break;
					}
					case "get_state": {
						log.info("Getting current process state");

						ShellCommandExecutor executor = getExecutor();
						String state = executor.getCurrentState();
						this.lastResult = state;
						result = new ToolExecuteResult(state);
						break;
					}
					case "terminate": {
						log.info("Terminating current process");

						ShellCommandExecutor executor = getExecutor();
						executor.terminate();
						this.lastResult = "Process terminated";
						result = new ToolExecuteResult("Process terminated successfully");
						break;
					}
					default:
						return new ToolExecuteResult("Unknown action: " + action
								+ ". Supported actions: command, send_input, get_state, terminate");
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

			// Add execution log if available
			if (executionLog.length() > 0) {
				stateBuilder.append(executionLog.toString());
				stateBuilder.append("\n");
			}

			// Only add sections with actual data
			String workingDir = getWorkingDirectory();
			if (workingDir != null && !workingDir.isEmpty()) {
				stateBuilder.append("- Working Directory:\n");
				stateBuilder.append(workingDir);
				stateBuilder.append("\n\n");
			}

			if (!lastCommand.isEmpty()) {
				stateBuilder.append("- Last Command Executed:\n");
				stateBuilder.append(lastCommand);
				stateBuilder.append("\n\n");
			}

			if (isAlive) {
				stateBuilder.append("- Process Status: Running (waiting for input)\n\n");
				try {
					String currentState = executor.getCurrentState();
					if (currentState != null && !currentState.isEmpty() && !currentState.equals("No active process")
							&& !currentState.equals("Process has completed")) {
						stateBuilder.append("- Current Process Output:\n");
						stateBuilder.append(currentState);
						stateBuilder.append("\n\n");
					}
				}
				catch (Exception e) {
					log.warn("Error getting current state: {}", e.getMessage());
				}
			}
			else {
				if (!lastResult.isEmpty() && !lastResult.equals("Process terminated")) {
					stateBuilder.append(lastResult);
					stateBuilder.append("\n\n");
				}
			}

			return stateBuilder.toString();
		}
		catch (Exception e) {
			// Handle any unexpected errors gracefully
			log.warn("Error getting bash tool state string (non-fatal): {}", e.getMessage(), e);
			StringBuilder errorBuilder = new StringBuilder();
			if (executionLog.length() > 0) {
				errorBuilder.append(executionLog.toString());
				errorBuilder.append("\n");
			}
			String workingDir = getWorkingDirectory();
			if (workingDir != null && !workingDir.isEmpty()) {
				errorBuilder.append("- Working Directory: ").append(workingDir).append("\n");
			}
			if (!lastCommand.isEmpty()) {
				errorBuilder.append("- Last Command: ").append(lastCommand).append("\n");
			}
			if (!lastResult.isEmpty()) {
				errorBuilder.append("- Last Result: ").append(lastResult).append("\n");
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
