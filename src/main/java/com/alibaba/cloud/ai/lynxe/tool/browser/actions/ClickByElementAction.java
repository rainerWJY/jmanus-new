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
			try {
				// Use a reasonable timeout for element operations (max 10 seconds)
				int elementTimeout = getElementTimeoutMs();
				log.debug("Using element timeout: {}ms for click operations", elementTimeout);

				// Special handling for checkbox inputs: try to find and click the
				// associated label
				// This is especially important for Element UI checkboxes where the input
				// is hidden
				Object checkboxCheckResult = locator
					.evaluate("el => el && el.tagName === 'INPUT' && el.type === 'checkbox'");
				Boolean isCheckbox = checkboxCheckResult instanceof Boolean ? (Boolean) checkboxCheckResult : null;
				if (Boolean.TRUE.equals(isCheckbox)) {
					log.debug("Element at index {} is a checkbox input, attempting to find associated label", index);

					// Use JavaScript to find and click the label element instead
					// This handles Element UI checkboxes where the input is hidden
					// and standard HTML checkboxes with label associations
					try {
						Object jsClickResult = locator.evaluate(
								"""
										(el) => {
											// First, try to find parent label (works for Element UI and standard HTML)
											let label = el.closest('label');

											// If not found, try to find label by 'for' attribute matching input's id
											if (!label && el.id) {
												label = document.querySelector('label[for="' + el.id + '"]');
											}

											// For Element UI, also check for parent with class 'el-checkbox'
											// This handles cases where label wraps the checkbox
											if (!label) {
												let parent = el.parentElement;
												while (parent && parent !== document.body) {
													if (parent.tagName === 'LABEL' && parent.classList.contains('el-checkbox')) {
														label = parent;
														break;
													}
													parent = parent.parentElement;
												}
											}

											// If label found, click it
											if (label) {
												label.click();
												return true;
											}
											return false;
										}
										""");
						Boolean labelClicked = jsClickResult instanceof Boolean ? (Boolean) jsClickResult : null;

						if (Boolean.TRUE.equals(labelClicked)) {
							log.debug("Successfully clicked label for checkbox using JavaScript");
							Thread.sleep(500);
							return;
						}
						else {
							log.debug("No associated label found for checkbox, will try clicking input directly");
						}
					}
					catch (Exception jsError) {
						log.debug("JavaScript label click failed, will try clicking input directly: {}",
								jsError.getMessage());
					}
				}

				// For other elements, use standard waiting strategy
				// Wait for element to be visible and enabled before clicking
				locator.waitFor(new Locator.WaitForOptions().setTimeout(elementTimeout)
					.setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));

				// Try to scroll element into view if needed (non-blocking)
				try {
					locator.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(3000));
					log.debug("Element scrolled into view successfully");
				}
				catch (com.microsoft.playwright.TimeoutError scrollError) {
					log.warn("Failed to scroll element into view, but will attempt to click anyway: {}",
							scrollError.getMessage());
				}

				// Check if element is visible and enabled
				if (!locator.isVisible()) {
					throw new RuntimeException("Element is not visible");
				}

				// Wait for any loading indicators in overlays to disappear before
				// clicking
				// This handles cases where dialogs/overlays are still loading
				try {
					page.waitForFunction(
							"""
									() => {
										// Check if there are any visible loading indicators in overlays
										const loadingElements = document.querySelectorAll('.next-loading, .deep-loading, [class*="loading"]');
										for (const el of loadingElements) {
											const style = window.getComputedStyle(el);
											if (style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0') {
												// Check if it's inside an overlay
												const overlay = el.closest('.next-overlay-wrapper, .next-overlay-inner, [class*="overlay"]');
												if (overlay) {
													return false; // Still loading
												}
											}
										}
										return true; // No loading indicators found
									}
									""");
					log.debug("Waited for overlay loading to complete");
				}
				catch (Exception waitError) {
					log.debug("No loading indicators found or timeout waiting for loading: {}", waitError.getMessage());
					// Continue anyway, as this is not critical
				}

				// Try to click with standard method first
				try {
					locator.click(new Locator.ClickOptions().setTimeout(elementTimeout).setForce(false));
				}
				catch (com.microsoft.playwright.TimeoutError overlayError) {
					// If click fails due to overlay intercepting pointer events, try
					// JavaScript click as fallback
					String errorMessage = overlayError.getMessage();
					if (errorMessage != null && (errorMessage.contains("intercepts pointer events")
							|| errorMessage.contains("element is not actionable"))) {
						log.warn(
								"Click intercepted by overlay or element not actionable, attempting JavaScript click as fallback for element at index {}",
								index);
						try {
							// Wait a bit for the overlay to stabilize
							Thread.sleep(300);
							// Scroll element into view first
							try {
								locator.scrollIntoViewIfNeeded();
								Thread.sleep(100);
							}
							catch (Exception scrollEx) {
								log.debug("Scroll failed, continuing with click: {}", scrollEx.getMessage());
							}
							// Use JavaScript to directly trigger click event, bypassing
							// Playwright's clickability checks
							// Try multiple methods to ensure click works
							try {
								// Method 1: Direct click
								locator.evaluate("el => el.click()");
								log.debug("Successfully clicked element using JavaScript direct click");
							}
							catch (Exception directClickError) {
								log.debug("Direct click failed, trying MouseEvent dispatch: {}",
										directClickError.getMessage());
								// Method 2: Dispatch MouseEvent
								locator.evaluate("""
										(el) => {
											const event = new MouseEvent('click', {
												bubbles: true,
												cancelable: true,
												view: window
											});
											el.dispatchEvent(event);
										}
										""");
								log.debug("Successfully clicked element using MouseEvent dispatch");
							}
						}
						catch (Exception jsError) {
							log.warn("JavaScript click also failed, trying force click: {}", jsError.getMessage());
							// Last resort: use force click
							try {
								locator.click(new Locator.ClickOptions().setTimeout(elementTimeout).setForce(true));
							}
							catch (Exception forceError) {
								// If force click also fails, try JavaScript one more time
								// with simpler approach
								log.warn("Force click also failed, trying simple JavaScript click: {}",
										forceError.getMessage());
								locator.evaluate("el => { el.click(); }");
							}
						}
					}
					else {
						// Re-throw if it's not an overlay interception error
						throw overlayError;
					}
				}

				// Add small delay to ensure the action is processed
				Thread.sleep(500);

			}
			catch (com.microsoft.playwright.TimeoutError e) {
				log.error("Timeout waiting for element with idx {} to be ready for click: {}", index, e.getMessage());
				throw new RuntimeException("Timeout waiting for element to be ready for click: " + e.getMessage(), e);
			}
			catch (Exception e) {
				log.error("Error during click on element with idx {}: {}", index, e.getMessage());
				if (e instanceof RuntimeException) {
					throw (RuntimeException) e;
				}
				throw new RuntimeException("Error clicking element: " + e.getMessage(), e);
			}
		});
		return new ToolExecuteResult("Successfully clicked element at index " + index + " " + clickResultMessage);
	}

}
