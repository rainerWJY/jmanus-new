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
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.ScreenShotAction;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Screenshot browser tool that captures a screenshot.
 */
public class ScreenshotBrowserTool extends AbstractBrowserTool<ScreenshotBrowserTool.ScreenshotInput> {

	private static final Logger log = LoggerFactory.getLogger(ScreenshotBrowserTool.class);

	private static final String TOOL_NAME = "screenshot-browser";

	private final ToolI18nService toolI18nService;

	/**
	 * Input class for screenshot operations
	 */
	public static class ScreenshotInput {

		// No parameters needed for screenshot

	}

	public ScreenshotBrowserTool(BrowserUseTool browserUseTool, ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.toolI18nService = toolI18nService;
	}

	@Override
	protected BrowserRequestVO toBrowserRequestVO(ScreenshotInput input) {
		BrowserRequestVO request = new BrowserRequestVO();
		request.setAction("screenshot");
		return request;
	}

	@Override
	public ToolExecuteResult run(ScreenshotInput input) {
		log.info("ScreenshotBrowserTool request");
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			return executeActionWithRetry(() -> new ScreenShotAction(browserUseTool).execute(toBrowserRequestVO(input)),
					"screenshot");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing screenshot: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser screenshot timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing screenshot: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser screenshot failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing screenshot: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser screenshot failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("screenshot-browser");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("screenshot-browser");
	}

	@Override
	public Class<ScreenshotInput> getInputType() {
		return ScreenshotInput.class;
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
