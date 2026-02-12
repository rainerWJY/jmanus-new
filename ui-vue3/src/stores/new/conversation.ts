/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use it except in compliance with the License.
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

import { MemoryApiService, type Memory } from '@/api/memory-api-service'
import { logger } from '@/utils/logger'
import { defineStore } from 'pinia'
import { ref } from 'vue'

const CONVERSATION_ID_KEY = 'currentConversationId'

function loadSelectedConversationIdFromStorage(): string | null {
  try {
    return localStorage.getItem(CONVERSATION_ID_KEY)
  } catch {
    return null
  }
}

function saveSelectedConversationIdToStorage(id: string | null): void {
  try {
    if (id) {
      localStorage.setItem(CONVERSATION_ID_KEY, id)
    } else {
      localStorage.removeItem(CONVERSATION_ID_KEY)
    }
  } catch {
    // ignore
  }
}

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref<Memory[]>([])
  const selectedConversationId = ref<string | null>(loadSelectedConversationIdFromStorage())

  async function loadConversations(): Promise<void> {
    try {
      const list = await MemoryApiService.getMemories()
      conversations.value = list
    } catch (error) {
      logger.error('[ConversationStore] Failed to load conversations:', error)
      conversations.value = []
    }
  }

  function setConversations(list: Memory[]): void {
    conversations.value = list
  }

  function setSelectedConversationId(id: string | null): void {
    selectedConversationId.value = id
    saveSelectedConversationIdToStorage(id)
  }

  function clearSelectedConversation(): void {
    selectedConversationId.value = null
    saveSelectedConversationIdToStorage(null)
  }

  return {
    conversations,
    selectedConversationId,
    loadConversations,
    setConversations,
    setSelectedConversationId,
    clearSelectedConversation,
  }
})
