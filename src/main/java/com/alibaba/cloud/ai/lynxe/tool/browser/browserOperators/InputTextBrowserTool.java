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
import com.alibaba.cloud.ai.lynxe.tool.browser.actions.InputTextAction;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

/**
 * Input text browser tool that inputs text in an element.
 */
public class InputTextBrowserTool extends AbstractBrowserTool<InputTextBrowserTool.InputTextInput> {

	private static final Logger log = LoggerFactory.getLogger(InputTextBrowserTool.class);

	private static final String TOOL_NAME = "input-text-browser";

	private final ToolI18nService toolI18nService;

	/**
	 * Input class for input_text operations
	 */
	public static class InputTextInput {

		private Integer index;

		private String text;

		// Getters and setters
		public Integer getIndex() {
			return index;
		}

		public void setIndex(Integer index) {
			this.index = index;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

	}

	public InputTextBrowserTool(BrowserUseTool browserUseTool, ToolI18nService toolI18nService) {
		super(browserUseTool);
		this.toolI18nService = toolI18nService;
	}

	@Override
	protected BrowserRequestVO toBrowserRequestVO(InputTextInput input) {
		BrowserRequestVO request = new BrowserRequestVO();
		request.setAction("input_text");
		request.setIndex(input.getIndex());
		request.setText(input.getText());
		return request;
	}

	@Override
	public ToolExecuteResult run(InputTextInput input) {
		log.info("InputTextBrowserTool request: index={}", input.getIndex());
		try {
			ToolExecuteResult validation = validateDriver();
			if (validation != null) {
				return validation;
			}

			if (input.getIndex() == null) {
				return new ToolExecuteResult("Error: index parameter is required");
			}

			if (input.getText() == null || input.getText().trim().isEmpty()) {
				return new ToolExecuteResult("Error: text parameter is required");
			}

			return executeActionWithRetry(
					() -> new InputTextAction(browserUseTool).execute(toBrowserRequestVO(input)), "input_text");
		}
		catch (TimeoutError e) {
			log.error("Timeout error executing input_text: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser input_text timed out: " + e.getMessage());
		}
		catch (PlaywrightException e) {
			log.error("Playwright error executing input_text: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser input_text failed due to Playwright error: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error executing input_text: {}", e.getMessage(), e);
			return new ToolExecuteResult("Browser input_text failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("input-text-browser");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("input-text-browser");
	}

	@Override
	public Class<InputTextInput> getInputType() {
		return InputTextInput.class;
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

