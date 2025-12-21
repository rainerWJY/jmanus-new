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
import java.io.InterruptedIOException;
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
 * Mac command executor implementation using persistent interactive shell session
 */
public class MacShellExecutor implements ShellCommandExecutor {

	private static final Logger log = LoggerFactory.getLogger(MacShellExecutor.class);

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

	// Cache for shell path
	private static String shellPath = null;

	@Override
	public void initialize(String workingDir) throws Exception {
		if (shellProcess != null && shellProcess.isAlive()) {
			log.warn("Shell process already initialized, terminating existing process");
			terminate();
		}

		log.info("Initializing persistent shell session with working directory: {}", workingDir);
		currentWorkingDir = workingDir;

		// Get shell path
		String shell = getShellPath();

		// Start interactive shell process using 'script' to create a PTY
		// Interactive shells (zsh -i) require a TTY to produce output properly
		// The 'script' command creates a pseudo-terminal (PTY) that allows
		// interactive shells to work correctly in non-TTY environments
		// -q: quiet mode (suppress script startup message)
		// /dev/null: don't create a typescript file
		// Then run the interactive shell within the PTY
		ProcessBuilder pb = new ProcessBuilder("script", "-q", "/dev/null", shell, "-i");
		if (!StringUtils.isEmpty(workingDir)) {
			pb.directory(new File(workingDir));
		}

		// Redirect stderr to stdout to capture all output
		// Interactive shells often send prompts to stderr
		pb.redirectErrorStream(false); // Keep stderr separate so we can read both

		// Set environment variables
		pb.environment().put("LANG", "en_US.UTF-8");
		pb.environment().put("PATH", System.getenv("PATH") + ":/usr/local/bin");
		// Force unbuffered output for better marker detection
		pb.environment().put("PYTHONUNBUFFERED", "1"); // For Python scripts
		// Disable zsh prompt to avoid interference
		pb.environment().put("PROMPT", "");
		pb.environment().put("PS1", "");
		// Keep pager enabled for interactive commands
		// Do not set GIT_PAGER or PAGER to allow interactive paging

		shellProcess = pb.start();
		shellInput = new BufferedWriter(new OutputStreamWriter(shellProcess.getOutputStream(), "UTF-8"));
		shellOutput = new BufferedReader(new InputStreamReader(shellProcess.getInputStream(), "UTF-8"));
		shellError = new BufferedReader(new InputStreamReader(shellProcess.getErrorStream(), "UTF-8"));

		log.info("Started shell process using 'script' PTY: PID={}, command={}", shellProcess.pid(),
				String.join(" ", pb.command()));

		// Start background output reader thread
		startOutputReader();

		// Wait a bit for shell to initialize and consume initial prompt
		// When using 'script', there may be some initial output we need to consume
		Thread.sleep(1500);

		// Check if output reader is actually reading
		log.info("Checking output reader status - process alive: {}, reader running: {}",
				shellProcess != null && shellProcess.isAlive(), readerRunning.get());

		// Disable prompt and set up shell for better output capture
		try {
			log.info("Configuring shell for output capture");
			// Disable prompt to avoid interference
			shellInput.write("export PROMPT=''\n");
			shellInput.write("export PS1=''\n");
			shellInput.flush();
			Thread.sleep(200);

			// Send a test command to verify shell is working and producing output
			// Use a unique marker to identify the test output
			String testMarker = "SHELL_READY_TEST_" + System.currentTimeMillis();
			String testCommand = "echo '" + testMarker + "'\n";
			log.info("Sending test command: {}", testCommand.trim());
			shellInput.write(testCommand);
			shellInput.flush();
			Thread.sleep(1000); // Give more time for PTY to process

			// Check if we got any output
			String state = getCurrentState();
			log.info("Shell state after test command (length: {}): {}", state.length(),
					state.length() > 200 ? state.substring(0, 200) + "..." : state);

			// Check if test marker appears in output
			if (state.contains(testMarker)) {
				log.info("Test command output detected - shell is working correctly");
			}
			else {
				log.warn("Test command output not detected - shell may not be producing output");
			}
		}
		catch (Exception e) {
			log.warn("Error configuring shell: {}", e.getMessage(), e);
		}

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
			// Use printf with explicit flush to ensure output is not buffered
			// The \n in printf ensures the marker is on its own line
			// Use single quotes to avoid shell interpretation issues
			String startMarker = "printf '%s\\n' '" + CMD_START_PREFIX + commandId + "' && echo ''\n";
			log.info("Sending START marker command: {}", startMarker.trim());
			shellInput.write(startMarker);
			shellInput.flush();
			// Small delay to ensure marker is sent and processed
			Thread.sleep(100);

			log.info("Sending actual command: {}", command);
			shellInput.write(command + "\n");
			shellInput.flush();
			Thread.sleep(50);

			// Send end marker
			String endMarker = "printf '%s\\n' '" + CMD_END_PREFIX + commandId + "' && echo ''\n";
			log.info("Sending END marker command: {}", endMarker.trim());
			shellInput.write(endMarker);
			shellInput.flush();

			log.info("Command sent to shell with ID {}: {} (waiting for markers)", commandId, command);

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
			shellInput.write("printf \"" + CMD_START_PREFIX + commandId + "\\n\"\n");
			shellInput.flush();
			Thread.sleep(50);

			shellInput.write("cd \"" + targetDir.replace("\"", "\\\"") + "\"\n");
			shellInput.flush();

			shellInput.write("printf \"" + CMD_END_PREFIX + commandId + "\\n\"\n");
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
			log.info("Output reader thread started");
			log.info("Output reader: shellProcess={}, isAlive={}, readerRunning={}", shellProcess != null,
					shellProcess != null && shellProcess.isAlive(), readerRunning.get());
			try {
				// Read from stdout (blocking readLine).
				// For interactive shells, readLine() will block until a line is
				// available.
				// This is the correct approach - we need to wait for output.
				String line;
				int lineCount = 0;
				long startTime = System.currentTimeMillis();
				long lastReadTime = startTime;
				long lastLogTime = startTime;

				while (readerRunning.get() && shellProcess != null && shellProcess.isAlive()
						&& !Thread.currentThread().isInterrupted()) {
					try {
						// Log periodically if we're waiting for output
						long now = System.currentTimeMillis();
						if (lineCount == 0 && now - lastLogTime > 5000) {
							log.warn(
									"Output reader: Still waiting for first line (waited {}ms, process alive: {}, ready: {})",
									now - startTime, shellProcess != null && shellProcess.isAlive(),
									shellOutput.ready());
							lastLogTime = now;
						}

						// Blocking readLine - this will wait for output
						// Note: This will block indefinitely if no output is produced
						line = shellOutput.readLine();

						if (line == null) {
							// EOF reached - process might have terminated
							log.warn("Output reader: EOF reached (read {} lines, process alive: {})", lineCount,
									shellProcess != null && shellProcess.isAlive());
							if (shellProcess == null || !shellProcess.isAlive()) {
								break;
							}
							// If process is still alive but we got null, wait a bit and
							// retry
							Thread.sleep(100);
							continue;
						}

						lineCount++;
						lastReadTime = System.currentTimeMillis();

						// Log first 20 lines and then every 50th line
						if (lineCount <= 20 || lineCount % 50 == 0) {
							log.info("Output reader: read line #{} ({}ms since start): {}", lineCount,
									lastReadTime - startTime,
									line.length() > 80 ? line.substring(0, 80) + "..." : line);
						}
						else {
							log.debug("Output reader: read line #{}: {}", lineCount,
									line.length() > 80 ? line.substring(0, 80) + "..." : line);
						}

						processOutputLine(line);
					}
					catch (InterruptedIOException e) {
						Thread.currentThread().interrupt();
						log.info("Output reader thread interrupted during read");
						break;
					}
					catch (IOException e) {
						if (readerRunning.get()) {
							log.error("IO error in output reader thread (read {} lines so far)", lineCount, e);
						}
						// If process died, break
						if (shellProcess == null || !shellProcess.isAlive()) {
							break;
						}
						// Otherwise, wait and retry
						try {
							Thread.sleep(100);
						}
						catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							break;
						}
					}
				}
				log.info("Output reader thread exiting (read {} lines total, ran for {}ms)", lineCount,
						System.currentTimeMillis() - startTime);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.info("Output reader thread interrupted");
			}
			catch (Exception e) {
				if (readerRunning.get()) {
					log.error("Unexpected error in output reader thread", e);
				}
			}
		}, "ShellOutputReader");

		// Start error reader thread
		Thread errorReaderThread = new Thread(() -> {
			log.info("Error reader thread started");
			int errorLineCount = 0;
			try {
				// Read from stderr (blocking). See stdout reader note above.
				// Interactive shells often send prompts to stderr
				String line;
				while (readerRunning.get() && shellProcess != null && shellProcess.isAlive()
						&& !Thread.currentThread().isInterrupted() && (line = shellError.readLine()) != null) {
					errorLineCount++;
					if (errorLineCount <= 20 || errorLineCount % 50 == 0) {
						log.info("Error reader: read line #{}: {}", errorLineCount,
								line.length() > 80 ? line.substring(0, 80) + "..." : line);
					}
					else {
						log.debug("Error reader: read line #{}: {}", errorLineCount,
								line.length() > 80 ? line.substring(0, 80) + "..." : line);
					}
					processErrorLine(line);
				}
				log.info("Error reader thread exiting (read {} lines)", errorLineCount);
			}
			catch (InterruptedIOException e) {
				Thread.currentThread().interrupt();
				log.info("Error reader thread interrupted");
			}
			catch (Exception e) {
				if (readerRunning.get()) {
					log.error("Error in error reader thread (read {} lines)", errorLineCount, e);
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

		// Debug: log all lines being read (first 100 chars to avoid spam)
		log.debug("Read line (length={}): {}", line.length(),
				line.length() > 100 ? line.substring(0, 100) + "..." : line);

		// Check for command start marker (exact match for better reliability)
		if (line.startsWith(CMD_START_PREFIX)) {
			// Extract command ID (everything after the prefix)
			String commandId = line.substring(CMD_START_PREFIX.length()).trim();
			CommandOutput cmdOutput = commandOutputs.get(commandId);
			if (cmdOutput != null) {
				cmdOutput.active = true;
				log.info("Command started: {} (active commands: {})", commandId, commandOutputs.size());
			}
			else {
				log.warn("Received START marker for unknown command ID: {} (available IDs: {})", commandId,
						commandOutputs.keySet());
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
				log.info("Command completed: {} (output length: {}, error length: {})", commandId,
						cmdOutput.output.length(), cmdOutput.error.length());
			}
			else {
				log.warn("Received END marker for unknown command ID: {} (available IDs: {})", commandId,
						commandOutputs.keySet());
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
				shellProcess.destroy();
				if (!shellProcess.waitFor(5, TimeUnit.SECONDS)) {
					shellProcess.destroyForcibly();
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
				processedInput = "\n";
			}
			else if ("\\t".equals(input)) {
				processedInput = "\t";
			}
			else if (" ".equals(input) || "space".equalsIgnoreCase(input)) {
				processedInput = " ";
			}
			// For password-like input, auto-append newline if not present
			else if (!processedInput.contains("\n") && !processedInput.contains("\r")) {
				processedInput = processedInput + "\n";
				log.debug("Auto-appended newline to input");
			}

			shellInput.write(processedInput);
			shellInput.flush();

			// Mask password in logs
			String logInput = processedInput;
			if (processedInput.length() > 4 && processedInput.length() < 100 && !processedInput.contains(" ")
					&& !processedInput.contains("\n")) {
				logInput = "[PASSWORD_MASKED]";
			}
			log.info("Sent input to shell: {} (length: {})", logInput.replace("\n", "\\n").replace(" ", "[SPACE]"),
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
	 * Get shell path (zsh preferred, bash as fallback)
	 */
	private String getShellPath() {
		if (shellPath != null) {
			return shellPath;
		}

		// Try zsh first
		String[] zshPaths = { "/bin/zsh", "/usr/bin/zsh", "/usr/local/bin/zsh", "/opt/homebrew/bin/zsh" };
		for (String path : zshPaths) {
			if (new File(path).exists() && new File(path).canExecute()) {
				log.info("Found zsh at: {}", path);
				shellPath = path;
				return shellPath;
			}
		}

		// Try which zsh
		try {
			Process whichProcess = new ProcessBuilder("which", "zsh").start();
			whichProcess.waitFor(5, TimeUnit.SECONDS);
			if (whichProcess.exitValue() == 0) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(whichProcess.getInputStream()))) {
					String path = reader.readLine();
					if (path != null && !path.trim().isEmpty()) {
						File zshFile = new File(path.trim());
						if (zshFile.exists() && zshFile.canExecute()) {
							log.info("Found zsh via 'which' at: {}", path.trim());
							shellPath = path.trim();
							return shellPath;
						}
					}
				}
			}
		}
		catch (Exception e) {
			log.debug("Failed to find zsh via 'which': {}", e.getMessage());
		}

		// Fallback to bash
		String[] bashPaths = { "/bin/bash", "/usr/bin/bash", "/usr/local/bin/bash" };
		for (String path : bashPaths) {
			if (new File(path).exists() && new File(path).canExecute()) {
				log.warn("zsh not found, using bash at: {}", path);
				shellPath = path;
				return shellPath;
			}
		}

		// Final fallback
		log.error("Neither zsh nor bash found, using /bin/bash as fallback");
		shellPath = "/bin/bash";
		return shellPath;
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
