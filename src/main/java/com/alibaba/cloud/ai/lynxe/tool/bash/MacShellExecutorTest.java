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
import java.util.Scanner;

/**
 * Test class for MacShellExecutor to debug timeout issues
 */
public class MacShellExecutorTest {

	public static void main(String[] args) {
		System.out.println("=== MacShellExecutor Test ===");
		System.out.println("This test will help debug timeout issues with command execution");
		System.out.println();

		MacShellExecutor executor = new MacShellExecutor();
		Scanner scanner = new Scanner(System.in);

		try {
			// Initialize shell
			String workingDir = System.getProperty("user.dir");
			System.out.println("Initializing shell with working directory: " + workingDir);
			executor.initialize(workingDir);
			System.out.println("Shell initialized successfully");
			System.out.println("Process alive: " + executor.isProcessAlive());
			System.out.println();

			// Wait a bit and check initial state
			Thread.sleep(1000);
			System.out.println("=== Initial Shell State ===");
			try {
				String state = executor.getCurrentState();
				System.out.println("Initial state (first 500 chars):");
				System.out.println(state.length() > 500 ? state.substring(0, 500) + "..." : state);
			}
			catch (Exception e) {
				System.err.println("Error getting initial state: " + e.getMessage());
			}
			System.out.println();

			// Test commands - start with simple ones
			String[] testCommands = { "echo 'Hello World'", "pwd", "echo 'Test command with markers'" };

			System.out.println("=== Testing Commands ===");
			for (String cmd : testCommands) {
				System.out.println("\n--- Executing: " + cmd + " ---");
				System.out.println("Before execution - Process alive: " + executor.isProcessAlive());

				long startTime = System.currentTimeMillis();
				try {
					List<String> results = executor.execute(List.of(cmd), workingDir);
					long duration = System.currentTimeMillis() - startTime;
					System.out.println("Command completed in " + duration + "ms");
					System.out.println("Result count: " + results.size());
					for (int i = 0; i < results.size(); i++) {
						String result = results.get(i);
						System.out.println("Result[" + i + "] (length: " + result.length() + "):");
						if (result.length() > 200) {
							System.out.println(result.substring(0, 200) + "...");
						}
						else {
							System.out.println(result);
						}
					}
				}
				catch (Exception e) {
					long duration = System.currentTimeMillis() - startTime;
					System.err.println("Error executing command after " + duration + "ms: " + e.getMessage());
					e.printStackTrace();

					// Check state after error
					try {
						String state = executor.getCurrentState();
						System.out.println("State after error (last 300 chars):");
						int start = Math.max(0, state.length() - 300);
						System.out.println(state.substring(start));
					}
					catch (Exception stateEx) {
						System.err.println("Could not get state: " + stateEx.getMessage());
					}
				}

				// Small delay between commands
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}

			// Interactive mode
			System.out.println("\n=== Interactive Mode ===");
			System.out.println("Enter commands to test (type 'exit' to quit, 'state' to get current state):");
			while (true) {
				System.out.print("bash> ");
				String input = scanner.nextLine().trim();

				if (input.isEmpty()) {
					continue;
				}

				if ("exit".equalsIgnoreCase(input)) {
					break;
				}

				if ("state".equalsIgnoreCase(input)) {
					try {
						String state = executor.getCurrentState();
						System.out.println("Current state:");
						System.out.println(state);
					}
					catch (Exception e) {
						System.err.println("Error getting state: " + e.getMessage());
						e.printStackTrace();
					}
					continue;
				}

				if ("alive".equalsIgnoreCase(input)) {
					boolean alive = executor.isProcessAlive();
					System.out.println("Process alive: " + alive);
					continue;
				}

				try {
					System.out.println("Executing: " + input);
					List<String> results = executor.execute(List.of(input), workingDir);
					System.out.println("Result:");
					for (String result : results) {
						System.out.println(result);
					}
				}
				catch (Exception e) {
					System.err.println("Error: " + e.getMessage());
					e.printStackTrace();
				}
			}

		}
		catch (Exception e) {
			System.err.println("Fatal error: " + e.getMessage());
			e.printStackTrace();
		}
		finally {
			System.out.println("\nTerminating shell...");
			executor.terminate();
			scanner.close();
			System.out.println("Test completed");
		}
	}

}
