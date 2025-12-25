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
package com.alibaba.cloud.ai.lynxe.llm;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service to automatically limit conversation memory size based on character count. Uses
 * LLM to summarize older dialog rounds while maintaining recent 5000 characters.
 *
 * @author lynxe
 */
@Service
public class ConversationMemoryLimitService {

	private static final Logger log = LoggerFactory.getLogger(ConversationMemoryLimitService.class);

	private static final int RECENT_CHARS_TO_KEEP = 5000;

	private static final int SUMMARY_MIN_CHARS = 3000;

	private static final int SUMMARY_MAX_CHARS = 4000;

	private static final double RETENTION_RATIO = 0.4; // Retain 40% of content

	private static final String COMPRESSION_CONFIRMATION_MESSAGE = "Got it. Thanks for the additional context!";

	@Autowired
	private LynxeProperties lynxeProperties;

	@Autowired
	private LlmService llmService;

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * Check and limit conversation memory size for a given conversation ID. Maintains
	 * recent 5000 chars (at least one complete dialog round) and summarizes older rounds
	 * into a 3000-4000 char UserMessage.
	 * @param chatMemory The chat memory instance
	 * @param conversationId The conversation ID to check and limit
	 */
	public void checkAndLimitMemory(ChatMemory chatMemory, String conversationId) {
		if (chatMemory == null || conversationId == null || conversationId.trim().isEmpty()) {
			return;
		}

		try {
			List<Message> messages = chatMemory.get(conversationId);
			if (messages == null || messages.isEmpty()) {
				return;
			}

			int totalChars = calculateTotalCharacters(messages);
			int maxChars = getMaxCharacterCount();
			if (totalChars <= maxChars) {
				log.debug("Conversation memory size ({}) is within limit ({}) for conversationId: {}", totalChars,
						maxChars, conversationId);
				return;
			}

			log.info(
					"Conversation memory size ({}) exceeds limit ({}) for conversationId: {}. Summarizing older messages...",
					totalChars, maxChars, conversationId);

			// Summarize and trim messages
			summarizeAndTrimMessages(chatMemory, conversationId, messages);

		}
		catch (Exception e) {
			log.warn("Failed to check and limit conversation memory for conversationId: {}", conversationId, e);
		}
	}

	/**
	 * Calculate total character count of all messages by serializing to JSON. This gives
	 * a more accurate count of the actual data that would be sent to LLM.
	 * @param messages List of messages
	 * @return Total character count
	 */
	public int calculateTotalCharacters(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return 0;
		}

		try {
			// Directly serialize the entire messages list to JSON
			String json = objectMapper.writeValueAsString(messages);
			return json.length();
		}
		catch (Exception e) {
			log.warn("Failed to serialize messages to JSON for character count calculation: {}", e.getMessage());
			// Fallback to simple text length calculation
			return messages.stream().mapToInt(message -> {
				String text = message.getText();
				return (text != null && !text.trim().isEmpty()) ? text.length() : 0;
			}).sum();
		}
	}

	/**
	 * Extract text content from a message.
	 * @param message The message
	 * @return Text content, or empty string if content cannot be extracted
	 */
	private String extractMessageContent(Message message) {
		if (message == null) {
			return "";
		}

		try {
			StringBuilder content = new StringBuilder();

			// Extract text content
			String text = message.getText();
			if (text != null && !text.isEmpty()) {
				content.append(text);
			}

			// Extract tool calls from AssistantMessage
			if (message instanceof AssistantMessage assistantMessage) {
				var toolCalls = assistantMessage.getToolCalls();
				if (toolCalls != null && !toolCalls.isEmpty()) {
					if (content.length() > 0) {
						content.append("\n");
					}
					content.append("[Tool Calls: ");
					for (int i = 0; i < toolCalls.size(); i++) {
						var toolCall = toolCalls.get(i);
						if (i > 0) {
							content.append(", ");
						}
						content.append(toolCall.name()).append("(").append(toolCall.arguments()).append(")");
					}
					content.append("]");
				}
			}
			// Extract tool responses from ToolResponseMessage
			else if (message instanceof ToolResponseMessage toolResponseMessage) {
				var responses = toolResponseMessage.getResponses();
				if (responses != null && !responses.isEmpty()) {
					if (content.length() > 0) {
						content.append("\n");
					}
					content.append("[Tool Responses: ");
					for (int i = 0; i < responses.size(); i++) {
						var response = responses.get(i);
						if (i > 0) {
							content.append(", ");
						}
						String responseData = response.responseData();
						// Limit response data length to avoid too long content
						if (responseData != null && responseData.length() > 200) {
							responseData = responseData.substring(0, 200) + "...";
						}
						content.append(responseData);
					}
					content.append("]");
				}
			}

			return content.toString();
		}
		catch (Exception e) {
			log.debug("Failed to extract content from message: {}", e.getMessage());
			return "";
		}
	}

	/**
	 * Summarize and trim messages: retain 40% of content (by character count), ensuring
	 * at least one complete round is kept. Summarize older rounds into a 3000-4000 char
	 * UserMessage.
	 * @param chatMemory The chat memory instance
	 * @param conversationId The conversation ID
	 * @param messages Current list of messages
	 */
	private void summarizeAndTrimMessages(ChatMemory chatMemory, String conversationId, List<Message> messages) {
		// Group messages into dialog rounds (UserMessage + AssistantMessage pairs)
		List<DialogRound> dialogRounds = groupMessagesIntoRounds(messages);

		if (dialogRounds.isEmpty()) {
			log.warn("No dialog rounds found for conversationId: {}", conversationId);
			return;
		}

		// Calculate total character count of all rounds
		int totalChars = dialogRounds.stream().mapToInt(DialogRound::getTotalChars).sum();

		// Calculate target retention: 40% of total content
		int targetRetentionChars = (int) (totalChars * RETENTION_RATIO);

		// If total is very small, keep all rounds
		if (totalChars <= 0 || targetRetentionChars <= 0) {
			log.debug("Total character count ({}) is too small, keeping all rounds for conversationId: {}", totalChars,
					conversationId);
			return;
		}

		// Find which rounds to keep and which to summarize
		// Strategy: Keep rounds from newest to oldest until accumulated chars reach 40%
		// retention
		List<DialogRound> roundsToKeep = new ArrayList<>();
		List<DialogRound> roundsToSummarize = new ArrayList<>();

		int accumulatedChars = 0;
		boolean hasKeptAtLeastOneRound = false;

		// Start from the newest round and work backwards
		for (int i = dialogRounds.size() - 1; i >= 0; i--) {
			DialogRound round = dialogRounds.get(i);
			int roundChars = round.getTotalChars();

			// Always keep at least the newest round (even if it exceeds 40%)
			if (i == dialogRounds.size() - 1) {
				roundsToKeep.add(0, round);
				accumulatedChars += roundChars;
				hasKeptAtLeastOneRound = true;
			}
			else {
				// For other rounds, check if we can add them within 40% retention limit
				if (accumulatedChars + roundChars <= targetRetentionChars) {
					roundsToKeep.add(0, round); // Add at beginning to maintain
												// chronological order
					accumulatedChars += roundChars;
					hasKeptAtLeastOneRound = true;
				}
				else {
					// Can't add this round, all remaining are older and should be
					// summarized
					for (int j = i; j >= 0; j--) {
						roundsToSummarize.add(0, dialogRounds.get(j));
					}
					break;
				}
			}
		}

		// Ensure we kept at least one round (fallback if somehow no rounds were kept)
		if (!hasKeptAtLeastOneRound && !dialogRounds.isEmpty()) {
			DialogRound newestRound = dialogRounds.get(dialogRounds.size() - 1);
			roundsToKeep.add(newestRound);
			// Add all others to summarize
			for (int i = 0; i < dialogRounds.size() - 1; i++) {
				roundsToSummarize.add(dialogRounds.get(i));
			}
		}

		// Summarize older rounds
		UserMessage summaryMessage = null;
		if (!roundsToSummarize.isEmpty()) {
			summaryMessage = summarizeRounds(roundsToSummarize);
		}

		// Rebuild memory: summary first (as UserMessage), then confirmation (as
		// AssistantMessage), then recent rounds
		// This maintains the user-assistant message pair pattern similar to
		// state_snapshot storage
		chatMemory.clear(conversationId);

		if (summaryMessage != null) {
			// Add summary as UserMessage (like state_snapshot)
			chatMemory.add(conversationId, summaryMessage);
			// Add confirmation AssistantMessage to maintain user-assistant pair pattern
			AssistantMessage confirmationMessage = new AssistantMessage(COMPRESSION_CONFIRMATION_MESSAGE);
			chatMemory.add(conversationId, confirmationMessage);
			log.info("Added summarized message ({} chars) with confirmation for conversationId: {}",
					summaryMessage.getText().length(), conversationId);
		}

		// Add recent rounds
		for (DialogRound round : roundsToKeep) {
			for (Message message : round.getMessages()) {
				chatMemory.add(conversationId, message);
			}
		}

		int keptChars = calculateTotalCharacters(
				roundsToKeep.stream().flatMap(round -> round.getMessages().stream()).toList());
		double actualRetentionRatio = totalChars > 0 ? (double) keptChars / totalChars : 0.0;
		log.info(
				"Summarized conversation memory for conversationId: {}. Kept {} recent rounds ({} chars, {:.1f}% retention), summarized {} older rounds into {} chars",
				conversationId, roundsToKeep.size(), keptChars, String.format("%.1f", actualRetentionRatio * 100),
				roundsToSummarize.size(), summaryMessage != null ? summaryMessage.getText().length() : 0);
	}

	/**
	 * Group messages into dialog rounds. Supports three grouping scenarios:
	 * <ol>
	 * <li>UserMessage -> AssistantMessage -> ToolResponseMessage (complete round with
	 * tool call)</li>
	 * <li>UserMessage -> AssistantMessage (round without tool call)</li>
	 * <li>AssistantMessage -> ToolResponseMessage (agent memory scenario)</li>
	 * </ol>
	 * @param messages List of messages
	 * @return List of dialog rounds
	 */
	private List<DialogRound> groupMessagesIntoRounds(List<Message> messages) {
		List<DialogRound> rounds = new ArrayList<>();
		DialogRound currentRound = null;

		for (Message message : messages) {
			if (message instanceof UserMessage) {
				// Scenario: UserMessage starts a new round
				// Can be followed by AssistantMessage (with or without
				// ToolResponseMessage)
				// Complete previous round if exists
				if (currentRound != null) {
					rounds.add(currentRound);
				}
				// Start new round with UserMessage
				currentRound = new DialogRound();
				currentRound.addMessage(message);
			}
			else if (message instanceof AssistantMessage) {
				// Check if current round has UserMessage (Scenario 2: UserMessage ->
				// AssistantMessage)
				// or if it's a standalone AssistantMessage (Scenario 3: AssistantMessage
				// -> ToolResponseMessage)
				if (currentRound != null) {
					// Check if current round already has a UserMessage
					boolean hasUserMessage = currentRound.getMessages()
						.stream()
						.anyMatch(msg -> msg instanceof UserMessage);
					if (hasUserMessage) {
						// Scenario 2: UserMessage -> AssistantMessage
						// Add AssistantMessage to current round (round may complete here
						// or wait for ToolResponseMessage)
						currentRound.addMessage(message);
					}
					else {
						// Current round doesn't have UserMessage, complete it and start
						// new round
						rounds.add(currentRound);
						currentRound = new DialogRound();
						currentRound.addMessage(message);
					}
				}
				else {
					// Scenario 3: AssistantMessage -> ToolResponseMessage (agent memory
					// scenario)
					// Start new round with AssistantMessage
					currentRound = new DialogRound();
					currentRound.addMessage(message);
				}
			}
			else if (message instanceof ToolResponseMessage) {
				// ToolResponseMessage completes a round
				// Can be part of:
				// - Scenario 1: UserMessage -> AssistantMessage -> ToolResponseMessage
				// - Scenario 3: AssistantMessage -> ToolResponseMessage
				if (currentRound == null) {
					// No current round, create one (edge case)
					currentRound = new DialogRound();
				}
				currentRound.addMessage(message);
				// Round is complete, add it to rounds
				rounds.add(currentRound);
				currentRound = null;
			}
			else {
				// Other message types, add to current round if exists
				if (currentRound != null) {
					currentRound.addMessage(message);
				}
			}
		}

		// Add the last round if it exists and wasn't completed
		// This handles incomplete rounds like UserMessage -> AssistantMessage (Scenario
		// 2)
		if (currentRound != null) {
			rounds.add(currentRound);
		}

		return rounds;
	}

	/**
	 * Summarize multiple dialog rounds into a single UserMessage in state_snapshot XML
	 * format. The summary should be between 3000-4000 chars and structured as
	 * state_snapshot XML.
	 * @param rounds Dialog rounds to summarize
	 * @return Summarized UserMessage in state_snapshot XML format
	 */
	private UserMessage summarizeRounds(List<DialogRound> rounds) {
		try {
			// Build list of all messages from rounds
			List<Message> allMessages = new ArrayList<>();
			for (DialogRound round : rounds) {
				allMessages.addAll(round.getMessages());
			}

			// Convert entire message list to JSON as conversation text
			String conversationHistory;
			try {
				conversationHistory = objectMapper.writeValueAsString(allMessages);
			}
			catch (Exception e) {
				log.warn("Failed to serialize messages to JSON for summarization, using fallback", e);
				// Fallback: build text representation
				StringBuilder conversationText = new StringBuilder();
				for (Message message : allMessages) {
					String content = extractMessageContent(message);
					if (message instanceof UserMessage) {
						conversationText.append("User: ").append(content).append("\n\n");
					}
					else if (message instanceof AssistantMessage) {
						conversationText.append("Assistant: ").append(content).append("\n\n");
					}
					else if (message instanceof ToolResponseMessage) {
						conversationText.append("Tool Response: ").append(content).append("\n\n");
					}
				}
				conversationHistory = conversationText.toString();
			}

			// Create summarization prompt with state_snapshot XML format requirement
			String summaryPrompt = String.format(
					"""
							First, reason in your scratchpad. Then, generate the <state_snapshot>.

							Analyze the following conversation history and create a structured state_snapshot XML.
							The state_snapshot should be between %d and %d characters total.

							Required XML structure:
							<state_snapshot>
							<overall_goal>
							[The main objective or goal of the conversation]
							</overall_goal>
							<key_knowledge>
							[Important facts, commands, configurations, URLs, file paths, and key information discovered]
							</key_knowledge>
							<file_system_state>
							[Files that were created, modified, deleted, or accessed (use prefixes: CREATED, MODIFIED, DELETED, ACCESSED)]
							</file_system_state>
							<recent_actions>
							[Recent tool calls, commands executed, searches performed, and actions taken]
							</recent_actions>
							<current_plan>
							[Current plan items with status: [DONE], [IN PROGRESS], [PENDING]]
							</current_plan>
							</state_snapshot>

							Guidelines:
							- Preserve all critical information: URLs, file paths, commands, configurations
							- Include tool names and their results when relevant
							- Track file system changes accurately
							- Maintain plan status and progress
							- Keep the total length between %d and %d characters
							- Output the XML content directly, no additional text before or after

							Conversation history:
							%s
							""",
					SUMMARY_MIN_CHARS, SUMMARY_MAX_CHARS, SUMMARY_MIN_CHARS, SUMMARY_MAX_CHARS, conversationHistory);

			// Use LLM to generate summary in state_snapshot format
			ChatClient chatClient = llmService.getDefaultDynamicAgentChatClient();
			ChatResponse response = chatClient.prompt()
				.system("You are a helpful assistant that creates structured state_snapshot summaries. "
						+ "Always output valid XML in the exact format requested.")
				.user(summaryPrompt)
				.call()
				.chatResponse();

			String summary = response.getResult().getOutput().getText();

			// Ensure summary is within target range (simple truncation if needed)
			if (summary.length() < SUMMARY_MIN_CHARS) {
				log.warn("Generated summary is too short ({} chars), using as-is", summary.length());
			}
			else if (summary.length() > SUMMARY_MAX_CHARS) {
				log.warn("Generated summary is too long ({} chars), truncating...", summary.length());
				summary = summary.substring(0, SUMMARY_MAX_CHARS);
			}

			// Store as UserMessage regardless of format correctness (as requested)
			return new UserMessage(summary);

		}
		catch (Exception e) {
			log.error("Failed to summarize dialog rounds", e);
			// Fallback: create a simple summary
			String fallbackSummary = String.format(
					"Previous conversation history (%d dialog rounds) has been summarized due to length constraints.",
					rounds.size());
			return new UserMessage(fallbackSummary);
		}
	}

	/**
	 * Inner class to represent a dialog round. For conversation memory: typically
	 * UserMessage + AssistantMessage pairs For agent memory: typically AssistantMessage
	 * (with tool calls) + ToolResponseMessage pairs
	 */
	private static class DialogRound {

		private final List<Message> messages = new ArrayList<>();

		public void addMessage(Message message) {
			messages.add(message);
		}

		public List<Message> getMessages() {
			return messages;
		}

		public int getTotalChars() {
			return messages.stream().mapToInt(msg -> {
				String text = msg.getText();
				return text != null ? text.length() : 0;
			}).sum();
		}

	}

	/**
	 * Force compress conversation memory to break potential loops. This method compresses
	 * the memory regardless of character count limits, keeping only the most recent round
	 * and summarizing all older rounds.
	 * @param chatMemory The chat memory instance
	 * @param conversationId The conversation ID to compress memory for
	 */
	public void forceCompressConversationMemory(ChatMemory chatMemory, String conversationId) {
		if (chatMemory == null || conversationId == null || conversationId.trim().isEmpty()) {
			return;
		}

		try {
			List<Message> messages = chatMemory.get(conversationId);
			if (messages == null || messages.isEmpty()) {
				log.debug("No messages found for conversationId: {}, skipping forced compression", conversationId);
				return;
			}

			log.info(
					"Force compressing conversation memory for conversationId: {} to break potential loop. Message count: {}",
					conversationId, messages.size());

			// Group messages into dialog rounds
			List<DialogRound> dialogRounds = groupMessagesIntoRounds(messages);

			if (dialogRounds.isEmpty()) {
				log.warn("No dialog rounds found for conversationId: {}", conversationId);
				return;
			}

			// Calculate total character count of all rounds
			int totalChars = dialogRounds.stream().mapToInt(DialogRound::getTotalChars).sum();

			// Calculate target retention: 40% of total content
			int targetRetentionChars = (int) (totalChars * RETENTION_RATIO);

			// If total is very small, keep all rounds
			if (totalChars <= 0 || targetRetentionChars <= 0) {
				log.debug("Total character count ({}) is too small, keeping all rounds for conversationId: {}",
						totalChars, conversationId);
				return;
			}

			// Force compression: keep rounds from newest to oldest until accumulated
			// chars reach 40% retention
			List<DialogRound> roundsToKeep = new ArrayList<>();
			List<DialogRound> roundsToSummarize = new ArrayList<>();

			int accumulatedChars = 0;
			boolean hasKeptAtLeastOneRound = false;

			// Start from the newest round and work backwards
			for (int i = dialogRounds.size() - 1; i >= 0; i--) {
				DialogRound round = dialogRounds.get(i);
				int roundChars = round.getTotalChars();

				// Always keep at least the newest round (even if it exceeds 40%)
				if (i == dialogRounds.size() - 1) {
					roundsToKeep.add(round);
					accumulatedChars += roundChars;
					hasKeptAtLeastOneRound = true;
				}
				else {
					// For other rounds, check if we can add them within 40% retention
					// limit
					if (accumulatedChars + roundChars <= targetRetentionChars) {
						roundsToKeep.add(0, round); // Add at beginning to maintain
													// chronological order
						accumulatedChars += roundChars;
						hasKeptAtLeastOneRound = true;
					}
					else {
						// Can't add this round, all remaining are older and should be
						// summarized
						for (int j = i; j >= 0; j--) {
							roundsToSummarize.add(0, dialogRounds.get(j));
						}
						break;
					}
				}
			}

			// Ensure we kept at least one round (fallback if somehow no rounds were kept)
			if (!hasKeptAtLeastOneRound && !dialogRounds.isEmpty()) {
				roundsToKeep.add(dialogRounds.get(dialogRounds.size() - 1));
				// Add all others to summarize
				for (int i = 0; i < dialogRounds.size() - 1; i++) {
					roundsToSummarize.add(dialogRounds.get(i));
				}
			}

			// Summarize older rounds
			UserMessage summaryMessage = null;
			if (!roundsToSummarize.isEmpty()) {
				summaryMessage = summarizeRounds(roundsToSummarize);
			}

			// Rebuild memory: summary first (as UserMessage), then confirmation (as
			// AssistantMessage), then most recent round
			// This maintains the user-assistant message pair pattern similar to
			// state_snapshot storage
			chatMemory.clear(conversationId);

			if (summaryMessage != null) {
				// Add summary as UserMessage (like state_snapshot)
				chatMemory.add(conversationId, summaryMessage);
				// Add confirmation AssistantMessage to maintain user-assistant pair
				// pattern
				AssistantMessage confirmationMessage = new AssistantMessage(COMPRESSION_CONFIRMATION_MESSAGE);
				chatMemory.add(conversationId, confirmationMessage);
				log.info("Added forced summary message ({} chars) with confirmation for conversationId: {}",
						summaryMessage.getText().length(), conversationId);
			}

			// Add most recent round
			for (DialogRound round : roundsToKeep) {
				for (Message message : round.getMessages()) {
					chatMemory.add(conversationId, message);
				}
			}

			int keptChars = calculateTotalCharacters(
					roundsToKeep.stream().flatMap(round -> round.getMessages().stream()).toList());
			double actualRetentionRatio = totalChars > 0 ? (double) keptChars / totalChars : 0.0;
			log.info(
					"Forced compression completed for conversationId: {}. Kept {} recent round(s) ({} chars, {}% retention), summarized {} older rounds into {} chars",
					conversationId, roundsToKeep.size(), keptChars, String.format("%.1f", actualRetentionRatio * 100),
					roundsToSummarize.size(), summaryMessage != null ? summaryMessage.getText().length() : 0);
		}
		catch (Exception e) {
			log.warn("Failed to force compress conversation memory for conversationId: {}", conversationId, e);
		}
	}

	/**
	 * Force compress agent memory to break potential loops caused by repeated tool call
	 * results. This method compresses the memory regardless of character count limits.
	 * @param messages The list of messages to compress
	 * @return Compressed list of messages containing summary and most recent round
	 */
	public List<Message> forceCompressAgentMemory(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			log.debug("No messages found, skipping forced compression");
			return new ArrayList<>(messages);
		}

		try {
			log.info("Force compressing agent memory to break potential loop. Message count: {}", messages.size());

			// Group messages into dialog rounds
			List<DialogRound> dialogRounds = groupMessagesIntoRounds(messages);

			if (dialogRounds.isEmpty()) {
				log.warn("No dialog rounds found, returning original messages");
				return new ArrayList<>(messages);
			}

			// Calculate total character count of all rounds
			int totalChars = dialogRounds.stream().mapToInt(DialogRound::getTotalChars).sum();

			// Calculate target retention: 40% of total content
			int targetRetentionChars = (int) (totalChars * RETENTION_RATIO);

			// If total is very small, keep all rounds
			if (totalChars <= 0 || targetRetentionChars <= 0) {
				log.debug("Total character count ({}) is too small, keeping all rounds", totalChars);
				return new ArrayList<>(messages);
			}

			// Force compression: keep rounds from newest to oldest until accumulated
			// chars reach 40% retention
			List<DialogRound> roundsToKeep = new ArrayList<>();
			List<DialogRound> roundsToSummarize = new ArrayList<>();

			int accumulatedChars = 0;
			boolean hasKeptAtLeastOneRound = false;

			// Start from the newest round and work backwards
			for (int i = dialogRounds.size() - 1; i >= 0; i--) {
				DialogRound round = dialogRounds.get(i);
				int roundChars = round.getTotalChars();

				// Always keep at least the newest round (even if it exceeds 40%)
				if (i == dialogRounds.size() - 1) {
					roundsToKeep.add(round);
					accumulatedChars += roundChars;
					hasKeptAtLeastOneRound = true;
				}
				else {
					// For other rounds, check if we can add them within 40% retention
					// limit
					if (accumulatedChars + roundChars <= targetRetentionChars) {
						roundsToKeep.add(0, round); // Add at beginning to maintain
													// chronological order
						accumulatedChars += roundChars;
						hasKeptAtLeastOneRound = true;
					}
					else {
						// Can't add this round, all remaining are older and should be
						// summarized
						for (int j = i; j >= 0; j--) {
							roundsToSummarize.add(0, dialogRounds.get(j));
						}
						break;
					}
				}
			}

			// Ensure we kept at least one round (fallback if somehow no rounds were kept)
			if (!hasKeptAtLeastOneRound && !dialogRounds.isEmpty()) {
				roundsToKeep.add(dialogRounds.get(dialogRounds.size() - 1));
				// Add all others to summarize
				for (int i = 0; i < dialogRounds.size() - 1; i++) {
					roundsToSummarize.add(dialogRounds.get(i));
				}
			}

			// Summarize older rounds
			UserMessage summaryMessage = null;
			if (!roundsToSummarize.isEmpty()) {
				summaryMessage = summarizeRounds(roundsToSummarize);
			}

			// Build compressed message list: summary first (as UserMessage), then
			// confirmation (as AssistantMessage), then most recent round
			// This maintains the user-assistant message pair pattern similar to
			// state_snapshot storage
			List<Message> compressedMessages = new ArrayList<>();

			if (summaryMessage != null) {
				// Add summary as UserMessage (like state_snapshot)
				compressedMessages.add(summaryMessage);
				// Add confirmation AssistantMessage to maintain user-assistant pair
				// pattern
				AssistantMessage confirmationMessage = new AssistantMessage(COMPRESSION_CONFIRMATION_MESSAGE);
				compressedMessages.add(confirmationMessage);
				log.info("Added forced summary message ({} chars) with confirmation",
						summaryMessage.getText().length());
			}

			// Add most recent round
			for (DialogRound round : roundsToKeep) {
				compressedMessages.addAll(round.getMessages());
			}

			int keptChars = calculateTotalCharacters(
					roundsToKeep.stream().flatMap(round -> round.getMessages().stream()).toList());
			double actualRetentionRatio = totalChars > 0 ? (double) keptChars / totalChars : 0.0;
			log.info(
					"Forced compression completed. Kept {} recent round(s) ({} chars, {}% retention), summarized {} older rounds into {} chars",
					roundsToKeep.size(), keptChars, String.format("%.1f", actualRetentionRatio * 100),
					roundsToSummarize.size(), summaryMessage != null ? summaryMessage.getText().length() : 0);

			return compressedMessages;
		}
		catch (Exception e) {
			log.warn("Failed to force compress agent memory", e);
			// Return original messages on error
			return new ArrayList<>(messages);
		}
	}

	/**
	 * Check if messages exceed the limit and compress both conversation and agent memory
	 * if needed. This method checks the total character count of all messages
	 * (conversation + agent) and compresses them if they exceed the limit.
	 * @param conversationMemory The conversation memory instance
	 * @param conversationId The conversation ID
	 * @param agentMessages The agent memory messages
	 * @return Compressed agent messages if compression occurred, original messages
	 * otherwise
	 */
	public List<Message> checkAndCompressIfNeeded(ChatMemory conversationMemory, String conversationId,
			List<Message> agentMessages) {
		if (agentMessages == null) {
			agentMessages = new ArrayList<>();
		}

		try {
			// Get conversation messages
			List<Message> conversationMessages = new ArrayList<>();
			if (conversationMemory != null && conversationId != null && !conversationId.trim().isEmpty()) {
				List<Message> convMsgs = conversationMemory.get(conversationId);
				if (convMsgs != null) {
					conversationMessages = convMsgs;
				}
			}

			// Combine all messages to check total size
			List<Message> allMessages = new ArrayList<>();
			allMessages.addAll(conversationMessages);
			allMessages.addAll(agentMessages);

			// Calculate total character count
			int totalChars = calculateTotalCharacters(allMessages);
			int maxChars = getMaxCharacterCount();

			if (totalChars <= maxChars) {
				log.debug("Total memory size ({}) is within limit ({}), no compression needed", totalChars, maxChars);
				return agentMessages;
			}

			log.info("Total memory size ({}) exceeds limit ({}). Force compressing conversation and agent memory...",
					totalChars, maxChars);

			// Step 1: Force compress conversation memory first
			if (conversationMemory != null && conversationId != null && !conversationId.trim().isEmpty()
					&& !conversationMessages.isEmpty()) {
				try {
					forceCompressConversationMemory(conversationMemory, conversationId);
					log.info("Force compressed conversation memory for conversationId: {}", conversationId);
				}
				catch (Exception e) {
					log.warn("Failed to compress conversation memory for conversationId: {}", conversationId, e);
				}
			}

			// Step 2: Force compress agent memory
			if (!agentMessages.isEmpty()) {
				List<Message> compressedAgentMessages = forceCompressAgentMemory(agentMessages);
				log.info("Force compressed agent memory. Original: {} messages, Compressed: {} messages",
						agentMessages.size(), compressedAgentMessages.size());
				return compressedAgentMessages;
			}

			return agentMessages;
		}
		catch (Exception e) {
			log.warn("Failed to check and compress memory", e);
			return agentMessages;
		}
	}

	/**
	 * Get the configured maximum character count from LynxeProperties.
	 * @return Maximum character count
	 */
	public int getMaxCharacterCount() {
		return lynxeProperties != null ? lynxeProperties.getConversationMemoryMaxChars() : 30000;
	}

}
