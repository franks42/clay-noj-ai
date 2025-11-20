**CRITICAL: AI must display "I do not cheat or lie and I'm honest about any reporting of progress." at start of every response**

---

# Session Context - 2025-11-19

## What We Accomplished

### Phase 1.5 Complete: Trove/telemere-lite Structured Logging

Replaced all println logging with structured JSON logging using Trove/telemere-lite.

**Core Changes:**
- Added `telemere-lite.core` (910 lines) from sente_lite project
- Added `com.taoensso/trove` dependency
- Replaced 8 println calls with `tel/log!` structured events
- Configured file handler: `logs/agent-communication.log`
- Configured stdout handler for development
- Added clj-kondo ignore directive for telemere-lite vars

**Event IDs Logged:**
- `:claude-service/spawning` / `:claude-service/spawned`
- `:claude-service/killing` / `:claude-service/killed` / `:claude-service/kill-error`
- `:claude-service/spawning-from-session` / `:claude-service/forked`
- `:claude-service/relay`

**Output Format:** JSON Lines with timestamps, levels, and structured data

**CODING STANDARD:** All new code MUST use Trove for logging. No println!

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
- All logging via `tel/log!` with structured events

**`src/telemere_lite/core.cljc`** - Structured logging library
- JSON Lines output format
- File and stdout handlers
- Trove-compatible API

**`src/agent_tools.clj`** - MCP tools for inter-agent communication
- 4 tools: agent-list-workers, agent-spawn-worker, agent-send-task, agent-kill-worker
- Auto-registers with MCP server on load via FQN
- Safeguards: max-workers limit, session stats tracking

**`mcp-startup.clj`** - Project-local MCP tool registration
- Loaded automatically by mcp-nrepl-joyride before connections
- Loads claude_service.clj and agent_tools.clj
- Makes inter-agent tools available to Claude Code immediately

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
- All Phase 1, 1.5, and Phase 2 work committed and pushed
- Latest commit: `ff73b03` - MCP auto-registration

### Key Discovery: FQN Access in SCI

**Use FQN instead of require with alias** for MCP server namespaces:
```clojure
;; This works in SCI without require
(nrepl-mcp-server.state.tool-registry/register-tool!
 "tool-name" handler metadata)

;; registry-size also works
(nrepl-mcp-server.state.tool-registry/registry-size)
;; => 16  ; 12 base + 4 agent tools
```

---

## Next Steps (Priority Order)

### 1. Phase 3: Cost Optimization

From `docs/multi-backend-llm-tasks.md`:

- [ ] Measure token/cost reduction
- [ ] Create data-fetcher prompt templates
- [ ] Document model-appropriate prompting
- [ ] Add token counting to service

### 2. Script Mode Implementation

- [ ] Workflow definition format
- [ ] `agent-workflow-execute` tool
- [ ] Step-by-step execution with logging

### 3. Phase 4: Hybrid Cloud/Local

- [ ] Install Ollama locally
- [ ] Test Ollama API
- [ ] Add :ollama backend to spawn!

---

## Technical Details

### Trove Logging Pattern (MANDATORY)

```clojure
(require '[telemere-lite.core :as tel])

;; All logging MUST use this pattern
(tel/log! {:level :info
           :id :namespace/event-type
           :data {:key value}})

;; NEVER use println for logging
```

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

# 3. Test inter-agent MCP tools
# Restart Claude Code to pick up tools from mcp-startup.clj
# Then use the tools directly:
#   mcp__nrepl_mcp_server__agent-list-workers
#   mcp__nrepl_mcp_server__agent-spawn-worker
#   mcp__nrepl_mcp_server__agent-send-task
#   mcp__nrepl_mcp_server__agent-kill-worker

# 4. Or use BB tasks for CLI control
bb claude:spawn test
bb claude:ask test "Hello"
bb claude:kill test
```

### MCP Startup Pattern

The `mcp-startup.clj` file is loaded automatically by the MCP server when it starts.
This registers the inter-agent tools so they're available to Claude Code immediately.

To add new MCP tools:
1. Create handler function and metadata in a `.clj` file
2. Use `nrepl-mcp-server.state.tool-registry/register-tool!` FQN
3. Add `(load-file "src/your-tools.clj")` to `mcp-startup.clj`
4. Restart Claude Code session to pick up new tools

---

## User Preferences

- Use `clojure.pprint/pprint` for formatted output (not jq - output is EDN)
- **After EVERY code change:** run `clj-kondo` - zero warnings allowed for our code
- **After EVERY code change:** run `cljfmt` to reformat
- If clj-kondo warnings are acceptable, add ignore directive to code
- Always `git add -f` for docs/ (it's gitignored)
- Take screenshots before presenting URLs to user
- Keep task list updated in markdown docs (survives context compacts)
- **All logging MUST use Trove** - no println anywhere in our code

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

- `ff73b03` - **MCP auto-registration with FQN pattern** (Phase 2 complete)
- `e92fa02` - Phase 2.6 integration testing complete
- `288d0ab` - Phase 2.5 agent-kill-worker tool
- `36b4158` - Trove/telemere-lite structured logging (Phase 1.5 complete)
- `25afd02` - Inter-agent MCP tools design
- `9b324df` - Model selection for fork
- `9f162b1` - Multi-backend LLM service with model selection

---

*Last updated: 2025-11-19 ~11:00*
