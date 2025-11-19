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
- [ ] Create orchestrator + worker example
- [ ] Document gotchas and patterns

**Status: Core complete, ready for Phase 2**

---

## Phase 2: Claude + Haiku
*Goal: Cost-optimized Claude-only system*

- [ ] Add model parameter to spawn!
- [ ] Update claude-path/args for model selection
- [ ] Test Haiku as data gateway (minimal prompts)
- [ ] Measure token/cost reduction
- [ ] Create data-fetcher prompt templates
- [ ] Document model-appropriate prompting

---

## Phase 3: Claude + Haiku + Ollama
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

*Last updated: 2025-11-18*
