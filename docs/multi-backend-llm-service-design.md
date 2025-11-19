# Multi-Backend LLM Service Design

## Overview

This document describes the design for extending `claude_service.clj` to support multiple LLM backends, enabling cost-effective multi-agent systems that can use Claude for critical tasks and cheaper/free local models for routine work.

## Motivation

Running a "Claude army" for multi-agent workflows can burn tokens quickly. By supporting multiple backends, we can:

1. **Reduce costs** - Use free local models (Ollama, llama.cpp) for testing and routine tasks
2. **Enable domain experts** - Smaller, focused contexts per expert = fewer tokens
3. **Provide fallbacks** - Switch backends without changing application code
4. **Support experimentation** - Test agent architectures without cost concerns

---

## Quick Start: The Minimal Viable Setup

**Don't get overwhelmed by options. Start here:**

### Step 1: Two-Component Architecture

```
┌──────────────────┐     MCP      ┌─────────────┐
│  Claude Haiku    │─────────────►│ MCP Servers │
│  (Data fetcher)  │◄─────────────│             │
└────────┬─────────┘              └─────────────┘
         │ Raw data
         ▼
┌──────────────────┐
│  Ollama          │
│  (Analysis)      │
└──────────────────┘
```

### Step 2: Why This Works

- **Haiku** ($0.80/1M tokens) fetches data via MCP - only Claude has native MCP support
- **Ollama** (free) does the actual analysis - most of the inference work
- **Combined**: ~90% cost reduction vs using Sonnet for everything

### Step 3: Prompt Engineering for Data Gateway

Keep Haiku prompts minimal to reduce tokens:

```
# Bad (expensive)
"Get portfolio data for pb1xyz and analyze staking rewards,
identify concerns, and suggest optimizations."

# Good (cheap)
"Call fetch_complete_wallet_summary for pb1xyz.
Return ONLY raw JSON. No interpretation."
```

### Step 4: Incremental Implementation

**Each step adds one variable. Master it before moving on.**

#### Phase 1: Claude Only
```
┌─────────────┐     MCP      ┌─────────────┐
│   Claude    │─────────────►│ MCP Servers │
│   Sonnet    │◄─────────────│             │
└─────────────┘              └─────────────┘
```
- Learn `claude_service.clj` patterns
- Test spawn/ask/fork workflow
- Understand the agentic loop
- **Milestone**: Working multi-instance orchestration

#### Phase 2: Claude + Haiku
```
┌─────────────┐              ┌─────────────┐
│   Claude    │──────────────│   Claude    │
│   Sonnet    │              │   Haiku     │
│ Orchestrator│              │ Data Fetch  │
└─────────────┘              └──────┬──────┘
                                    │ MCP
                                    ▼
                             ┌─────────────┐
                             │ MCP Servers │
                             └─────────────┘
```
- Add `--model` flag support to spawn
- Learn model-appropriate prompting
- See cost reduction (Haiku ~4x cheaper)
- **Milestone**: Cost-optimized Claude-only system

#### Phase 3: Claude + Haiku + Ollama
```
┌─────────────┐              ┌─────────────┐
│   Claude    │              │   Claude    │
│   Sonnet    │              │   Haiku     │
│ Orchestrator│              │ Data Fetch  │
└──────┬──────┘              └──────┬──────┘
       │                            │ MCP
       │                            ▼
       │                     ┌─────────────┐
       │                     │ MCP Servers │
       │                     └─────────────┘
       │
       ▼
┌─────────────┐
│   Ollama    │
│  Analysis   │
│    Free     │
└─────────────┘
```
- Install Ollama, test locally
- Create `openai-wrapper` script
- Integrate HTTP backend into service
- **Milestone**: Hybrid cloud/local system

#### Phase 4: Optimization (only when needed)
- Context compression
- Domain expert specialization
- Cost tracking
- Automatic backend selection

### Why This Order Matters

| Step | New Variables | Risk |
|------|---------------|------|
| Phase 1 | Claude service patterns | Low |
| Phase 2 | + Model selection | Low |
| Phase 3 | + HTTP protocol, local inference | Medium |
| Phase 4 | + Multiple optimizations | High |

**Debugging rule**: When something breaks, you know which component caused it.

### That's It

Start with Phase 1. Move to Phase 2 when Phase 1 is solid. Everything else in this document is reference material for later phases.

---

## Model Selection and Cost Optimization

### Available Claude Models

```bash
# Use Haiku (cheapest, fastest)
claude --model claude-haiku-3-5-20241022

# Use Sonnet (default, balanced)
claude --model claude-sonnet-4-20250514

# Use Opus (most capable, expensive)
claude --model claude-opus-4-20250514
```

### Cost Comparison

| Model | Input (per 1M) | Output (per 1M) | Speed | Best For |
|-------|----------------|-----------------|-------|----------|
| **Haiku 3.5** | $0.80 | $4.00 | Fastest | Data fetching, simple routing |
| **Sonnet 4** | $3.00 | $15.00 | Fast | General tasks, orchestration |
| **Opus 4** | $15.00 | $75.00 | Slower | Complex reasoning, synthesis |

**Haiku is ~4x cheaper than Sonnet** - ideal for the data gateway role.

### Multi-Model Strategy

```clojure
(def model-configs
  {:data-fetcher  "claude-haiku-3-5-20241022"    ; MCP data access
   :orchestrator  "claude-sonnet-4-20250514"     ; Task routing
   :synthesizer   "claude-opus-4-20250514"})     ; Final analysis

(spawn! "fetcher" :model (:data-fetcher model-configs))
```

### Data Gateway Prompt Pattern

When using Claude only for MCP tool access, minimize tokens:

**System Prompt:**
```
You are a data fetching agent. Your ONLY job is to call MCP tools
and return raw data. Never analyze. Never interpret. Never suggest.
Return data in the exact format requested. Minimize output tokens.
```

**User Prompt:**
```
Call fetch_complete_wallet_summary for pb1xyz.
Return as:
{
  "total_aum": <value>,
  "staked": <value>,
  "rewards": <value>
}
Nothing else.
```

**Token Savings:**

| Approach | Input | Output | Total |
|----------|-------|--------|-------|
| "Analyze portfolio" | 50 | 500+ | 550+ |
| "Fetch, return JSON only" | 30 | 50 | 80 |

~85% reduction by treating Claude as a data pipe.

---

## Protocol Analysis

### Claude Code CLI Protocol

Claude uses a proprietary streaming JSONL protocol via stdin/stdout:

```json
// Input (send to stdin)
{"type":"user","message":{"role":"user","content":"Hello"}}

// Output (read from stdout)
{"type":"system","subtype":"init","session_id":"abc-123",...}
{"type":"assistant","message":{"role":"assistant","content":"Hi!"}}
{"type":"result","session_id":"abc-123","result":"Hi!","cost_usd":0.001}
```

**Key characteristics:**
- Transport: Subprocess stdin/stdout
- Format: JSONL (newline-delimited JSON)
- Session management: Server-side via `session_id` and `--resume`
- Context: Managed by CLI, sends to stateless API

### OpenAI API Protocol

OpenAI's protocol has become the **de facto industry standard**:

```json
// Request
POST /v1/chat/completions
{
  "model": "gpt-4",
  "messages": [
    {"role": "system", "content": "You are helpful."},
    {"role": "user", "content": "Hello"}
  ],
  "temperature": 0.7,
  "stream": false
}

// Response (non-streaming)
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "choices": [{
    "index": 0,
    "message": {"role": "assistant", "content": "Hi!"},
    "finish_reason": "stop"
  }],
  "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
}

// Response (streaming via SSE)
data: {"choices":[{"delta":{"content":"Hi"}}]}
data: {"choices":[{"delta":{"content":"!"}}]}
data: [DONE]
```

**Key characteristics:**
- Transport: HTTP REST
- Format: JSON (SSE for streaming)
- Session management: Stateless - client sends full history each time
- Specification: https://github.com/openai/openai-openapi

### Protocol Comparison

| Aspect | Claude CLI | OpenAI API |
|--------|-----------|------------|
| Transport | stdin/stdout | HTTP |
| Format | JSONL | JSON/SSE |
| Message structure | `{"role":"user","content":"..."}` | `{"role":"user","content":"..."}` |
| Roles | system, user, assistant | system, user, assistant |
| Context management | Server maintains session | Client sends full history |
| Streaming | JSONL objects | SSE `data:` lines |

**Key insight:** The core message format (`role` + `content`) is essentially identical. An adapter layer is straightforward.

### Backends Using OpenAI Protocol

- **llama.cpp server** - Full OpenAI compatibility
- **vLLM** - Drop-in OpenAI replacement
- **Ollama** - Partial compatibility (raw JSON, not SSE)
- **LM Studio** - OpenAI compatible
- **LocalAI** - OpenAI compatible

---

## Cloud vs Local Inference Architecture

A critical distinction when choosing backends is **where inference runs**.

### Claude Code CLI: Cloud Inference

```
┌─────────────────────────────────┐
│          CLOUD                  │
│  ┌───────────────────────┐      │
│  │   Claude Model        │      │
│  │   (Inference here)    │      │
│  └───────────┬───────────┘      │
└──────────────┼──────────────────┘
               │ HTTP API
               │
┌──────────────┼──────────────────┐
│          LOCAL                  │
│  ┌───────────▼───────────┐      │
│  │   Claude Code CLI     │      │
│  │   (Orchestrator only) │      │
│  └───────────┬───────────┘      │
│              │                  │
│  ┌───────────▼───────────┐      │
│  │   MCP Servers         │      │
│  │   (Tool execution)    │      │
│  └───────────────────────┘      │
└─────────────────────────────────┘
```

**Zero local inference** with Claude Code. The CLI is purely an orchestrator:
- HTTP client to Anthropic API
- MCP server manager
- Tool call router
- Context accumulator

The **intelligence is in the cloud**, the **execution is local**.

### The Agentic Loop

When Claude uses tools, this cycle occurs:

1. **Local → Cloud:** Send prompt to Anthropic API
2. **Cloud:** Claude runs inference, returns "use this tool"
3. **Local:** CLI executes tool via MCP server
4. **Local → Cloud:** Send tool result as new context
5. **Cloud:** Claude runs inference again
6. **Repeat** until Claude returns final answer (no tool calls)

Each cycle adds tokens to context - this is why agentic workflows can get expensive.

### Local Inference: Ollama, llama.cpp, vLLM

```
┌─────────────────────────────────┐
│          LOCAL                  │
│  ┌───────────────────────┐      │
│  │   LLM Model           │      │
│  │   (Inference here)    │      │
│  └───────────┬───────────┘      │
│              │                  │
│  ┌───────────▼───────────┐      │
│  │   HTTP Server         │      │
│  │   (Ollama/llama.cpp)  │      │
│  └───────────┬───────────┘      │
│              │                  │
│  ┌───────────▼───────────┐      │
│  │   Your Application    │      │
│  └───────────────────────┘      │
└─────────────────────────────────┘
```

With local models, inference runs on your machine - free after hardware setup.

### Inference Location Summary

| Backend | Inference | Cost Model | Capabilities |
|---------|-----------|------------|--------------|
| **Claude Code CLI** | Cloud | Per token | Full agent (tools, sessions) |
| **Claude API** | Cloud | Per token | Chat only |
| **OpenAI/GPT** | Cloud | Per token | Chat + function calling |
| **Ollama** | Local | Free | Chat + function calling |
| **llama.cpp** | Local | Free | Chat + function calling |
| **vLLM** | Local | Free | Chat + function calling |

### MCP Support Across Backends

**Claude Code CLI** has native MCP support - it's the reference implementation.

**Ollama** does not have native MCP support yet (GitHub issue #7865), but third-party bridges enable it:
- `ollama-mcp-bridge` - Connects Ollama to MCP servers
- `mcp-client-for-ollama` - TUI client with MCP support
- Requires models with function-calling support (e.g., `qwen2.5:7b`)

**Note:** Reasoning models like DeepSeek are not compatible with MCP due to different output patterns.

### Trade-offs

| Aspect | Cloud (Claude) | Local (Ollama) |
|--------|----------------|----------------|
| **Model quality** | State-of-the-art | Good, improving |
| **Cost** | $$$ per token | Free (after hardware) |
| **Latency** | Network dependent | Local speed |
| **Privacy** | Data sent to cloud | Data stays local |
| **Setup** | API key only | Install model, run server |
| **MCP support** | Native | Via bridges |

---

## Cost Optimization Through Domain Experts

### The Problem

One generalist agent with huge context = expensive:
```
Single agent × 50,000 tokens × $0.003/1K = $0.15 per request
```

### The Solution

Multiple domain experts with focused contexts:
```
5 experts × 5,000 tokens × $0.003/1K = $0.075 per request
= 50% cost for better results
```

### Architecture

```
┌─────────────────────┐
│    Orchestrator     │  Small context: task routing only
│   (Claude/GPT-4)    │
└──────────┬──────────┘
           │
    ┌──────┼──────┬──────────┐
    ▼      ▼      ▼          ▼
┌──────┐┌──────┐┌──────┐┌──────┐
│Secur-││Perf  ││Style ││Docs  │
│ity   ││Expert││Expert││Expert│
│Expert││      ││      ││      │
└──────┘└──────┘└──────┘└──────┘
 Ollama  Ollama  Ollama  Ollama
 llama3  llama3  llama3  llama3

Context:  Context:  Context:  Context:
OWASP,    BigO,     Linting,  API refs,
CVEs      Profiling Patterns  Examples
```

### Benefits

1. **Smaller context** → fewer tokens → lower cost
2. **Focused context** → less noise → better reasoning
3. **Domain knowledge** → can use smaller models per expert
4. **Parallelization** → experts work simultaneously
5. **Flexibility** → use Claude for orchestrator, local for workers

---

## Context Compression Strategies

Since OpenAI-compatible APIs are stateless, clients must manage conversation history. For long conversations, compression saves tokens.

### Strategy 1: Sliding Window

Keep only last N messages:

```clojure
(defn sliding-window [messages max-count]
  (let [system-msg (first (filter #(= "system" (:role %)) messages))
        recent (take-last max-count
                         (remove #(= "system" (:role %)) messages))]
    (if system-msg
      (cons system-msg recent)
      recent)))
```

**Trade-off:** Simple but loses older context completely.

### Strategy 2: Summarization

Use LLM to compress older history:

```clojure
(def compressed-history
  [{:role "system" :content "You are a security researcher"}
   {:role "user" :content "SUMMARY: Previously analyzed auth module.
    Found 3 SQL injection risks in login.clj. Decided to use
    parameterized queries. Now continuing with session management."}
   ;; Recent messages in full detail
   {:role "user" :content "Check the session timeout logic"}])
```

**Trade-off:** Best quality preservation but requires LLM call for compression.

### Strategy 3: Semantic Pruning

Use embeddings to keep only relevant messages:

```clojure
(defn relevant-messages [messages current-query top-k]
  (let [query-emb (embed current-query)
        scored (map #(assoc % :score
                           (cosine-sim query-emb (embed (:content %))))
                    messages)]
    (->> scored (sort-by :score >) (take top-k))))
```

**Trade-off:** Keeps relevant context but may miss related information.

### Strategy 4: Hierarchical Compression

Progressive compression by age:

```
[0-10 msgs]   → Full detail
[10-50 msgs]  → Bullet point summary
[50+ msgs]    → One paragraph overview
```

### Practical Implementation

```clojure
(defn compress-context [messages]
  (let [token-count (estimate-tokens messages)]
    (cond
      (< token-count 2000)  messages                    ; Keep full
      (< token-count 8000)  (sliding-window messages 20) ; Trim old
      :else                 (summarize-and-trim messages 10)))) ; Compress
```

---

## Implementation Approaches

### Approach 1: Subprocess Wrapper (Recommended)

Create a Babashka script that wraps OpenAI-compatible APIs with Claude CLI protocol:

```bash
#!/usr/bin/env bb
;; scripts/openai-wrapper

(require '[babashka.http-client :as http]
         '[cheshire.core :as json]
         '[clojure.java.io :as io])

(def endpoint (or (System/getenv "LLM_ENDPOINT") "http://localhost:11434"))
(def model (or (System/getenv "LLM_MODEL") "llama3"))

(defn call-api [messages]
  (-> (http/post (str endpoint "/v1/chat/completions")
                 {:headers {"Content-Type" "application/json"}
                  :body (json/generate-string
                         {:model model
                          :messages messages
                          :stream false})})  ; Non-streaming for simplicity
      :body
      (json/parse-string true)))

(defn -main []
  (let [history (atom [])
        reader (io/reader *in*)]
    (doseq [line (line-seq reader)]
      (let [msg (json/parse-string line true)]
        (when (= "user" (:type msg))
          ;; Add to history
          (swap! history conj {:role "user"
                               :content (get-in msg [:message :content])})
          ;; Call API
          (let [response (call-api @history)
                content (get-in response [:choices 0 :message :content])
                session-id (str (hash @history))]
            ;; Update history
            (swap! history conj {:role "assistant" :content content})
            ;; Output in Claude format
            (println (json/generate-string
                      {:type "result"
                       :result content
                       :session_id session-id}))
            (flush)))))))

(-main)
```

**Usage in claude_service.clj:**

```clojure
(def backends
  {:claude  {:cmd "/Users/franksiebenlist/.claude/local/claude"
             :args ["-p" "--verbose" "--input-format" "stream-json"
                    "--output-format" "stream-json"]}
   :ollama  {:cmd "./scripts/openai-wrapper"
             :args []
             :env {"LLM_ENDPOINT" "http://localhost:11434"
                   "LLM_MODEL" "llama3"}}
   :openai  {:cmd "./scripts/openai-wrapper"
             :args []
             :env {"LLM_ENDPOINT" "https://api.openai.com"
                   "LLM_MODEL" "gpt-4"
                   "OPENAI_API_KEY" "..."}}})

(defn spawn!
  [name & {:keys [backend] :or {backend :claude}}]
  (let [{:keys [cmd args env]} (get backends backend)
        proc (p/process (into [cmd] args)
                        {:env (merge (System/getenv) env)
                         :shutdown p/destroy-tree})]
    ;; ... rest of spawn logic
    ))
```

**Advantages:**
- Same process isolation as Claude
- Same `spawn!`/`kill!`/`fork!` lifecycle
- Swap backends by changing keyword
- History management in wrapper

### Approach 2: Direct HTTP Integration

Embed HTTP calls directly in claude_service.clj:

```clojure
(defn spawn-http!
  [name {:keys [endpoint model api-key]}]
  (swap! registry assoc name
         {:name name
          :type :http
          :endpoint endpoint
          :model model
          :api-key api-key
          :history (atom [])
          :status :running
          :created-at (System/currentTimeMillis)}))

(defn ask-http [name prompt]
  (let [{:keys [endpoint model api-key history]} (get @registry name)
        _ (swap! history conj {:role "user" :content prompt})
        response (http/post (str endpoint "/v1/chat/completions")
                           {:headers {"Content-Type" "application/json"
                                      "Authorization" (str "Bearer " api-key)}
                            :body (json/generate-string
                                   {:model model
                                    :messages @history
                                    :stream false})})
        content (-> response :body
                    (json/parse-string true)
                    (get-in [:choices 0 :message :content]))]
    (swap! history conj {:role "assistant" :content content})
    content))

(defn ask [name prompt]
  (let [svc (get @registry name)]
    (case (:type svc)
      :subprocess (ask-subprocess name prompt)
      :http (ask-http name prompt))))
```

**Advantages:**
- No subprocess overhead
- Direct control over HTTP options
- Easier debugging

**Disadvantages:**
- Different lifecycle from Claude instances
- Mixed abstractions in one service

### Approach 3: Hybrid (Best of Both)

Use subprocess for all backends (including HTTP wrapper):

- Uniform lifecycle management
- Process isolation for all backends
- Consistent `spawn!`/`kill!`/`fork!` semantics
- Wrapper handles HTTP details

This is the **recommended approach**.

---

## Babashka HTTP Client Capabilities

### What's Supported

```clojure
(require '[babashka.http-client :as http])

;; Basic request
(http/post url {:body json-string
                :headers {"Content-Type" "application/json"}})

;; Streaming response
(http/get url {:as :stream})  ; Returns InputStream

;; WebSocket
(require '[babashka.http-client.websocket :as ws])
```

### SSE Support

**No built-in SSE parsing**, but `:as :stream` provides the foundation:

```clojure
(defn parse-sse-stream [input-stream]
  (with-open [rdr (io/reader input-stream)]
    (loop [lines (line-seq rdr)
           content (StringBuilder.)]
      (if-let [line (first lines)]
        (cond
          (str/starts-with? line "data: [DONE]")
          (str content)

          (str/starts-with? line "data: ")
          (let [chunk (-> (subs line 6)
                         (json/parse-string true)
                         (get-in [:choices 0 :delta :content]))]
            (recur (rest lines)
                   (.append content (or chunk ""))))

          :else (recur (rest lines) content))
        (str content)))))
```

### Recommendation: Use Non-Streaming

For agent-to-agent communication, **`stream: false` is simpler and sufficient**:

```clojure
(http/post endpoint
  {:body (json/generate-string
           {:model "llama3"
            :messages messages
            :stream false})})
```

SSE streaming is mainly for:
- Real-time UI token display
- Reducing perceived latency for humans

For backend agents collecting full responses, non-streaming works fine.

---

## Execution Model

### Synchronous with Parallel Agents

With `stream: false`, each request is synchronous (blocking):

```
Agent Request ──► Server Processing ──► Complete Response
                  (5-30 seconds)
```

But you can run **multiple agents in parallel**:

```clojure
;; Parallel execution via futures
(def results
  [(future (ask "security-expert" code))   ; Blocks ~10s
   (future (ask "perf-expert" code))       ; Blocks ~8s
   (future (ask "docs-expert" code))])     ; Blocks ~12s

;; All complete in ~12s (slowest), not 30s (sum)
(map deref results)
```

This is what `ask-async` already does - wraps synchronous `ask` in a future.

---

## Backend Comparison

| Backend | Cost | Setup | GPU | Latency | Best For |
|---------|------|-------|-----|---------|----------|
| **Claude** | $$$ | None | N/A | Low | Complex reasoning, orchestration |
| **GPT-4** | $$ | API key | N/A | Low | General tasks |
| **Ollama** | Free | Easy | Optional | Medium | Development, testing |
| **llama.cpp** | Free | Medium | Optional | Medium | Local production |
| **vLLM** | Free | Complex | Required | Low | High throughput |

### Recommended Setup

1. **Development/Testing:** Ollama (free, easy setup)
2. **Local Production:** llama.cpp server
3. **Orchestrator:** Claude (needs complex reasoning)
4. **Workers:** Ollama/llama.cpp (routine tasks)

---

## API Design

### Extended spawn! Function

```clojure
(defn spawn!
  "Spawn a new LLM instance.

   Options:
     :backend - :claude, :ollama, :openai, :llama-cpp, :vllm
     :model   - Model name (backend-specific)
     :endpoint - API endpoint (for HTTP backends)
     :api-key - API key (if required)

   Examples:
     (spawn! \"researcher\")  ; Default: Claude
     (spawn! \"worker\" :backend :ollama :model \"llama3\")
     (spawn! \"critic\" :backend :openai :model \"gpt-4\")"
  [name & {:keys [backend model endpoint api-key]
           :or {backend :claude}}]
  ...)
```

### Backend Registry

```clojure
(def backend-configs
  {:claude
   {:type :subprocess
    :cmd "/Users/franksiebenlist/.claude/local/claude"
    :args ["-p" "--verbose" "--input-format" "stream-json"
           "--output-format" "stream-json"]}

   :ollama
   {:type :subprocess
    :cmd "./scripts/openai-wrapper"
    :default-endpoint "http://localhost:11434"
    :default-model "llama3"}

   :openai
   {:type :subprocess
    :cmd "./scripts/openai-wrapper"
    :default-endpoint "https://api.openai.com"
    :default-model "gpt-4"
    :requires-key true}

   :llama-cpp
   {:type :subprocess
    :cmd "./scripts/openai-wrapper"
    :default-endpoint "http://localhost:8080"
    :default-model "default"}})
```

---

## Example Workflows

### Cost-Optimized Code Review

```clojure
;; Orchestrator: Claude (complex reasoning)
(spawn! "orchestrator" :backend :claude)

;; Workers: Ollama (free, parallel)
(spawn! "security" :backend :ollama :model "llama3")
(spawn! "performance" :backend :ollama :model "llama3")
(spawn! "style" :backend :ollama :model "llama3")

;; Build shared context in orchestrator
(ask "orchestrator" "Analyze this codebase structure...")
(ask "orchestrator" "Key files: src/core.clj, src/api.clj...")

;; Dispatch to workers
(def tasks
  [(ask-async "security" "Review for vulnerabilities: ...")
   (ask-async "performance" "Review for performance issues: ...")
   (ask-async "style" "Review for code style: ...")])

;; Collect results
(def findings (map #(wait-response % 60000) tasks))

;; Orchestrator synthesizes
(ask "orchestrator"
     (str "Synthesize these findings into a report: " findings))
```

### Development/Testing Pipeline

```clojure
;; Use free local models during development
(spawn! "test-agent" :backend :ollama :model "llama3")

;; Test your prompts and workflows
(ask "test-agent" "Complex prompt being developed...")

;; When ready for production, switch to Claude
(kill! "test-agent")
(spawn! "prod-agent" :backend :claude)
```

---

## Future Enhancements

### 1. Context Compression Integration

```clojure
(defn ask-with-compression [name prompt & {:keys [max-tokens]}]
  (let [svc (get @registry name)
        compressed (compress-context (:history svc) max-tokens)]
    (ask-with-history name prompt compressed)))
```

### 2. Automatic Backend Selection

```clojure
(defn smart-spawn! [name task-description]
  (let [complexity (analyze-complexity task-description)
        backend (cond
                  (> complexity 0.8) :claude
                  (> complexity 0.5) :openai
                  :else :ollama)]
    (spawn! name :backend backend)))
```

### 3. Cost Tracking

```clojure
(defn ask-with-cost [name prompt]
  (let [start-tokens (count-tokens prompt)
        result (ask name prompt)
        end-tokens (count-tokens result)
        cost (calculate-cost (:backend (get @registry name))
                            start-tokens end-tokens)]
    {:result result :cost cost}))
```

### 4. Fallback Chain

```clojure
(defn ask-with-fallback [name prompt backends]
  (loop [[backend & rest] backends]
    (if backend
      (try
        (ask name prompt :backend backend)
        (catch Exception e
          (log/warn "Backend failed, trying next:" backend)
          (recur rest)))
      (throw (ex-info "All backends failed" {:backends backends})))))
```

---

## Implementation Roadmap

### Phase 1: OpenAI Wrapper Script
- [ ] Create `scripts/openai-wrapper` in Babashka
- [ ] Support non-streaming mode
- [ ] Handle message history
- [ ] Output Claude-compatible JSON

### Phase 2: Backend Registry
- [ ] Add backend configurations to claude_service.clj
- [ ] Extend `spawn!` with `:backend` option
- [ ] Update `ask` to handle both subprocess types

### Phase 3: Context Management
- [ ] Implement sliding window compression
- [ ] Add token counting utilities
- [ ] Optional summarization hook

### Phase 4: Cost Optimization
- [ ] Add cost tracking per request
- [ ] Implement smart backend selection
- [ ] Add usage reporting

---

## References

- OpenAI API Specification: https://github.com/openai/openai-openapi
- Ollama API: https://github.com/ollama/ollama/blob/main/docs/api.md
- llama.cpp server: https://github.com/ggerganov/llama.cpp/tree/master/examples/server
- vLLM: https://docs.vllm.ai/en/latest/serving/openai_compatible_server/
- Babashka http-client: https://github.com/babashka/http-client

---

*Document created: 2025-11-18*
*Related: claude_service.clj, dynamic-tool-loading-design.md*
