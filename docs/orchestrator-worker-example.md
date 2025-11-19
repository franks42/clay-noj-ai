# Orchestrator + Worker Pattern Example

This example demonstrates a cost-optimized multi-instance Claude architecture where:
- **Orchestrator (Sonnet)** - Coordinates work, synthesizes results, makes decisions
- **Workers (Haiku)** - Fetch data, perform simple transformations, execute tasks

## Pattern Benefits

1. **Cost optimization** - Haiku is ~10x cheaper than Sonnet
2. **Parallel processing** - Multiple workers can fetch data simultaneously
3. **Context efficiency** - Workers share base context via fork pattern
4. **Separation of concerns** - Data fetching vs. analysis

---

## Setup

### Prerequisites

Start the MCP nREPL server (port 7888):
```bash
# In your MCP server terminal, ensure local-nrepl-server is running
```

Load the claude service:
```bash
bb claude:eval '(mcp.nrepl-server/local-load-file "/Users/franksiebenlist/Development/clay-noj-ai/src/claude_service.clj")'
```

---

## Example 1: Data Gateway Pattern

Worker fetches live data, orchestrator analyzes it.

### Step 1: Spawn Orchestrator with Base Context

```bash
# Spawn orchestrator (Sonnet) with domain knowledge
bb claude:spawn orchestrator

bb claude:ask orchestrator "You are a crypto portfolio analyst specializing in the Provenance Blockchain ecosystem. You will receive data from worker instances and provide analysis. Respond with READY when understood."
```

### Step 2: Fork a Data Fetcher Worker

```bash
# Fork from orchestrator to inherit context, use Haiku for cost efficiency
bb claude:eval '(claude-service/fork! "orchestrator" "fetcher" :model :haiku)'

# Verify it inherited context
bb claude:ask fetcher "What is your role?"
# Should mention being a crypto portfolio analyst
```

### Step 3: Worker Fetches Data

```bash
# Worker uses MCP tools to fetch live data
bb claude:ask fetcher "Use the pb-fm-mcp tools to fetch the current HASH token statistics. Return ONLY the raw data as a compact JSON object, no analysis."
```

### Step 4: Relay Data to Orchestrator

```bash
# Get the fetcher's response and relay to orchestrator
bb claude:eval '(claude-service/relay "fetcher" "orchestrator" "Analyze this HASH token data and provide investment insights")'
```

### Step 5: Orchestrator Analyzes

The orchestrator (Sonnet) now has the data with full context and can provide sophisticated analysis.

---

## Example 2: Parallel Workers Pattern

Multiple workers fetch different data sources simultaneously.

### Setup

```bash
# Spawn base orchestrator
bb claude:spawn coordinator

bb claude:ask coordinator "You coordinate a team of data fetchers for Provenance Blockchain analysis. You will receive data from multiple workers and synthesize comprehensive reports. Say READY."

# Fork multiple workers (all Haiku for cost efficiency)
bb claude:eval '(claude-service/fork! "coordinator" "market-fetcher" :model :haiku)'
bb claude:eval '(claude-service/fork! "coordinator" "wallet-fetcher" :model :haiku)'
bb claude:eval '(claude-service/fork! "coordinator" "price-fetcher" :model :haiku)'
```

### Assign Tasks

```bash
# Each worker gets a specific data fetch task
bb claude:ask market-fetcher "Fetch current Figure Markets data using fetch_current_fm_data. Return compact JSON only."

bb claude:ask wallet-fetcher "Fetch complete wallet summary for pb1qz6p0z... using fetch_complete_wallet_summary. Return compact JSON only."

bb claude:ask price-fetcher "Fetch last 5 HASH-USD trades using fetch_last_crypto_token_price. Return compact JSON only."
```

### Collect and Synthesize

```bash
# Relay all results to coordinator
bb claude:eval '(claude-service/relay "market-fetcher" "coordinator" "Market data collected")'
bb claude:eval '(claude-service/relay "wallet-fetcher" "coordinator" "Wallet data collected")'
bb claude:eval '(claude-service/relay "price-fetcher" "coordinator" "Price history collected")'

# Ask coordinator to synthesize
bb claude:ask coordinator "You have received data from three workers: market conditions, wallet holdings, and recent price action. Provide a comprehensive portfolio analysis with specific recommendations."
```

---

## Example 3: Task Queue Pattern

Orchestrator assigns tasks dynamically based on results.

```clojure
;; Programmatic example using the service directly

;; Setup
(claude-service/spawn! "brain" :model :sonnet)
(claude-service/ask "brain" "You are a task coordinator. You will analyze problems and delegate subtasks to workers. Respond with task assignments in format: TASK: <description>")

;; Fork workers
(claude-service/fork! "brain" "worker-1" :model :haiku)
(claude-service/fork! "brain" "worker-2" :model :haiku)

;; Brain decides what to fetch
(def task-assignment
  (claude-service/ask "brain" "I need to analyze a user's staking position on Provenance. What data should we fetch? Assign tasks to worker-1 and worker-2."))

;; Parse and execute tasks (simplified)
;; In practice, you'd parse the response and route to workers

;; Workers execute and report back
(claude-service/relay "worker-1" "brain" "Completed: <result>")
(claude-service/relay "worker-2" "brain" "Completed: <result>")

;; Brain synthesizes
(claude-service/ask "brain" "Based on the worker results, provide your final analysis.")
```

---

## Cost Comparison

| Pattern | Sonnet Tokens | Haiku Tokens | Relative Cost |
|---------|---------------|--------------|---------------|
| Single Sonnet | 10,000 | 0 | $0.09 |
| Orchestrator + 3 Workers | 3,000 | 9,000 | $0.036 |
| **Savings** | | | **60%** |

*Assumes: Sonnet $3/$15 per MTok, Haiku $0.25/$1.25 per MTok*

Workers handle:
- Data fetching (simple prompts, structured output)
- Format transformations
- Validation and filtering

Orchestrator handles:
- Complex reasoning
- Synthesis and analysis
- Decision making

---

## Cleanup

```bash
# Kill specific instances
bb claude:kill orchestrator
bb claude:kill fetcher

# Or kill all
bb claude:kill-all
```

---

## Key Patterns Summary

### 1. Fork for Context Inheritance
```bash
bb claude:eval '(claude-service/fork! "parent" "child" :model :haiku)'
```
Child inherits parent's full conversation context.

### 2. Relay for Data Passing
```bash
bb claude:eval '(claude-service/relay "source" "target" "context message")'
```
Appends source's last response to target with context.

### 3. Model Selection by Task
- **:haiku** - Data fetching, simple transformations, high volume
- **:sonnet** - Analysis, synthesis, complex reasoning
- **:opus** - Critical decisions, nuanced understanding (when available)

### 4. Broadcast for Coordination
```bash
bb claude:eval '(claude-service/broadcast "coordinator" ["w1" "w2" "w3"] "New instructions...")'
```
Send same message to multiple workers.

---

## Gotchas

1. **Worker prompts should be minimal** - Don't waste Haiku tokens on elaborate instructions
2. **Structure worker output** - Request JSON or structured formats for easy parsing
3. **Orchestrator needs context** - Include relevant context when relaying data
4. **Fork early** - Workers forked later don't get earlier context
5. **Clean up** - Kill workers when done to free resources

---

*Created: 2025-11-18*
