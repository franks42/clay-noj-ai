# Stateful Parameterized Notebooks - Design Document

## Problem Statement

Clay notebooks can be parameterized with URL query parameters (e.g., `?wallet=pb1xyz&minAUM=1000`). When users interact with the page (clicking links, submitting forms), these parameters need to be preserved and potentially merged with new parameters.

### Requirements

1. **Parameter Preservation**: Original params carry forward across interactions
2. **Parameter Updates**: New params merge with/override existing ones
3. **Privacy**: Confidential data (wallet addresses) must not leak to URLs, logs, referrers
4. **REST Compliance**: URLs should follow RESTful resource identity principles
5. **Usability**: Bookmarkable, shareable, works with browser navigation

## Design Decisions

### Decision 1: Server-Side State (vs URL-Based)

**Choice**: Store parameters server-side with opaque UUID

**Alternatives Considered**:
- Query params in URL → Privacy leak (wallet in logs/history)
- Base64 encoded in URL → Privacy leak (trivially decodable)
- Encrypted in URL → Complex key management
- Cookie-stored params → Not shareable, breaks multi-tab

**Rationale**:
- Wallet addresses are confidential - cannot appear in URLs
- Opaque UUID reveals nothing about the params
- Server-side storage is the only way to fully hide params

**Trade-off Accepted**: Server must maintain state, requires cleanup mechanism

### Decision 2: POST for All Parameter Submissions

**Choice**: All parameter changes via HTTP POST

**Alternatives Considered**:
- GET with query params → Privacy leak
- Hybrid (POST initial, GET updates) → Inconsistent security model

**Rationale**:
- POST body not logged in access logs
- POST body not in browser history
- POST body not in referrer headers
- Consistent security model for all params

**Trade-off Accepted**: Links must be converted to form submissions (minimal JS helper)

### Decision 3: UUID State-ID (vs Content-Hash)

**Choice**: Random UUID per state creation

**Alternatives Considered**:
- Content-addressed hash → Same params = same URL (cacheable forever)

**Rationale**:
- Page content depends on external mutable state (time, prices, blockchain)
- Same params at different times produces different pages
- UUID is honest: "evaluation context" not "exact content"
- REST allows resources to become unavailable (410/404)

**Trade-off Accepted**: No content-based deduplication

### Decision 4: TTL-Based Expiration

**Choice**: States expire after configurable TTL (e.g., 24 hours)

**Rationale**:
- Prevents unbounded memory growth
- Natural cleanup without tombstone tracking
- Expired states return 404 (no need to distinguish from unknown)
- Users can re-submit params to create fresh state

**Trade-off Accepted**: Old bookmarks may become invalid

### Decision 5: New UUID on Parameter Update

**Choice**: Each parameter change creates new state-id

**Alternatives Considered**:
- Mutate existing state-id → Violates REST (same URL, different content)

**Rationale**:
- URL uniquely identifies parameter set
- Enables browser back/forward navigation
- Each state is immutable snapshot of params
- More RESTful resource identity

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Client (Browser)                     │
├─────────────────────────────────────────────────────────┤
│  1. Form submits params via POST                        │
│  2. Links converted to POST via minimal JS              │
│  3. Navigation uses opaque state-id URLs                │
└─────────────────┬───────────────────────────────────────┘
                  │
                  │ HTTP POST/GET
                  ▼
┌─────────────────────────────────────────────────────────┐
│                    Clay Server                          │
├─────────────────────────────────────────────────────────┤
│  Routes:                                                │
│  - POST /page.html           → Create state, redirect   │
│  - POST /app/page.html/{id}  → Update state, redirect   │
│  - GET  /app/page.html/{id}  → Render with params       │
├─────────────────────────────────────────────────────────┤
│  State Storage (*states atom):                          │
│  {                                                      │
│    "uuid-1" {:params {...} :expires <timestamp>}        │
│    "uuid-2" {:params {...} :expires <timestamp>}        │
│  }                                                      │
└─────────────────┬───────────────────────────────────────┘
                  │
                  │ Evaluate with params
                  ▼
┌─────────────────────────────────────────────────────────┐
│                 Notebook Evaluation                     │
├─────────────────────────────────────────────────────────┤
│  - Reads params from clay/*url-params*                  │
│  - Fetches live data (prices, blockchain state)         │
│  - Renders fresh HTML                                   │
└─────────────────────────────────────────────────────────┘
```

## Request Flows

### Flow 1: Initial Parameter Submission

```
POST /dashboard.html
Content-Type: application/x-www-form-urlencoded

wallet=pb1xyz&minAUM=1000

→ Server:
  1. Parse body params
  2. Generate UUID: a7f3b2c1-...
  3. Store: {uuid → {:params {wallet, minAUM}, :expires now+24h}}
  4. Return 303 See Other
     Location: /app/dashboard.html/a7f3b2c1-...

→ Browser follows redirect:
  GET /app/dashboard.html/a7f3b2c1-...

→ Server:
  1. Extract state-id from URL
  2. Look up params
  3. Evaluate notebook with params (fresh data)
  4. Return 200 OK with HTML
```

### Flow 2: Parameter Update

```
POST /app/dashboard.html/a7f3b2c1-...
Content-Type: application/x-www-form-urlencoded

filter=active

→ Server:
  1. Look up current state: {wallet, minAUM}
  2. Merge with new params: {wallet, minAUM, filter}
  3. Generate new UUID: b8d4e2f5-...
  4. Store new state
  5. Return 303 See Other
     Location: /app/dashboard.html/b8d4e2f5-...
```

### Flow 3: State Not Found

```
GET /app/dashboard.html/invalid-or-expired

→ Server:
  1. Look up state-id → not found
  2. Return 404 Not Found
     "State not found or expired. Please submit params again."
```

## URL Patterns

| Pattern | Method | Purpose |
|---------|--------|---------|
| `/{page}.html` | POST | Initial param submission |
| `/app/{page}.html/{state-id}` | GET | Render with state params |
| `/app/{page}.html/{state-id}` | POST | Update params, create new state |

## Security Properties

| Property | Status | Mechanism |
|----------|--------|-----------|
| Params in URL | ✅ Protected | Opaque UUID only |
| Params in browser history | ✅ Protected | POST body |
| Params in server logs | ✅ Protected | POST body (not typically logged) |
| Params in referrer header | ✅ Protected | POST body |
| Bookmarkable | ✅ Supported | State-id in URL |
| Shareable | ✅ Supported | Same state-id = same params |
| Multi-tab safe | ✅ Supported | Each tab has own state-id |
| HTTPS required | ⚠️ Recommended | Encrypts POST body on wire |

## Implementation Components

### 1. State Management (~40 lines)

```clojure
(defonce *states (atom {}))

(defn create-state! [params ttl-hours] ...)
(defn get-state [id] ...)
(defn cleanup-expired! [] ...)
```

### 2. Route Handlers (~60 lines)

```clojure
(defn handle-initial-post [uri body-params] ...)
(defn handle-state-post [state-id body-params] ...)
(defn handle-state-get [page state-id] ...)
(defn parse-state-url [uri] ...)
```

### 3. JavaScript Helper (~15 lines)

Injected into pages to convert links to POST:

```javascript
document.addEventListener('click', (e) => {
  if (e.target.tagName === 'A' && e.target.href.includes('?')) {
    // Convert to form POST
  }
});
```

### 4. Routing Integration (~30 lines)

Updates to `routes` function to handle new patterns.

**Total**: ~145 lines

## Configuration Options

```clojure
{:state-ttl-hours 24        ; How long states persist
 :cleanup-interval-ms 3600000 ; Background cleanup frequency (1 hour)
 :max-states 10000}         ; Optional: limit total states
```

## Testing Strategy

### Unit Tests
- State creation/retrieval
- Expiration logic
- Parameter merging

### Integration Tests
- POST → redirect → GET flow
- Parameter update flow
- Expired state handling

### Manual/Playwright Tests
- Form submission
- Link clicking (JS conversion)
- Browser navigation (back/forward)
- Multiple tabs

## Future Considerations

### Persistence
Current design uses in-memory atom. For production:
- Redis for distributed state
- Database for persistence across restarts
- File-based for simple persistence

### Authentication
Current design allows anyone with URL to access state. Could add:
- HMAC signature in URL
- Rate limiting per state-id
- IP-based restrictions

### Analytics
Could track:
- State creation frequency
- TTL patterns (how long until access stops)
- Popular parameter combinations

## Summary

This design prioritizes **privacy** and **REST compliance** over simplicity. The trade-off of server-side state is acceptable because:

1. ✅ No sensitive data in URLs/logs
2. ✅ Proper REST resource identity (each state = unique URL)
3. ✅ Bookmarkable and shareable
4. ✅ Multi-tab safe
5. ✅ Clean browser navigation
6. ✅ Consistent security model (all POSTs)

The implementation is reasonably small (~145 lines) and follows established patterns (similar to how login sessions work).

---

*Document created: 2025-01-17*
*Status: Design approved, ready for implementation*
