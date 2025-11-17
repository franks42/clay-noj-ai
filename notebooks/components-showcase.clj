(ns components-showcase
  "Showcase of all components from the component library.

   This notebook demonstrates every component with real data examples,
   showing the full capabilities of the component library built on calc.clj."
  (:require [scicloj.kindly.v4.kind :as kind]
            [components :as c]
            [calc]))

;; =============================================================================
;; Header
;; =============================================================================

(kind/md "# Component Library Showcase

This notebook demonstrates all 9 components from the Component Library Design System.

**Built with:**
- components.clj (Tier 1 - Component Library)
- calc.clj (Tier 0 - Financial Calculations)
- Clay/Noj (Live Documentation)

Each component is data-driven, accepting configuration maps and returning
Kindly-compatible visualizations.")

;; =============================================================================
;; Component 1: Metric Cards
;; =============================================================================

(kind/md "## 1. Metric Cards

Single-value metric displays with gradient backgrounds, customizable colors, and
optional formatting. Supports different styles (gradient, solid, outline) and sizes.")

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(3, 1fr)"
                :gap "20px"
                :margin "20px 0"}}

  (c/metric-card {:title "HASH Price"
                  :value [0.028 :usd]
                  :label "USD per HASH"
                  :color :green
                  :style :gradient})

  (c/metric-card {:title "Total AUM"
                  :value [177475 :usd]
                  :label "Total Assets Under Management"
                  :color :blue
                  :style :gradient})

  (c/metric-card {:title "Total HASH"
                  :value [29037044 :hash]
                  :label "All Wallets Combined"
                  :color :orange
                  :style :solid})])

(kind/md "### Different Styles")

^:kindly/hide-code
(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(3, 1fr)"
                :gap "20px"
                :margin "20px 0"}}

  (c/metric-card {:title "Gradient Style"
                  :value [12345 :usd]
                  :label "Default style"
                  :color :purple
                  :style :gradient})

  (c/metric-card {:title "Solid Style"
                  :value [12345 :usd]
                  :label "Flat background"
                  :color :purple
                  :style :solid})

  (c/metric-card {:title "Outline Style"
                  :value [12345 :usd]
                  :label "Bordered with white bg"
                  :color :purple
                  :style :outline})])

;; =============================================================================
;; Component 2: Portfolio Summary Card
;; =============================================================================

(kind/md "## 2. Portfolio Summary Card

Multi-column summary display with gradient background, perfect for dashboard headers
showing key portfolio metrics at a glance.")

^:kindly/hide-code
(c/portfolio-summary-card
 {:title "💎 Total Portfolio Summary"
  :metrics [{:label "Total AUM"
             :value [177475 :usd]
             :sublabel "All Wallets"}
            {:label "HASH Price"
             :value [0.028 :usd]
             :sublabel "Current Market"}
            {:label "Total HASH"
             :value [29037044 :hash]
             :sublabel "Combined Holdings"}
            {:label "Wallets"
             :value 6
             :sublabel "Active"}]
  :color :purple
  :columns 4})

(kind/md "### Three-Column Variant")

^:kindly/hide-code
(c/portfolio-summary-card
 {:title "🎯 Staking Summary"
  :metrics [{:label "Staked HASH"
             :value [17500000 :hash]}
            {:label "Pending Rewards"
             :value [20730 :hash]}
            {:label "Est. APY"
             :value "68.7%"}]
  :color :green
  :columns 3
  :style :solid})

;; =============================================================================
;; Component 3: Data Table
;; =============================================================================

(kind/md "## 3. Data Table

Formatted tables with automatic type handling (tokens, numbers, percentages),
striped rows, and proper alignment. Built-in formatters use calc.clj for consistency.")

^:kindly/hide-code
(c/data-table
 {:columns [{:key :wallet
             :label "Wallet"
             :align :left}
            {:key :aum
             :label "AUM (USD)"
             :align :right
             :format :token}
            {:key :staked
             :label "Staked HASH"
             :align :right
             :format :number}
            {:key :pending
             :label "Pending Rewards"
             :align :right
             :format :number}
            {:key :type
             :label "Account Type"
             :align :center}]
  :rows [{:wallet "cold trust"
          :aum [801 :usd]
          :staked 0
          :pending 0
          :type "✅ Liquid"}
         {:wallet "trading validator"
          :aum [100 :usd]
          :staked 17500000
          :pending 20730
          :type "🔒 Vesting"}
         {:wallet "trading 2"
          :aum [29 :usd]
          :staked 0
          :pending 0
          :type "✅ Liquid"}
         {:wallet "keplr 1"
          :aum [49 :usd]
          :staked 0
          :pending 0
          :type "✅ Liquid"}
         {:wallet "keplr 2"
          :aum [6 :usd]
          :staked 0
          :pending 0
          :type "✅ Liquid"}
         {:wallet "keplr 3"
          :aum [1 :usd]
          :staked 0
          :pending 0
          :type "✅ Liquid"}]
  :striped? true
  :hover? true})

;; =============================================================================
;; Component 4: Insight Cards
;; =============================================================================

(kind/md "## 4. Insight Cards

Semantic message cards for displaying insights, warnings, recommendations, and status
messages. Five semantic types with appropriate colors and icons.")

^:kindly/hide-code
(c/insight-card
 {:type :info
  :title "Staking Optimization"
  :message "Pending rewards of 20,730 HASH should be claimed within the next 7 days to maintain optimal APY. Current estimated APY is 68.7% based on 7-day observation period."
  :action "Claim rewards now"})

^:kindly/hide-code
(c/insight-card
 {:type :success
  :title "Vesting Compliance Verified"
  :message "All vesting schedules are properly covered with 125% coverage ratio. The excess coverage of $250,000 provides additional safety margin for market volatility."})

^:kindly/hide-code
(c/insight-card
 {:type :warning
  :title "Market Volatility Detected"
  :message "HASH price has shown 15% fluctuation over the past 30 days. Consider reviewing portfolio rebalancing strategy and risk exposure."
  :action "Review portfolio allocation"})

^:kindly/hide-code
(c/insight-card
 {:type :premium
  :title "Staking Performance Analysis"
  :message "Your validator is in the top 10% for performance. Current staking value of $812 USD including pending rewards represents strong returns."})

^:kindly/hide-code
(c/insight-card
 {:type :danger
  :title "Critical Action Required"
  :message "Vesting schedule deadline approaching in 3 days. Ensure sufficient liquid assets are available to maintain compliance."
  :action "Review vesting requirements"})

;; =============================================================================
;; Component 5: Action Items List
;; =============================================================================

(kind/md "## 5. Action Items List

Numbered list of recommended actions with formatted descriptions. Perfect for
dashboards that suggest next steps based on portfolio analysis.")

^:kindly/hide-code
(c/action-items-list
 {:title "🎯 Recommended Actions"
  :items ["Claim Rewards: Harvest 20,730 HASH in pending staking rewards ($812 USD value) to optimize returns and compound earnings"
          "Review Vesting: Ensure 100% compliance with vesting schedules across all wallets, with particular attention to trading validator account"
          "Monitor Staking: Track validator performance metrics and consider re-delegation if APY drops below market average"
          "Diversify Holdings: Current portfolio is heavily concentrated in staking position (96% of total HASH). Consider diversification strategy"
          "Update Price Alerts: Set price alerts for HASH at key support ($0.025) and resistance ($0.032) levels"
          "Review Security: Conduct quarterly security audit of wallet access controls and multi-sig configurations"]
  :color :blue})

;; =============================================================================
;; Component 6: Progress Bar
;; =============================================================================

(kind/md "## 6. Progress Bar

Visual progress indicator with optional detail breakdown. Supports over 100% values
for ratios and coverage metrics.")

(kind/md "### Vesting Coverage (Over 100%)")

^:kindly/hide-code
(c/progress-bar
 {:title "Vesting Coverage Ratio"
  :value 1250000
  :max 1000000
  :color :green
  :details [{:label "Covered Assets" :value [1250000 :usd]}
            {:label "Required Coverage" :value [1000000 :usd]}
            {:label "Excess Coverage" :value [250000 :usd] :highlight? true}]})

(kind/md "### Staking Progress (Under 100%)")

^:kindly/hide-code
(c/progress-bar
 {:title "Staking Goal Progress"
  :value 17500000
  :max 25000000
  :color :blue
  :details [{:label "Current Staked" :value [17500000 :hash]}
            {:label "Target Amount" :value [25000000 :hash]}
            {:label "Remaining" :value [7500000 :hash] :highlight? false}]
  :style :solid})

;; =============================================================================
;; Component 7: Donut Chart (Plotly)
;; =============================================================================

(kind/md "## 7. Donut Chart

Interactive Plotly donut chart for portfolio breakdown and distribution analysis.
Automatic color assignment from design system.")

^:kindly/hide-code
(c/donut-chart
 {:title "Portfolio Distribution by Wallet (USD)"
  :data [{:label "cold trust" :value 801}
         {:label "trading validator" :value 100}
         {:label "trading 2" :value 29}
         {:label "keplr 1" :value 49}
         {:label "keplr 2" :value 6}
         {:label "keplr 3" :value 1}]
  :colors [:green :blue :orange :purple :red :neutral]})

(kind/md "### Asset Type Distribution")

^:kindly/hide-code
(c/donut-chart
 {:title "Asset Distribution by Type"
  :data [{:label "Staked HASH" :value 17500000}
         {:label "Liquid HASH" :value 29037044}
         {:label "Pending Rewards" :value 20730}]
  :colors [:blue :green :orange]
  :height 350})

;; =============================================================================
;; Component 8: Grouped Bar Chart (Plotly)
;; =============================================================================

(kind/md "## 8. Grouped Bar Chart

Interactive Plotly bar chart for comparing metrics across categories.
Supports multiple series with custom colors.")

^:kindly/hide-code
(c/grouped-bar-chart
 {:title "Wallet Assets Comparison"
  :categories ["cold trust" "trading validator" "trading 2" "keplr 1" "keplr 2" "keplr 3"]
  :series [{:name "AUM (USD)"
            :values [801 100 29 49 6 1]
            :color :green}
           {:name "Staked HASH (Millions)"
            :values [0 17.5 0 0 0 0]
            :color :blue}]
  :y-axis-label "Amount"})

(kind/md "### Performance Metrics")

^:kindly/hide-code
(c/grouped-bar-chart
 {:title "Validator Performance Metrics"
  :categories ["Q1 2025" "Q2 2025" "Q3 2025" "Q4 2025"]
  :series [{:name "Rewards Earned (HASH)"
            :values [5000 8000 12000 20730]
            :color :green}
           {:name "Commission (HASH)"
            :values [500 800 1200 2073]
            :color :orange}]
  :y-axis-label "HASH Amount"
  :height 350})

;; =============================================================================
;; Component 9: Time Series Line Chart (Plotly)
;; =============================================================================

(kind/md "## 9. Time Series Line Chart

Interactive time series visualization for tracking metrics over time.
Supports multiple series with markers and hover details.")

^:kindly/hide-code
(c/time-series-chart
 {:title "HASH Price History (90 Days)"
  :series [{:name "Price (USD)"
            :x ["2024-10-16" "2024-10-23" "2024-10-30" "2024-11-06"
                "2024-11-13" "2024-11-20" "2024-11-27" "2024-12-04"
                "2024-12-11" "2024-12-18" "2024-12-25" "2025-01-01"
                "2025-01-08" "2025-01-15"]
            :y [0.025 0.0245 0.026 0.0255 0.027 0.0265 0.028 0.0275
                0.029 0.0285 0.027 0.0265 0.0275 0.028]
            :color :green}]
  :y-axis-label "Price (USD)"
  :x-axis-label "Date"})

(kind/md "### Portfolio Value Over Time")

^:kindly/hide-code
(c/time-series-chart
 {:title "Portfolio Value Tracking (6 Months)"
  :series [{:name "Total AUM (USD)"
            :x ["2024-07-01" "2024-08-01" "2024-09-01" "2024-10-01"
                "2024-11-01" "2024-12-01" "2025-01-01"]
            :y [150000 155000 160000 165000 170000 175000 177475]
            :color :blue}
           {:name "Staking Value (USD)"
            :x ["2024-07-01" "2024-08-01" "2024-09-01" "2024-10-01"
                "2024-11-01" "2024-12-01" "2025-01-01"]
            :y [400 450 500 600 700 800 812]
            :color :green}]
  :y-axis-label "Value (USD)"
  :x-axis-label "Month"
  :height 350})

;; =============================================================================
;; Summary
;; =============================================================================

(kind/md "## Summary

This showcase demonstrates all 9 components from the component library:

1. **Metric Card** - Single value displays with gradient/solid/outline styles
2. **Portfolio Summary Card** - Multi-column dashboard headers
3. **Data Table** - Formatted tables with type-aware rendering
4. **Insight Card** - Semantic message cards (info, success, warning, danger, premium)
5. **Action Items List** - Numbered recommendation lists
6. **Progress Bar** - Visual progress with detail breakdown
7. **Donut Chart** - Interactive Plotly donut charts
8. **Grouped Bar Chart** - Multi-series bar comparisons
9. **Time Series Chart** - Historical trend visualization

### Usage in Your Dashboards

```clojure
(require '[components :as c])

;; Simple metric
(c/metric-card {:title \"HASH Price\"
                :value [0.028 :usd]
                :color :green})

;; Data table
(c/data-table {:columns [...] :rows [...]})

;; Chart
(c/donut-chart {:title \"Distribution\" :data [...]})
```

### Integration with calc.clj

All components use calc.clj for:
- Token amount formatting (calc/format)
- Number formatting with commas (calc/with-commas)
- Percentage formatting (calc/format-percentage)
- Type-safe financial calculations

### Design System

Components follow the centralized design system:
- **Colors**: Primary (green, blue, orange, purple, red) + Semantic
- **Typography**: Display, Title, Heading, Body, Small, Tiny
- **Spacing**: xs, sm, md, lg, xl
- **Gradients**: Pre-defined for all primary colors")

(kind/md "---

*Components Library v1.0*
*Built with Clay/Noj + calc.clj*
*Generated: 2025-11-16*")
