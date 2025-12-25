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

import com.alibaba.cloud.ai.lynxe.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.BrowserRequestVO;
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.KeyEnterAction;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Key enter browser tool that presses Enter key.
 */
public class KeyEnterBrowserTool extends AbstractBrowserTool<KeyEnterBrowserTool.KeyEnterInput> {

	private static final Logger log = LoggerFactory.getLogger(KeyEnterBrowserTool.class);

	private static final String TOOL_NAME = "key-enter-browser";

	private final ToolI18nService toolI18nService;

	/**
	 * Input class for key_enter operations
	 */
	public static class KeyEnterInput {

		private Integer index;

		// Getters and setters
		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

	}

	public KeyEnterBrowserTool(BrowserUseTool browserUseTool, ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.toolI18nService = toolI18nService;
	}

	@Override
	protected BrowserRequestVO toBrowserRequestVO(KeyEnterInput input) {
		BrowserRequestVO request = new BrowserRequestVO();
		request.setAction("key_enter");
		request.setIndex(input.getIndex());
		return request;
	}

	@Override
	public ToolExecuteResult run(KeyEnterInput input) {
		log.info("KeyEnterBrowserTool request: index={}", input.getIndex());
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			if (input.getIndex() == null) {
				return new ToolExecuteResult("Error: index parameter is required");
			}

			return executeActionWithRetry(() -> new KeyEnterAction(browserUseTool).execute(toBrowserRequestVO(input)),
					"key_enter");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing key_enter: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser key_enter timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing key_enter: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser key_enter failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing key_enter: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser key_enter failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("key-enter-browser");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("key-enter-browser");
	}

	@Override
	public Class<KeyEnterInput> getInputType() {
		return KeyEnterInput.class;
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public String getCurrentToolStateString() {
		return "";
	}

}
