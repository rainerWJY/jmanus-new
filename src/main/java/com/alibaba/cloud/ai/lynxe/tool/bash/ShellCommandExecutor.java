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

import java.util.List;

/**
 * Shell command executor interface. Provides cross-platform (Windows/Linux/Mac) shell
 * command execution capability using a persistent interactive shell session
 */
public interface ShellCommandExecutor {

	/**
	 * Initialize the persistent shell session
	 * @param workingDir Initial working directory for the shell
	 * @throws Exception If initialization fails
	 */
	void initialize(String workingDir) throws Exception;

	/**
	 * Execute shell commands in the persistent shell session
	 * @param commands List of commands to execute
	 * @param workingDir Working directory (will change directory if different from
	 * current)
	 * @return List of command execution results
	 */
	List<String> execute(List<String> commands, String workingDir);

	/**
	 * Terminate the persistent shell process
	 */
	void terminate();

	/**
	 * Send input to the persistent shell process
	 * @param input The input to send (e.g., 'n' for next page, 'q' for quit)
	 * @throws Exception If there's an error sending input
	 */
	void sendInput(String input) throws Exception;

	/**
	 * Get current shell state/output without executing a new command
	 * @return Current shell output/state
	 * @throws Exception If there's an error getting state
	 */
	String getCurrentState() throws Exception;

	/**
	 * Check if the persistent shell process is still running
	 * @return true if shell process is alive, false otherwise
	 */
	boolean isProcessAlive();

	/**
	 * Get the current system type
	 * @return System type (windows/linux/mac)
	 */
	default String getOsType() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			return "windows";
		}
		else if (os.contains("mac")) {
			return "mac";
		}
		else {
			return "linux";
		}
	}

}
