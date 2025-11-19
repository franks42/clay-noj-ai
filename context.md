# Session Context - 2025-11-18

## What We Accomplished

### Phase 1 Complete: Multi-Backend LLM Service MVP

Built a working multi-instance Claude orchestration system with cost optimization.

**Core Features:**
- Model selection: `spawn!` and `fork!` with `:model :haiku/:sonnet/:opus`
- BB tasks for CLI control: `bb claude:spawn/ask/fork/kill/list`
- Context inheritance via fork pattern
- Relay for data passing between instances
- Async workflow with poll-response

**Demo Run Recorded:**
- Complete walkthrough in `docs/orchestrator-worker-demo-run.md`
- Shows Sonnet orchestrator + Haiku worker pattern
- Live HASH token analysis with real blockchain data

### Documentation Created

| File | Purpose |
|------|---------|
| `docs/multi-backend-llm-service-design.md` | Architecture and phased implementation |
| `docs/multi-backend-llm-tasks.md` | Task tracking (Phase 1 complete) |
| `docs/orchestrator-worker-example.md` | Usage patterns and examples |
| `docs/claude-service-gotchas.md` | SCI compatibility, API patterns |
| `docs/orchestrator-worker-demo-run.md` | Recorded demo with output |
| `docs/inter-agent-mcp-tools-design.md` | **NEW** - Next phase design |

---

## Key Files

### Source Code

**`src/claude_service.clj`** - Core multi-instance service
- `spawn!` / `fork!` - Create instances with model selection
- `ask` / `ask-async` / `poll-response` - Communication
- `relay` / `broadcast` - Inter-agent messaging
- `kill!` / `kill-all!` - Cleanup

**`bb.edn`** - BB tasks using `babashka.nrepl-client`
```bash
bb claude:spawn orchestrator
bb claude:ask orchestrator "prompt"
bb claude:fork orchestrator worker haiku
bb claude:kill-all
```

### Design Documents

**`docs/inter-agent-mcp-tools-design.md`** - Next implementation phase
- MCP tools for agent-to-agent communication
- Extensive logging system
- Script mode and confirmation mode
- Dynamic loading into running server

**`/Users/franksiebenlist/Development/mcp-nrepl-joyride/docs/dynamic-tool-loading-design.md`**
- Infrastructure for dynamically loading tools at runtime
- Already supports what we need for inter-agent tools

---

## Current State

### Running Services (need restart after reboot)

1. **Clay/Noj server** - `bb clay:start`
   - HTTP: http://localhost:1971
   - nREPL: localhost:7890

2. **MCP nREPL server** - Started via MCP connection
   - Port: 7888
   - Load service: `(mcp.nrepl-server/local-load-file "/path/to/claude_service.clj")`

3. **Claude instances** - Killed on reboot
   - Need to respawn after restart

### Git Status

- Branch: `main`
- All Phase 1 work committed and pushed
- Tag: `v0.2.0-multi-backend-mvp`

---

## Next Steps (Priority Order)

### 1. Implement Inter-Agent MCP Tools (Phase 1)

From `docs/inter-agent-mcp-tools-design.md`:

- [ ] Logging system (`logs/agent-communication.log`)
- [ ] `agent-spawn-worker` tool
- [ ] `agent-send-task` tool
- [ ] `agent-kill-worker` tool
- [ ] `agent-list-workers` tool
- [ ] Basic safeguards (max-workers limit)

**Key insight:** Can load dynamically into running server:
```clojure
(mcp.nrepl-server/local-load-file "/path/to/agent_tools.clj")
;; Tools self-register, immediately available
```

### 2. Script Mode Implementation

- [ ] Workflow definition format
- [ ] `agent-workflow-execute` tool
- [ ] Step-by-step execution with logging

### 3. Phase 2: Cost Optimization

From `docs/multi-backend-llm-tasks.md`:

- [ ] Measure token/cost reduction
- [ ] Create data-fetcher prompt templates
- [ ] Add token counting to service

---

## Technical Details

### SCI Compatibility (Important!)

```clojure
;; DON'T use:
(defonce ^:private foo ...)  ; Wrong number of args error
(defn- private-fn ...)       ; defn- not available

;; DO use:
(defonce foo ...)
(defn helper ...)
```

### Model IDs

```clojure
{:haiku  "claude-3-5-haiku-20241022"   ; Note: 3-5, not haiku-3-5
 :sonnet "claude-sonnet-4-20250514"
 :opus   "claude-opus-4-20250514"}
```

### babashka.nrepl-client API

```clojure
;; Single function call, handles everything
(nrepl/eval-expr {:port 7888 :expr "(+ 1 2)"})
;; => {:vals ["3"]}
```

### Loading Service

Use MCP tool (more reliable than bb claude:eval):
```clojure
(mcp.nrepl-server/local-load-file
  "/Users/franksiebenlist/Development/clay-noj-ai/src/claude_service.clj")
```

---

## Quick Start Tomorrow

```bash
# 1. Start Clay server
cd /Users/franksiebenlist/Development/clay-noj-ai
bb clay:start
# Wait 5 seconds

# 2. Verify servers running
bb clay:status

# 3. Load claude service (via MCP)
# Use local-load-file MCP tool

# 4. Test it works
bb claude:spawn test
bb claude:ask test "Hello"
bb claude:kill test
```

---

## User Preferences

- Use `clojure.pprint/pprint` for formatted output (not jq - output is EDN)
- Run `clj-kondo` before committing - **warnings are NOT OK** for our code
- Always `git add -f` for docs/ (it's gitignored)
- Take screenshots before presenting URLs to user
- Keep task list updated in markdown docs (survives context compacts)

---

## Key Patterns

### Fork for Cost Optimization

```bash
# Set up orchestrator with context once (Sonnet)
bb claude:spawn orchestrator
bb claude:ask orchestrator "You are a crypto analyst..."

# Fork cheap workers that inherit context (Haiku)
bb claude:fork orchestrator worker haiku
bb claude:ask worker "fetch data..."  # Worker knows it's a crypto analyst!
```

### Relay for Data Passing

```clojure
;; Takes worker's last response + context message → sends to target
(claude-service/relay "worker" "orchestrator" "Analyze this data")
```

---

## Important Commits

- `25afd02` - Inter-agent MCP tools design
- `0b34a30` - Relay explanation in demo doc
- `1bf36f8` - Recorded demo run
- `9b324df` - Model selection for fork
- `d52a49b` - Phase 1 documentation complete
- `9f162b1` - Multi-backend LLM service with model selection

---

*Last updated: 2025-11-18 ~23:00*
