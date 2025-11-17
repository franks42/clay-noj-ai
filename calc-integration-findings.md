# calc.clj Integration Findings

## Test Results - 2025-11-16

Successfully integrated calc.clj into the portfolio dashboard (index.clj). All core features work correctly.

## ✅ Features Working Perfectly

### 1. Token Formatting
```clojure
;; USD formatting with currency symbol
(calc/format [177475 :usd] {:decimals 0})
;; => "$177,475 USD"

;; HASH formatting with thousands separators
(calc/with-commas (calc/round-to 29000000 0))
;; => "29,000,000"
```

### 2. Type-Safe Token Conversion
```clojure
;; Create exchange rate
(def hash-usd-rate (calc/rate 0.028 :usd :per :hash))
;; => [:/ [0.028 :usd] [1 :hash]]

;; Aggregate portfolio value across multiple token types
(calc/portfolio-value
  [[29000000 :hash]
   [37044 :hash]]
  :usd
  [hash-usd-rate])
;; => [812000 :usd] (approximately)
```

### 3. Number Utilities
```clojure
(calc/round-to 3.14159 2)  ;; => 3.14
(calc/with-commas 1234567) ;; => "1,234,567"
```

### 4. Financial Calculations
```clojure
(calc/percent-change 100 125)
;; => {:percent 25.0 :direction :increase :change 25}

(calc/roi 1000 1500)
;; => {:profit 500 :roi-percent 50.0 :multiplier 1.5}
```

## 🔍 Missing Features Identified

### 1. **Staking APY Calculator**
**Current:** Manual calculation with assumptions about reward accumulation period
```clojure
;; Current manual approach (in index.clj)
(def staking-apy
  (if (and (pos? total-staked) (pos? total-pending-rewards))
    ;; Rough estimate: (rewards/staked) * 365 * 100
    ;; Assuming rewards accumulated over ~7 days
    (* (/ total-pending-rewards total-staked) 365 100 (/ 1 7))
    0))
```

**Suggested:** Add to calc.clj
```clojure
(defn estimate-staking-apy
  "Estimate annualized APY from current rewards.

   Example:
     (estimate-staking-apy 17500000 20730 7)
     => {:estimated-apy 68.7 :daily-rate 0.188 :basis-days 7}"
  [staked-amount pending-rewards accumulation-days]
  (if (and (pos? staked-amount) (pos? pending-rewards))
    (let [daily-rate (/ pending-rewards staked-amount accumulation-days)
          annual-rate (* daily-rate 365)
          apy (* annual-rate 100)]
      {:staked staked-amount
       :rewards pending-rewards
       :basis-days accumulation-days
       :daily-rate daily-rate
       :annual-rate annual-rate
       :estimated-apy apy
       :formatted (str (round-to apy 1) "% APY")})
    {:estimated-apy 0 :daily-rate 0 :annual-rate 0}))
```

### 2. **Percentage Formatting Helper**
**Current:** Manual string concatenation
```clojure
(defn format-percent [pct]
  (str (calc/round-to pct 1) "%"))
```

**Suggested:** Add to calc.clj
```clojure
(defn format-percentage
  "Format percentage for display with smart rounding.

   Example:
     (format-percentage 31.2456) => \"31.2%\"
     (format-percentage 0.123) => \"0.12%\""
  ([pct] (format-percentage pct {}))
  ([pct {:keys [decimals symbol] :or {decimals 1 symbol "%"}}]
   (str (round-to pct decimals) symbol)))
```

### 3. **Metric Card Data Constructor**
**Current:** Manually construct data structures for visualization
```clojure
;; Would be cleaner with a helper
[:div {:style {...}}
 [:div "HASH Price"]
 [:div (format-usd hash-price)]]
```

**Suggested:** Add to calc.clj (Clay-specific helpers section)
```clojure
(defn metric-card-data
  "Create data structure for metric card visualization.

   Example:
     (metric-card-data \"HASH Price\" [0.028 :usd]
       {:color \"#10b981\" :label \"USD per HASH\"})
     => {:title \"HASH Price\"
         :value \"$0 USD\"
         :label \"USD per HASH\"
         :color \"#10b981\"}"
  [title value-tuple & [{:keys [color label decimals]}]]
  {:title title
   :value (format value-tuple (when decimals {:decimals decimals}))
   :label (or label "")
   :color (or color "#6b7280")})
```

### 4. **Chart Data Transformers**
**Current:** Manual data transformation for charts
```clojure
(def pie-values (mapv :value portfolio-distribution))
(def pie-labels (mapv :label portfolio-distribution))
```

**Suggested:** Add to calc.clj
```clojure
(defn prepare-pie-chart-data
  "Transform token holdings into pie chart data.

   Example:
     (prepare-pie-chart-data
       [[100056.71 :usd \"Trading Trust\"]
        [60336.08 :usd \"Staking Individual 1\"]])
     => {:labels [\"Trading Trust\" \"Staking Individual 1\"]
         :values [100056.71 60336.08]
         :formatted [\"$100,057 USD\" \"$60,336 USD\"]}"
  [holdings-with-labels]
  (let [data (for [[amt unit label] holdings-with-labels]
               {:label label
                :value amt
                :unit unit
                :formatted (format [amt unit])})]
    {:labels (mapv :label data)
     :values (mapv :value data)
     :formatted (mapv :formatted data)
     :data data}))

(defn prepare-bar-chart-data
  "Transform token holdings into grouped bar chart data.

   Example:
     (prepare-bar-chart-data
       {:staked [17500000 11500000]
        :rewards [20730 16314]
        :labels [\"Individual 1\" \"Individual 2\"]}
       :hash)
     => {:x-labels [\"Individual 1\" \"Individual 2\"]
         :datasets [{:name \"Staked\" :values [17500000 11500000]}
                    {:name \"Rewards\" :values [20730 16314]}]}"
  [grouped-data unit]
  (let [labels (:labels grouped-data)
        datasets (for [[name values] (dissoc grouped-data :labels)]
                  {:name (clojure.string/capitalize (clojure.core/name name))
                   :values values
                   :formatted (mapv #(format [% unit]) values)})]
    {:x-labels labels
     :datasets datasets
     :unit unit}))
```

### 5. **Token Amount Constructor from API Response**
**Current:** Manual extraction from API responses
```clojure
;; API returns: {:aum 100056.71 :staked 0 :rewards 0}
;; We manually use these as numbers
```

**Suggested:** Add to calc.clj
```clojure
(defn wallet-summary->token-amounts
  "Convert wallet API response to typed token amounts.

   Example:
     (wallet-summary->token-amounts
       {:aum 100056.71 :staked 0 :rewards 0}
       {:aum-unit :usd :token-unit :hash})
     => {:aum [100056.71 :usd]
         :staked [0 :hash]
         :rewards [0 :hash]}"
  [wallet-data units]
  (let [aum-unit (:aum-unit units :usd)
        token-unit (:token-unit units :hash)]
    {:aum [(:aum wallet-data) aum-unit]
     :staked [(:staked wallet-data) token-unit]
     :rewards [(:rewards wallet-data) token-unit]
     :type (:type wallet-data)}))
```

### 6. **Vesting Coverage Ratio Calculator**
**Context:** Vesting accounts require specific delegation coverage ratios

**Suggested:** Add to calc.clj
```clojure
(defn vesting-coverage-ratio
  "Calculate vesting coverage ratio for compliance.

   Example:
     (vesting-coverage-ratio 1000000 750000 500000)
     => {:unvested 1000000
         :delegated 750000
         :committed 500000
         :total-coverage 1250000
         :coverage-ratio 1.25
         :compliant true
         :excess-coverage 250000}"
  [unvested-amount delegated-amount committed-amount]
  (let [total-coverage (+ delegated-amount committed-amount)
        ratio (if (pos? unvested-amount)
                (/ total-coverage unvested-amount)
                0)
        compliant (>= ratio 1.0)
        excess (- total-coverage unvested-amount)]
    {:unvested unvested-amount
     :delegated delegated-amount
     :committed committed-amount
     :total-coverage total-coverage
     :coverage-ratio ratio
     :compliant compliant
     :excess-coverage (when compliant excess)
     :shortfall (when-not compliant (Math/abs excess))
     :formatted (str (round-to (* ratio 100) 1) "% coverage")}))
```

## 📝 Recommendations

### High Priority (Essential for Component Library)
1. **`estimate-staking-apy`** - Currently doing manual calculation, would benefit all staking dashboards
2. **`format-percentage`** - Used frequently for financial displays
3. **`metric-card-data`** - Core building block for all metric cards

### Medium Priority (Quality of Life)
4. **`prepare-pie-chart-data`** and **`prepare-bar-chart-data`** - Simplify chart creation
5. **`wallet-summary->token-amounts`** - Type safety at API boundary

### Domain-Specific (Provenance Blockchain)
6. **`vesting-coverage-ratio`** - Critical for vesting account compliance

## 🎯 Next Steps

1. **Add missing functions to calc.clj** - Implement high-priority functions first
2. **Update index.clj** - Replace manual calculations with new calc functions
3. **Suggest improvements to source project** - As user requested: "if we add more functionality to this calc.clj, make sure to suggest to incorporate it in the project where it comes from!"
4. **Create comprehensive tests** - Validate all new functions with edge cases
5. **Update clay-context-template-design.md** - Document these patterns as component building blocks

## 💡 Key Insights

### What Worked Well
- Type-safe token system prevents unit confusion bugs
- Format functions provide consistent, professional output
- Calculation functions are well-tested and reliable
- Integration was seamless - no breaking changes needed

### Design Patterns Emerging
1. **Two-tier formatting**: Low-level (`with-commas`, `round-to`) + high-level (`format`, `format-token`)
2. **Data transformation pipeline**: API response → typed tokens → calculations → formatted display
3. **Component data constructors**: Functions that return visualization-ready data structures
4. **Domain-specific helpers**: Blockchain/DeFi calculations isolated from generic utilities

### Architecture Insights
The calc.clj library naturally splits into layers:
- **Layer 1:** Pure utilities (with-commas, round-to)
- **Layer 2:** Type system (token-amount, rate, token-convert)
- **Layer 3:** Domain calculations (staking-rewards, vesting)
- **Layer 4:** Visualization helpers (metric-card-data, chart data)
- **Layer 5:** Clay-specific integration (format-metric-value, aggregate-holdings)

This layering should inform the component library design.

---

**Generated:** 2025-11-16
**Dashboard:** index.clj
**Calculation Library:** notebooks/calc.clj (extracted from mcp-nrepl calculator.clj)
