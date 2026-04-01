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
package com.alibaba.cloud.ai.lynxe.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Lynxe root redirect controller. When enabled (lynxe.web.root-redirect-enabled=true,
 * default), GET "/" redirects to lynxe.web.init-path. Set root-redirect-enabled to false
 * when embedding Lynxe so the host app can map "/" itself.
 *
 * @author dahua
 * @time 2025/7/31
 */
@Controller
@ConditionalOnProperty(name = "lynxe.web.root-redirect-enabled", havingValue = "true", matchIfMissing = true)
public class RedirectController {

	private final LynxeProperties lynxeProperties;

	public RedirectController(LynxeProperties lynxeProperties) {
		this.lynxeProperties = lynxeProperties;
	}

	@RequestMapping("/")
	public String redirect() {
		return lynxeProperties.getWeb().getInitPath();
	}

}
