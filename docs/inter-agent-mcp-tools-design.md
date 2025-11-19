# Inter-Agent Communication MCP Tools - Design Document

*Created: 2025-11-18*

## Overview

This design extends the multi-backend LLM service with MCP tools that enable Claude instances to communicate with and manage other Claude instances. Built on the dynamic tool loading infrastructure from `mcp-nrepl-joyride`.

## Goals

1. **Agent autonomy** - Orchestrators can spawn/manage workers without external control
2. **Full observability** - Extensive logging of all inter-agent communication
3. **Controlled execution** - Script mode and confirmation mode for safety
4. **Cost protection** - Limits on workers, tokens, and spawning
5. **Dynamic loading** - Tools loaded at runtime into existing MCP server

---

## Architecture

### Integration with Existing Infrastructure

```
┌─────────────────────────────────────────────────────┐
│              nrepl-mcp-server (port 7888)           │
├─────────────────────────────────────────────────────┤
│  Existing Tools:                                     │
│  - local-eval, local-load-file                      │
│  - nrepl-eval, nrepl-connection                     │
│  - calculate, pb-fm-mcp tools                       │
├─────────────────────────────────────────────────────┤
│  NEW: Inter-Agent Tools (dynamically loaded)        │
│  - agent-spawn-worker                               │
│  - agent-send-task                                  │
│  - agent-get-response                               │
│  - agent-kill-worker                                │
│  - agent-list-workers                               │
│  - agent-workflow-execute                           │
├─────────────────────────────────────────────────────┤
│  claude_service.clj (already loaded)                │
│  - spawn!, fork!, ask, relay, kill!, etc.           │
└─────────────────────────────────────────────────────┘
```

### Component Flow

```
Orchestrator (Sonnet)
    │
    ├─> Calls MCP tool: agent-spawn-worker
    │       │
    │       └─> claude-service/fork! with :haiku
    │           └─> Logs to agent-communication.log
    │
    ├─> Calls MCP tool: agent-send-task
    │       │
    │       └─> claude-service/ask
    │           └─> Logs request + response
    │
    └─> Calls MCP tool: agent-get-response
            │
            └─> Returns worker's output
```

---

## Logging System

### Log Format

All inter-agent actions logged to `logs/agent-communication.log`:

```
2025-11-18T22:15:01.123Z [SPAWN]  orchestrator -> worker-1 | model=haiku pid=12345
2025-11-18T22:15:02.456Z [SEND]   orchestrator -> worker-1 | "fetch HASH stats" (42 chars)
2025-11-18T22:15:05.789Z [RECV]   worker-1 -> orchestrator | response (1234 chars)
2025-11-18T22:15:06.012Z [KILL]   orchestrator -> worker-1 | cleanup complete
2025-11-18T22:15:06.345Z [COST]   session total | spawns=3 requests=12 est_tokens=15000
```

### Log Levels

| Level | Description |
|-------|-------------|
| `SPAWN` | Worker creation |
| `SEND` | Message sent to worker |
| `RECV` | Response received from worker |
| `KILL` | Worker terminated |
| `RELAY` | Data passed between agents |
| `COST` | Token/cost tracking |
| `ERROR` | Failures and exceptions |
| `LIMIT` | Rate/quota limits hit |

### Implementation

```clojure
(defonce agent-log-file (atom "logs/agent-communication.log"))

(defn log-agent-action!
  "Log inter-agent action with timestamp"
  [action from to details]
  (let [timestamp (java.time.Instant/now)
        entry (format "%s [%-6s] %s -> %s | %s%n"
                      timestamp action from to details)]
    (spit @agent-log-file entry :append true)
    ;; Also print for immediate visibility
    (print entry)))
```

---

## Execution Modes

### Mode 1: Script Mode (No Improvisation)

Orchestrator follows a predefined workflow exactly.

```clojure
;; Workflow definition
{:name "portfolio-analysis"
 :description "Fetch blockchain data and analyze"
 :max-workers 3
 :steps
 [{:id 1 :action :spawn :name "fetcher" :model :haiku}
  {:id 2 :action :send :target "fetcher"
         :prompt "Use fetch_current_hash_statistics. Return JSON only."}
  {:id 3 :action :relay :from "fetcher"
         :prompt "Analyze this data for investment insights"}
  {:id 4 :action :kill :name "fetcher"}]}
```

**Tool: agent-workflow-execute**

```clojure
(defn handle [{:keys [workflow]}]
  (log-agent-action! "SCRIPT" "system" "orchestrator"
                     (str "Starting workflow: " (:name workflow)))

  (doseq [step (:steps workflow)]
    (log-agent-action! "STEP" "orchestrator" (:target step "system")
                       (str "Step " (:id step) ": " (:action step)))

    (case (:action step)
      :spawn (claude-service/fork! ...)
      :send  (claude-service/ask ...)
      :relay (claude-service/relay ...)
      :kill  (claude-service/kill! ...))

    ;; Brief pause between steps for observability
    (Thread/sleep 500))

  {:status :complete :workflow (:name workflow)})
```

### Mode 2: Confirmation Mode

Each action requires explicit approval.

```clojure
;; Orchestrator proposes action
{:type "confirmation_required"
 :action "spawn"
 :details {:name "fetcher" :model "haiku"}
 :prompt "I want to spawn a Haiku worker called 'fetcher'"
 :options ["approve" "deny" "modify"]}

;; User responds
{:decision "approve"}
;; or
{:decision "modify"
 :changes {:model "sonnet"}}
```

**Implementation via callback**

```clojure
(defonce confirmation-callback (atom nil))

(defn request-confirmation!
  "Request user confirmation for an action"
  [action details]
  (if-let [callback @confirmation-callback]
    (callback {:action action :details details})
    ;; No callback = auto-approve (script mode)
    {:decision "approve"}))

(defn set-confirmation-mode!
  "Enable confirmation mode with callback"
  [callback-fn]
  (reset! confirmation-callback callback-fn))
```

---

## MCP Tool Specifications

### 1. agent-spawn-worker

Spawn a new worker instance.

```clojure
(def tool-name "agent-spawn-worker")

(def metadata
  {:description "Spawn a worker Claude instance for task execution"
   :inputSchema
   {:type "object"
    :properties
    {:name {:type "string"
            :description "Unique name for the worker"}
     :model {:type "string"
             :enum ["haiku" "sonnet" "opus"]
             :description "Model to use (default: haiku)"}
     :inherit-context {:type "boolean"
                       :description "Fork from caller's context (default: true)"}}
    :required ["name"]}})

(defn handle [{:keys [name model inherit-context]
               :or {model "haiku" inherit-context true}}]
  ;; Check limits
  (when (>= (count-active-workers) @max-workers)
    (log-agent-action! "LIMIT" "system" name "Max workers exceeded")
    (throw (ex-info "Worker limit exceeded" {:limit @max-workers})))

  ;; Request confirmation if in confirmation mode
  (let [decision (request-confirmation! :spawn {:name name :model model})]
    (when (= "deny" (:decision decision))
      (throw (ex-info "Action denied by user" {}))))

  ;; Spawn worker
  (let [result (if inherit-context
                 (claude-service/fork! (current-instance) name :model (keyword model))
                 (claude-service/spawn! name :model (keyword model)))]

    (log-agent-action! "SPAWN" (current-instance) name
                       (format "model=%s pid=%s" model (:pid result)))

    {:status "spawned"
     :worker name
     :model model
     :pid (:pid result)}))
```

### 2. agent-send-task

Send a task to a worker.

```clojure
(def tool-name "agent-send-task")

(def metadata
  {:description "Send a task prompt to a worker and wait for response"
   :inputSchema
   {:type "object"
    :properties
    {:worker {:type "string"
              :description "Name of worker to send task to"}
     :prompt {:type "string"
              :description "Task prompt for the worker"}
     :timeout-ms {:type "integer"
                  :description "Timeout in milliseconds (default: 60000)"}}
    :required ["worker" "prompt"]}})

(defn handle [{:keys [worker prompt timeout-ms]
               :or {timeout-ms 60000}}]
  (log-agent-action! "SEND" (current-instance) worker
                     (format "\"%s\" (%d chars)"
                             (subs prompt 0 (min 50 (count prompt)))
                             (count prompt)))

  (let [start-time (System/currentTimeMillis)
        response (claude-service/ask worker prompt)
        elapsed (- (System/currentTimeMillis) start-time)]

    (log-agent-action! "RECV" worker (current-instance)
                       (format "response (%d chars, %dms)"
                               (count response) elapsed))

    {:status "complete"
     :worker worker
     :response response
     :elapsed-ms elapsed}))
```

### 3. agent-get-response

Get a worker's last response (for async workflows).

```clojure
(def tool-name "agent-get-response")

(def metadata
  {:description "Get the last response from a worker"
   :inputSchema
   {:type "object"
    :properties
    {:worker {:type "string"
              :description "Name of worker"}}
    :required ["worker"]}})

(defn handle [{:keys [worker]}]
  (let [response (claude-service/get-last-response worker)]
    {:worker worker
     :response response
     :timestamp (:timestamp response)}))
```

### 4. agent-kill-worker

Terminate a worker.

```clojure
(def tool-name "agent-kill-worker")

(def metadata
  {:description "Terminate a worker instance"
   :inputSchema
   {:type "object"
    :properties
    {:worker {:type "string"
              :description "Name of worker to kill"}}
    :required ["worker"]}})

(defn handle [{:keys [worker]}]
  (log-agent-action! "KILL" (current-instance) worker "cleanup requested")

  (let [result (claude-service/kill! worker)]
    (log-agent-action! "KILL" (current-instance) worker "cleanup complete")

    {:status "killed"
     :worker worker}))
```

### 5. agent-list-workers

List all active workers.

```clojure
(def tool-name "agent-list-workers")

(def metadata
  {:description "List all active worker instances"
   :inputSchema
   {:type "object"
    :properties {}}})

(defn handle [_]
  (let [services (claude-service/list-services)
        workers (for [[name info] services]
                  {:name name
                   :model (:model info)
                   :pid (:pid info)
                   :requests (:request-count info)
                   :created (:created-at info)})]
    {:workers workers
     :count (count workers)
     :limit @max-workers}))
```

### 6. agent-workflow-execute

Execute a predefined workflow script.

```clojure
(def tool-name "agent-workflow-execute")

(def metadata
  {:description "Execute a predefined workflow script"
   :inputSchema
   {:type "object"
    :properties
    {:workflow {:type "object"
                :description "Workflow definition with steps"}}
    :required ["workflow"]}})
```

---

## Safeguards

### Worker Limits

```clojure
(defonce max-workers (atom 5))
(defonce max-requests-per-worker (atom 20))
(defonce max-total-requests (atom 100))

(defonce session-stats
  (atom {:spawns 0
         :requests 0
         :estimated-tokens 0}))
```

### Cost Tracking

```clojure
(def token-estimates
  "Rough token estimates per request"
  {:haiku 500
   :sonnet 1000
   :opus 2000})

(defn track-cost! [model action]
  (swap! session-stats update :requests inc)
  (swap! session-stats update :estimated-tokens
         + (get token-estimates model 1000))

  ;; Log cost periodically
  (when (zero? (mod (:requests @session-stats) 10))
    (log-agent-action! "COST" "system" "session"
                       (format "spawns=%d requests=%d est_tokens=%d"
                               (:spawns @session-stats)
                               (:requests @session-stats)
                               (:estimated-tokens @session-stats)))))
```

### Auto-Cleanup

```clojure
(defn cleanup-idle-workers!
  "Kill workers that haven't been used recently"
  [idle-threshold-ms]
  (let [now (System/currentTimeMillis)
        services (claude-service/list-services)]
    (doseq [[name info] services]
      (when (> (- now (:last-used info now)) idle-threshold-ms)
        (log-agent-action! "CLEANUP" "system" name "idle timeout")
        (claude-service/kill! name)))))
```

---

## Dynamic Loading

### Tool Manifest

```clojure
;; tools/agent-tools-manifest.edn
{:tool-name "agent-communication-tools"
 :version "0.1.0"
 :description "Inter-agent communication MCP tools"

 :files ["src/agent_tools/logging.clj"
         "src/agent_tools/safeguards.clj"
         "src/agent_tools/confirmation.clj"]

 :main "src/agent_tools/mcp_tools.clj"

 :lifecycle
 {:start-fn start!
  :stop-fn stop!}

 :default-config
 {:log-file "logs/agent-communication.log"
  :max-workers 5
  :max-requests 100
  :confirmation-mode false}}
```

### Loading into Running Server

```clojure
;; Via local-eval in the MCP server
(mcp.nrepl-server/local-load-file
  "/path/to/agent_tools/mcp_tools.clj")

;; Tools self-register and become available immediately
;; No server restart needed
```

### Self-Registration Pattern

```clojure
(ns agent-tools.mcp-tools
  (:require [nrepl-mcp-server.state.tool-registry :as registry]))

;; Define all tools...

;; Self-register on load
(registry/register-tool! "agent-spawn-worker"
  spawn-worker/handle
  spawn-worker/metadata)

(registry/register-tool! "agent-send-task"
  send-task/handle
  send-task/metadata)

;; etc...

(println "Agent communication tools loaded and registered")
```

---

## Usage Examples

### Example 1: Autonomous Portfolio Analysis

Orchestrator autonomously spawns workers and analyzes data:

```
User: "Analyze my wallet pb1qz6p0z..."

Orchestrator:
  1. Calls agent-spawn-worker {name: "wallet-fetcher", model: "haiku"}
  2. Calls agent-send-task {worker: "wallet-fetcher",
                           prompt: "fetch_complete_wallet_summary for pb1qz6p0z..."}
  3. Calls agent-spawn-worker {name: "market-fetcher", model: "haiku"}
  4. Calls agent-send-task {worker: "market-fetcher",
                           prompt: "fetch_current_hash_statistics"}
  5. Receives both responses
  6. Synthesizes analysis using both data sources
  7. Calls agent-kill-worker for both workers
  8. Returns comprehensive portfolio analysis
```

### Example 2: Script-Based Workflow

```clojure
;; Define reusable workflow
(def portfolio-workflow
  {:name "standard-portfolio-analysis"
   :steps
   [{:action :spawn :name "data-fetcher" :model :haiku}
    {:action :send :target "data-fetcher"
     :prompt "Fetch wallet summary for {{wallet_address}}"}
    {:action :relay :from "data-fetcher"
     :prompt "Analyze portfolio health and provide recommendations"}
    {:action :kill :name "data-fetcher"}]})

;; Execute via MCP
(agent-workflow-execute {:workflow portfolio-workflow
                         :params {:wallet_address "pb1qz6p0z..."}})
```

### Example 3: Confirmation Mode

```clojure
;; Enable confirmation mode
(set-confirmation-mode!
  (fn [action]
    (println "Confirm:" action)
    (let [response (read-line)]
      {:decision response})))

;; Now all agent actions require confirmation:
;; > Confirm: {:action :spawn :details {:name "worker-1" :model "haiku"}}
;; approve
;; > Confirm: {:action :send :details {:worker "worker-1" :prompt "..."}}
;; approve
```

---

## Log Analysis Tools

### View Recent Activity

```bash
tail -f logs/agent-communication.log
```

### Filter by Action Type

```bash
grep "\[SPAWN\]" logs/agent-communication.log
grep "\[COST\]" logs/agent-communication.log
```

### Session Summary

```bash
# Count actions
grep -c "\[SPAWN\]" logs/agent-communication.log
grep -c "\[SEND\]" logs/agent-communication.log

# Get final cost estimate
tail -1 logs/agent-communication.log | grep COST
```

---

## Implementation Phases

### Phase 1: Core Tools + Logging
- [ ] Implement logging system
- [ ] agent-spawn-worker
- [ ] agent-send-task
- [ ] agent-kill-worker
- [ ] agent-list-workers
- [ ] Basic safeguards (max-workers)

### Phase 2: Script Mode
- [ ] Workflow definition format
- [ ] agent-workflow-execute
- [ ] Step-by-step logging
- [ ] Error handling and rollback

### Phase 3: Confirmation Mode
- [ ] Confirmation callback system
- [ ] Integration with logging
- [ ] Modify action support

### Phase 4: Cost Optimization
- [ ] Token estimation
- [ ] Cost tracking and reporting
- [ ] Automatic model selection
- [ ] Budget limits

---

## Security Considerations

1. **No recursive spawning** - Workers cannot spawn other workers
2. **Limited tools** - Workers only get data-fetching tools, not agent-management
3. **Audit trail** - Complete log of all actions
4. **Rate limiting** - Prevent runaway costs
5. **Timeout enforcement** - All operations have timeouts

---

## Testing Strategy

### Unit Tests
- Tool registration
- Logging format
- Safeguard enforcement

### Integration Tests
- Full workflow execution
- Multi-worker coordination
- Error recovery

### Cost Verification
- Token estimates vs actual
- Budget limit enforcement

---

*Status: Design Phase*
*Next: Implement Phase 1 (Core Tools + Logging)*
