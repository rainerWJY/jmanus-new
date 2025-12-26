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

import com.alibaba.cloud.ai.lynxe.tool.browser.actions.BrowserRequestVO;
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.CloseTabAction;
import com.alibaba.cloud.ai.lynxe.tool.browser.service.BrowserUseCommonService;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Close tab browser tool that closes a specified tab (defaults to current tab).
 */
public class CloseTabBrowserTool extends AbstractBrowserTool<CloseTabBrowserTool.CloseTabInput> {

	private static final Logger log = LoggerFactory.getLogger(CloseTabBrowserTool.class);

	private static final String TOOL_NAME = "close-tab-browser";

	private final ToolI18nService toolI18nService;

	/**
	 * Input class for close_tab operations
	 */
	public static class CloseTabInput {

		@JsonProperty("tab_id")
		private Integer tabId;

		// Getters and setters
		public Integer getTabId() {
			return tabId;
		}

		public void setTabId(Integer tabId) {
			this.tabId = tabId;
		}

	}

	public CloseTabBrowserTool(BrowserUseCommonService browserUseTool, ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.toolI18nService = toolI18nService;
	}

	@Override
	protected BrowserRequestVO toBrowserRequestVO(CloseTabInput input) {
		BrowserRequestVO request = new BrowserRequestVO();
		request.setAction("close_tab");
		request.setTabId(input.getTabId());
		return request;
	}

	@Override
	public ToolExecuteResult run(CloseTabInput input) {
		log.info("CloseTabBrowserTool request: tab_id={}", input.getTabId());
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			return executeActionWithRetry(() -> new CloseTabAction(browserUseTool).execute(toBrowserRequestVO(input)),
					"close_tab");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing close_tab: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser close_tab timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing close_tab: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser close_tab failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing close_tab: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser close_tab failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("close-tab-browser");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("close-tab-browser");
	}

	@Override
	public Class<CloseTabInput> getInputType() {
		return CloseTabInput.class;
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
