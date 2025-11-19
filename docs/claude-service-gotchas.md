# Claude Service - Gotchas and Patterns

Lessons learned while building and using the multi-instance Claude service.

---

## SCI Compatibility

The service runs in Babashka's SCI interpreter. Key constraints:

### Don't Use
```clojure
;; These fail in SCI
(defonce ^:private registry (atom {}))  ; Wrong number of args
(defn- helper [] ...)                    ; defn- not available
```

### Do Use
```clojure
;; SCI-compatible
(defonce registry (atom {}))
(defn helper [] ...)  ; Just use defn, accept it's public
```

### Testing Compatibility
Always test loading before assuming it works:
```bash
bb claude:eval '(mcp.nrepl-server/local-load-file "/path/to/file.clj")'
```

---

## Model IDs

### Correct Format
```clojure
{:haiku  "claude-3-5-haiku-20241022"    ; Note: 3-5, not haiku-3-5
 :sonnet "claude-sonnet-4-20250514"
 :opus   "claude-opus-4-20250514"}
```

### Common Mistake
```clojure
;; WRONG - causes 404 error
"claude-haiku-3-5-20241022"

;; RIGHT
"claude-3-5-haiku-20241022"
```

---

## babashka.nrepl-client API

### Wrong Approach
```clojure
;; This pattern doesn't exist in babashka.nrepl-client
(let [conn (nrepl/connect {:port 7888})]
  (nrepl/eval conn "(+ 1 2)")
  (nrepl/close conn))
```

### Correct Approach
```clojure
(require '[babashka.nrepl-client :as nrepl])

;; Single function call, handles everything
(nrepl/eval-expr {:port 7888 :expr "(+ 1 2)"})
;; => {:vals ["3"]}
```

### Getting the Value
```clojure
(let [result (nrepl/eval-expr {:port 7888 :expr "(+ 1 2)"})]
  (first (:vals result)))
;; => "3"
```

---

## Fork Pattern

### Context Inheritance Works
Workers forked from a parent inherit the FULL conversation history:

```bash
bb claude:ask orchestrator "You analyze Provenance Blockchain data"
bb claude:eval '(claude-service/fork! "orchestrator" "worker" :model :haiku)'
bb claude:ask worker "What is your role?"
# Worker knows it analyzes Provenance Blockchain data!
```

### Fork Timing Matters
```bash
# This worker gets the context
bb claude:ask parent "Important context here"
bb claude:eval '(claude-service/fork! "parent" "worker1")'

# More context added
bb claude:ask parent "More important stuff"

# This worker gets MORE context
bb claude:eval '(claude-service/fork! "parent" "worker2")'

# worker2 knows more than worker1!
```

### Fork vs Spawn
- **fork!** - Inherits context, useful for workers that need domain knowledge
- **spawn!** - Fresh instance, no context, useful for independent tasks

---

## Worker Prompting

### Minimize Token Usage
Workers (especially Haiku) should get minimal prompts:

```bash
# BAD - wastes tokens
bb claude:ask fetcher "Please use the MCP tools available to you to fetch the current market data from the Figure Markets exchange. When you receive the data, please format it nicely and return it to me so I can analyze it further."

# GOOD - minimal and structured
bb claude:ask fetcher "Fetch current FM data via fetch_current_fm_data. Return compact JSON only."
```

### Request Structured Output
```bash
# Request specific format for easy parsing
bb claude:ask worker "Return result as: {\"price\": X, \"volume\": Y}"
```

---

## Relay Pattern

### Include Context
```bash
# BAD - target doesn't know what the data means
bb claude:eval '(claude-service/relay "fetcher" "analyzer" "")'

# GOOD - provide context for the data
bb claude:eval '(claude-service/relay "fetcher" "analyzer" "This is live HASH market data - analyze trends")'
```

### Relay Appends
Relay appends the source's LAST response to the target. If you need multiple pieces of data:
```bash
# Relay each piece with context
bb claude:eval '(claude-service/relay "w1" "coord" "Market data:")'
bb claude:eval '(claude-service/relay "w2" "coord" "Wallet data:")'
bb claude:eval '(claude-service/relay "w3" "coord" "Price history:")'

# Then ask for synthesis
bb claude:ask coord "Synthesize all the data above into a report"
```

---

## Resource Management

### Always Clean Up
Instances consume resources (processes, memory):
```bash
# Kill specific instance
bb claude:kill worker-1

# Kill all instances
bb claude:kill-all
```

### Check What's Running
```bash
bb claude:list
# Shows all active instances with PIDs
```

### Orphaned Processes
If the service crashes, Claude processes may be orphaned:
```bash
# Find orphaned claude processes
ps aux | grep claude | grep -v grep

# Kill them
pkill -f "claude.*stream-json"
```

---

## Async Workflow

### For Long Operations
```clojure
;; Start async request
(def req-id (claude-service/ask-async "worker" "Long running task..."))

;; Poll for completion
(loop []
  (let [result (claude-service/poll-response req-id)]
    (case (:status result)
      :complete (:result result)
      :error    (throw (ex-info "Failed" result))
      :pending  (do (Thread/sleep 1000) (recur)))))
```

### Timeout Considerations
- Default ask timeout is based on Claude's response time
- Long MCP tool calls can take 30+ seconds
- Use ask-async for operations that might be slow

---

## Common Error Patterns

### "Service not found"
```clojure
;; Instance doesn't exist or was killed
(claude-service/ask "nonexistent" "hello")
;; => Exception: Claude service 'nonexistent' not found
```
Fix: Check `bb claude:list` and spawn if needed.

### "Already exists"
```clojure
;; Can't spawn same name twice
(claude-service/spawn! "worker")
(claude-service/spawn! "worker")
;; => Exception: Claude service 'worker' already exists
```
Fix: Kill first or use different name.

### Model 404
```
Error: model: claude-haiku-3-5-20241022
```
Fix: Use correct model ID format (`claude-3-5-haiku-20241022`).

### SCI Load Error
```
Wrong number of args (5) passed to: sci.impl.namespaces/defonce*
```
Fix: Remove `^:private` metadata from defonce.

---

## Performance Tips

### Parallel Spawning
Spawn workers in parallel when possible:
```clojure
;; Spawn multiple workers quickly
(doseq [n (range 5)]
  (future (claude-service/spawn! (str "worker-" n) :model :haiku)))
```

### Reuse Instances
Don't spawn/kill for every task - reuse workers:
```bash
# Reuse the same worker
bb claude:ask worker "task 1"
bb claude:ask worker "task 2"
bb claude:ask worker "task 3"
```

### Right-Size Models
- **Haiku** - Simple tasks, data fetching, formatting
- **Sonnet** - Analysis, reasoning, synthesis
- **Opus** - Complex decisions, nuanced understanding

---

## Debugging

### Check Instance Status
```bash
bb claude:status worker-1
# Shows state, PID, model, etc.
```

### View All Instances
```bash
bb claude:list
```

### Test Connectivity
```bash
bb claude:eval '(+ 1 2)'
# Should return 3
```

### Direct REPL Access
```clojure
;; If bb tasks fail, connect directly
(require '[babashka.nrepl-client :as nrepl])
(nrepl/eval-expr {:port 7888 :expr "(claude-service/list-services)"})
```

---

*Last updated: 2025-11-18*
