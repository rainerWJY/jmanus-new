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
package com.alibaba.cloud.ai.lynxe.tool.browser.browserOperators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.BrowserRequestVO;
import com.alibaba.cloud.ai.lynxe.tool.browser.service.BrowserUseCommonService;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Abstract base class for browser tools that provides shared functionality.
 */
public abstract class AbstractBrowserTool<T> extends AbstractBaseTool<T> {

	protected static final Logger log = LoggerFactory.getLogger(AbstractBrowserTool.class);

	protected final BrowserUseCommonService browserUseTool;

	public AbstractBrowserTool(BrowserUseCommonService browserUseTool) {
		this.browserUseTool = browserUseTool;
	}

	@Override
	public void setCurrentPlanId(String planId) {
		super.setCurrentPlanId(planId);
		// Synchronize planId to BrowserUseTool instance
		if (browserUseTool != null) {
			browserUseTool.setCurrentPlanId(planId);
		}
	}

	@Override
	public void setRootPlanId(String rootPlanId) {
		super.setRootPlanId(rootPlanId);
		// Synchronize rootPlanId to BrowserUseTool instance
		if (browserUseTool != null) {
			browserUseTool.setRootPlanId(rootPlanId);
		}
	}

	/**
	 * Execute action with retry mechanism for better reliability
	 */
	protected ToolExecuteResult executeActionWithRetry(ActionExecutor executor, String actionName) {
		int maxRetries = 2;
		int retryDelay = 1000; // 1 second

		for (int attempt = 1; attempt <= maxRetries; attempt++) {
			try {
				return executor.execute();
			}
			catch (TimeoutError e) {
				if (attempt == maxRetries) {
					log.error("Action '{}' timed out after {} attempts: {}", actionName, maxRetries, e.getMessage());
					throw e;
				}
				log.warn("Action '{}' timed out on attempt {}, retrying: {}", actionName, attempt, e.getMessage());
			}
			catch (PlaywrightException e) {
				// Some Playwright exceptions are not worth retrying
				if (e.getMessage().contains("Target page, context or browser has been closed")
						|| e.getMessage().contains("Browser has been closed")
						|| e.getMessage().contains("Context has been closed")) {
					log.error("Action '{}' failed due to closed browser/context: {}", actionName, e.getMessage());
					throw e;
				}

				if (attempt == maxRetries) {
					log.error("Action '{}' failed after {} attempts: {}", actionName, maxRetries, e.getMessage());
					throw e;
				}
				log.warn("Action '{}' failed on attempt {}, retrying: {}", actionName, attempt, e.getMessage());
			}
			catch (RuntimeException e) {
				// For runtime exceptions, don't retry
				log.error("Action '{}' failed with non-retryable error: {}", actionName, e.getMessage());
				throw e;
			}
			catch (Exception e) {
				// For checked exceptions, wrap and don't retry
				log.error("Action '{}' failed with non-retryable error: {}", actionName, e.getMessage());
				throw new RuntimeException("Action failed: " + actionName, e);
			}

			// Wait before retry
			try {
				Thread.sleep(retryDelay);
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted during retry delay for action: " + actionName, ie);
			}
		}

		// Should never reach here
		throw new RuntimeException("Unexpected end of retry loop for action: " + actionName);
	}

	/**
	 * Validate driver availability before executing any action
	 */
	protected ToolExecuteResult validateDriver() {
		try {
			com.alibaba.cloud.ai.lynxe.tool.browser.service.DriverWrapper driver = browserUseTool.getDriver();
			if (driver == null) {
				return new ToolExecuteResult("Browser driver is not available");
			}

			// Check if browser is still connected
			if (driver.getBrowser() == null || !driver.getBrowser().isConnected()) {
				return new ToolExecuteResult("Browser is not connected. Please try again or restart the browser.");
			}

			// Check if current page is valid
			com.microsoft.playwright.Page currentPage = driver.getCurrentPage();
			if (currentPage == null || currentPage.isClosed()) {
				return new ToolExecuteResult("Current page is not available. Please navigate to a page first.");
			}
			return null; // Validation passed
		}
		catch (Exception e) {
			log.error("Driver validation failed: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser driver validation failed: " + e.getMessage());
		}
	}

	/**
	 * Convert tool input to BrowserRequestVO
	 */
	protected abstract BrowserRequestVO toBrowserRequestVO(T input);

	/**
	 * Functional interface for action execution
	 */
	@FunctionalInterface
	protected interface ActionExecutor {

		ToolExecuteResult execute() throws Exception;

	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up browser resources for plan: {}", planId);
			browserUseTool.cleanup(planId);
		}
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
