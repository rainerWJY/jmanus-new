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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windows command executor implementation using persistent interactive shell session
 */
public class WindowsShellExecutor implements ShellCommandExecutor {

	private static final Logger log = LoggerFactory.getLogger(WindowsShellExecutor.class);

	private static final String CMD_START_PREFIX = "__CMD_START__";

	private static final String CMD_END_PREFIX = "__CMD_END__";

	private static final int COMMAND_TIMEOUT_SECONDS = 60;

	// Persistent shell process
	private Process shellProcess;

	private BufferedWriter shellInput;

	private BufferedReader shellOutput;

	private BufferedReader shellError;

	// Working directory tracking
	private String currentWorkingDir;

	// Output reading
	private Thread outputReaderThread;

	private final AtomicBoolean readerRunning = new AtomicBoolean(false);

	private final Map<String, CommandOutput> commandOutputs = new ConcurrentHashMap<>();

	private final StringBuilder allOutput = new StringBuilder();

	@Override
	public void initialize(String workingDir) throws Exception {
		if (shellProcess != null && shellProcess.isAlive()) {
			log.warn("Shell process already initialized, terminating existing process");
			terminate();
		}

		log.info("Initializing persistent shell session with working directory: {}", workingDir);
		currentWorkingDir = workingDir;

		// Start interactive cmd.exe process (/K keeps it running)
		ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/K");
		if (!StringUtils.isEmpty(workingDir)) {
			pb.directory(new File(workingDir));
		}

		// Windows-specific environment variable setup
		pb.environment().put("PATHEXT", ".COM;.EXE;.BAT;.CMD");
		pb.environment().put("SystemRoot", System.getenv("SystemRoot"));

		shellProcess = pb.start();
		shellInput = new BufferedWriter(new OutputStreamWriter(shellProcess.getOutputStream(), "GBK"));
		shellOutput = new BufferedReader(new InputStreamReader(shellProcess.getInputStream(), "GBK"));
		shellError = new BufferedReader(new InputStreamReader(shellProcess.getErrorStream(), "GBK"));

		// Start background output reader thread
		startOutputReader();

		// Wait a bit for shell to initialize
		Thread.sleep(500);

		// Change to working directory if specified
		if (!StringUtils.isEmpty(workingDir)) {
			ensureWorkingDir(workingDir);
		}

		log.info("Persistent shell session initialized successfully");
	}

	@Override
	public List<String> execute(List<String> commands, String workingDir) {
		// Ensure shell is initialized
		if (shellProcess == null || !shellProcess.isAlive()) {
			try {
				initialize(workingDir != null ? workingDir : System.getProperty("user.dir"));
			}
			catch (Exception e) {
				log.error("Failed to initialize shell process", e);
				return commands.stream()
					.map(cmd -> "Error: Failed to initialize shell - " + e.getMessage())
					.collect(Collectors.toList());
			}
		}

		return commands.stream().map(command -> {
			try {
				// Handle empty command
				if (command.trim().isEmpty()) {
					return getCurrentState();
				}

				// Handle ctrl+c
				if ("ctrl+c".equalsIgnoreCase(command.trim())) {
					try {
						shellInput.write("\u0003"); // Ctrl+C character
						shellInput.flush();
					}
					catch (IOException e) {
						log.error("Error sending Ctrl+C", e);
					}
					return "Ctrl+C sent";
				}

				// Handle Windows background commands (remove & suffix, use start /B)
				if (command.endsWith("&")) {
					command = "start /B " + command.substring(0, command.length() - 1);
				}

				// Ensure working directory
				if (workingDir != null && !workingDir.isEmpty() && !workingDir.equals(currentWorkingDir)) {
					ensureWorkingDir(workingDir);
				}

				// Execute command with markers
				return executeCommand(command);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Command execution interrupted: {}", command);
				return "Error: Command execution was interrupted";
			}
			catch (Exception e) {
				// Check if exception was caused by thread interrupt
				if (Thread.currentThread().isInterrupted()) {
					Thread.currentThread().interrupt();
					log.warn("Thread interrupted during command execution: {}", command);
					return "Error: Command execution was interrupted";
				}
				log.error("Error executing command: {}", command, e);
				return "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
			}
		}).collect(Collectors.toList());
	}

	private String executeCommand(String command) throws Exception {
		// Generate unique command ID (timestamp + random to ensure uniqueness)
		long timestamp = System.currentTimeMillis();
		int random = (int) (Math.random() * 10000);
		String commandId = timestamp + "_" + random;
		CommandOutput cmdOutput = new CommandOutput();
		commandOutputs.put(commandId, cmdOutput);

		try {
			// Send command markers and command
			// Windows cmd.exe uses echo (no quotes needed for simple strings)
			shellInput.write("echo " + CMD_START_PREFIX + commandId + "\r\n");
			shellInput.flush();
			Thread.sleep(50);

			shellInput.write(command + "\r\n");
			shellInput.flush();

			shellInput.write("echo " + CMD_END_PREFIX + commandId + "\r\n");
			shellInput.flush();

			log.debug("Sent command to shell with ID {}: {}", commandId, command);

			// Wait for command to complete (with timeout)
			// This await can be interrupted, which we handle below
			boolean completed = false;
			try {
				completed = cmdOutput.latch.await(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Command execution interrupted: {}", command);
				// Send Ctrl+C to cancel the command in the shell
				try {
					shellInput.write("\u0003"); // Ctrl+C
					shellInput.flush();
				}
				catch (IOException ioEx) {
					log.debug("Failed to send Ctrl+C after interrupt: {}", ioEx.getMessage());
				}
				// Remove from tracking and throw to propagate interrupt
				commandOutputs.remove(commandId);
				throw new InterruptedException("Command execution was interrupted: " + command);
			}

			// Check if thread was interrupted even if await didn't throw
			if (Thread.currentThread().isInterrupted()) {
				log.warn("Thread interrupted detected after command wait: {}", command);
				Thread.currentThread().interrupt();
				// Send Ctrl+C to cancel
				try {
					shellInput.write("\u0003"); // Ctrl+C
					shellInput.flush();
				}
				catch (IOException ioEx) {
					log.debug("Failed to send Ctrl+C after interrupt: {}", ioEx.getMessage());
				}
				commandOutputs.remove(commandId);
				throw new InterruptedException("Command execution was interrupted: " + command);
			}

			if (!completed) {
				log.warn("Command timed out: {} (commandId: {})", command, commandId);
				// If we have some output, return it even though we didn't get the end
				// marker
				if (cmdOutput.output.length() > 0 || cmdOutput.error.length() > 0) {
					String partialOutput = cmdOutput.output.toString().trim();
					if (partialOutput.isEmpty() && cmdOutput.error.length() > 0) {
						return "Error: " + cmdOutput.error.toString().trim();
					}
					log.info("Returning partial output for timed out command: {}", command);
					return partialOutput;
				}
				return "Error: Command timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds";
			}

			// Return output (excluding markers)
			String output = cmdOutput.output.toString().trim();
			if (output.isEmpty() && !cmdOutput.error.toString().isEmpty()) {
				return "Error: " + cmdOutput.error.toString().trim();
			}
			return output;
		}
		finally {
			commandOutputs.remove(commandId);
		}
	}

	private void ensureWorkingDir(String targetDir) throws IOException, InterruptedException {
		if (targetDir == null || targetDir.equals(currentWorkingDir)) {
			return;
		}

		log.debug("Changing working directory from {} to {}", currentWorkingDir, targetDir);

		// Send cd command
		long timestamp = System.currentTimeMillis();
		String commandId = String.valueOf(timestamp);
		CommandOutput cmdOutput = new CommandOutput();
		commandOutputs.put(commandId, cmdOutput);

		try {
			shellInput.write("echo " + CMD_START_PREFIX + commandId + "\r\n");
			shellInput.write("cd /d \"" + targetDir.replace("\"", "\"\"") + "\"\r\n");
			shellInput.write("echo " + CMD_END_PREFIX + commandId + "\r\n");
			shellInput.flush();

			// Wait for cd to complete
			try {
				if (cmdOutput.latch.await(5, TimeUnit.SECONDS)) {
					// Check if cd was successful (no error output)
					if (cmdOutput.error.toString().trim().isEmpty()) {
						currentWorkingDir = targetDir;
						log.debug("Successfully changed working directory to: {}", targetDir);
					}
					else {
						log.warn("Failed to change directory: {}", cmdOutput.error.toString().trim());
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Interrupted while changing working directory");
				throw e;
			}
		}
		finally {
			commandOutputs.remove(commandId);
		}
	}

	private void startOutputReader() {
		if (readerRunning.get()) {
			return;
		}

		readerRunning.set(true);
		outputReaderThread = new Thread(() -> {
			try {
				// Read from stdout
				String line;
				while (readerRunning.get() && shellProcess.isAlive() && !Thread.currentThread().isInterrupted()) {
					if (shellOutput.ready()) {
						line = shellOutput.readLine();
						if (line != null) {
							processOutputLine(line);
						}
					}
					else {
						Thread.sleep(10); // Small delay to avoid busy waiting
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.debug("Output reader thread interrupted");
			}
			catch (Exception e) {
				if (readerRunning.get()) {
					log.error("Error in output reader thread", e);
				}
			}
		}, "ShellOutputReader");

		// Start error reader thread
		Thread errorReaderThread = new Thread(() -> {
			try {
				String line;
				while (readerRunning.get() && shellProcess.isAlive() && !Thread.currentThread().isInterrupted()) {
					if (shellError.ready()) {
						line = shellError.readLine();
						if (line != null) {
							processErrorLine(line);
						}
					}
					else {
						Thread.sleep(10);
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.debug("Error reader thread interrupted");
			}
			catch (Exception e) {
				if (readerRunning.get()) {
					log.error("Error in error reader thread", e);
				}
			}
		}, "ShellErrorReader");

		outputReaderThread.setDaemon(true);
		errorReaderThread.setDaemon(true);
		outputReaderThread.start();
		errorReaderThread.start();
	}

	private void processOutputLine(String line) {
		if (line == null) {
			return;
		}

		// Check for command start marker (exact match for better reliability)
		if (line.startsWith(CMD_START_PREFIX)) {
			// Extract command ID (everything after the prefix)
			String commandId = line.substring(CMD_START_PREFIX.length()).trim();
			CommandOutput cmdOutput = commandOutputs.get(commandId);
			if (cmdOutput != null) {
				cmdOutput.active = true;
				log.debug("Command started: {}", commandId);
			}
			else {
				log.warn("Received START marker for unknown command ID: {}", commandId);
			}
			return; // Don't add marker line to output
		}
		// Check for command end marker (exact match for better reliability)
		else if (line.startsWith(CMD_END_PREFIX)) {
			// Extract command ID (everything after the prefix)
			String commandId = line.substring(CMD_END_PREFIX.length()).trim();
			CommandOutput cmdOutput = commandOutputs.get(commandId);
			if (cmdOutput != null) {
				cmdOutput.active = false;
				cmdOutput.latch.countDown();
				log.debug("Command completed: {}", commandId);
			}
			else {
				log.warn("Received END marker for unknown command ID: {}", commandId);
			}
			return; // Don't add marker line to output
		}
		// Also check for markers that might be embedded in other text (fallback)
		else if (line.contains(CMD_START_PREFIX)) {
			int startIdx = line.indexOf(CMD_START_PREFIX);
			if (startIdx >= 0) {
				String commandId = line.substring(startIdx + CMD_START_PREFIX.length()).trim();
				// Remove any trailing text after the command ID
				int spaceIdx = commandId.indexOf(' ');
				if (spaceIdx > 0) {
					commandId = commandId.substring(0, spaceIdx);
				}
				CommandOutput cmdOutput = commandOutputs.get(commandId);
				if (cmdOutput != null) {
					cmdOutput.active = true;
					log.debug("Command started (embedded marker): {}", commandId);
					return; // Don't add marker line to output
				}
			}
		}
		else if (line.contains(CMD_END_PREFIX)) {
			int startIdx = line.indexOf(CMD_END_PREFIX);
			if (startIdx >= 0) {
				String commandId = line.substring(startIdx + CMD_END_PREFIX.length()).trim();
				// Remove any trailing text after the command ID
				int spaceIdx = commandId.indexOf(' ');
				if (spaceIdx > 0) {
					commandId = commandId.substring(0, spaceIdx);
				}
				CommandOutput cmdOutput = commandOutputs.get(commandId);
				if (cmdOutput != null) {
					cmdOutput.active = false;
					cmdOutput.latch.countDown();
					log.debug("Command completed (embedded marker): {}", commandId);
					return; // Don't add marker line to output
				}
			}
		}

		// Add line to all active command outputs
		allOutput.append(line).append("\n");
		for (CommandOutput cmdOutput : commandOutputs.values()) {
			if (cmdOutput.active) {
				cmdOutput.output.append(line).append("\n");
			}
		}
	}

	private void processErrorLine(String line) {
		// Add error line to all active command outputs
		allOutput.append(line).append("\n");
		for (CommandOutput cmdOutput : commandOutputs.values()) {
			if (cmdOutput.active) {
				cmdOutput.error.append(line).append("\n");
			}
		}
	}

	@Override
	public void terminate() {
		readerRunning.set(false);

		if (outputReaderThread != null) {
			try {
				outputReaderThread.interrupt();
			}
			catch (Exception e) {
				log.debug("Error interrupting output reader thread", e);
			}
		}

		if (shellProcess != null && shellProcess.isAlive()) {
			try {
				// Windows: use taskkill to ensure process and child processes are
				// terminated
				try {
					Runtime.getRuntime().exec("taskkill /F /T /PID " + shellProcess.pid());
					if (!shellProcess.waitFor(5, TimeUnit.SECONDS)) {
						shellProcess.destroyForcibly();
					}
				}
				catch (Exception e) {
					log.debug("Error using taskkill, falling back to destroy: {}", e.getMessage());
					shellProcess.destroy();
					if (!shellProcess.waitFor(5, TimeUnit.SECONDS)) {
						shellProcess.destroyForcibly();
					}
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				shellProcess.destroyForcibly();
			}
			log.info("Shell process terminated");
		}

		// Clean up resources
		shellProcess = null;
		shellInput = null;
		shellOutput = null;
		shellError = null;
		currentWorkingDir = null;
		commandOutputs.clear();
		allOutput.setLength(0);
	}

	@Override
	public void sendInput(String input) throws Exception {
		if (shellProcess == null || !shellProcess.isAlive()) {
			throw new IllegalStateException("Shell process is not running");
		}
		if (shellInput == null) {
			throw new IllegalStateException("Shell input stream is not available");
		}

		try {
			// Replace special sequences
			String processedInput = input;
			if ("\\n".equals(input) || "Enter".equalsIgnoreCase(input)) {
				processedInput = "\r\n"; // Windows uses \r\n
			}
			else if ("\\t".equals(input)) {
				processedInput = "\t";
			}
			else if (" ".equals(input) || "space".equalsIgnoreCase(input)) {
				processedInput = " ";
			}
			// For password-like input, auto-append newline if not present
			else if (!processedInput.contains("\n") && !processedInput.contains("\r")) {
				processedInput = processedInput + "\r\n";
				log.debug("Auto-appended newline to input");
			}

			shellInput.write(processedInput);
			shellInput.flush();

			// Mask password in logs
			String logInput = processedInput;
			if (processedInput.length() > 4 && processedInput.length() < 100 && !processedInput.contains(" ")
					&& !processedInput.contains("\n") && !processedInput.contains("\r")) {
				logInput = "[PASSWORD_MASKED]";
			}
			log.info("Sent input to shell: {} (length: {})",
					logInput.replace("\r\n", "\\r\\n").replace("\n", "\\n").replace(" ", "[SPACE]"),
					processedInput.length());
		}
		catch (IOException e) {
			log.error("Error sending input to shell", e);
			throw new Exception("Failed to send input: " + e.getMessage(), e);
		}
	}

	@Override
	public String getCurrentState() throws Exception {
		if (shellProcess == null) {
			return "No active shell process";
		}
		if (!shellProcess.isAlive()) {
			return "Shell process has terminated";
		}

		// Return recent output (last 100 lines)
		String[] lines = allOutput.toString().split("\n");
		int start = Math.max(0, lines.length - 100);
		StringBuilder recent = new StringBuilder();
		for (int i = start; i < lines.length; i++) {
			recent.append(lines[i]).append("\n");
		}
		return recent.toString();
	}

	@Override
	public boolean isProcessAlive() {
		return shellProcess != null && shellProcess.isAlive();
	}

	/**
	 * Internal class to track command output
	 */
	private static class CommandOutput {

		final StringBuilder output = new StringBuilder();

		final StringBuilder error = new StringBuilder();

		final CountDownLatch latch = new CountDownLatch(1);

		volatile boolean active = false;

	}

}
