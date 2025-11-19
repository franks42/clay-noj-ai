# Multi-Backend LLM Service - Implementation Tasks

## Phase 1: Claude-Only MVP
*Goal: Working multi-instance orchestration*

- [x] Fix SCI compatibility (remove `^:private`, `defn-`)
- [x] Load claude_service.clj into MCP server
- [x] Test spawn!/ask/kill! basic workflow
- [x] Test fork! pattern (context inheritance)
- [x] Test ask-async/poll-response workflow
- [x] Test relay/broadcast for Claude-to-Claude
- [x] Create bb tasks with babashka.nrepl-client
- [x] Add `--model` flag support to spawn! (Haiku/Sonnet/Opus)
- [x] Create orchestrator + worker example
- [x] Document gotchas and patterns

**Status: Phase 1 COMPLETE**

---

## Phase 1.5: Structured Logging
*Goal: Replace println with Trove/telemere-lite*

- [x] Copy telemere-lite.core from sente_lite project
- [x] Add com.taoensso/trove dependency
- [x] Replace all println calls with tel/log! in claude_service.clj
- [x] Configure file handler (logs/agent-communication.log)
- [x] Configure stdout handler for development
- [x] Test logging with spawn/kill workflow
- [x] Add clj-kondo ignore directive for telemere-lite vars

**Status: Phase 1.5 COMPLETE**

---

## Phase 2: Inter-Agent MCP Tools
*Goal: MCP tools for agent-to-agent communication*

### 2.1 Core Infrastructure
- [x] Create `src/agent_tools.clj` with namespace and requires
- [x] Add safeguards state (max-workers, session-stats atoms)
- [x] Test: Load file into MCP server

### 2.2 agent-list-workers Tool
- [ ] Implement tool metadata and handle function
- [ ] Register tool with MCP registry
- [ ] Test: Call tool, verify returns empty list
- [ ] Test: Spawn instance manually, verify tool sees it

### 2.3 agent-spawn-worker Tool
- [ ] Implement tool metadata and handle function
- [ ] Add max-workers limit check
- [ ] Log spawn with tel/log!
- [ ] Test: Spawn worker via tool
- [ ] Test: Verify worker appears in list
- [ ] Test: Hit max-workers limit

### 2.4 agent-send-task Tool
- [ ] Implement tool metadata and handle function
- [ ] Log send/receive with tel/log!
- [ ] Test: Send task to spawned worker
- [ ] Test: Verify response returned

### 2.5 agent-kill-worker Tool
- [ ] Implement tool metadata and handle function
- [ ] Log kill with tel/log!
- [ ] Test: Kill worker via tool
- [ ] Test: Verify worker removed from list

### 2.6 Integration Testing
- [ ] Full workflow: spawn -> send -> kill
- [ ] Verify logs in agent-communication.log
- [ ] Test error handling (kill non-existent, send to dead)

**Commit/push after each subsection (2.1, 2.2, etc.)**

---

## Phase 3: Cost Optimization
*Goal: Measure and optimize multi-model costs*

- [x] Add model parameter to spawn! (done in Phase 1)
- [x] Test Haiku as data gateway (done in Phase 1)
- [ ] Measure token/cost reduction
- [ ] Create data-fetcher prompt templates
- [ ] Document model-appropriate prompting
- [ ] Add token counting to service

---

## Phase 4: Claude + Haiku + Ollama
*Goal: Hybrid cloud/local system*

- [ ] Install Ollama locally
- [ ] Test Ollama API (llama3, qwen2.5)
- [ ] Create `scripts/openai-wrapper` (Babashka)
- [ ] Add :ollama backend to spawn!
- [ ] Test mixed backend workflow
- [ ] Create hybrid orchestrator example

---

## Phase 4: Optimization
*Goal: Production-ready multi-backend system*

- [ ] Implement sliding window context compression
- [ ] Add token counting utilities
- [ ] Implement cost tracking per request
- [ ] Add automatic backend selection
- [ ] Create fallback chain support
- [ ] Usage reporting/dashboard

---

## Current BB Tasks

```bash
bb claude:eval '(+ 1 2)'           # Generic eval
bb claude:list                      # List instances
bb claude:spawn <name>              # Spawn instance
bb claude:ask <name> <prompt>       # Sync ask
bb claude:fork <src> <new>          # Fork with context
bb claude:kill <name>               # Kill instance
bb claude:kill-all                  # Cleanup
bb claude:status [name]             # Status check
```

---

## Coding Standards

### Logging - MANDATORY

**All new code MUST use Trove for logging. No println!**

```clojure
(require '[telemere-lite.core :as tel])

(tel/log! {:level :info
           :id :namespace/event-type
           :data {:key value}})
```

Dependencies: `com.taoensso/trove`, `telemere-lite.core`
Output: JSON Lines to `logs/agent-communication.log`

---

## Notes & Learnings

### SCI Compatibility
- Use `defonce` not `defonce ^:private`
- Use `defn` not `defn-`
- Test loading with `local-load-file` before assuming it works

### Context Inheritance
Fork pattern works - workers remember base context. This is the key cost-saving pattern for building shared knowledge once.

### BB Tasks
Use `babashka.nrepl-client` with `eval-expr`:
```clojure
(nrepl/eval-expr {:port 7888 :expr "(+ 1 2)"})
;; Returns {:vals ["3"]}
```

---

## Documentation

- **Design:** `docs/multi-backend-llm-service-design.md`
- **Examples:** `docs/orchestrator-worker-example.md`
- **Gotchas:** `docs/claude-service-gotchas.md`
- **Architecture:** `docs/claude-service-architecture.md`

---

*Last updated: 2025-11-18*
