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
package com.alibaba.cloud.ai.lynxe.mcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import com.alibaba.cloud.ai.lynxe.mcp.model.vo.McpServerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

class McpPlaceholderResolverTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setKey() {
		System.setProperty("DASHSCOPE_API_KEY", "resolved-test-key");
	}

	@AfterEach
	void clearKey() {
		System.clearProperty("DASHSCOPE_API_KEY");
	}

	@Test
	void resolveForTransport_expandsHeaderPlaceholders() {
		McpPlaceholderResolver resolver = new McpPlaceholderResolver(new StandardEnvironment(), objectMapper);
		McpServerConfig raw = new McpServerConfig(objectMapper);
		raw.setUrl("https://example.com/mcp");
		raw.setHeaders(Map.of("Authorization", "Bearer ${DASHSCOPE_API_KEY}"));

		McpServerConfig resolved = resolver.resolveForTransport(raw);
		assertEquals("Bearer resolved-test-key", resolved.getHeaders().get("Authorization"));
		assertEquals("https://example.com/mcp", resolved.getUrl());
	}

	@Test
	void resolve_returnsOriginalWhenPropertyMissing() {
		McpPlaceholderResolver resolver = new McpPlaceholderResolver(new StandardEnvironment(), objectMapper);
		String in = "Bearer ${THIS_PROPERTY_DEFINITELY_DOES_NOT_EXIST_12345}";
		assertEquals(in, resolver.resolve(in));
	}

}
