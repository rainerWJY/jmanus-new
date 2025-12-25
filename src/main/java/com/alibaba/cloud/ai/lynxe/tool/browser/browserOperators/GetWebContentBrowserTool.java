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
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.WriteCurrentWebContentAction;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.TextFileService;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Get web content browser tool that gets current page ARIA snapshot content.
 */
public class GetWebContentBrowserTool extends AbstractBrowserTool<GetWebContentBrowserTool.GetWebContentInput> {

	private static final Logger log = LoggerFactory.getLogger(GetWebContentBrowserTool.class);

	private static final String TOOL_NAME = "get-web-content-browser";

	private final ToolI18nService toolI18nService;

	private final TextFileService textFileService;

	/**
	 * Input class for get_web_content operations
	 */
	public static class GetWebContentInput {

		// No parameters needed for get_web_content

	}

	public GetWebContentBrowserTool(BrowserUseTool browserUseTool, TextFileService textFileService,
			ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.textFileService = textFileService;
		this.toolI18nService = toolI18nService;
	}

	@Override
	protected BrowserRequestVO toBrowserRequestVO(GetWebContentInput input) {
		BrowserRequestVO request = new BrowserRequestVO();
		request.setAction("get_web_content");
		return request;
	}

	@Override
	public ToolExecuteResult run(GetWebContentInput input) {
		log.info("GetWebContentBrowserTool request");
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			return executeActionWithRetry(() -> new WriteCurrentWebContentAction(browserUseTool, textFileService)
				.execute(toBrowserRequestVO(input)), "get_web_content");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing get_web_content: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser get_web_content timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing get_web_content: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser get_web_content failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing get_web_content: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser get_web_content failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("get-web-content-browser");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("get-web-content-browser");
	}

	@Override
	public Class<GetWebContentInput> getInputType() {
		return GetWebContentInput.class;
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
