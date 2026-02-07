Here’s a concrete way to **design the whole structure of data** in your frontend, so it stays clear, scalable, and easy to refactor (including with Pinia).

---

# 1. Design process (steps)

## Step 1: List domains and ownership

- **Domain** = a clear area of the app (e.g. “chat”, “plan templates”, “execution”, “memory”, “config”).
- For each domain, decide:
  - **Who owns this data?** (one store or one “owner” composable)
  - **Who only reads/uses it?** (other stores, composables, views)

From your app, domains could look like:

| Domain                   | Owns                                     | Consumers                             |
| ------------------------ | ---------------------------------------- | ------------------------------------- |
| **Namespace**            | Current workspace/tenant                 | API calls, config screens             |
| **Plan templates**       | List, current template, editor state     | Sidebar, RightPanel, Execution        |
| **Chat / conversations** | Dialogs, messages, current conversation  | Chat UI, input, execution             |
| **Execution**            | Running plans, polling, plan records     | Chat, RightPanel, ExecutionController |
| **Memory**               | Conversation list, selected conversation | Direct view, chat, API                |
| **Task**                 | “Task to run”, visit state               | Home, direct view, execution trigger  |
| **UI state**             | Sidebar collapse, right panel tab, etc.  | Layout components                     |
| **Tools / config**       | Available tools, model config, MCP       | Config views, execution               |

Do this in a doc or table first; it becomes your “data map”.

---

## Step 2: Define the “shape” of data (models)

For each domain, write down:

- **Core entities** (e.g. `PlanTemplate`, `MessageDialog`, `PlanExecutionRecord`).
- **IDs and relations** (e.g. `conversationId` → many `dialogs`; `planId` → one `PlanExecutionRecord`).
- **What is normalized** (e.g. “plans by id”, “messages by dialogId”) vs **denormalized** (e.g. “current template” for the editor).

You already have types under `src/types/`. A redesign means:

- One place (e.g. `types/` or `models/`) where **all** domain models live.
- Stores and composables only reference these types; no ad‑hoc shapes.

Example for “conversations + chat”:

```text
Conversation (id, …)
  └── Dialog[] (id, conversationId?, planId?, messages[], …)
        └── Message[] (id, type, content, planExecution?, …)

Execution
  └── PlanExecutionRecord by planId (rootPlanId, status, agentExecutionSequence, …)
```

Do the same for templates, memory, task, etc.

---

## Step 3: Decide where each kind of data lives

Use a simple matrix:

| Data kind                                                 | Prefer                             | Reason                                                        |
| --------------------------------------------------------- | ---------------------------------- | ------------------------------------------------------------- |
| **Shared across routes/components**                       | Pinia store                        | Single source of truth, devtools, easy to inject              |
| **Server-backed (list/detail)**                           | Pinia store + API layer            | Cache, loading/error state, sync with backend                 |
| **UI-only (modals, tabs, collapse)**                      | Pinia store **or** component state | Store if multiple components need it; else `ref` in component |
| **Derived only**                                          | Computed in store or composable    | No duplication; recompute from source of truth                |
| **Form draft / transient**                                | Component or composable            | Don’t put in global store until “saved” or “applied”          |
| **True singleton (e.g. current user, current namespace)** | One store, one “current” ref       | Avoid two places holding “current”                            |

Apply this to your current “reactive stores” and “singleton composables”: anything that is **shared and long‑lived** becomes a store; the rest stays in components/composables.

---

## Step 4: Draw data flow (read vs write)

- **Writes**: only the **owner** of that domain should mutate (e.g. only `messageDialog` store pushes to `dialogList`).
- **Reads**: anyone can read via store getters or `storeToRefs`.
- **Cross-domain**: prefer **store A calls store B** or **component calls multiple stores**, and avoid long chains (e.g. A → B → C for a simple update).

For your app, a high‑level flow could be:

```text
User action (e.g. “Send message”)
  → InputArea / ChatContainer
  → messageDialog store (sendMessage)
  → API
  → messageDialog store (update dialogList, isRunning, etc.)
  → planExecution store (if planId: track, poll)
  → messageDialog store (update message from plan record)
```

So: **one clear write path per domain**; reads can come from many places.

---

## Step 5: Choose a store layout (folder/module structure)

Two common options:

**Option A – One store per domain (recommended for you)**

```text
stores/
  namespace.ts
  task.ts
  planTemplateConfig.ts   # list, selection, editor config
  template.ts             # sidebar template list UI (sort, collapse)
  messageDialog.ts        # dialogs, messages, running, streaming
  planExecution.ts        # tracked plans, records, polling
  memory.ts               # conversation list, selected conversation
  rightPanel.ts           # selected step, active tab
  availableTools.ts
  parameterHistory.ts
  ui.ts                   # optional: sidebar collapse, global modals
```

**Option B – Grouped by feature**

```text
stores/
  workspace/
    namespace.ts
  plan/
    template.ts
    templateConfig.ts
    execution.ts
    parameterHistory.ts
  chat/
    messageDialog.ts
    memory.ts
  app/
    task.ts
    rightPanel.ts
    availableTools.ts
  ui/
    sidebar.ts
```

Use A for simplicity; use B if the app grows and you want feature-based boundaries.

---

## Step 6: Normalize where it helps

- **Normalize**: store entities by id (e.g. `dialogsById`, `messagesByDialogId`). Good for “same entity in many places” and updates in one place.
- **Denormalize**: keep “current template”, “current conversation”, “selected step” as separate refs for easy binding.

Example:

- `messageDialog` store:
  - `dialogsById: Record<string, Dialog>`
  - `activeDialogId: string | null`
  - `conversationId: string | null`
  - getter `messages` = merge messages from dialogs for `conversationId` (or active dialog).
- `planExecution` store:
  - `recordsByPlanId: Record<string, PlanExecutionRecord>`
  - no “current” plan in store; “current” comes from `messageDialog.rootPlanId` or `task.currentTask?.planId`.

That way you avoid duplicating the same dialog or the same plan record in multiple shapes.

---

## Step 7: Document rules and boundaries

Write a short “data design” doc (can live in `.cursor/rules/` or `docs/`) that states:

- **Domain list** and which store owns what.
- **Naming**: e.g. “Stores: `useXxxStore`; state: nouns; actions: verbs.”
- **Rules**: e.g. “No composable holds global mutable state; only stores do.” “API calls only from stores or dedicated API layer.”
- **ConversationId / current conversation**: one owner (e.g. `memory` store or `messageDialog` store), others read from there.

That doc becomes the reference when you implement or refactor.

---

# 2. Redesign checklist for your app

- [ ] **Domains** listed with owner and consumers (Step 1).
- [ ] **Types/models** centralized; stores use them only (Step 2).
- [ ] **Matrix** filled: what is in store vs component vs composable (Step 3).
- [ ] **Write path** per domain clear; no “who updates this?” ambiguity (Step 4).
- [ ] **Store layout** chosen (flat vs grouped) and file names fixed (Step 5).
- [ ] **Normalization** decided for dialogs, messages, plan records (Step 6).
- [ ] **Single owner** for `conversationId`, “current template”, “current plan” (Step 7).
- [ ] **Pinia** used for all shared state; composables only orchestrate or derive (optional rule).

---

# 3. One possible “target” structure (high level)

Conceptually you could aim for:

```text
types/ (or models/)
  plan-template.ts, message-dialog.ts, plan-execution-record.ts, tool.ts, …

api/
  … (unchanged; no app state here)

stores/
  namespace, task, planTemplateConfig, template, messageDialog,
  planExecution, memory, rightPanel, availableTools, parameterHistory, ui?

composables/
  useConversationHistory  → uses messageDialog + planExecution + memory
  useTaskExecutionState   → uses task + messageDialog
  useToast, useRequest, useFileUpload, … (no global state)
```

- **Stores** = structure of data (state + actions + getters).
- **Composables** = workflows and derivation that use one or more stores.
- **Views/components** = use stores (and a few composables), no singleton refs.

If you tell me your priorities (e.g. “simplify chat + execution first” or “make config and templates clear”), I can outline a **concrete target data structure** (entities, stores, and who owns what) just for that part, still without making any edits.
