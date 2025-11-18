# Claude Service Architecture

## Overview

A persistent Claude subprocess service that enables programmatic AI interactions with full MCP tool access, context preservation, and the foundation for multi-agent Claude systems.

## The Problem

When users interact with AI-generated pages (forms, buttons, etc.), the AI is "out of the loop" - page code can't access MCP tools or invoke Claude. We needed a way to:

1. Keep Claude available for programmatic invocation
2. Preserve conversation context across calls
3. Provide efficient token usage (avoid reloading full history)
4. Enable multiple access methods (MCP, CLI, other processes)

## Discovery: Claude Code Pipe Mode

Claude Code supports a programmatic pipe mode with JSONL streaming:

```bash
# Single request
echo "What is 2+2?" | claude -p --output-format json

# Stream JSON protocol
printf '{"type":"user","message":{"role":"user","content":"Remember X"}}
{"type":"user","message":{"role":"user","content":"What was X?"}}
' | claude -p --input-format stream-json --output-format stream-json
```

**Key findings:**
- Multi-turn conversations work in single invocation
- Claude waits for more input on stdin (doesn't exit after first message)
- Context is preserved across messages
- Cache stays warm for efficient token usage

## Architecture

### Core Design

```
┌─────────────────────────────────────────┐
│  nREPL MCP Server (Babashka)            │
│                                         │
│  claude-service.clj                     │
│    ├── state (atom)                     │
│    │   ├── process                      │
│    │   ├── stdin/stdout pipes           │
│    │   ├── reader/writer                │
│    │   └── session-id                   │
│    │                                    │
│    └── Claude subprocess                │
│        ├── stdin (kept open)            │
│        └── stdout (JSONL responses)     │
│                                         │
│  API:                                   │
│    (start!)                             │
│    (stop!)                              │
│    (ask "prompt")                       │
│    (status)                             │
└─────────────────────────────────────────┘
           ▲              ▲
           │              │
      local-eval      nREPL:7888
           │              │
    (MCP clients)    ask-claude CLI
```

### Why This Architecture?

1. **nREPL MCP Server as host**: Already a persistent Babashka instance with process management
2. **Subprocess with open pipes**: Keeps Claude alive, context preserved
3. **Multiple access methods**:
   - `local-eval` for MCP clients
   - nREPL port 7888 for CLI tools
4. **Shared state**: Atoms in bb server enable inter-process communication

## Implementation

### claude-service.clj

Core service managing the Claude subprocess:

```clojure
(ns claude-service
  (:require [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

;; State
(defonce state
  (atom {:process nil
         :stdin nil
         :stdout nil
         :reader nil
         :writer nil
         :session-id nil
         :status :stopped}))

;; Start persistent Claude subprocess
(defn start! []
  (let [proc (p/process ["claude" "-p" "--verbose"
                         "--input-format" "stream-json"
                         "--output-format" "stream-json"]
                        {:shutdown p/destroy-tree})
        writer (io/writer (:in proc))
        reader (io/reader (:out proc))]
    (swap! state assoc
           :process proc
           :writer writer
           :reader reader
           :status :running)))

;; Send prompt and get response
(defn ask [prompt]
  (let [{:keys [writer reader]} @state
        msg {:type "user"
             :message {:role "user" :content prompt}}]
    ;; Send JSONL message
    (.write writer (json/generate-string msg))
    (.write writer "\n")
    (.flush writer)

    ;; Read responses until result
    (loop [result nil]
      (let [line (.readLine reader)
            parsed (json/parse-string line true)]
        (case (:type parsed)
          "result" (get-in parsed [:result :result])
          (recur result))))))
```

### ask-claude CLI

Babashka script using bencode nREPL client:

```clojure
#!/usr/bin/env bb

(require '[bencode.core :as bencode])
(import '[java.net Socket]
        '[java.io PushbackInputStream])

(defn nrepl-eval [code]
  (with-open [socket (Socket. "localhost" 7888)
              in (PushbackInputStream. (.getInputStream socket))
              out (.getOutputStream socket)]
    (bencode/write-bencode out {:op "eval" :code code})
    ;; Read responses until done...
    ))

(defn send-to-claude [prompt]
  (nrepl-eval (format "(claude-service/ask %s)" (pr-str prompt))))

;; CLI interface
(case (first *command-line-args*)
  "--status" (println (nrepl-eval "(claude-service/status)"))
  "--start"  (println (nrepl-eval "(claude-service/start!)"))
  "--stop"   (println (nrepl-eval "(claude-service/stop!)"))
  (println (send-to-claude (first *command-line-args*))))
```

## Usage

### Setup

```clojure
;; 1. Load service into nREPL MCP server
(local-load-file "src/claude_service.clj")

;; 2. Start local nREPL server for CLI access
(local-nrepl-server :op "start" :port 7888)

;; 3. Start Claude service
(local-eval "(claude-service/start!)")
```

### Via MCP (local-eval)

```clojure
(local-eval "(claude-service/ask \"What is 2+2?\")")
;; => "4"

(local-eval "(claude-service/ask \"Remember: SECRET-123\")")
(local-eval "(claude-service/ask \"What was the secret?\")")
;; => "SECRET-123"
```

### Via CLI

```bash
./scripts/ask-claude "What is 2+2?"
./scripts/ask-claude --status
./scripts/ask-claude --stop

echo "Analyze this code" | ./scripts/ask-claude
```

## Token Efficiency

### With --resume (old approach)
Each request reloads full conversation history:
- Request 1: 65k tokens (cache creation)
- Request 2: 65k tokens (cache read) + new tokens
- Request 3: 65k tokens (cache read) + new tokens
- Cost grows with history × requests

### With persistent subprocess (new approach)
History stays loaded:
- Request 1: 65k tokens (cache creation)
- Request 2: ~38 tokens (incremental)
- Request 3: ~38 tokens (incremental)
- 10x+ more efficient for multi-turn

## Multi-Agent Architecture

### Spawning Sub-Claudes

The parent Claude can spawn worker Claudes for delegation:

```clojure
;; Parent Claude spawns workers
(local-eval "(claude-service/ask \"Analyze all security vulnerabilities\")")
(local-eval "(claude-service/ask \"Which is most critical?\")")
(local-eval "(claude-service/ask \"Generate a fix for that one\")")
```

### Multiple Claude Services

Extend to support named services:

```clojure
;; Multiple independent Claude instances
(defonce services (atom {}))

(defn start-service! [name]
  (swap! services assoc name (create-claude-subprocess)))

(defn ask-service [name prompt]
  (send-to-subprocess (get @services name) prompt))

;; Usage
(start-service! :researcher)
(start-service! :reviewer)
(start-service! :fixer)

(ask-service :researcher "Find issues")
(ask-service :reviewer "Prioritize issues")
(ask-service :fixer "Fix top priority")
```

### Async Communication Patterns

#### Pipeline Pattern
```
Claude A → Claude B → Claude C
(research)  (review)   (implement)
```

```clojure
;; Shared state for communication
(def shared-state (atom {:findings nil :priorities nil :fixes nil}))

;; Pipeline execution
(future
  (let [findings (ask-service :researcher "Find all issues")]
    (swap! shared-state assoc :findings findings)))

(future
  (loop []
    (when-let [findings (:findings @shared-state)]
      (let [priorities (ask-service :reviewer
                         (str "Prioritize: " findings))]
        (swap! shared-state assoc :priorities priorities)))
    (Thread/sleep 1000)
    (recur)))

(future
  (loop []
    (when-let [priorities (:priorities @shared-state)]
      (let [fixes (ask-service :fixer
                    (str "Fix top 3: " priorities))]
        (swap! shared-state assoc :fixes fixes)))
    (Thread/sleep 1000)
    (recur)))
```

#### Fan-Out Pattern
```
        ┌→ Claude B (task 1)
Claude A ─→ Claude C (task 2)
        └→ Claude D (task 3)
```

```clojure
(defn fan-out [tasks]
  (let [results (atom {})
        futures (for [[name task] tasks]
                  (future
                    (let [result (ask-service name task)]
                      (swap! results assoc name result))))]
    (doseq [f futures] @f)
    @results))

(fan-out {:analyzer "Find performance issues"
          :security "Find security issues"
          :style    "Find code style issues"})
```

#### Consensus Pattern
```
Claude A ─┐
Claude B ─┼→ Aggregator → Final Decision
Claude C ─┘
```

```clojure
(defn consensus [prompt services]
  (let [responses (for [svc services]
                    (ask-service svc prompt))
        aggregated (str/join "\n---\n" responses)]
    (ask-service :aggregator
      (str "Multiple analyses:\n" aggregated
           "\n\nSynthesize the best answer:"))))
```

#### Message Queue Pattern

```clojure
;; Shared message queues
(def queues (atom {:research (clojure.lang.PersistentQueue/EMPTY)
                   :review   (clojure.lang.PersistentQueue/EMPTY)
                   :fix      (clojure.lang.PersistentQueue/EMPTY)}))

(defn enqueue [queue-name msg]
  (swap! queues update queue-name conj msg))

(defn dequeue [queue-name]
  (let [q (get @queues queue-name)
        msg (peek q)]
    (swap! queues update queue-name pop)
    msg))

;; Workers poll their queues
(defn start-worker [name input-queue output-queue]
  (future
    (loop []
      (when-let [msg (dequeue input-queue)]
        (let [result (ask-service name msg)]
          (when output-queue
            (enqueue output-queue result))))
      (Thread/sleep 100)
      (recur))))
```

## Future Enhancements

### 1. Service Registry
```clojure
(defn list-services [] (keys @services))
(defn service-status [name] (:status (get @services name)))
(defn kill-all-services [] (doseq [s (vals @services)] (stop! s)))
```

### 2. Conversation Branching
```clojure
(defn fork-conversation [service-name]
  "Create new service with same conversation history")
```

### 3. Checkpointing
```clojure
(defn save-conversation [service-name path]
  "Save conversation state to disk")
(defn restore-conversation [service-name path]
  "Restore from checkpoint")
```

### 4. Load Balancing
```clojure
(defn ask-pool [pool-name prompt]
  "Route to least-busy Claude in pool")
```

### 5. Monitoring Dashboard
- Active services and their status
- Token usage per service
- Message throughput
- Conversation lengths

## Key Architectural Insights

### Claude Code vs SDK: Full Capabilities for Free

By using Claude Code's pipe mode instead of the Anthropic SDK, each spawned Claude inherits the complete Claude Code runtime:

| Claude SDK | Claude Code Subprocess |
|------------|------------------------|
| Raw API access | Full Claude Code runtime |
| No tools | 150+ MCP tools |
| No file access | Read/Write/Edit/Glob/Grep |
| No web access | WebSearch, WebFetch |
| No integrations | GitHub, AWS, memory, tree-sitter... |
| Manual tool implementation | All tools pre-configured |
| No sub-agents | Task tool with specialized agents |

**You'd have to rebuild months of Claude Code development to match this with the SDK.**

Each spawned Claude can:
- Search and edit codebases
- Run bash commands
- Search the web
- Access GitHub repos
- Use all configured MCP servers
- Spawn its own sub-agents via Task tool

### Decentralized Swarm: No Hierarchy Required

The architecture is fundamentally decentralized - there's no need for a "master" Claude:

```
┌─────────────────────────────┐
│  nREPL MCP Server (:7888)   │
│                             │
│  claude-service             │
│    ├── Claude A             │
│    ├── Claude B             │
│    └── Claude C             │
└─────────────────────────────┘
    ▲     ▲     ▲     ▲
    │     │     │     │
   bb   REPL  curl  Emacs
  script       │    CIDER
              any
            nREPL
            client
```

**Any nREPL client can orchestrate the swarm:**
- Babashka scripts
- Emacs/CIDER
- VS Code Calva
- IntelliJ Cursive
- Web servers
- Another Claude
- Any process that speaks nREPL

**The Claudes are peers, not subordinates.** There's no required hierarchy - any client can:
- Spawn new Claudes
- Send them tasks
- Read their results
- Coordinate communication between them

This enables true distributed AI where orchestration can come from anywhere, and Claudes can even orchestrate each other.

### Federated Topology: Location-Aware Reach

Multiple nREPL-MCP instances can be deployed across different environments, each defining the "reach" of its attached Claudes:

```
┌─────────────────────┐         ┌─────────────────────┐
│  Laptop nREPL-MCP   │         │  AWS nREPL-MCP      │
│                     │         │                     │
│  Claude A ──────────┼─────────┼──► Claude C         │
│  Claude B           │◄────────┼────── Claude D      │
│                     │  nREPL  │                     │
│  Reach:             │  over   │  Reach:             │
│  - Local files      │ network │  - S3 buckets       │
│  - Dev environment  │         │  - RDS databases    │
│  - Personal secrets │         │  - Lambda functions │
│  - USB devices      │         │  - VPC resources    │
└─────────────────────┘         └─────────────────────┘
         │                               │
         └───────────┬───────────────────┘
                     │
            Combined Reach:
            Both environments!
```

**Each nREPL-MCP instance is a locality:**
- **Laptop**: Access to local filesystem, dev tools, personal credentials
- **AWS EC2**: Access to cloud resources, production data, scaled compute
- **On-prem server**: Access to internal databases, legacy systems
- **Edge device**: Access to IoT sensors, local hardware

**Federation benefits:**
- Claudes can coordinate across environments
- Sensitive data stays in its locality (security boundary)
- Leverage different compute resources (GPU on cloud, fast SSD on laptop)
- Geographic distribution for latency optimization

**Example: Hybrid development workflow**
```clojure
;; Laptop Claude analyzes local code
(ask-laptop :analyzer "Review my local changes in ~/project")

;; AWS Claude runs heavy computation
(ask-aws :compute "Run ML training on this dataset in S3")

;; Coordinate results
(ask-laptop :integrator
  (str "Integrate local analysis with cloud results: "
       @laptop-findings @aws-results))
```

**This is federated AI with location-aware capabilities** - each node contributes its unique reach to the collective swarm.

### nREPL as the Universal Protocol

The nREPL protocol already provides the exact infrastructure needed for Claude swarms:

**Protocol Parallel:**

```
nREPL Protocol                    Claude Protocol (stream-json)
─────────────────                 ─────────────────────────────
┌─────────────────┐               ┌─────────────────┐
│ Request         │               │ Request         │
│  - id: uuid     │               │  - type: "user" │
│  - op: "eval"   │               │  - message: ... │
│  - code: "..."  │               │  - (session_id) │
│  - session: ... │               └────────┬────────┘
└────────┬────────┘                        │
         │ async, queued                   │ async, streamed
         ▼                                 ▼
┌─────────────────┐               ┌─────────────────┐
│ Response(s)     │               │ Response(s)     │
│  - id: uuid     │ ← correlation │  - session_id   │ ← correlation
│  - value: ...   │               │  - result: ...  │
│  - status: ...  │               │  - type: ...    │
└─────────────────┘               └─────────────────┘
```

**The insight**: nREPL is already a message-passing protocol for distributed, async, addressable computation. We don't need to invent a new protocol for Claude swarms - we can **wrap Claude instances as nREPL-addressable endpoints**.

**Unified Addressing Model:**

```clojure
;; Current: nREPL MCP server connects to JVM nREPL servers
(nrepl-connection :op "connect" :connection "localhost:7890" :nickname "clay")
(nrepl-eval :connection "clay" :code "(+ 1 2)")

;; Future: Same pattern for Claude instances
(claude-spawn :nickname "researcher")
(claude-spawn :nickname "reviewer")
(claude-spawn :nickname "implementer")

;; Uniform async interface
(claude-ask :connection "researcher" :prompt "Find issues" :id "req-001")
(claude-ask :connection "reviewer" :prompt "Prioritize" :id "req-002")

;; Get responses by ID (async)
(claude-get-response "req-001")  ;; => {:status :pending}
(claude-get-response "req-001")  ;; => {:status :complete :result "..."}

;; Or use sync convenience
(claude-ask-sync "researcher" "Quick question")
```

**What this enables:**

1. **Uniform addressing** - `(ask :researcher ...)` same as `(eval :clay ...)`
2. **Async by default** - Fire requests, collect responses later
3. **Request correlation** - Track multi-turn conversations by ID
4. **Fan-out/gather** - Send to many, collect as they complete
5. **Claude-to-Claude** - Researcher can ask Reviewer directly

The nREPL MCP server becomes a **message broker** for both compute servers AND Claude instances. Same protocol, same tooling, same async patterns.

The existing `messaging.clj` in nREPL MCP server already has all the pieces:
- `send-message-async` - Fire and forget with ID
- `result-processing-async` - Collect responses by ID
- `generate-id` - UUID v7 for request correlation
- `merge-responses` - Aggregate multiple response messages

**Architecture Evolution:**

```
Phase 1 (Current):
  nREPL Client → nREPL MCP Server → JVM nREPL Servers

Phase 2 (Claude Service):
  nREPL Client → nREPL MCP Server → Claude Subprocess
                                  → JVM nREPL Servers

Phase 3 (Unified):
  Any Client → nREPL MCP Server → Named Claude Instances (async, queued)
                                → Named nREPL Servers (async, queued)
                                → (Same protocol, same patterns)
```

The nREPL MCP server is not just an enabler - it's the **foundational infrastructure** for a Claude swarm system.

### Session Forking: Checkpoint & Branch Pattern

Claude sessions can be forked using `--resume <session-id>`, enabling powerful patterns for shared context and parallel specialization.

**How Claude Sessions Work:**

```
Original session abc-123:
  Turn 1: "Remember X"
  Turn 2: "What was X?" → "X"

Resume from abc-123 (creates NEW branch):
  Turn 1: "Remember X"      ← copied from original
  Turn 2: "What was X?"     ← copied from original
  Turn 3: "Now focus on Y"  ← NEW, only in this branch
```

When you resume a session:
- Claude loads the conversation history from disk (JSONL file)
- You get a **COPY** of that context at that moment
- Then you diverge independently
- Branches **cannot see each other's new messages** after forking

**Checkpoint & Fork Pattern:**

```clojure
;; 1. Build shared context in a base instance
(spawn! "base")
(ask "base" "You are analyzing project X. Key files: a.clj, b.clj, c.clj")
(ask "base" "The coding standards are: strict typing, no mutations")
(ask "base" "The architecture uses: event sourcing, CQRS")

;; 2. Get the checkpoint (session-id)
(def checkpoint-id (:session-id (service-info "base")))
;; => "abc-123-def-456"

;; 3. Fork specialized workers from checkpoint
(spawn-from-session! "security" checkpoint-id)
(spawn-from-session! "performance" checkpoint-id)
(spawn-from-session! "style" checkpoint-id)

;; 4. Each inherits full context, then specializes
(ask "security" "Focus only on security vulnerabilities")
(ask "performance" "Focus only on performance bottlenecks")
(ask "style" "Focus only on code style issues")

;; Each worker has:
;; - All the base context (project, standards, architecture)
;; - Their own specialization
;; - Independent conversation history going forward
```

**Benefits:**
- **No context rebuilding**: Workers don't repeat the expensive context-loading phase
- **Consistent starting point**: All workers have identical understanding
- **Parallel specialization**: Each can focus on different aspects
- **Token efficient**: Context loaded once, forked many times

**Federated Forking (Laptop → Cloud):**

```clojure
;; On laptop: Build context with local file access
(spawn! "local")
(ask "local" "Analyze ~/project - here's what I found: ...")
(ask "local" "The dependencies are: ...")
(def checkpoint (:session-id (service-info "local")))

;; On AWS: Resume with full context, run heavy compute
;; (session files would need to be on shared storage)
(spawn-from-session! "cloud-worker" checkpoint)
(ask "cloud-worker" "Run ML analysis on this codebase")
;; Cloud worker already knows everything laptop learned
```

**Limitations:**
- Branches diverge independently (no real-time sync)
- Session files must be accessible (local disk or shared storage)
- Each fork is a full subprocess (memory cost)

**Implementation:**

```clojure
(defn spawn-from-session!
  "Spawn a new Claude instance that resumes from an existing session.
   The new instance inherits all conversation history from that session,
   then diverges independently."
  [name session-id]
  (when (get @registry name)
    (throw (ex-info (str "Claude service '" name "' already exists") {:name name})))

  (println (str "Spawning Claude instance '" name "' from session: " session-id))

  (let [args (concat claude-args ["--resume" session-id])
        proc (p/process (into [claude-path] args)
                        {:shutdown p/destroy-tree})
        ;; ... rest of spawn logic
        ]
    ;; Track that this was forked from another session
    (swap! registry assoc name
           (assoc service
                  :forked-from session-id
                  :fork-time (System/currentTimeMillis)))
    ...))
```

## Benefits

1. **Persistent context**: No history reload per request
2. **Token efficient**: 10x+ savings on multi-turn
3. **Full tool access**: All 150+ MCP tools available
4. **Multiple access methods**: MCP, CLI, nREPL
5. **Multi-agent ready**: Foundation for Claude swarms
6. **Async communication**: Services can coordinate via shared state
7. **Zero tool implementation**: Inherit all Claude Code capabilities
8. **Decentralized**: No master node required, any client can orchestrate

## Real-World Example: Wallet Expert Flow

A concrete application showing how a persistent Claude instance integrates with Clay for interactive notebooks:

```
┌─────────────────────────────────────────────────────────────┐
│  User visits wallet-holdings.html                           │
│  Enters wallet address: pb1xyz...                           │
│  Submits form                                               │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Clay HTTP Server (:1971)                                   │
│  Ring middleware intercepts /wallet?address=pb1xyz...       │
│  Triggers message to wallet-expert                          │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ (via bb-task or nREPL call to :7888)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  nREPL MCP Server (:7888)                                   │
│    └── claude-service                                       │
│          └── wallet-expert Claude                           │
│                                                             │
│  wallet-expert already knows:                               │
│    - MCP tools (fetch_wallet_summary, calculate)            │
│    - Component library                                      │
│    - How to write Clay notebooks                            │
│    - Connection to Clay nREPL (:7890)                       │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ wallet-expert connects to Clay
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Clay nREPL Server (:7890)                                  │
│                                                             │
│  wallet-expert:                                             │
│    1. Fetches wallet data via MCP tools                     │
│    2. Calculates portfolio metrics                          │
│    3. Edits wallet-holdings.clj with new data               │
│    4. Clay file-watcher re-renders                          │
│    5. Browser live-reloads with updated portfolio           │
└─────────────────────────────────────────────────────────────┘
```

**Ring Middleware in Clay:**

```clojure
(defn wallet-request-handler [request]
  (let [address (get-in request [:query-params "address"])]
    ;; Trigger wallet-expert via ask-claude script
    (shell/sh "./scripts/ask-claude" "-n" "wallet-expert"
              (str "Update notebooks/wallet-holdings.clj for: " address))
    {:status 200 :body "Processing..."}))

(defn wrap-wallet-handler [handler]
  (fn [request]
    (if (and (= (:uri request) "/wallet")
             (get-in request [:query-params "address"]))
      (wallet-request-handler request)
      (handler request))))
```

**Why This Works:**

The wallet-expert Claude is persistent and maintains full context about:
- The project structure and component library
- Available MCP tools for blockchain data
- How to write and edit Clay notebooks
- Connection to Clay's nREPL for direct evaluation

When it receives a new wallet address, it already knows *how* to update the notebook - it just needs the *what*.

## Files

- `src/claude_service.clj` - Core service implementation
- `scripts/ask-claude` - CLI tool
- `docs/claude-service-architecture.md` - This document

## Prerequisites

- nREPL MCP server running
- Claude Code installed (`~/.claude/local/claude`)
- Babashka for CLI script

---

*Created: 2025-11-17*
*Status: Working implementation with single service, multi-agent patterns documented for future development*
