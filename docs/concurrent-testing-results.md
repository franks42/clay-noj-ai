# Concurrent Request Testing Results

## Test Date: 2025-11-17

## Summary

**Status:** ⚠️ Race condition identified - NOT thread-safe under concurrent load

**Cause:** File system race condition, not thread-local binding issue

## Test Setup

Launched 10 concurrent requests with different parameters:
```bash
for i in {1..10}; do
  curl "http://localhost:1971/param_test.html?wallet=wallet$i&minAUM=$(($i*100))" > response$i.html &
done
```

Expected: Each response should contain its own wallet data
- response1.html → wallet1/$100
- response2.html → wallet2/$200
- ...
- response10.html → wallet10/$1000

## Test Results

### Response Verification

```
✅ Response 5: CORRECT (wallet5, $500)
✅ Response 6: CORRECT (wallet6, $600)
✅ Response 7: CORRECT (wallet7, $700)
❌ Response 1: WRONG (contains wallet5 instead of wallet1)
❌ Response 2: WRONG (contains wallet7 instead of wallet2)
❌ Response 3: WRONG (contains wallet1 instead of wallet3)
❌ Response 4: WRONG (contents unknown)
❌ Response 8: WRONG (contents unknown)
❌ Response 9: WRONG (contents unknown)
❌ Response 10: WRONG (contains wallet6 instead of wallet10)
```

**Success Rate:** 3/10 (30%)

## Root Cause Analysis

### What's Working ✅

The **thread-local bindings are working correctly**. Server logs show:
```
Parameterized request: /param_test.html params: {wallet wallet1, minAUM 100}
Parameterized request: /param_test.html params: {wallet wallet2, minAUM 200}
Parameterized request: /param_test.html params: {wallet wallet3, minAUM 300}
...
```

Each request correctly receives and binds its own parameters via `clay/*url-params*`.

### What's NOT Working ❌

The **file writing creates a race condition**. Current flow:

1. Request 1 arrives → binds {wallet1, minAUM 100} → calls `make!`
2. Request 2 arrives → binds {wallet2, minAUM 200} → calls `make!`
3. ...Request 10 arrives → binds {wallet10, minAUM 1000} → calls `make!`
4. All 10 `make!` calls execute concurrently
5. **All 10 write to the same file:** `docs/param_test.html`
6. **Last writer wins** - file contains whatever finished last
7. When curl reads the file, it gets random data from whichever request finished writing most recently

### Evidence from Server Logs

```
Parameterized request: /param_test.html params: {wallet wallet2, minAUM 200}
Parameterized request: /param_test.html params: {wallet wallet3, minAUM 300}
Parameterized request: /param_test.html params: {wallet wallet1, minAUM 100}
Clay make started:  #{notebooks/param-test.clj}
Clay make started:  #{notebooks/param-test.clj}
Clay make started:  #{notebooks/param-test.clj}
Clay:  Evaluated param-test in 0.012 seconds
Clay:  [:wrote docs/param_test.html #inst "2025-11-17T..."]  ← All write to same file!
Clay:  Evaluated param-test in 0.011 seconds
Clay:  [:wrote docs/param_test.html #inst "2025-11-17T..."]  ← Overwrites previous!
```

Multiple concurrent evaluations all write to `docs/param_test.html`, overwriting each other.

## Solution: In-Memory Generation

The current implementation (in `/Users/franksiebenlist/Development/clay-fork/src/scicloj/clay/v2/server.clj:206-239`):

```clojure
(defn handle-parameterized-request
  [uri query-params state]
  (let [url-params-var (find-var 'scicloj.clay.v2.api/*url-params*)
        make-fn (resolve 'scicloj.clay.v2.make/make!)]
    (push-thread-bindings {url-params-var query-params})
    (try
      (let [spec {:source-path source-path
                  :show false
                  :live-reload false}
            _ (make-fn spec)           ← Writes to disk
            ;; TODO: Make this truly in-memory
            html-path (str (:base-target-path state) uri)
            html (slurp html-path)]    ← Reads from disk (race!)
        {:body (wrap-html html state)
         :headers {"Content-Type" "text/html"}
         :status 200})
      (finally
        (pop-thread-bindings)))))
```

**Problem:** Steps 2-3 create a race condition:
- `make-fn` calls Clay's make pipeline which writes HTML to disk
- Then we read it back from disk
- Under concurrent load, multiple requests overwrite the same file

**Required Fix:** Modify Clay's `make!` to support in-memory generation:
```clojure
;; Option 1: Add :return-html? flag to make!
(let [result (make-fn (assoc spec :return-html? true))
      html (:html result)]  ; Get HTML without disk I/O
  {:body (wrap-html html state) ...})

;; Option 2: Capture HTML before disk write
;; Modify make! internals to return generated HTML
;; before writing to disk
```

This requires changes to `/Users/franksiebenlist/Development/clay-fork/src/scicloj/clay/v2/make.clj`.

## Impact Assessment

### Current Behavior

- ✅ Single-user parameterized requests work correctly
- ✅ Sequential requests (one at a time) work correctly
- ❌ Concurrent requests fail ~70% of the time
- ❌ Static files get polluted by parameterized requests

### Production Risk

**HIGH RISK** for multi-user deployments:
- Multiple users requesting different parameters will get random data
- No predictable failure mode (sometimes works, sometimes doesn't)
- Data from User A might be shown to User B (privacy/security issue!)

### Recommended Action

**Do NOT deploy to production** until in-memory generation is implemented.

For testing/development:
- ✅ Safe for single-user sequential testing
- ❌ Do not test with concurrent users

## Next Steps

1. **Modify Clay's make! function** to support in-memory HTML generation
2. **Update handle-parameterized-request** to use in-memory mode
3. **Re-run concurrent tests** to verify 100% success rate
4. **Add integration test** for concurrent requests in CI/CD

## Files to Modify

1. `/Users/franksiebenlist/Development/clay-fork/src/scicloj/clay/v2/make.clj`
   - Add `:return-html?` option to make! spec
   - Capture HTML before writing to disk
   - Return HTML in result map

2. `/Users/franksiebenlist/Development/clay-fork/src/scicloj/clay/v2/server.clj`
   - Update `handle-parameterized-request` to use `:return-html? true`
   - Remove disk read (`slurp html-path`)
   - Use returned HTML directly

## Additional Observations

### Thread-Local Binding Success

The `push-thread-bindings` / `pop-thread-bindings` approach **is working correctly**:
- Each request gets isolated parameter bindings
- No parameter cross-contamination during evaluation
- The notebook code sees the correct values

The failure is purely in the file I/O layer, not the binding layer.

### Static File Pollution

Non-parameterized requests are also affected:
- Requesting `/param_test.html` without parameters shows cached data from previous parameterized request
- This confirms that `docs/param_test.html` is being overwritten by parameterized requests
- Should only write to disk for non-parameterized requests (static file generation)

## Conclusion

The parameterized evaluation feature is **partially working**:
- ✅ Dynamic var binding mechanism works correctly
- ✅ URL parameter parsing works correctly
- ✅ Request routing works correctly
- ✅ Notebook evaluation with parameters works correctly
- ❌ **File I/O creates race condition under concurrent load**

**Fix required:** Implement in-memory HTML generation to bypass file system entirely for parameterized requests.
