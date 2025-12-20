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
package com.alibaba.cloud.ai.lynxe.tool.image;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.alibaba.cloud.ai.lynxe.model.entity.DynamicModelEntity;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Image generation provider for Alibaba Tongyi Qwen (通义千问) API
 * Uses DashScope multimodal generation API
 * Handles Qwen image generation and editing models only
 * For Wanx models, use WanxImageGenerationProvider
 */
@Component
public class TongyiImageGenerationProvider implements ImageGenerationProvider {

	private static final Logger log = LoggerFactory.getLogger(TongyiImageGenerationProvider.class);

	private final ObjectProvider<RestClient.Builder> restClientBuilderProvider;

	private final ObjectMapper objectMapper;

	public TongyiImageGenerationProvider(ObjectProvider<RestClient.Builder> restClientBuilderProvider,
			ObjectMapper objectMapper) {
		this.restClientBuilderProvider = restClientBuilderProvider;
		this.objectMapper = objectMapper;
	}

	/**
	 * Check if this provider supports the given model
	 * 
	 * Supported models include:
	 * 
	 * Qwen Image Generation (通义千问文生图):
	 *   - qwen-image-plus, qwen-image
	 *   Endpoint: /api/v1/services/aigc/multimodal-generation/generation (synchronous)
	 * 
	 * Qwen Image Editing (通义千问图像编辑):
	 *   - qwen-image-edit-plus, qwen-image-edit-plus-2025-10-30, qwen-image-edit
	 *   Endpoint: /api/v1/services/aigc/multimodal-generation/generation (synchronous)
	 * 
	 * Note: For Wanx models, use WanxImageGenerationProvider
	 */
	@Override
	public boolean supports(DynamicModelEntity modelEntity, String modelName) {
		if (modelEntity == null || modelName == null) {
			return false;
		}

		String baseUrl = modelEntity.getBaseUrl();
		if (baseUrl == null) {
			return false;
		}

		String lowerBaseUrl = baseUrl.toLowerCase();
		String lowerModelName = modelName.toLowerCase();

		// Step 1: Validate URI - must be dashscope
		boolean isDashScope = lowerBaseUrl.contains("dashscope.aliyuncs.com");
		if (!isDashScope) {
			return false;
		}

		// Step 2: Validate modelName - check for supported Qwen image generation models only
		
		// Qwen Image Generation Models (通义千问文生图)
		boolean isQwenImagePlus = lowerModelName.contains("qwen-image-plus");
		boolean isQwenImage = lowerModelName.contains("qwen-image") && !lowerModelName.contains("edit");
		
		// Qwen Image Editing Models (通义千问图像编辑)
		boolean isQwenImageEdit = lowerModelName.contains("qwen-image-edit");
		
		// Check if model matches any supported Qwen pattern
		if (isQwenImagePlus || isQwenImage || isQwenImageEdit) {
			log.debug("Detected Qwen image generation: model={}, baseUrl={}", modelName, baseUrl);
			return true;
		}

		return false;
	}
	@Override
	public ToolExecuteResult generateImage(ImageGenerationRequest request, DynamicModelEntity modelEntity,
			String modelName, String rootPlanId) {
		try {
			// Validate API key
			String apiKey = modelEntity.getApiKey();
			if (apiKey == null || apiKey.trim().isEmpty()) {
				throw new IllegalArgumentException("API key is required for Qwen API");
			}

			// Get base URL from model entity or use default
			String rawBaseUrl = modelEntity.getBaseUrl();
			String baseUrl = AbstractBaseTool.normalizeBaseUrl(rawBaseUrl);
			if (baseUrl == null || baseUrl.trim().isEmpty()) {
				baseUrl = "https://dashscope.aliyuncs.com";
			}

			// Build request body in Qwen format
			Map<String, Object> requestBody = new HashMap<>();
			requestBody.put("model", modelName);

			// Build input.messages array
			Map<String, Object> input = new HashMap<>();
			Map<String, Object> userMessage = new HashMap<>();
			userMessage.put("role", "user");

			// Build content array with text
			Map<String, Object> textContent = new HashMap<>();
			textContent.put("text", request.getPrompt());
			userMessage.put("content", List.of(textContent));
			input.put("messages", List.of(userMessage));
			requestBody.put("input", input);

			// Build parameters
			Map<String, Object> parameters = new HashMap<>();
			parameters.put("negative_prompt", "");
			parameters.put("prompt_extend", true);
			parameters.put("watermark", false);

			// Parse size from request (e.g., "1024x1024" -> "1024*1024")
			String size = "1024*1024"; // Default
			if (request.getSize() != null && !request.getSize().trim().isEmpty()) {
				String sizeStr = request.getSize().trim();
				// Convert "1024x1024" to "1024*1024"
				size = sizeStr.replace("x", "*");
			}
			parameters.put("size", size);
			requestBody.put("parameters", parameters);

			// Log the request JSON for debugging
			try {
				String jsonRequest = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
				log.debug("Qwen API Request JSON:\n{}", jsonRequest);
			}
			catch (Exception e) {
				log.warn("Failed to serialize request for logging: {}", e.getMessage());
			}

			// Create RestClient with Qwen API configuration
			RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
			RestClient restClient;
			if (restClientBuilder != null) {
				restClient = restClientBuilder.clone()
					.baseUrl(baseUrl)
					.defaultHeader("Authorization", "Bearer " + apiKey)
					.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.build();
			}
			else {
				restClient = RestClient.builder()
					.baseUrl(baseUrl)
					.defaultHeader("Authorization", "Bearer " + apiKey)
					.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.build();
			}

			// Build API endpoint - Qwen uses synchronous multimodal-generation endpoint
			String endpoint = "/api/v1/services/aigc/multimodal-generation/generation";
			log.debug("Calling Qwen API: {}{}", baseUrl, endpoint);

			// Make the API call
			String responseJson = restClient.post()
				.uri(endpoint)
				.body(requestBody)
				.retrieve()
				.body(String.class);

			if (responseJson == null || responseJson.trim().isEmpty()) {
				return new ToolExecuteResult("No response received from Qwen API");
			}

			log.debug("Qwen API Response received (length: {} characters)", responseJson.length());

			// Extract images from Qwen response format
			List<String> imageUrls = extractImagesFromResponse(responseJson);

			if (imageUrls.isEmpty()) {
				// Check if there's an error in the response
				try {
					@SuppressWarnings("unchecked")
					Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
					Object error = responseMap.get("error");
					if (error != null) {
						String errorMsg = error.toString();
						log.error("Qwen API returned error: {}", errorMsg);
						return new ToolExecuteResult("Qwen API error: " + errorMsg);
					}
				}
				catch (Exception e) {
					log.debug("Failed to parse error from response: {}", e.getMessage());
				}
				return new ToolExecuteResult("No images found in Qwen API response");
			}

			// Return result as JSON string
			Map<String, Object> resultMap = new HashMap<>();
			resultMap.put("images", imageUrls);
			resultMap.put("count", imageUrls.size());
			resultMap.put("prompt", request.getPrompt());
			if (modelName != null && !modelName.trim().isEmpty()) {
				resultMap.put("model", modelName);
			}
			if (request.getSize() != null) {
				resultMap.put("size", request.getSize());
			}
			if (request.getQuality() != null) {
				resultMap.put("quality", request.getQuality());
			}
			resultMap.put("method", "qwen");

			String resultJson = objectMapper.writeValueAsString(resultMap);
			log.info("Qwen image generation successful: {} image(s) generated", imageUrls.size());
			return new ToolExecuteResult(resultJson);
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			log.error("JSON processing error in Qwen image generation: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to serialize image generation result", e);
		}
		catch (Exception e) {
			log.error("Error in Qwen image generation: {}", e.getMessage(), e);
			throw e; // Re-throw to be handled by run() method
		}
	}

	/**
	 * Extract images from Qwen API response
	 * Qwen returns images in output.choices[].message.content[].image format
	 * @param responseJson JSON response string from Qwen API
	 * @return List of image URLs
	 */
	@SuppressWarnings("unchecked")
	private List<String> extractImagesFromResponse(String responseJson) {
		List<String> imageUrls = new ArrayList<>();

		try {
			Map<String, Object> responseMap = objectMapper.readValue(responseJson, Map.class);
			Map<String, Object> output = (Map<String, Object>) responseMap.get("output");

			if (output == null) {
				log.debug("No output found in Qwen API response");
				return imageUrls;
			}

			List<Map<String, Object>> choices = (List<Map<String, Object>>) output.get("choices");
			if (choices == null || choices.isEmpty()) {
				log.debug("No choices found in Qwen API response");
				return imageUrls;
			}

			for (Map<String, Object> choice : choices) {
				Map<String, Object> message = (Map<String, Object>) choice.get("message");
				if (message == null) {
					continue;
				}

				List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
				if (content == null || content.isEmpty()) {
					continue;
				}

				for (Map<String, Object> contentItem : content) {
					String image = (String) contentItem.get("image");
					if (image != null && !image.trim().isEmpty()) {
						imageUrls.add(image);
						log.debug("Extracted image from Qwen response: {}", image);
					}
				}
			}
		}
		catch (Exception e) {
			log.error("Error extracting images from Qwen API response: {}", e.getMessage(), e);
		}

		return imageUrls;
	}

}
