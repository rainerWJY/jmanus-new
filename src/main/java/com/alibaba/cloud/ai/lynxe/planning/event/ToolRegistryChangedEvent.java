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

package com.alibaba.cloud.ai.lynxe.planning.event;

/**
 * Published when the tool registry changes (e.g. coordinator tools created/updated in
 * DB). Listeners such as PlanningFactory invalidate the tool callback map cache so the
 * next request rebuilds with the new tools.
 */
public class ToolRegistryChangedEvent {

}
