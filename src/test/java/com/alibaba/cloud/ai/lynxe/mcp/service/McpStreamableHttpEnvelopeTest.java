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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.lynxe.mcp.config.McpProperties;
import com.alibaba.cloud.ai.lynxe.mcp.model.po.McpConfigStatus;
import com.alibaba.cloud.ai.lynxe.mcp.model.po.McpConfigType;
import com.alibaba.cloud.ai.lynxe.mcp.model.vo.McpServerConfig;
import com.alibaba.cloud.ai.lynxe.mcp.repository.McpConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cursor / Aliyun-style {@code streamableHttp} + {@code baseUrl} envelope.
 */
@ExtendWith(MockitoExtension.class)
class McpStreamableHttpEnvelopeTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private McpConfigRepository repository;

	@Mock
	private McpCacheManager cacheManager;

	@Mock
	private McpProperties mcpProperties;

	private McpService mcpService;

	@BeforeEach
	void setUp() {
		McpConfigValidator validator = new McpConfigValidator(mcpProperties);
		mcpService = new McpService(repository, validator, cacheManager, objectMapper);
	}

	@Test
	void deserialize_baseUrl_mapsToUrl_andStreamableHttpType_isStreaming() throws Exception {
		String json = """
				{
				  "type": "streamableHttp",
				  "baseUrl": "https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp",
				  "headers": { "Authorization": "Bearer ${DASHSCOPE_API_KEY}" },
				  "isActive": true
				}
				""";
		McpServerConfig config = objectMapper.readValue(json, McpServerConfig.class);
		assertEquals("https://dashscope.aliyuncs.com/api/v1/mcps/WebSearch/mcp", config.getUrl());
		assertEquals(McpConfigType.STREAMING, config.getConnectionType());
		assertEquals("Bearer ${DASHSCOPE_API_KEY}", config.getHeaders().get("Authorization"));
	}

	@Test
	void typeSse_forcesSseEvenWithoutSseInPath() throws Exception {
		String json = """
				{
				  "type": "sse",
				  "url": "https://example.com/mcp"
				}
				""";
		McpServerConfig config = objectMapper.readValue(json, McpServerConfig.class);
		assertEquals(McpConfigType.SSE, config.getConnectionType());
	}

	@Test
	void mcpService_batchImport_setsStreamingAndStatus() throws Exception {
		when(repository.findByMcpServerName("WebSearch")).thenReturn(null);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		String batch = """
				{
				  "mcpServers": {
				    "WebSearch": {
				      "type": "streamableHttp",
				      "baseUrl": "https://www.google.com/api/v1/mcps/WebSearch/mcp",
				      "headers": { "Authorization": "Bearer token" },
				      "isActive": true
				    }
				  }
				}
				""";

		var saved = mcpService.saveMcpServers(batch);
		assertEquals(1, saved.size());
		assertEquals(McpConfigType.STREAMING, saved.get(0).getConnectionType());
		assertEquals(McpConfigStatus.ENABLE, saved.get(0).getStatus());
		JsonNode cfg = objectMapper.readTree(saved.get(0).getConnectionConfig());
		assertTrue(cfg.has("url"));
		assertEquals("https://www.google.com/api/v1/mcps/WebSearch/mcp", cfg.get("url").asText());
		verify(cacheManager).invalidateAllCache();
	}

	@Test
	void mcpService_batchImport_isActiveFalse_disables() throws Exception {
		when(repository.findByMcpServerName("X")).thenReturn(null);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		String batch = """
				{
				  "mcpServers": {
				    "X": {
				      "type": "streamableHttp",
				      "baseUrl": "https://www.google.com/mcp",
				      "isActive": false
				    }
				  }
				}
				""";

		var saved = mcpService.saveMcpServers(batch);
		assertEquals(McpConfigStatus.DISABLE, saved.get(0).getStatus());
	}

	@Test
	void mcpService_batchImport_explicitStatusWinsOverIsActive() throws Exception {
		when(repository.findByMcpServerName("Y")).thenReturn(null);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		String batch = """
				{
				  "mcpServers": {
				    "Y": {
				      "type": "streamableHttp",
				      "baseUrl": "https://www.google.com/mcp",
				      "status": "DISABLE",
				      "isActive": true
				    }
				  }
				}
				""";

		var saved = mcpService.saveMcpServers(batch);
		assertEquals(McpConfigStatus.DISABLE, saved.get(0).getStatus());
	}

}
