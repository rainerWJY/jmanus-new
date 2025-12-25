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
package com.alibaba.cloud.ai.lynxe.tool.browser.actions;

import com.alibaba.cloud.ai.lynxe.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;

public class ClickByElementAction extends BrowserAction {

	private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClickByElementAction.class);

	public ClickByElementAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Integer index = request.getIndex();
		if (index == null) {
			return new ToolExecuteResult("Index is required for 'click' action");
		}

		// Check if element exists
		if (!elementExistsByIdx(index)) {
			return new ToolExecuteResult("Element with index " + index + " not found in ARIA snapshot");
		}

		Page page = getCurrentPage();
		Locator locator = getLocatorByIdx(index);
		if (locator == null) {
			return new ToolExecuteResult("Failed to create locator for element with index " + index);
		}

		String clickResultMessage = clickAndSwitchToNewTabIfOpened(page, () -> {
			// Primary method: Use mouse simulation click
			try {
				log.debug("Attempting primary method: mouse simulation click for element at index {}", index);
				clickWithMouseSimulation(page, locator, index);
				log.info("Successfully clicked element at index {} using mouse simulation (primary method)", index);
			}
			catch (Exception e) {
				log.warn("Primary method (mouse simulation) failed for element with idx {}: {}", index, e.getMessage());
				log.info("Attempting fallback: standard locator.click() for element at index {}", index);
				// Fallback method: Use standard locator.click()
				try {
					// Use a reasonable timeout for element operations (max 10 seconds)
					int elementTimeout = getElementTimeoutMs();
					log.debug("Using element timeout: {}ms for fallback click operations", elementTimeout);

					// Wait for element to be visible and enabled before clicking
					locator.waitFor(new Locator.WaitForOptions().setTimeout(elementTimeout)
						.setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));

					// Try to scroll element into view if needed (non-blocking)
					try {
						locator.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000));
						log.debug("Element scrolled into view successfully for fallback click");
					}
					catch (com.microsoft.playwright.TimeoutError scrollError) {
						log.warn("Failed to scroll element into view, but will attempt to click anyway: {}",
								scrollError.getMessage());
					}

					// Check if element is visible and enabled
					if (!locator.isVisible()) {
						throw new RuntimeException("Element is not visible");
					}

					// Click with explicit timeout and force option
					locator.click(new Locator.ClickOptions().setTimeout(elementTimeout).setForce(false));

					// Add small delay to ensure the action is processed
					Thread.sleep(500);

					log.info("Successfully clicked element at index {} using fallback method (locator.click)", index);
				}
				catch (Exception fallbackException) {
					log.error(
							"Both primary (mouse simulation) and fallback (locator.click) methods failed for element with idx {}: Primary error: {}, Fallback error: {}",
							index, e.getMessage(), fallbackException.getMessage());
					throw new RuntimeException("Primary method (mouse simulation) failed: " + e.getMessage()
							+ ". Fallback method (locator.click) also failed: " + fallbackException.getMessage(), e);
				}
			}
		});
		return new ToolExecuteResult("Successfully clicked element at index " + index + " " + clickResultMessage);
	}

	/**
	 * Primary method: Simulate mouse movement to element center and click This method
	 * moves the mouse to the center of the element and performs a click
	 * @param page The Playwright Page instance
	 * @param locator The Locator for the element
	 * @param index The element index for logging
	 * @throws RuntimeException if the mouse simulation fails
	 */
	private void clickWithMouseSimulation(Page page, Locator locator, Integer index) {
		try {
			// Get element bounding box to calculate center coordinates
			BoundingBox box = locator.boundingBox(new Locator.BoundingBoxOptions().setTimeout(5000));
			if (box == null) {
				throw new RuntimeException("Failed to get bounding box for element at index " + index);
			}

			// Calculate center point of the element
			double centerX = box.x + box.width / 2.0;
			double centerY = box.y + box.height / 2.0;

			log.debug("Element at index {} bounding box: x={}, y={}, width={}, height={}", index, box.x, box.y,
					box.width, box.height);
			log.debug("Calculated center point: ({}, {})", centerX, centerY);

			// Scroll element into view if needed (non-blocking)
			try {
				locator.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000));
				log.debug("Element scrolled into view for mouse simulation");
			}
			catch (com.microsoft.playwright.TimeoutError scrollError) {
				log.warn("Failed to scroll element into view for mouse simulation, but will attempt click anyway: {}",
						scrollError.getMessage());
			}

			// Recalculate bounding box after scrolling (in case position changed)
			BoundingBox updatedBox = locator.boundingBox(new Locator.BoundingBoxOptions().setTimeout(3000));
			if (updatedBox != null) {
				centerX = updatedBox.x + updatedBox.width / 2.0;
				centerY = updatedBox.y + updatedBox.height / 2.0;
				log.debug("Updated center point after scroll: ({}, {})", centerX, centerY);
			}

			// Move mouse to the center of the element
			page.mouse().move(centerX, centerY);
			log.debug("Mouse moved to position ({}, {})", centerX, centerY);

			// Small delay to ensure mouse movement is registered
			Thread.sleep(100);

			// Click at the center position
			page.mouse().click(centerX, centerY);
			log.info("Mouse clicked at position ({}, {}) for element at index {}", centerX, centerY, index);

			// Add small delay to ensure the action is processed
			Thread.sleep(500);

		}
		catch (com.microsoft.playwright.TimeoutError e) {
			log.error("Timeout getting bounding box for mouse simulation on element with idx {}: {}", index,
					e.getMessage());
			throw new RuntimeException("Timeout getting element bounding box for mouse simulation: " + e.getMessage(),
					e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Interrupted during mouse simulation for element with idx {}: {}", index, e.getMessage());
			throw new RuntimeException("Interrupted during mouse simulation", e);
		}
		catch (Exception e) {
			log.error("Error during mouse simulation click on element with idx {}: {}", index, e.getMessage());
			throw new RuntimeException("Error during mouse simulation click: " + e.getMessage(), e);
		}
	}

}
