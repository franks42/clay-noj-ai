# Clay Parameterized Evaluation Enhancement

**Status:** Design & Research Phase
**Created:** 2025-11-16
**Goal:** Enable dynamic, parameterized notebook rendering via URL parameters

---

## Problem Statement

Currently, Clay generates **static HTML** from Clojure notebooks:
- HTML is generated once when `.clj` file is saved/modified
- All data is fetched and baked into the HTML at evaluation time
- Users cannot dynamically change parameters without AI intervention
- Interactive dashboards require pre-generating all possible combinations or client-side JS workarounds

**Example limitation:**
- Want to view wallet `pb1abc...` → AI must edit code and trigger re-evaluation
- Want different AUM threshold → AI must edit code and trigger re-evaluation
- Want to compare 10 different wallets → Need 10 different notebook files or complex JS

## Proposed Solution

**Enable Clay's HTTP server to accept URL parameters and trigger re-evaluation:**

```
GET /index.html?wallet=pb1xyz...&minAUM=500
```

**Flow:**
1. Clay HTTP server receives request with parameters
2. Parameters are made available to notebook during evaluation (e.g., `clay/*url-params*`)
3. Clay triggers programmatic re-evaluation of the notebook
4. Notebook code reads parameters and fetches appropriate data
5. Clay waits for HTML generation to complete
6. Clay serves the freshly generated HTML

**Notebook code example:**
```clojure
(ns index
  (:require [scicloj.kindly.v4.kind :as kind]))

;; Read URL parameters provided by Clay
(def wallet-address
  (get clay/*url-params* :wallet "pb1abc..."))  ;; default if not provided

(def min-aum
  (parse-long (get clay/*url-params* :minAUM "0")))

;; Fetch fresh data based on parameters
(def wallet-data
  (fetch-wallet-summary wallet-address))

(def filtered-wallets
  (filter #(>= (:aum %) min-aum) all-wallets))

;; Render dashboard with parameterized data
(kind/hiccup
  [:div
   [:h1 "Portfolio: " wallet-address]
   [:p "Minimum AUM: $" min-aum]
   (render-dashboard wallet-data filtered-wallets)])
```

## Benefits

### Immediate Value

1. **Direct user interaction** - No AI involvement needed for parameter changes
2. **Fresh data** - Each request fetches current blockchain/exchange data
3. **Unlimited combinations** - Don't need to pre-generate all possible parameter values
4. **Clean URLs** - Share links with specific parameters: `index.html?wallet=pb1xyz...`
5. **Faster iteration** - User can explore data directly via URL manipulation

### User Experience

**Before (current Clay):**
- User: "Show me wallet pb1xyz..."
- AI: *Edits code, triggers re-evaluation, waits for HTML generation*
- User: Views result
- User: "Now show me pb1abc..."
- AI: *Repeat entire process*

**After (with parameterized evaluation):**
- User: Navigates to `index.html?wallet=pb1xyz...`
- Clay: *Re-evaluates with parameter, serves fresh HTML*
- User: Changes URL to `?wallet=pb1abc...`
- Clay: *Re-evaluates with new parameter, serves fresh HTML*
- **AI not involved!**

### Future Possibilities

1. **User → AI messaging channel:**
   ```clojure
   ;; In notebook, during parameterized evaluation
   (def user-message (get clay/*url-params* :ai-message))

   (when user-message
     ;; Write to queue that AI monitors
     (spit "inbox/user-messages.edn"
           (pr-str {:timestamp (System/currentTimeMillis)
                    :message user-message
                    :context {:wallet wallet-address}})
           :append true))
   ```

2. **Async AI processing:**
   - Simple queries → Direct parameterized rendering (fast)
   - Complex requests → Queue for AI processing
   - User polls or subscribes for results

3. **Form submissions:**
   - Use POST requests for complex parameter sets
   - Enable multi-step workflows

## Implementation Approach

### 0. Critical Design Decision: In-Memory Generation

**IMPORTANT:** Parameterized requests generate HTML **in memory only** and never write to disk.

**Why this is essential:**

1. **Refresh behavior:**
   - `index.html?wallet=pb1abc...` → Generates with params
   - `index.html` (refresh without params) → Serves original static file
   - Users expect default view when no params provided

2. **Multi-user safety:**
   - User A: `?wallet=pb1abc...` → Generates in memory → Serves to A
   - User B: `?wallet=pb1xyz...` → Generates in memory → Serves to B
   - No file conflicts, completely isolated

3. **State synchronization:**
   - Static `/docs/index.html` only changes when `.clj` file is manually edited
   - Parameterized HTML never pollutes the static file
   - No confusion about "what params generated this file"

**Implementation:**
```clojure
(if (and (seq params) (re-matches #".*\.html$" uri))
  ;; PARAMETERIZED: Generate in memory, serve directly, NEVER write to disk
  (let [html (binding [clay/*url-params* params]
               (generate-html-in-memory source-file))]  ; Don't call write!
    {:body html :status 200})

  ;; NON-PARAMETERIZED: Serve static file from disk
  (serve-static-file uri))
```

### 1. HTTP Handler Modification

Clay's HTTP server needs to:
- Parse URL query parameters (Ring provides `:query-params`)
- Detect when parameters are present
- Trigger programmatic re-evaluation **in memory only**
- Return generated HTML directly (no disk write)

**Pseudocode:**
```clojure
(defn handle-request [req]
  (let [uri (:uri req)
        params (:query-params req)]

    (if (and (seq params) (re-matches #".*\.html$" uri))
      ;; Parameterized request - generate in memory
      (do
        (log/info "Parameterized request:" uri params)
        (binding [clay/*url-params* params]
          (let [html (generate-html-in-memory! source-file)]  ; In memory only!
            {:body html :status 200})))

      ;; Regular request - serve static file
      (serve-static-html uri))))
```

### 2. Dynamic Variable for Parameters

Add a dynamic var to Clay's API:
```clojure
(def ^:dynamic *url-params*
  "URL query parameters available during parameterized evaluation.
   Map of keyword keys to string values.
   Example: {:wallet \"pb1abc...\" :minAUM \"500\"}"
  {})
```

### 3. Programmatic Evaluation API

Clay needs a way to trigger evaluation programmatically (not via file watch):
```clojure
(defn evaluate-and-wait!
  "Synchronously evaluate a notebook file and wait for HTML generation.
   Returns path to generated HTML file.
   Used for parameterized evaluation from HTTP requests."
  [source-file]
  (let [result-promise (promise)]
    (clay/make! {:source-path source-file
                 :on-complete #(deliver result-promise %)})
    @result-promise))  ;; Block until complete
```

### 4. Caching Strategy (Optional)

For performance, consider caching:
- Cache generated HTML by parameter values
- TTL-based expiration for fresh data
- Cache invalidation on manual file edits

**Example:**
```clojure
(def param-cache
  (atom {}))  ;; {params-hash html-path}

(defn get-or-generate-html [params source-file]
  (let [cache-key (hash params)]
    (or (get @param-cache cache-key)
        (let [html-path (evaluate-and-wait! source-file)]
          (swap! param-cache assoc cache-key html-path)
          html-path))))
```

## Research Questions ✅ COMPLETED

Clay repository examined at: `/tmp/clay-research/`

### 1. HTTP Server ✅

**Answer:**
- Clay uses **http-kit** (`org.httpkit.server`)
- Request handler: `routes` function in `src/scicloj/clay/v2/server.clj` (lines 196-245)
- Static file serving: Lines 227-236 in `routes` function
  ```clojure
  (let [f (io/file (str (:base-target-path state) uri))]
    (if (.exists f)
      {:body (if (re-matches #".*\.html$" uri)
               (-> f slurp (wrap-html state))  ;; HTML gets wrapped
               f)                              ;; Other files served as-is
       :headers {...}
       :status 200}
      ...))
  ```

**Key insight:** Very straightforward to modify - just need to detect query params before the file existence check.

### 2. Evaluation Pipeline ✅

**Answer:**
- `clay/make!` exists in `src/scicloj/clay/v2/make.clj` (line 474)
- Can be called programmatically: `(make! {:source-path "notebooks/index.clj"})`
- Currently appears to be synchronous within the evaluation, but uses callbacks for server updates
- The API is in `src/scicloj/clay/v2/api.clj` line 34: `(defn make! [spec] ...)`

**Current `make!` flow:**
```clojure
(defn make! [spec]
  (let [config (config/config spec)
        {:keys [main-spec single-ns-specs]} (extract-specs config spec)]
    (println "Clay make started: " full-source-paths)
    (when show (server/open! main-spec) (server/loading!))
    ;; ... evaluation happens ...
    (mapv handle-single-source-spec! single-ns-specs)  ;; This does the work
    ;; Returns summary
    ))
```

**Challenge:** Need to wait for `make!` to complete before serving HTML. May need to add callback or make it explicitly synchronous.

### 3. File Watching ✅

**Answer:**
- File watching is separate from programmatic evaluation
- `live-reload/start!` and `live-reload/toggle!` in `make.clj` lines 490-492
- Watching can be enabled/disabled via `:live-reload` option
- Programmatic `make!` calls are independent of file watching

**Key insight:** No conflicts between file-watch triggers and programmatic triggers.

### 4. Dynamic Vars ✅

**Answer:**
- Did not find existing dynamic vars for evaluation context
- Would need to add `(def ^:dynamic *url-params* {})`
- Binding point: Before calling `make!` in the HTTP handler

**Implementation location:**
```clojure
;; In server.clj routes function
(binding [clay/*url-params* (:query-params req)]
  (make/make! {:source-path derived-from-uri}))
```

**Challenge:** Need to ensure dynamic binding is visible during notebook evaluation (likely need to pass through the evaluation layers).

### 5. Compatibility ✅

**Answer:**
- This can be **fully backwards compatible**
- Only activates when query params are present
- Static file serving remains unchanged when no params
- No breaking changes to existing API

**Strategy:**
```clojure
(if (and (seq query-params) (re-matches #".*\.html$" uri))
  ;; NEW: Parameterized evaluation path
  (handle-parameterized-request uri query-params)
  ;; EXISTING: Static file serving path
  (serve-static-file uri))
```

## Prototype Plan

### Phase 1: Research ✅ COMPLETED
- [x] Document design
- [x] Clone Clay repository to local tmp directory (`/tmp/clay-research/`)
- [x] Examine HTTP server implementation (`src/scicloj/clay/v2/server.clj`)
- [x] Examine evaluation pipeline (`src/scicloj/clay/v2/make.clj`)
- [x] Identify hook points for modifications

**Key Files to Modify:**
1. `src/scicloj/clay/v2/server.clj` - Add parameterized request handling (lines ~227-236)
2. `src/scicloj/clay/v2/api.clj` or new file - Add `*url-params*` dynamic var
3. `src/scicloj/clay/v2/make.clj` - Possibly add synchronous completion mechanism

### Phase 2: Minimal Prototype
- [ ] Fork Clay or create local modification
- [ ] Add `*url-params*` dynamic var
- [ ] Modify HTTP handler to parse query params
- [ ] Implement basic parameterized evaluation
- [ ] Test with simple notebook

### Phase 3: Full Implementation
- [ ] Add proper error handling
- [ ] Implement `evaluate-and-wait!` API
- [ ] Add logging for debugging
- [ ] Test with complex notebook (wallet dashboard)
- [ ] Measure performance (evaluation time, response time)

### Phase 4: Refinement
- [ ] Add caching (if needed)
- [ ] Handle concurrent requests
- [ ] Add configuration options (enable/disable feature)
- [ ] Documentation and examples

### Phase 5: Engagement
- [ ] Create demo video/screenshots
- [ ] Write up use cases and benefits
- [ ] Prepare pull request with working code
- [ ] Engage Clay maintainers with working prototype

## Success Criteria

**Minimum Viable Prototype:**
- [ ] User can navigate to `index.html?wallet=pb1xyz...`
- [ ] Notebook code can read `(get clay/*url-params* :wallet)`
- [ ] Fresh data is fetched based on parameter
- [ ] HTML is generated and served dynamically
- [ ] Multiple requests with different parameters work correctly

**Production Ready:**
- [ ] No breaking changes to existing Clay functionality
- [ ] Proper error handling (invalid params, evaluation failures)
- [ ] Reasonable performance (< 5 seconds for typical notebook)
- [ ] Thread-safe for concurrent requests
- [ ] Configuration to enable/disable feature
- [ ] Documentation and examples

## Alternative Approaches Considered

### 1. Client-Side JavaScript + Embedded Data
**Approach:** Pre-fetch all data during evaluation, embed in HTML, use JS to show relevant subset.

**Pros:**
- No Clay modifications needed
- Works with current Clay

**Cons:**
- All data must be fetched upfront
- Doesn't scale to unlimited parameter combinations
- Stale data (no fresh fetches per request)
- Large HTML files with embedded data

### 2. Separate HTTP Server
**Approach:** Build a separate server that triggers Clay programmatically via nREPL.

**Pros:**
- No Clay modifications
- Full control over routing/handling

**Cons:**
- More complex architecture (two servers)
- Need to coordinate between servers
- User needs to run multiple processes

### 3. Pre-Generate All Combinations
**Approach:** Generate `wallet_pb1abc.html`, `wallet_pb1def.html`, etc.

**Pros:**
- Simple, works with current Clay

**Cons:**
- Doesn't scale to unlimited parameters
- Stale data
- Need to know all values upfront
- Lots of files to manage

**Why parameterized evaluation is better:**
- Scales to unlimited parameter combinations
- Fresh data on every request
- Clean architecture (one modification to Clay)
- Better user experience

## Open Questions

1. **Performance:** How fast can Clay re-evaluate a notebook? Is < 5 seconds realistic?
2. **Concurrency:** How should Clay handle multiple simultaneous parameterized requests?
3. **Caching:** Is caching necessary, or is re-evaluation fast enough?
4. **POST vs GET:** Should we support POST requests for complex parameter sets?
5. **Backwards Compatibility:** How to ensure this doesn't break existing Clay usage?

## Next Steps

1. Clone Clay repository to examine implementation
2. Find HTTP server code and evaluation pipeline
3. Identify specific locations for modifications
4. Create minimal prototype with wallet dashboard use case
5. Test and iterate
6. Engage maintainers with working demo

---

## Feasibility Assessment

### Verdict: **HIGHLY FEASIBLE** ✅

Based on code examination, this enhancement is achievable with localized changes to 3 files:

1. **Simple architecture** - Clay uses standard Ring/http-kit patterns
2. **Clean separation** - HTTP serving is separate from evaluation logic
3. **Programmatic API exists** - `make!` can already be called from code
4. **No framework obstacles** - http-kit supports synchronous handlers
5. **Backwards compatible** - Only activates with query params present

### Implementation Complexity: **Low to Medium**

**Easy parts:**
- Detecting query params in request ✅
- Calling `make!` programmatically ✅
- Serving generated HTML ✅

**Medium complexity:**
- Binding `*url-params*` through evaluation layers
- Ensuring `make!` completes before serving response
- Handling concurrent parameterized requests

**Optional enhancements:**
- Caching by parameter values
- POST request support
- Timeout handling

### Thread Safety & Concurrency Concerns

#### Critical Issue: Dynamic Var Thread Safety

**The Concern:**
When using `*url-params*` as a dynamic var, concurrent requests could potentially interfere:

```clojure
Thread 1 (User A): (binding [*url-params* {:wallet "pb1abc..."}]
                     (make! spec))  ; Starts evaluation

Thread 2 (User B): (binding [*url-params* {:wallet "pb1xyz..."}]
                     (make! spec))  ; Concurrent evaluation

// Question: Could Thread 1 see Thread 2's params during evaluation?
```

#### How Clojure Dynamic Vars Work

**Thread-Local Bindings:**
- `binding` creates thread-local bindings in Clojure
- Each HTTP request thread has its own independent binding
- http-kit creates separate threads per request
- **No interference between threads** ✅

**Example:**
```clojure
;; HTTP Thread 1 (User A's request)
(binding [*url-params* {:wallet "pb1abc..."}]
  (make! spec))
  ;; This thread only sees {:wallet "pb1abc..."}

;; HTTP Thread 2 (User B's request) - independent!
(binding [*url-params* {:wallet "pb1xyz..."}]
  (make! spec))
  ;; This thread only sees {:wallet "pb1xyz..."}

// ✅ Completely isolated!
```

#### Potential Problem: Child Threads

**If `make!` spawns child threads**, they don't inherit dynamic bindings by default:

```clojure
(binding [*url-params* params]
  (make! spec)
    ;; If make! does this:
    (future
      (eval-code)  ;; ⚠️ This thread WON'T see *url-params*!
      ))
```

**Based on code review:**
- `make!` uses `mapv` (sequential), not `pmap` (parallel)
- Appears to be synchronous
- **Likely safe**, but needs testing

#### Solution Approaches

**Approach 1: Thread-Local Binding (Simplest - Start Here)**
```clojure
(defn routes [{:keys [query-params] :as req}]
  (if (seq query-params)
    (binding [clay/*url-params* query-params]
      (let [html (make! spec)]  ; If synchronous, binding stays valid
        {:body html :status 200}))
    (serve-static-file)))
```

**Pros:**
- Simplest implementation
- Works if `make!` is synchronous
- Standard Clojure pattern

**Cons:**
- Breaks if `make!` spawns threads
- Need to verify synchronous execution

**Approach 2: Explicit Parameter Passing (Safest)**
```clojure
;; Don't use dynamic var - pass params through call chain
(defn make-with-params! [spec params]
  ;; Pass params to evaluation context explicitly
  (assoc spec :url-params params))

(defn routes [{:keys [query-params] :as req}]
  (if (seq query-params)
    (let [html (make-with-params! spec query-params)]
      {:body html :status 200})
    (serve-static-file)))
```

**Pros:**
- Thread-safe guaranteed
- No dynamic var complications
- Explicit data flow

**Cons:**
- Requires modifying `make!` internals
- More invasive code changes

**Approach 3: Hybrid - Bind at Eval Time**
```clojure
;; Set dynamic var right before eval'ing user code
(defn eval-form-with-params [form params]
  (binding [*url-params* params]
    (eval form)))  ; Binding active during user code execution
```

**Pros:**
- Binding only during user code eval
- More controlled scope

**Cons:**
- Need to hook into eval layer
- More complex implementation

#### Recommended Strategy

**Phase 1 - Prototype (Approach 1):**
1. Use dynamic var with thread-local binding
2. Assume `make!` is synchronous (appears to be from code review)
3. Test with concurrent requests

**Phase 2 - Validate with Concurrency Tests:**
```bash
# Terminal 1
curl "http://localhost:1971/index.html?wallet=pb1abc..." &

# Terminal 2 (simultaneously!)
curl "http://localhost:1971/index.html?wallet=pb1xyz..." &

# Check both responses have correct wallet data
```

**Phase 3 - If Threading Issues Found:**
1. Switch to Approach 2 (explicit params)
2. Or use `bound-fn` to propagate bindings to child threads

#### Additional Concurrency Consideration: In-Memory Generation

**Why in-memory generation solves most issues:**

```clojure
;; Each request generates independently
Thread 1: Generates HTML for wallet A → Returns HTML → Done
Thread 2: Generates HTML for wallet B → Returns HTML → Done

// No shared state! No file conflicts!
```

**Benefits:**
- No race conditions on file writes
- Each request completely isolated
- No cache invalidation needed (unless we add caching)

#### Test Scenarios

**Test 1: Sequential Requests (Baseline)**
```bash
curl "http://localhost:1971/index.html?wallet=pb1abc..." > response1.html
curl "http://localhost:1971/index.html?wallet=pb1xyz..." > response2.html

# Verify response1 has pb1abc data
# Verify response2 has pb1xyz data
```

**Test 2: Concurrent Requests (Critical!)**
```bash
# Launch 10 concurrent requests with different params
for i in {1..10}; do
  curl "http://localhost:1971/index.html?wallet=wallet$i" > response$i.html &
done
wait

# Verify each response has correct wallet data
```

**Test 3: Refresh Behavior**
```bash
# Request with params
curl "http://localhost:1971/index.html?wallet=pb1abc..." > response1.html

# Request without params (should get static file)
curl "http://localhost:1971/index.html" > response2.html

# Verify response2 is the original static file, not pb1abc data
```

**Test 4: High Concurrency (Stress Test)**
```bash
# Use Apache Bench for load testing
ab -n 100 -c 10 "http://localhost:1971/index.html?wallet=pb1abc..."

# Verify all responses are correct
# Check for any threading errors in logs
```

#### What to Watch For

**During testing:**
1. **Wrong wallet data** - indicates thread interference
2. **Mixed data** - indicates concurrent write conflicts
3. **Eval errors** - indicates binding not visible
4. **Null params** - indicates binding not propagated

**In logs:**
```
Clay make started: #{notebooks/index.clj}
Clay: Evaluated index in 0.X seconds
Clay: [:wrote docs/index.html ...]  # Should NOT write for parameterized!
```

#### Decision Point

After concurrency testing:
- ✅ **All tests pass** → Thread-local binding works! Ship it.
- ❌ **Threading issues** → Switch to explicit parameter passing (Approach 2)

### Estimated Effort

- **Minimal prototype:** 4-6 hours (basic working version)
- **Production-ready:** 12-16 hours (error handling, testing, docs)
- **With caching:** +4-8 hours

### Risk Assessment: **Low Risk**

- Changes are isolated to specific functions
- Backwards compatibility maintained
- Can be feature-flagged if needed
- Easy to test incrementally

### Next Steps

**Immediate (this session):**
1. ✅ Document design and architecture
2. ✅ Research Clay implementation
3. ✅ Confirm feasibility

**Next session:**
1. Create fork or local branch of Clay
2. Implement minimal prototype
3. Test with wallet dashboard use case
4. Iterate on implementation

**Before engaging maintainers:**
1. Working prototype with demo
2. Performance measurements
3. Documentation and examples
4. Pull request with clean code

---

**References:**
- Clay repository: https://github.com/scicloj/clay
- Clay clone for research: `/tmp/clay-research/`
- Related discussion: [Will create GitHub issue after prototype]
