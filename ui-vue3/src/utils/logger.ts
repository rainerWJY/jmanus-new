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

import log from 'loglevel'

type LogLevel = 'trace' | 'debug' | 'info' | 'warn' | 'error' | 'silent'
const VALID_LEVELS: LogLevel[] = ['trace', 'debug', 'info', 'warn', 'error', 'silent']
const envLevel = import.meta.env.VITE_LOG_LEVEL
const level: LogLevel =
  envLevel && VALID_LEVELS.includes(envLevel as LogLevel)
    ? (envLevel as LogLevel)
    : import.meta.env.DEV
      ? 'debug'
      : 'warn'
log.setDefaultLevel(level)

// Expose logger on window in development so you can change level from console, e.g. __logger.setLevel('trace')
if (import.meta.env.DEV && typeof window !== 'undefined') {
  ;(window as Window & { __logger?: typeof log }).__logger = log
}

export const logger = log
