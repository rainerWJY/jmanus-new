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
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.ClickByElementAction;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Click browser tool that clicks an element by index.
 */
public class ClickBrowserTool extends AbstractBrowserTool<ClickBrowserTool.ClickInput> {

	private static final Logger log = LoggerFactory.getLogger(ClickBrowserTool.class);

	private static final String TOOL_NAME = "click-browser";

	private final ToolI18nService toolI18nService;

	/**
	 * Input class for click operations
	 */
	public static class ClickInput {

		private Integer index;

		// Getters and setters
		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

	}

	public ClickBrowserTool(BrowserUseTool browserUseTool, ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.toolI18nService = toolI18nService;
	}

	@Override
	protected BrowserRequestVO toBrowserRequestVO(ClickInput input) {
		BrowserRequestVO request = new BrowserRequestVO();
		request.setAction("click");
		request.setIndex(input.getIndex());
		return request;
	}

	@Override
	public ToolExecuteResult run(ClickInput input) {
		log.info("ClickBrowserTool request: index={}", input.getIndex());
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			if (input.getIndex() == null) {
				return new ToolExecuteResult("Error: index parameter is required");
			}

			return executeActionWithRetry(
					() -> new ClickByElementAction(browserUseTool).execute(toBrowserRequestVO(input)), "click");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing click: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser click timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing click: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser click failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing click: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser click failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("click-browser");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("click-browser");
	}

	@Override
	public Class<ClickInput> getInputType() {
		return ClickInput.class;
	}

	@Override
	public String getServiceGroup() {
		return "browser-service-group";
	}

	@Override
	public String getCurrentToolStateString() {
		return "";
	}

}
