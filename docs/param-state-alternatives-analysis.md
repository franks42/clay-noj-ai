# State Management Alternatives for Parameterized Notebooks

## Problem Statement

When a Clay notebook is parameterized with URL query params, subsequent interactions (clicking links, submitting forms) need to preserve the original parameters while potentially adding/overriding new ones.

**Current Implementation**: JavaScript injection that intercepts forms/links
**User Concern**: "This all starts to look pretty hacky"

## Alternative Approaches

### Option 1: JavaScript Parameter Preservation (Current)

**How it works:**
```
GET /index.html?wallet=pb1abc&minAUM=1000
→ Server injects JavaScript into HTML <head>
→ JS intercepts form submissions and link clicks
→ Automatically adds hidden fields / updates URLs
```

**Pros:**
- ✅ Zero server-side state
- ✅ Works immediately without session setup
- ✅ Parameters visible in URL (bookmarkable)
- ✅ No session cleanup needed

**Cons:**
- ❌ Hacky - abuses client-side state management
- ❌ Requires JavaScript (breaks without JS)
- ❌ Complex same-origin checking logic
- ❌ Fights against stateless HTTP design
- ❌ Difficult to test comprehensively
- ❌ Security surface area (XSS if params not sanitized)

**Implementation Complexity**: Already implemented (56 lines)

**Use Case Fit**: Poor - fighting the architecture

---

### Option 2: Session-ID in URL Path

**How it works:**
```
GET /index.html?wallet=pb1abc&minAUM=1000
→ Server creates session "dft34", stores {wallet: pb1abc, minAUM: 1000}
→ Redirect to /app/dft34/index.html
→ All subsequent links/forms go to /app/dft34/...
→ Server looks up session state on each request
```

**Pros:**
- ✅ Clean URLs after first request
- ✅ Parameters not exposed in every URL
- ✅ Easy to understand pattern
- ✅ Standard session management approach
- ✅ No JavaScript required
- ✅ Can handle complex state (not just strings)

**Cons:**
- ❌ Requires server-side session storage (atom/db)
- ❌ Session cleanup/expiration needed
- ❌ URL rewriting complexity
- ❌ Not bookmarkable (session may expire)
- ❌ Requires routing changes (match `/app/:session-id/:page`)
- ❌ Race conditions if multiple browsers share session

**Implementation Complexity**: Medium (~150 lines)
- Session storage (atom with expiration)
- URL rewriting middleware
- Route pattern matching
- Session cleanup background task

**Use Case Fit**: Good for long-lived interactive sessions

**Example Routes:**
```clojure
;; New routes needed:
[:get "/app/:session-id/:page.html"] ; Lookup session, render with params
[:post "/app/:session-id/..."]       ; Update session state

;; Session storage:
(def *sessions (atom {}))  ; {session-id {:params {...} :created-at ...}}
```

---

### Option 3: Base64 Encoded Params in URL

**How it works:**
```
GET /index.html?wallet=pb1abc&minAUM=1000
→ Server base64 encodes params: "eyJ3YWxsZXQiOiJwYjFhYmMiLCJtaW5BVU0iOjEwMDB9"
→ Redirect to /app/eyJ3YWxsZXQiOiJwYjFhYmMi.../index.html
→ All links/forms include the base64 state in path
→ Server decodes on each request
```

**Pros:**
- ✅ Truly stateless (no server storage)
- ✅ Bookmarkable (state in URL)
- ✅ No session cleanup needed
- ✅ Simple to implement
- ✅ No JavaScript required
- ✅ Can handle complex state

**Cons:**
- ❌ URLs become very long with many params
- ❌ Not human-readable
- ❌ URL length limits (2048 chars in browsers)
- ❌ State visible to anyone with URL
- ❌ Need URL encoding of base64 (/ and + characters)
- ❌ Still requires URL rewriting

**Implementation Complexity**: Low (~80 lines)
- Base64 encode/decode helpers
- URL pattern matching `/app/:encoded-state/:page`
- Decode middleware

**Use Case Fit**: Good for moderate amounts of state

**Example Implementation:**
```clojure
(defn encode-params [params]
  (-> params
      pr-str
      (.getBytes "UTF-8")
      (java.util.Base64/getUrlEncoder .encodeToString)))

(defn decode-params [encoded]
  (-> encoded
      (java.util.Base64/getUrlDecoder .decode)
      (String. "UTF-8")
      read-string))

;; Route: /app/:encoded/index.html
;; Extract and decode params for rendering
```

---

### Option 4: Stateless with Explicit Param Handling (Recommended)

**How it works:**
```clojure
;; Notebook explicitly includes current params in all URLs
(defn url-with-params [page & new-params]
  (str page "?"
       (str/join "&"
         (concat
           (map (fn [[k v]] (str k "=" v)) clay/*url-params*)
           new-params))))

;; All links/forms explicitly include params
[:a {:href (url-with-params "index.html" "filter=active")} "Filter"]
```

**Pros:**
- ✅ Truly stateless
- ✅ Explicit > implicit (Clojure philosophy)
- ✅ No magic behavior
- ✅ Easy to test
- ✅ Easy to understand
- ✅ Parameters visible in URL
- ✅ Bookmarkable
- ✅ No JavaScript required
- ✅ No server changes needed

**Cons:**
- ❌ Requires notebook authors to use helper functions
- ❌ More verbose than automatic preservation
- ❌ Could forget to include params (human error)

**Implementation Complexity**: Trivial (~20 lines of helper functions)

**Use Case Fit**: Excellent - idiomatic Clojure

**Example Helper Functions:**
```clojure
(ns scicloj.clay.v2.params
  (:require [clojure.string :as str]
            [scicloj.clay.v2.api :as clay]))

(defn current-params []
  clay/*url-params*)

(defn merge-params [& kvs]
  (merge (current-params)
         (apply hash-map kvs)))

(defn params->query-string [params]
  (->> params
       (map (fn [[k v]] (str (name k) "=" v)))
       (str/join "&")))

(defn url [page & new-kvs]
  (let [params (apply merge-params new-kvs)
        qs (params->query-string params)]
    (str page (when (seq qs) (str "?" qs)))))

;; Usage in notebook:
[:a {:href (url "index.html" :filter "active")} "Filter"]
[:form {:action (url "index.html")} ...]
```

---

### Option 5: Cookie-Based Sessions

**How it works:**
```
GET /index.html?wallet=pb1abc
→ Server sets cookie: clay-session=dft34
→ Stores session data server-side
→ All subsequent requests include cookie
→ Server looks up session on each request
```

**Pros:**
- ✅ Clean URLs
- ✅ Standard web approach
- ✅ No JavaScript required
- ✅ Can handle complex state

**Cons:**
- ❌ Not bookmarkable
- ❌ Breaks with multiple tabs (shared cookie)
- ❌ Cookie management complexity
- ❌ Session storage and cleanup needed
- ❌ Testing becomes harder (need cookie handling)
- ❌ Privacy concerns (persistent cookies)

**Implementation Complexity**: Medium (~120 lines)

**Use Case Fit**: Poor - breaks multi-tab workflows

---

### Option 6: JWT Tokens in URL

**How it works:**
```
GET /index.html?wallet=pb1abc
→ Server creates signed JWT with params
→ Redirect to /app?token=eyJhbGc...
→ All links include token
→ Server validates and decodes token
```

**Pros:**
- ✅ Stateless (state in token)
- ✅ Signed (tamper-proof)
- ✅ Can expire (security)
- ✅ Industry standard

**Cons:**
- ❌ Overkill for this use case
- ❌ Requires JWT library
- ❌ Key management needed
- ❌ Still requires URL rewriting
- ❌ Larger than base64 (includes signature)

**Implementation Complexity**: High (~200 lines + dependencies)

**Use Case Fit**: Overkill - no security threat model here

---

## Comparison Matrix

| Criterion | JavaScript (Current) | Session-ID Path | Base64 Path | Explicit (Recommended) | Cookies | JWT |
|-----------|---------------------|-----------------|-------------|------------------------|---------|-----|
| **Stateless** | ✅ Yes | ❌ No | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes |
| **Bookmarkable** | ✅ Yes | ❌ No | ✅ Yes | ✅ Yes | ❌ No | ⚠️ Limited |
| **No JavaScript** | ❌ No | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes |
| **Clean URLs** | ⚠️ Params visible | ✅ Yes | ❌ Encoded | ⚠️ Params visible | ✅ Yes | ⚠️ Token visible |
| **Implementation Complexity** | Medium | Medium | Low | **Trivial** | Medium | High |
| **Testability** | Hard | Medium | Easy | **Easy** | Medium | Hard |
| **Idiomatic Clojure** | ❌ No | ⚠️ Imperative | ⚠️ Imperative | **✅ Yes** | ❌ No | ❌ No |
| **Explicit > Implicit** | ❌ Magic | ❌ Magic | ❌ Magic | **✅ Explicit** | ❌ Magic | ❌ Magic |
| **Multi-tab Safe** | ✅ Yes | ⚠️ Shared state | ✅ Yes | ✅ Yes | ❌ No | ✅ Yes |
| **No Session Cleanup** | ✅ Yes | ❌ Required | ✅ Yes | ✅ Yes | ❌ Required | ✅ Yes |

---

## Recommendation: Option 4 (Stateless with Explicit Param Handling)

### Why This Is The Best Choice

1. **Idiomatic Clojure**: Explicit > implicit, pure functions, no magic
2. **Simplest Implementation**: ~20 lines of helper functions
3. **Most Testable**: Pure functions, no side effects
4. **Truly Stateless**: Aligns with HTTP and Ring philosophy
5. **Bookmarkable**: URLs contain all state
6. **Multi-tab Safe**: Each tab independent
7. **No Cleanup Required**: No sessions to expire

### How to Migrate

**Step 1**: Create helper functions in `clay.v2.params` namespace

**Step 2**: Update documentation with examples

**Step 3**: Revert JavaScript injection from server.clj

**Step 4**: Update param-interactive-test.clj to use helpers:

```clojure
(ns param-interactive-test
  (:require [scicloj.kindly.v4.kind :as kind]
            [scicloj.clay.v2.api :as clay]
            [scicloj.clay.v2.params :as params]))  ; NEW

;; Read params same as before
(def wallet-param (get clay/*url-params* "wallet" "default"))

;; Links using helper
(kind/hiccup
 [:a {:href (params/url "index.html" :wallet "pb1new")}
  "Change Wallet"])

;; Forms using helper
[:form {:action (params/url "index.html")}
 [:input {:name "search"}]
 [:button "Search"]]
```

### Why NOT Session-ID or Base64?

Both require:
- URL rewriting/routing changes
- Pattern matching logic
- Either server-side storage (sessions) OR long ugly URLs (base64)
- More complex testing

They solve a problem we don't actually have - the query params work fine, we just need a clean way to include them in subsequent requests.

### Edge Cases Handled

**Q: What if user forgets to use helper?**
A: Link/form works but params not preserved - same as any broken link. Explicit behavior.

**Q: What about external links?**
A: Helper could check domain, but simpler: just don't call helper for external links.

**Q: How to test?**
A: Pure functions - unit test the helpers directly. Integration test by checking generated URLs.

---

## Code Example: Complete Implementation

```clojure
;; File: src/scicloj/clay/v2/params.clj
(ns scicloj.clay.v2.params
  "Helper functions for URL parameter management in parameterized notebooks.

  Usage:
    (require '[scicloj.clay.v2.params :as params])

    ;; In links:
    [:a {:href (params/url \"page.html\" :filter \"active\")} \"Filter\"]

    ;; In forms:
    [:form {:action (params/url \"search.html\")} ...]
  "
  (:require [clojure.string :as str]
            [scicloj.clay.v2.api :as clay]))

(defn current-params
  "Get current URL parameters from Clay's dynamic var."
  []
  clay/*url-params*)

(defn merge-params
  "Merge current params with new key-value pairs.
  New values override existing ones."
  [& kvs]
  (merge (current-params)
         (apply hash-map kvs)))

(defn params->query-string
  "Convert param map to URL query string.
  Example: {:wallet \"pb1abc\" :minAUM 1000} => \"wallet=pb1abc&minAUM=1000\""
  [params]
  (->> params
       (map (fn [[k v]]
              (str (name k) "="
                   (java.net.URLEncoder/encode (str v) "UTF-8"))))
       (str/join "&")))

(defn url
  "Build URL with current params plus any new ones.

  Examples:
    (url \"index.html\")                    ; Preserves all current params
    (url \"index.html\" :filter \"active\")  ; Adds/updates filter param
    (url \"index.html\" :wallet \"pb1new\")  ; Overrides wallet param
  "
  [page & new-kvs]
  (let [params (if (seq new-kvs)
                 (apply merge-params new-kvs)
                 (current-params))
        qs (params->query-string params)]
    (str page (when (seq qs) (str "?" qs)))))

(defn form-action
  "Convenience function for form actions.
  Alias for (url page & new-kvs)."
  [page & new-kvs]
  (apply url page new-kvs))

;; Optional: Helper for common patterns
(defn link
  "Build a link hiccup element with param preservation.

  Example:
    (link \"Filter Active\" \"index.html\" :filter \"active\")
    => [:a {:href \"index.html?wallet=pb1abc&filter=active\"} \"Filter Active\"]
  "
  [text page & new-kvs]
  [:a {:href (apply url page new-kvs)} text])
```

### Usage in Notebooks

**Before (JavaScript approach):**
```clojure
;; Params preserved automatically via JS injection
[:a {:href "?filter=active"} "Filter"]
```

**After (Explicit approach):**
```clojure
(require '[scicloj.clay.v2.params :as params])

;; Explicit param preservation
[:a {:href (params/url "index.html" :filter "active")} "Filter"]

;; Or using helper:
(params/link "Filter Active" "index.html" :filter "active")
```

**Tests:**
```clojure
(ns scicloj.clay.v2.params-test
  (:require [clojure.test :refer :all]
            [scicloj.clay.v2.params :as params]
            [scicloj.clay.v2.api :as clay]))

(deftest test-merge-params
  (binding [clay/*url-params* {"wallet" "pb1abc" "minAUM" "1000"}]
    (is (= {"wallet" "pb1abc" "minAUM" "1000" "filter" "active"}
           (params/merge-params :filter "active")))
    (is (= {"wallet" "pb1new" "minAUM" "1000"}
           (params/merge-params :wallet "pb1new")))))

(deftest test-url-generation
  (binding [clay/*url-params* {"wallet" "pb1abc"}]
    (is (= "index.html?wallet=pb1abc"
           (params/url "index.html")))
    (is (= "index.html?wallet=pb1abc&filter=active"
           (params/url "index.html" :filter "active")))))
```

---

## Decision Flowchart

```
Do you need server-side state storage?
├─ YES → Consider Session-ID Path (Option 2)
│   └─ But ask: Why? Clay is designed to be stateless
│
└─ NO (stateless preferred)
    │
    ├─ Do you want automatic/magic behavior?
    │   ├─ YES → JavaScript (Option 1) - but hacky
    │   └─ NO → Explicit helpers (Option 4) ✅ RECOMMENDED
    │
    └─ Do you want parameters hidden in URL?
        ├─ YES → Base64 (Option 3) - ugly URLs
        └─ NO → Explicit helpers (Option 4) ✅ RECOMMENDED
```

---

## Conclusion

**Recommendation**: Implement Option 4 (Stateless with Explicit Param Handling)

**Action Items**:
1. Create `src/scicloj/clay/v2/params.clj` with helper functions
2. Remove JavaScript injection from `server.clj`
3. Update `param-interactive-test.clj` to demonstrate helpers
4. Add documentation and examples
5. Write unit tests for helper functions

**Rationale**: This is the most idiomatic Clojure solution that aligns with Clay's stateless design philosophy, requires minimal code, is trivial to test, and makes parameter handling explicit rather than magical.

The JavaScript approach was a clever hack, but it fights against the architecture. The explicit approach embraces it.
