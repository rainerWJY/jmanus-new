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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

/**
 * Service for managing shell executors per planId Similar to ChromeDriverService,
 * maintains shell executor instances for each plan
 */
@Service
@Primary
public class ShellExecutorService {

	private static final Logger log = LoggerFactory.getLogger(ShellExecutorService.class);

	private final ConcurrentHashMap<String, ShellCommandExecutor> executors = new ConcurrentHashMap<>();

	private final Lock executorLock = new ReentrantLock();

	/**
	 * Get executor for a specific planId Creates a new executor if one doesn't exist for
	 * the planId
	 * @param planId The plan ID
	 * @return ShellCommandExecutor instance for the planId
	 */
	public ShellCommandExecutor getExecutor(String planId) {
		if (planId == null) {
			throw new IllegalArgumentException("planId cannot be null");
		}

		ShellCommandExecutor currentExecutor = executors.get(planId);
		if (currentExecutor != null) {
			// Check if the existing executor is still healthy
			if (isExecutorHealthy(currentExecutor)) {
				return currentExecutor;
			}
			else {
				log.warn("Existing executor for planId {} is unhealthy, recreating", planId);
				closeExecutorForPlan(planId);
				currentExecutor = null;
			}
		}

		try {
			if (!executorLock.tryLock(30, TimeUnit.SECONDS)) {
				throw new RuntimeException("Failed to acquire executor lock within 30 seconds for planId: " + planId);
			}
			try {
				currentExecutor = executors.get(planId);
				if (currentExecutor != null && isExecutorHealthy(currentExecutor)) {
					return currentExecutor;
				}
				log.info("Creating new shell executor for planId: {}", planId);
				currentExecutor = ShellExecutorFactory.createExecutor();
				if (currentExecutor != null) {
					executors.put(planId, currentExecutor);
					log.info("Successfully created and cached new executor for planId: {}", planId);
				}
				else {
					log.error("Failed to create new executor for planId: {}", planId);
					throw new RuntimeException("Failed to create new executor for planId: " + planId);
				}
			}
			finally {
				executorLock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for executor lock for planId: " + planId, e);
		}
		catch (Exception e) {
			log.error("Unexpected error while getting executor for planId: {}", planId, e);
			throw new RuntimeException("Failed to get executor for planId: " + planId, e);
		}

		return currentExecutor;
	}

	/**
	 * Check if executor is healthy
	 * @param executor The executor to check
	 * @return true if executor is healthy, false otherwise
	 */
	private boolean isExecutorHealthy(ShellCommandExecutor executor) {
		if (executor == null) {
			return false;
		}
		// Check if the persistent shell process is alive
		return executor.isProcessAlive();
	}

	/**
	 * Close executor for a specific planId
	 * @param planId The plan ID
	 */
	public void closeExecutorForPlan(String planId) {
		ShellCommandExecutor executor = executors.remove(planId);
		if (executor != null) {
			try {
				// Terminate any running process
				executor.terminate();
				log.info("Closed executor for planId: {}", planId);
			}
			catch (Exception e) {
				log.warn("Error closing executor for planId {}: {}", planId, e.getMessage());
			}
		}
	}

	/**
	 * Cleanup all executors on shutdown
	 */
	@PreDestroy
	private void cleanupAllExecutors() {
		log.info("Starting cleanup of all shell executors");
		try {
			// Close all executors first before clearing the map
			for (String planId : executors.keySet()) {
				ShellCommandExecutor executor = executors.get(planId);
				if (executor != null) {
					try {
						log.info("Closing executor for planId: {}", planId);
						executor.terminate();
					}
					catch (Exception e) {
						log.warn("Error closing executor for planId {}: {}", planId, e.getMessage());
					}
				}
			}

			// Now clear the map after all resources are closed
			executors.clear();
			log.info("Successfully cleaned up all shell executors");
		}
		catch (Exception e) {
			log.error("Error cleaning up shell executors", e);
		}
	}

}
