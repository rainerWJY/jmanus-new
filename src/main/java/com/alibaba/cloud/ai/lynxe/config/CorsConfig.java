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
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for Lynxe API endpoints. Only active when
 * {@code lynxe.cors.enabled=true} (default). When embedding Lynxe as a library, set
 * {@code lynxe.cors.enabled=false} so only your app's CORS config applies and you avoid
 * "allowedOrigins(*) + allowCredentials" merge issues with Spring Security.
 *
 * @author Lynxe Team
 */
@Configuration
@ConditionalOnProperty(name = "lynxe.cors.enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// When allowCredentials is true, allowedOrigins cannot be "*". Use explicit
		// origin patterns so merged CORS config (e.g. with @CrossOrigin or Security)
		// does not trigger "allowedOrigins cannot contain '*'".
		String[] originPatterns = new String[] { "http://localhost:*", "http://127.0.0.1:*", "https://localhost:*",
				"https://127.0.0.1:*" };
		registry.addMapping("/api/**")
			.allowedOriginPatterns(originPatterns)
			.allowedMethods("*")
			.allowedHeaders("*")
			.allowCredentials(true);
		// Also apply to root paths (e.g. OpenAI-compatible adapter)
		registry.addMapping("/**")
			.allowedOriginPatterns(originPatterns)
			.allowedMethods("*")
			.allowedHeaders("*")
			.allowCredentials(true);
	}

}
