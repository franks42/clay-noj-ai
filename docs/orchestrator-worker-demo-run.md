# Orchestrator + Worker Pattern - Demo Run

*Recorded: 2025-11-18*

This is a complete walkthrough of the **data gateway pattern** with live output, demonstrating cost-optimized multi-instance Claude orchestration.

**Pattern:** Sonnet orchestrator (analysis) + Haiku workers (data fetching)

---

## Setup

### Clean Slate

```bash
$ bb claude:kill-all
```

```clojure
Killing Claude instance: orchestrator
Claude instance 'orchestrator' killed.
Killing Claude instance: fetcher
Claude instance 'fetcher' killed.

{:killed ("orchestrator" "fetcher")}
```

---

## Step 1: Spawn Orchestrator (Sonnet)

The orchestrator uses Sonnet (default) for sophisticated analysis.

```bash
$ bb claude:spawn orchestrator
```

```clojure
Spawning Claude instance: orchestrator
Claude instance 'orchestrator' spawned.

{:name "orchestrator"
 :status :running
 :model nil
 :pid 81491}
```

---

## Step 2: Set Orchestrator Context

Give the orchestrator its domain knowledge and role.

```bash
$ bb claude:ask orchestrator "You are a crypto portfolio analyst specializing in the Provenance Blockchain ecosystem. You will receive data from worker instances and provide analysis. Respond with READY when understood."
```

```
"READY"
```

---

## Step 3: Fork Haiku Worker

Fork a worker from orchestrator - it inherits all context but uses cheaper Haiku model.

```bash
$ bb claude:fork orchestrator fetcher haiku
```

```clojure
Spawning Claude 'fetcher' from session: 0b829536-5d2a-4fac-84ea-02576073dc77 (model: claude-3-5-haiku-20241022)
Claude 'fetcher' forked from session 0b829536-5d2a-4fac-84ea-02576073dc77

{:name "fetcher"
 :status :running
 :model :haiku
 :forked-from "0b829536-5d2a-4fac-84ea-02576073dc77"
 :pid 81778}
```

---

## Step 4: Verify Context Inheritance

The worker should know its role without being told - it inherited the orchestrator's context.

```bash
$ bb claude:ask fetcher "What is your role? Answer in one sentence."
```

```
"I am a specialized AI analyst for the Provenance Blockchain ecosystem,
designed to process and interpret blockchain data, wallet information,
and market statistics to provide comprehensive portfolio insights."
```

**✓ Context inheritance confirmed** - Worker knows it's a crypto analyst without us telling it.

---

## Step 5: Worker Fetches Live Data

Haiku worker fetches data from the Provenance blockchain via MCP tools.

```bash
$ bb claude:ask fetcher "Use fetch_current_hash_statistics to get HASH token stats. Return compact JSON only, no explanation."
```

```json
{
    "maxSupply": {"amount": "100000000000000000000", "denom": "nhash"},
    "currentSupply": {"amount": "99978477857646380000", "denom": "nhash"},
    "circulation": {"amount": "51267390679232227090", "denom": "nhash"},
    "communityPool": {"amount": "20233210917201848", "denom": "nhash"},
    "bonded": {"amount": "15988028779719272091", "denom": "nhash"},
    "burned": {"amount": "21522142353620000", "denom": "nhash"},
    "locked": {"amount": "32702825187777678971", "denom": "nhash"}
}
```

---

## Step 6: Relay Data to Orchestrator

Send the fetcher's data to orchestrator for analysis.

```bash
$ bb claude:eval '(claude-service/relay "fetcher" "orchestrator" "Analyze this HASH supply data. Provide key metrics and investment implications.")'
```

```clojure
Relay: fetcher -> orchestrator

"orchestrator-000007-04283312"
```

---

## Step 7: Get Orchestrator's Analysis

Poll for the completed analysis from Sonnet.

```bash
$ bb claude:eval '(clojure.pprint/pprint (claude-service/poll-response "orchestrator-000007-04283312"))'
```

```clojure
{:status :complete
 :claude "orchestrator"
 :result <see below>
 :completed-at 1763533791504}
```

### Orchestrator's Analysis Output:

## HASH Token Supply Analysis

### Key Metrics

| Metric | Value (HASH) | % of Max Supply |
|--------|-------------|-----------------|
| **Max Supply** | 100.00B | 100.0% |
| **Current Supply** | 99.98B | 99.98% |
| **Circulation** | 51.27B | 51.3% |
| **Staked/Bonded** | 15.99B | 16.0% |
| **Locked** | 32.70B | 32.7% |
| **Burned** | 21.52M | 0.02% |
| **Community Pool** | 20.23M | 0.02% |

---

### Supply Breakdown

**Circulating vs Non-Circulating:**
- **Liquid/Circulating:** 51.27B (51.3%)
- **Locked (Vesting):** 32.70B (32.7%)
- **Staked:** 15.99B (16.0%)

**Staking Ratio:** 31.2% of circulating supply is bonded

---

### Investment Implications

#### 🟢 Bullish Indicators

1. **Strong Staking Participation** - 31.2% staking ratio indicates committed holders and reduced sell pressure
2. **Deflationary Mechanism** - 21.5M tokens burned, with active burn mechanics
3. **Significant Lock-up** - 32.7% locked in vesting schedules reduces near-term supply pressure

#### 🟡 Considerations

1. **Vesting Unlock Risk** - 32.7B tokens in vesting will gradually enter circulation
2. **Near-Max Supply** - 99.98% of max supply already exists; no significant new issuance
3. **Community Pool** - Small community pool (20.2M) may limit future ecosystem funding

#### 📊 Supply Dynamics

- **Effective Float:** ~35.28B HASH (circulation minus staked)
- **Future Dilution:** Minimal - only 21.5M from max to current supply
- **Unlock Pressure:** Monitor vesting schedules for potential sell pressure

---

### Recommendation

The HASH tokenomics suggest a **mature supply model** with deflationary pressure from burns and strong holder commitment via staking. Key risks center on vesting unlocks. For position sizing, consider the 32.7B locked supply as potential future selling pressure.

---

## Final State

```bash
$ bb claude:eval '(clojure.pprint/pprint (claude-service/list-services))'
```

```clojure
{"orchestrator"
 {:status :running
  :created-at 1763533706364
  :session-id "0b829536-5d2a-4fac-84ea-02576073dc77"
  :request-count 2
  :pid 81491}

 "fetcher"
 {:status :running
  :created-at 1763533727525
  :session-id "0b829536-5d2a-4fac-84ea-02576073dc77"
  :request-count 2
  :pid 81778}}
```

---

## Summary

| Instance | Model | Requests | Purpose |
|----------|-------|----------|---------|
| orchestrator | Sonnet | 2 | Analysis, synthesis |
| fetcher | Haiku | 2 | Data fetching |

### Pattern Benefits

1. **Cost Efficiency** - Haiku (~10x cheaper) handles data fetching
2. **Context Inheritance** - Workers automatically know domain context
3. **Separation of Concerns** - Raw data vs. analysis
4. **Scalable** - Can fork multiple workers from same orchestrator

### Commands Used

```bash
bb claude:spawn <name>                    # Create instance
bb claude:ask <name> <prompt>             # Send message
bb claude:fork <source> <new> [model]     # Fork with context
bb claude:eval '<clojure-code>'           # Direct evaluation
bb claude:list                            # Show all instances
bb claude:kill-all                        # Cleanup
```

---

*Generated by clay-noj-ai multi-backend LLM service*
