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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.lynxe.mcp.model.vo.McpServerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Resolves {@code ${property}} placeholders in MCP URL and HTTP headers using the Spring
 * {@link Environment} (system properties, env vars, application.yml, etc.). Used at
 * transport build time so stored config may keep placeholders.
 */
@Component
public class McpPlaceholderResolver {

	private final Environment environment;

	private final ObjectMapper objectMapper;

	public McpPlaceholderResolver(Environment environment, ObjectMapper objectMapper) {
		this.environment = environment;
		this.objectMapper = objectMapper;
	}

	/**
	 * Resolve placeholders in a string. If resolution fails (missing property), returns
	 * the original value so callers can surface validation errors.
	 * @param value raw value, may be null
	 * @return resolved or original value
	 */
	public String resolve(String value) {
		if (value == null || value.isEmpty() || !value.contains("${")) {
			return value;
		}
		try {
			return environment.resolvePlaceholders(value);
		}
		catch (IllegalArgumentException ex) {
			return value;
		}
	}

	/**
	 * Copy of {@code serverConfig} with {@code url} and {@code headers} values resolved.
	 * Other fields are shallow-copied for transport setup; DB-backed config is unchanged.
	 */
	public McpServerConfig resolveForTransport(McpServerConfig serverConfig) {
		if (serverConfig == null) {
			return null;
		}
		McpServerConfig copy = new McpServerConfig(objectMapper);
		copy.setUrl(resolve(serverConfig.getUrl()));
		copy.setCommand(serverConfig.getCommand());
		copy.setArgs(serverConfig.getArgs() != null ? new java.util.ArrayList<>(serverConfig.getArgs())
				: new java.util.ArrayList<>());
		copy.setEnv(serverConfig.getEnv() != null ? new HashMap<>(serverConfig.getEnv()) : new HashMap<>());
		Map<String, String> headers = serverConfig.getHeaders();
		if (headers != null && !headers.isEmpty()) {
			copy.setHeaders(headers.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> resolve(e.getValue()), (a, b) -> b, HashMap::new)));
		}
		copy.setStatus(serverConfig.getStatus());
		copy.setTransportType(serverConfig.getTransportType());
		copy.setIsActive(serverConfig.getIsActive());
		copy.setDescription(serverConfig.getDescription());
		copy.setProviderName(serverConfig.getProviderName());
		return copy;
	}

}
