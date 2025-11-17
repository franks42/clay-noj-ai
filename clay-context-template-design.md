# Clay Context Template & Component Library Design

*Design Document - 2025-11-16*

## Executive Summary

This document outlines a design for creating reusable, domain-specific visualization components and templates for Clay/Noj notebooks. The goal is to capture "best practices" for presenting financial and blockchain data, making it easy to create consistent, high-quality dashboards quickly.

---

## 🎯 Problem Statement

### Current Challenges

1. **Code Duplication**: Every dashboard requires writing Hiccup/Plotly visualizations from scratch
2. **Inconsistency**: No standard patterns for presenting common data types (vesting schedules, price trends, portfolio breakdowns)
3. **Learning Curve**: Each visualization requires deep knowledge of Hiccup structure and Plotly configuration
4. **Knowledge Loss**: "Best" presentation choices are discovered through iteration but not captured for reuse
5. **Maintenance**: Improving a visualization pattern requires updating multiple notebooks

### Observations from Initial Implementation

From building the portfolio dashboard, we identified several reusable patterns:

**Visual Patterns:**
- Metric cards with gradient backgrounds (HASH price, circulation, bonded amount)
- Portfolio distribution pie charts with consistent color schemes
- Staking analysis bar charts with grouped data
- Wallet breakdown tables with type indicators
- Insight boxes with color-coded severity (info/success/warning)
- Action item lists with prioritization

**Data Presentation Challenges:**
- Vesting schedules (timeline, unlock dates, coverage ratios)
- Price trends (candlestick, volume, timeframe selection)
- Wallet comparisons (liquid vs vesting, AUM distribution)
- Staking performance (rewards, delegation spread, validator info)

**Code Structure Learnings:**
- Intermediate variables make code more maintainable
- Formatting functions should handle type conversion (to `double`)
- `^:kindly/hide-code` metadata essential for clean presentation
- Global code hiding via `:kinds-that-hide-code` configuration
- Comments as section headers create natural document structure

---

## 🏗️ Proposed Architecture

### Tier 0: Calculation Library (Foundation)

**File:** `notebooks/calc.clj`

**Purpose:** Type-safe financial calculations and token conversions

**Key Features:**
- **Token Conversion System**: Type-safe conversions with `token-convert`, `portfolio-value`
- **Formatting Functions**: `format`, `format-token`, `format-rate` with smart defaults
- **Financial Calculations**: ROI, percent-change, staking-rewards, APY/APR conversions
- **Vesting Utilities**: `lock-period-end`, `days-until`, `is-unlocked`
- **Number Formatting**: `with-commas`, `round-to` for display

**Example Usage:**
```clojure
(require '[calc :as c])

;; Token conversions
(c/token-convert [1000 :hash] :usd (c/rate 0.032 :usd :per :hash))
=> [32.0 :usd]

;; Portfolio aggregation
(c/portfolio-value
  [[1000 :hash] [5E7 :nhash] [10 :usd]]
  :usd
  [(c/rate 0.032 :usd :per :hash)])
=> [42.6 :usd]

;; Formatting
(c/format [17500000 :hash])
=> "17,500,000 HASH"

;; Vesting calculations
(c/lock-period-end 1700000000 365)
=> {:unlock-date "2024-11-15" :days-remaining 45}
```

**Benefits:**
- **Type Safety**: Prevents unit confusion (can't mix :hash and :usd without explicit conversion)
- **Consistency**: All calculations use same logic
- **Tested**: Extracted from production mcp-nrepl calculator
- **Domain-Specific**: Built for blockchain/DeFi use cases

### Tier 1: Component Library (Immediate - Week 1)

**File:** `notebooks/components.clj`

**Purpose:** Reusable functions that return configured `kind/*` visualizations

**Dependencies:** `calc.clj` for all calculations and formatting

**Benefits:**
- Single source of truth for visualization patterns
- Parameterized for flexibility
- Type-safe data contracts via calc library
- Easy to test and iterate

**Example Structure:**
```clojure
(ns components
  "Reusable visualization components for Clay/Noj notebooks"
  (:require [scicloj.kindly.v4.kind :as kind]
            [calc :as c]))

;; Theme Configuration
(def color-schemes
  {:green-gradient ["#10b981" "#059669"]
   :blue-gradient  ["#3b82f6" "#2563eb"]
   :purple-gradient ["#667eea" "#764ba2"]
   :orange-gradient ["#f59e0b" "#d97706"]})

;; Metric Cards (using calc for formatting)
(defn metric-card [config]
  (kind/hiccup
   [:div {:style (metric-card-style (:color-scheme config))}
    [:div (:label config)]
    [:div (c/format-metric-value (:value config) (:unit config))]
    [:div (:subtitle config)]]))

;; Charts
(defn pie-chart [config] ...)
(defn bar-chart [config] ...)
(defn line-chart [config] ...)
(defn candlestick-chart [config] ...)

;; Tables (using calc for cell formatting)
(defn data-table [config] ...)
(defn summary-table [config] ...)

;; Domain-Specific Components (using calc for calculations)
(defn vesting-timeline [vesting-data current-rate]
  ;; Uses c/lock-period-end, c/token-convert, c/format
  ...)

(defn price-trend [price-data timeframe]
  ;; Uses c/percent-change for trends
  ...)

(defn wallet-comparison [wallets rates]
  ;; Uses c/portfolio-value for aggregation
  ...)

(defn staking-overview [staking-data]
  ;; Uses c/staking-rewards for projections
  ...)

;; Layout Components
(defn metric-grid [metrics] ...)
(defn info-box [type title content] ...)
(defn action-list [items] ...)
```

### Tier 2: Pattern Library Documentation (Week 2)

**File:** `clay-component-library.md`

**Purpose:** Visual catalog of all components with usage examples

**Structure:**
```markdown
# Clay Component Library

## Metric Cards

### Purpose
Display key metrics prominently with visual hierarchy and color coding.

### API
```clojure
(c/metric-card
  {:label "Display name"
   :value "Formatted value"
   :subtitle "Additional context"
   :color-scheme :green-gradient})
```

### Use Cases
- Financial metrics (price, AUM, returns)
- Blockchain stats (circulation, staked, rewards)
- Portfolio summaries (total value, positions, P&L)

### Example
[Screenshot: metric-card-example.png]

### Data Contract
```clojure
{:label string       ; Required
 :value string       ; Required - pre-formatted
 :subtitle string    ; Optional
 :color-scheme keyword  ; Optional - defaults to :blue-gradient}
```

### Variations
- `metric-card-with-change` - Includes % change indicator
- `metric-card-sparkline` - Includes mini trend chart
```

### Tier 3: Template Notebooks (Week 3)

**Directory:** `notebooks/templates/`

**Purpose:** Complete, working notebooks for common use cases

**Templates:**

1. **`portfolio-dashboard.clj`**
   - Purpose: Comprehensive portfolio analysis
   - Components: Metric cards, pie charts, tables, action items
   - Data sources: Wallet summaries, market data
   - Use case: Daily portfolio review

2. **`vesting-tracker.clj`**
   - Purpose: Monitor vesting schedules and unlock dates
   - Components: Timeline charts, coverage ratio gauges, unlock calendars
   - Data sources: Vesting account data
   - Use case: Vesting compliance and planning

3. **`price-analysis.clj`**
   - Purpose: Technical analysis and price trends
   - Components: Candlestick charts, volume bars, moving averages
   - Data sources: Historical price data from Figure Markets
   - Use case: Trading decisions, market analysis

4. **`staking-monitor.clj`**
   - Purpose: Track staking performance and rewards
   - Components: Validator tables, reward trends, delegation charts
   - Data sources: Delegation data, validator info
   - Use case: Staking optimization

5. **`wallet-comparison.clj`**
   - Purpose: Compare multiple wallets side-by-side
   - Components: Comparison tables, distribution charts, type breakdown
   - Data sources: Multiple wallet summaries
   - Use case: Portfolio management across wallets

**Template Usage Pattern:**
```bash
# Copy template to working notebook
cp notebooks/templates/vesting-tracker.clj notebooks/index.clj

# Edit configuration section
# - Add wallet addresses
# - Configure timeframes
# - Customize thresholds

# Clay auto-reloads and renders
```

### Tier 4: Configuration-Driven Dashboards (Week 4)

**File:** `notebooks/config.edn`

**Purpose:** Declarative dashboard specification

**Example:**
```clojure
{:dashboard
 {:title "Portfolio Dashboard"
  :refresh-interval "5m"
  :data-sources
  {:wallets ["pb1..." "pb1..." "pb1..."]
   :market-data {:symbols ["HASH-USD" "BTC-USD"]}
   :vesting-accounts ["pb1..." "pb1..."]}

  :sections
  [{:type :metric-grid
    :metrics [{:label "HASH Price"
               :source [:market-data :HASH-USD :price]
               :format :usd
               :color-scheme :green-gradient}
              {:label "Total AUM"
               :source [:wallets :total-aum]
               :format :usd
               :color-scheme :purple-gradient}]}

   {:type :pie-chart
    :title "Portfolio Distribution"
    :data {:source [:wallets :aum-by-wallet]
           :labels :wallet-name
           :values :aum}}

   {:type :custom
    :component :vesting-timeline
    :data {:source [:vesting-accounts :schedule]}}]}}
```

**Renderer:**
```clojure
(ns renderer
  (:require [components :as c]))

(defn render-dashboard [config]
  "Generates complete notebook from config spec"
  (for [section (:sections config)]
    (case (:type section)
      :metric-grid (c/metric-grid (:metrics section))
      :pie-chart (c/pie-chart (:data section))
      :custom (resolve-component (:component section) (:data section)))))
```

### Tier 5: MCP Integration (Future)

**Purpose:** Make component library discoverable and accessible via AI tools

**Proposed Tools:**

1. **`mcp__clay__list_components`**
   ```clojure
   ;; Returns catalog of all components
   {:components
    [{:name "metric-card"
      :category "display"
      :description "Prominent metric display with gradient background"
      :parameters {...}
      :example-code "..."
      :screenshot-url "..."}]}
   ```

2. **`mcp__clay__get_component_template`**
   ```clojure
   ;; Returns ready-to-use code for component
   (mcp__clay__get_component_template "vesting-timeline")
   ;; => {:code "(c/vesting-timeline vesting-data)"
   ;;     :example-data "[{:date ... :amount ...}]"
   ;;     :docs "..."}
   ```

3. **`mcp__clay__generate_dashboard`**
   ```clojure
   ;; Generates complete notebook from high-level spec
   (mcp__clay__generate_dashboard
     {:type "portfolio-analysis"
      :wallets ["pb1..." "pb1..."]
      :sections ["overview" "staking" "vesting"]})
   ;; => Full notebook code
   ```

4. **`mcp__clay__suggest_visualization`**
   ```clojure
   ;; Recommends best visualization for data type
   (mcp__clay__suggest_visualization
     {:data-type "vesting-schedule"
      :dimensions {:time-series true :multi-wallet false}})
   ;; => {:recommended "vesting-timeline"
   ;;     :alternatives ["vesting-table" "unlock-calendar"]
   ;;     :rationale "..."}
   ```

---

## 📊 Component Specifications

### Core Components

#### 1. Metric Card

**Purpose:** Display a single metric prominently

**API:**
```clojure
(c/metric-card
  {:label "HASH Price"
   :value "$0.028"
   :subtitle "USD per HASH"
   :color-scheme :green-gradient
   :trend {:value 2.5 :direction :up}  ; Optional
   :sparkline [0.025 0.026 0.027 0.028]})  ; Optional
```

**Variants:**
- `metric-card` - Basic metric display
- `metric-card-with-trend` - Includes % change indicator
- `metric-card-with-sparkline` - Includes mini chart

#### 2. Pie Chart

**Purpose:** Show proportional distribution

**API:**
```clojure
(c/pie-chart
  {:title "Portfolio Distribution"
   :data [{:label "Trading Trust" :value 100057}
          {:label "Staking Individual 1" :value 60336}]
   :colors [:green :purple :pink :orange]
   :donut? true})  ; Optional donut/pie toggle
```

**Domain-Specific Variants:**
- `portfolio-distribution-chart` - Pre-configured for wallet AUM
- `staking-distribution-chart` - Pre-configured for delegation spread

#### 3. Bar Chart

**Purpose:** Compare values across categories

**API:**
```clojure
(c/bar-chart
  {:title "Staking Positions"
   :x-axis {:label "Wallet" :values ["Individual 1" "Individual 2"]}
   :series [{:name "Staked" :values [17500000 11500000] :color :green}
            {:name "Rewards" :values [20730 16314] :color :orange}]
   :grouped? true})
```

#### 4. Line/Timeline Chart

**Purpose:** Show trends over time

**API:**
```clojure
(c/line-chart
  {:title "HASH Price - 7 Days"
   :x-axis {:type :datetime :values [...]}
   :series [{:name "Price" :values [...] :color :blue}
            {:name "Volume" :values [...] :y-axis :secondary}]})
```

#### 5. Candlestick Chart

**Purpose:** OHLC price data

**API:**
```clojure
(c/candlestick-chart
  {:title "HASH-USD"
   :timeframe "1M"
   :data [{:timestamp ... :open ... :high ... :low ... :close ...}]
   :show-volume? true})
```

#### 6. Data Table

**Purpose:** Structured tabular data

**API:**
```clojure
(c/data-table
  {:columns ["Wallet" "AUM (USD)" "Type"]
   :rows [["Trading Trust" "$100,057" "✅ Liquid"]
          ["Staking Individual 1" "$60,336" "🔒 Vesting"]]
   :sortable? true
   :highlight-row (fn [row] (when (> (parse-usd (nth row 1)) 50000) :warning))})
```

#### 7. Info/Alert Box

**Purpose:** Highlight important information

**API:**
```clojure
(c/info-box
  {:type :warning  ; :info, :success, :warning, :error
   :title "Staking Optimization"
   :content "Pending rewards should be claimed and restaked."
   :icon "💡"})
```

#### 8. Action List

**Purpose:** Present actionable recommendations

**API:**
```clojure
(c/action-list
  {:title "Recommended Actions"
   :items [{:priority :high
            :action "Claim Staking Rewards"
            :details "37,044 HASH pending (~$1,037 value)"
            :deadline "ASAP"}
           {:priority :medium
            :action "Review Validators"
            :details "Check commission rates across 15 validators"}]})
```

### Domain-Specific Components

#### 1. Vesting Timeline

**Purpose:** Visualize vesting schedule with unlock dates

**API:**
```clojure
(c/vesting-timeline
  {:wallet-address "pb1..."
   :schedule [{:date "2025-01-01" :unlocked-amount 1000000 :total-amount 10000000}
              {:date "2025-02-01" :unlocked-amount 2000000 :total-amount 10000000}]
   :highlight-next-unlock? true
   :show-coverage-ratio? true})
```

**Visualization:**
- Area chart showing cumulative unlocked amount
- Line showing total vesting amount
- Markers at unlock dates
- Shaded region for locked amount
- Coverage ratio gauge

#### 2. Price Trend Comparison

**Purpose:** Compare price trends across multiple assets

**API:**
```clojure
(c/price-trend-comparison
  {:assets ["HASH-USD" "BTC-USD" "ETH-USD"]
   :timeframe "7d"
   :normalize? true  ; Show as % change
   :show-correlation? true})
```

#### 3. Wallet Health Scorecard

**Purpose:** Comprehensive wallet health assessment

**API:**
```clojure
(c/wallet-health-scorecard
  {:wallet-address "pb1..."
   :metrics {:aum {:value 100057 :trend :up :health :good}
             :diversification {:value 0.85 :health :excellent}
             :vesting-coverage {:value 1.2 :health :good}
             :staking-efficiency {:value 0.92 :health :excellent}}})
```

**Visualization:**
- Gauge chart for each metric
- Color-coded health indicators
- Trend arrows
- Composite score

#### 4. Staking Performance Dashboard

**Purpose:** Detailed staking analysis

**API:**
```clojure
(c/staking-performance
  {:wallet-address "pb1..."
   :delegation-data [...]
   :show-validator-details? true
   :highlight-underperformers? true})
```

**Components:**
- Delegation distribution pie chart
- Validator performance table (uptime, commission, rewards)
- Reward accumulation timeline
- Redelgation suggestions

---

## 🎨 Design Principles

### 1. Data-First Design

**Principle:** Components accept raw data structures, handle formatting internally

**Good:**
```clojure
(c/metric-card {:label "HASH Price" :value 0.028 :format :usd})
;; Component handles formatting to "$0.03"
```

**Avoid:**
```clojure
(c/metric-card {:label "HASH Price" :value "$0.03"})
;; Pre-formatted strings limit reusability
```

### 2. Sensible Defaults

**Principle:** Components work with minimal configuration, allow customization

**Example:**
```clojure
;; Minimal - uses defaults
(c/pie-chart {:data [{:label "A" :value 100}]})

;; Customized
(c/pie-chart {:data [...]
              :colors [:custom-1 :custom-2]
              :width 1000
              :show-legend? false})
```

### 3. Composability

**Principle:** Complex visualizations built from simple components

**Example:**
```clojure
(defn portfolio-summary [wallets]
  (kind/hiccup
   [:div
    (c/metric-grid (wallet-metrics wallets))
    (c/pie-chart (wallet-distribution wallets))
    (c/data-table (wallet-breakdown wallets))]))
```

### 4. Type Safety

**Principle:** Clear data contracts, validate inputs

**Implementation:**
```clojure
(s/def ::metric-card-config
  (s/keys :req-un [::label ::value]
          :opt-un [::subtitle ::color-scheme ::trend]))

(defn metric-card [config]
  (when-not (s/valid? ::metric-card-config config)
    (throw (ex-info "Invalid config" (s/explain-data ::metric-card-config config))))
  ...)
```

### 5. Progressive Enhancement

**Principle:** Basic version works, advanced features optional

**Example:**
```clojure
;; Basic vesting timeline
(c/vesting-timeline {:schedule vesting-data})

;; Enhanced with predictions
(c/vesting-timeline {:schedule vesting-data
                     :show-projections? true
                     :projection-scenarios [:conservative :expected :optimistic]})
```

### 6. Accessibility

**Principle:** Visualizations should be clear and informative

**Guidelines:**
- Use colorblind-friendly palettes
- Include text labels on charts
- Provide data tables as alternatives to charts
- Use semantic HTML in Hiccup
- Clear visual hierarchy

---

## 📝 Implementation Guidelines

### Component File Structure

```clojure
(ns components
  "Reusable visualization components for Clay/Noj notebooks

  Design principles:
  - Data-first: Accept raw data, format internally
  - Sensible defaults: Work with minimal config
  - Composable: Build complex from simple
  - Type-safe: Clear data contracts"
  (:require [scicloj.kindly.v4.kind :as kind]
            [clojure.spec.alpha :as s]))

;; ============================================================================
;; Theme Configuration
;; ============================================================================

(def color-schemes ...)

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn format-usd [amount] ...)
(defn format-hash [amount] ...)

;; ============================================================================
;; Core Components
;; ============================================================================

;; Metric Card
;; -----------
(s/def ::metric-card-config ...)

(defn metric-card
  "Display a single metric prominently with gradient background.

  Args:
    config - Map with :label, :value, optional :subtitle, :color-scheme

  Returns:
    Kindly hiccup visualization

  Example:
    (metric-card {:label \"HASH Price\"
                  :value 0.028
                  :format :usd
                  :color-scheme :green-gradient})"
  [config]
  ...)

;; Pie Chart
;; ---------
(defn pie-chart [config] ...)

;; ============================================================================
;; Domain-Specific Components
;; ============================================================================

;; Vesting Timeline
;; ----------------
(defn vesting-timeline [config] ...)
```

### Testing Strategy

```clojure
(ns components-test
  (:require [components :as c]
            [clojure.test :refer [deftest testing is]]))

(deftest metric-card-test
  (testing "Basic metric card"
    (let [result (c/metric-card {:label "Test" :value 100})]
      (is (kind/kind? result :kind/hiccup))))

  (testing "Invalid config throws"
    (is (thrown? Exception (c/metric-card {})))))
```

### Documentation Standards

Each component should have:
1. **Docstring** - Purpose, args, return value, example
2. **Spec** - Data contract for configuration
3. **Example** - Working code snippet
4. **Screenshot** - Visual example in component library doc

---

## 🚀 Migration Path

### Phase 1: Extract Current Patterns (Week 1)

1. Review `notebooks/index.clj`
2. Identify reusable patterns
3. Extract to `notebooks/components.clj`
4. Update `index.clj` to use components
5. Verify rendering unchanged

### Phase 2: Generalize Components (Week 2)

1. Make components more flexible
2. Add configuration options
3. Create variations for common use cases
4. Add specs and validation
5. Document in `clay-component-library.md`

### Phase 3: Build Templates (Week 3)

1. Create template notebooks
2. Test with real data
3. Document usage patterns
4. Build example gallery

### Phase 4: Configuration System (Week 4)

1. Design config schema
2. Implement renderer
3. Convert existing notebooks to config
4. Create config editor/validator

---

## 💡 Future Enhancements

### Interactive Components

**Idea:** Add interactivity to visualizations

**Example:**
```clojure
(c/interactive-price-chart
  {:data price-data
   :controls [:timeframe :indicators :overlays]
   :on-range-select (fn [start end] ...)})
```

**Challenges:**
- Clay/Noj static rendering model
- State management
- Event handling

**Possible Solutions:**
- Generate interactive Plotly configs
- Use Reagent/Re-frame for stateful components
- Server-side rendering with user input

### Component Marketplace

**Idea:** Sharable component library across projects

**Implementation:**
- Git repository of components
- Package manager integration (Clojars)
- Version management
- Community contributions

### AI-Assisted Dashboard Generation

**Idea:** Natural language to dashboard

**Example:**
```
User: "Show me my vesting schedule for the next 6 months with coverage ratios"

AI: Generates notebook with:
- Vesting timeline component
- Coverage ratio gauge
- Unlock date calendar
- Action items for upcoming unlocks
```

### Real-Time Updates

**Idea:** Live-updating dashboards

**Implementation:**
- WebSocket connection to data sources
- Auto-refresh on data changes
- Streaming data support

---

## 📚 References

- [Clay Documentation](https://scicloj.github.io/clay)
- [Kindly Specification](https://scicloj.github.io/kindly)
- [Plotly Documentation](https://plotly.com/javascript/)
- [Hiccup Guide](https://github.com/weavejester/hiccup)

---

*Document Status: Draft - Pending Implementation*
*Last Updated: 2025-11-16*
*Authors: Claude Code, User*
