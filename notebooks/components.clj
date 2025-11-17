(ns components
  "Reusable component library for Clay/Noj dashboards.

   This library provides data-driven, configurable UI components that follow
   the Component Library Design System specification. All components accept
   data as input and return Kindly-compatible visualization structures.

   Built on top of calc.clj for type-safe financial calculations and formatting.

   Usage:
     (require '[components :as c])
     (c/metric-card {:title \"HASH Price\"
                     :value [0.028 :usd]
                     :label \"USD per HASH\"
                     :color :green})

   Design System:
   - Primary Colors: green, blue, orange, purple, red
   - Semantic Colors: success, info, warning, danger, neutral
   - Typography Scale: display, title, heading, body, small, tiny
   - Spacing System: xs, sm, md, lg, xl"
  (:require [scicloj.kindly.v4.kind :as kind]
            [calc]))

;; =============================================================================
;; Design System Tokens
;; =============================================================================

(def ^:private design-tokens
  "Centralized design system tokens for colors, typography, and spacing."
  {:colors {:primary {:green "#10b981"
                      :blue "#3b82f6"
                      :orange "#f59e0b"
                      :purple "#8b5cf6"
                      :red "#ef4444"}
            :semantic {:success "#10b981"
                       :info "#3b82f6"
                       :warning "#fbbf24"
                       :danger "#ef4444"
                       :neutral "#6b7280"}
            :background {:light "#f9fafb"
                         :white "#ffffff"
                         :dark "#111827"}
            :text {:primary "#111827"
                   :secondary "#6b7280"
                   :white "#ffffff"}}

   :typography {:display "2.5em"
                :title "1.8em"
                :heading "1.2em"
                :body "1em"
                :small "0.9em"
                :tiny "0.85em"}

   :spacing {:xs "5px"
             :sm "10px"
             :md "20px"
             :lg "30px"
             :xl "40px"}

   :gradients {:green "linear-gradient(135deg, #10b981 0%, #059669 100%)"
               :blue "linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)"
               :orange "linear-gradient(135deg, #f59e0b 0%, #d97706 100%)"
               :purple "linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%)"
               :red "linear-gradient(135deg, #ef4444 0%, #dc2626 100%)"}})

(defn- get-color
  "Resolve a color from the design system.
   Accepts: keyword (:green, :success), hex string, or path [:primary :green]"
  [color-spec]
  (cond
    ;; Direct hex color
    (and (string? color-spec) (clojure.string/starts-with? color-spec "#"))
    color-spec

    ;; Keyword - try primary first, then semantic
    (keyword? color-spec)
    (or (get-in design-tokens [:colors :primary color-spec])
        (get-in design-tokens [:colors :semantic color-spec])
        (get-in design-tokens [:colors :background color-spec])
        (get-in design-tokens [:colors :text color-spec])
        "#6b7280") ; fallback to neutral

    ;; Path vector
    (vector? color-spec)
    (get-in design-tokens (into [:colors] color-spec))

    ;; Fallback
    :else "#6b7280"))

(defn- get-gradient
  "Get a gradient pattern by color name."
  [color-keyword]
  (get-in design-tokens [:gradients color-keyword]
          (get-in design-tokens [:gradients :blue]))) ; fallback

(defn- get-typography
  "Get typography size by scale name."
  [scale]
  (get-in design-tokens [:typography scale] "1em"))

(defn- get-spacing
  "Get spacing value by size name."
  [size]
  (get-in design-tokens [:spacing size] "20px"))

;; =============================================================================
;; Component 1: Metric Card
;; =============================================================================

(defn metric-card
  "Display a single metric with optional formatting.

   Required:
   - :title - String label for the metric
   - :value - Token amount [number unit] or plain number

   Optional:
   - :label - Secondary label (default: empty)
   - :color - Color keyword or hex (default: :green)
   - :style - :gradient (default), :solid, or :outline
   - :size - :small, :medium (default), :large
   - :decimals - Number of decimals for formatting
   - :text-align - :left, :center (default), :right
   - :show-icon? - Show icon (default: false)
   - :icon - Icon string (default: 💰)

   Example:
     (metric-card {:title \"HASH Price\"
                   :value [0.028 :usd]
                   :label \"USD per HASH\"
                   :color :green
                   :style :gradient})"
  [{:keys [title value label color style size decimals text-align show-icon? icon]
    :or {color :green
         style :gradient
         size :medium
         text-align :center
         show-icon? false
         icon "💰"}}]

  (let [;; Resolve color
        resolved-color (get-color color)

        ;; Format value using calc if it's a token amount
        formatted-value (if (and (vector? value) (= 2 (count value)))
                          (calc/format value (when decimals {:decimals decimals}))
                          (if decimals
                            (calc/with-commas (calc/round-to value decimals))
                            (calc/with-commas value)))

        ;; Size-based styling
        sizes {:small {:padding "20px" :title-size "0.8em" :value-size "1.8em"}
               :medium {:padding "30px" :title-size "0.9em" :value-size "2.5em"}
               :large {:padding "40px" :title-size "1em" :value-size "3em"}}
        size-style (get sizes size (:medium sizes))

        ;; Style-based background
        background (case style
                     :gradient (get-gradient color)
                     :solid resolved-color
                     :outline "#ffffff")

        ;; Text color based on style
        text-color (if (= style :outline) resolved-color "#ffffff")
        border (when (= style :outline) (str "2px solid " resolved-color))

        ;; Alignment
        alignment (name text-align)]

    (kind/hiccup
     [:div {:style {:background background
                    :padding (:padding size-style)
                    :border-radius "15px"
                    :color text-color
                    :text-align alignment
                    :box-shadow "0 4px 6px rgba(0,0,0,0.1)"
                    :border border}}
      (when show-icon?
        [:div {:style {:font-size "2em" :margin-bottom "10px"}} icon])
      [:div {:style {:font-size (:title-size size-style)
                     :opacity (if (= style :outline) "1" "0.9")}}
       title]
      [:div {:style {:font-size (:value-size size-style)
                     :font-weight "bold"
                     :margin "10px 0"}}
       formatted-value]
      (when label
        [:div {:style {:font-size (get-typography :tiny)
                       :opacity (if (= style :outline) "0.7" "0.8")}}
         label])])))

;; =============================================================================
;; Component 2: Portfolio Summary Card
;; =============================================================================

(defn portfolio-summary-card
  "Display a multi-column summary card with metrics.

   Required:
   - :title - Card title
   - :metrics - Vector of metric maps [{:label \"...\" :value [...]}]

   Optional:
   - :color - Background color (default: :purple)
   - :columns - Number of columns (default: 4)
   - :style - :gradient (default) or :solid

   Metric format:
   {:label \"Total AUM\"
    :value [177475 :usd]     ; Token amount
    :sublabel \"USD\"}         ; Optional

   Example:
     (portfolio-summary-card
       {:title \"💎 Total Portfolio Summary\"
        :metrics [{:label \"Total AUM\" :value [177475 :usd]}
                  {:label \"HASH Price\" :value [0.028 :usd]}
                  {:label \"Total HASH\" :value [29037044 :hash]}
                  {:label \"Wallets\" :value 6}]
        :color :purple})"
  [{:keys [title metrics color columns style]
    :or {color :purple
         columns 4
         style :gradient}}]

  (let [background (if (= style :gradient)
                     (get-gradient color)
                     (get-color color))]
    (kind/hiccup
     [:div {:style {:background background
                    :padding "30px"
                    :border-radius "15px"
                    :color "#ffffff"
                    :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
      [:h3 {:style {:margin "0 0 20px 0"
                    :font-size (get-typography :title)}}
       title]
      [:div {:style {:display "grid"
                     :grid-template-columns (str "repeat(" columns ", 1fr)")
                     :gap "20px"}}
       (for [{:keys [label value sublabel]} metrics]
         (let [formatted (if (vector? value)
                           (calc/format value)
                           (if (number? value)
                             (calc/with-commas value)
                             (str value)))]
           [:div {:style {:text-align "center"}}
            [:div {:style {:font-size (get-typography :small)
                           :opacity "0.9"
                           :margin-bottom "5px"}}
             label]
            [:div {:style {:font-size (get-typography :heading)
                           :font-weight "bold"}}
             formatted]
            (when sublabel
              [:div {:style {:font-size (get-typography :tiny)
                             :opacity "0.8"
                             :margin-top "5px"}}
               sublabel])]))]])))

;; =============================================================================
;; Component 3: Data Table
;; =============================================================================

(defn data-table
  "Render a data table with proper formatting.

   Required:
   - :columns - Vector of column specs [{:key :wallet :label \"Wallet\" :align :left}]
   - :rows - Vector of data maps

   Optional:
   - :striped? - Alternate row colors (default: true)
   - :hover? - Highlight on hover (default: true)
   - :compact? - Smaller padding (default: false)

   Column spec:
   {:key :wallet          ; Key in row map
    :label \"Wallet\"       ; Display label
    :align :left          ; :left (default), :center, :right
    :format :token}       ; :token, :number, :percentage, or custom fn

   Example:
     (data-table
       {:columns [{:key :wallet :label \"Wallet\" :align :left}
                  {:key :aum :label \"AUM (USD)\" :align :right :format :token}
                  {:key :staked :label \"Staked\" :align :right :format :number}]
        :rows [{:wallet \"cold trust\" :aum [801 :usd] :staked 0}
               {:wallet \"trading validator\" :aum [100 :usd] :staked 17500000}]
        :striped? true})"
  [{:keys [columns rows striped? hover? compact?]
    :or {striped? true
         hover? true
         compact? false}}]

  (let [padding (if compact? "8px" "12px")

        format-cell (fn [value format-type]
                      (cond
                        ;; Custom formatter function
                        (fn? format-type)
                        (format-type value)

                        ;; Token amount
                        (= format-type :token)
                        (if (vector? value)
                          (calc/format value)
                          (str value))

                        ;; Number with commas
                        (= format-type :number)
                        (if (number? value)
                          (calc/with-commas value)
                          (str value))

                        ;; Percentage
                        (= format-type :percentage)
                        (if (number? value)
                          (calc/format-percentage value)
                          (str value))

                        ;; Default - just stringify
                        :else (str value)))]

    (kind/hiccup
     [:table {:style {:width "100%"
                      :border-collapse "collapse"
                      :background "#ffffff"
                      :border-radius "8px"
                      :overflow "hidden"
                      :box-shadow "0 1px 3px rgba(0,0,0,0.1)"}}
      [:thead
       [:tr {:style {:background (get-color [:background :light])
                     :border-bottom "2px solid #e5e7eb"}}
        (for [{:keys [label align]} columns]
          [:th {:style {:padding padding
                        :text-align (name (or align :left))
                        :font-weight "600"
                        :color (get-color [:text :primary])}}
           label])]]
      [:tbody
       (map-indexed
        (fn [idx row]
          [:tr {:style (merge
                        {:border-bottom "1px solid #e5e7eb"}
                        (when striped?
                          (if (even? idx)
                            {:background "#ffffff"}
                            {:background "#f9fafb"}))
                        (when hover?
                          {:cursor "pointer"}))}
           (for [{:keys [key align format]} columns]
             [:td {:style {:padding padding
                           :text-align (name (or align :left))
                           :color (get-color [:text :primary])}}
              (format-cell (get row key) format)])])
        rows)]])))

;; =============================================================================
;; Component 4: Insight Card
;; =============================================================================

(defn insight-card
  "Display an insight or recommendation with semantic coloring.

   Required:
   - :type - :info, :success, :warning, :danger, :premium
   - :title - Card title
   - :message - Main message text

   Optional:
   - :icon - Emoji icon (default based on type)
   - :action - Optional action text

   Example:
     (insight-card
       {:type :info
        :title \"Staking Optimization\"
        :message \"Pending rewards should be claimed...\"
        :action \"Claim rewards now\"})"
  [{:keys [type title message icon action]}]

  (let [;; Type-based configuration
        config {:info {:bg "#dbeafe"
                       :border "#3b82f6"
                       :title-color "#1e40af"
                       :text-color "#1e3a8a"
                       :icon (or icon "💡")}
                :success {:bg "#d1fae5"
                          :border "#10b981"
                          :title-color "#065f46"
                          :text-color "#064e3b"
                          :icon (or icon "✅")}
                :warning {:bg "#fef3c7"
                          :border "#f59e0b"
                          :title-color "#92400e"
                          :text-color "#78350f"
                          :icon (or icon "⚠️")}
                :danger {:bg "#fee2e2"
                         :border "#ef4444"
                         :title-color "#991b1b"
                         :text-color "#7f1d1d"
                         :icon (or icon "🔴")}
                :premium {:bg "#f3e8ff"
                          :border "#8b5cf6"
                          :title-color "#5b21b6"
                          :text-color "#4c1d95"
                          :icon (or icon "💎")}}

        {:keys [bg border title-color text-color icon]} (get config type (:info config))]

    (kind/hiccup
     [:div {:style {:background bg
                    :padding "20px"
                    :border-radius "8px"
                    :border-left (str "4px solid " border)
                    :margin "10px 0"
                    :box-shadow "0 1px 3px rgba(0,0,0,0.1)"}}
      [:h4 {:style {:margin "0 0 10px 0"
                    :color title-color
                    :font-size (get-typography :heading)}}
       (str icon " " title)]
      [:p {:style {:margin (if action "0 0 10px 0" "0")
                   :color text-color
                   :line-height "1.6"}}
       message]
      (when action
        [:div {:style {:margin-top "10px"}}
         [:a {:href "#"
              :style {:color border
                      :text-decoration "none"
                      :font-weight "600"
                      :font-size (get-typography :small)}}
          (str "→ " action)]])])))

;; =============================================================================
;; Component 5: Action Items List
;; =============================================================================

(defn action-items-list
  "Display a numbered list of recommended actions.

   Required:
   - :title - List title
   - :items - Vector of action strings

   Optional:
   - :color - Border/number color (default: :blue)
   - :numbered? - Show numbers (default: true)

   Example:
     (action-items-list
       {:title \"Recommended Actions\"
        :items [\"Claim Rewards: Harvest 20,730 HASH...\"
                \"Review Vesting: Ensure compliance...\"
                \"Monitor Staking: Track validator performance...\"]})"
  [{:keys [title items color numbered?]
    :or {color :blue
         numbered? true}}]

  (let [border-color (get-color color)]
    (kind/hiccup
     [:div {:style {:background "#ffffff"
                    :padding "25px"
                    :border-radius "8px"
                    :border-left (str "4px solid " border-color)
                    :box-shadow "0 1px 3px rgba(0,0,0,0.1)"}}
      [:h4 {:style {:margin "0 0 15px 0"
                    :color (get-color [:text :primary])
                    :font-size (get-typography :heading)}}
       title]
      [:ol {:style {:margin "0"
                    :padding-left (if numbered? "20px" "0")
                    :list-style-type (if numbered? "decimal" "none")}}
       (for [item items]
         [:li {:style {:margin "10px 0"
                       :color (get-color [:text :secondary])
                       :line-height "1.6"}}
          [:span {:style {:color (get-color [:text :primary])}}
           ;; Extract bold action from description
           (let [[action & rest-text] (clojure.string/split item #":" 2)]
             (if (seq rest-text)
               [:span [:strong (str action ":")] (first rest-text)]
               item))]])]])))

;; =============================================================================
;; Component 6: Progress Bar
;; =============================================================================

(defn progress-bar
  "Display a progress bar with optional details.

   Required:
   - :title - Bar title
   - :value - Current value (number)
   - :max - Maximum value (number)

   Optional:
   - :color - Bar color (default: :green)
   - :show-percentage? - Show percentage label (default: true)
   - :details - Vector of detail maps [{:label \"...\" :value [...]}]
   - :style - :gradient (default) or :solid
   - :height - Bar height in px (default: 30)

   Example:
     (progress-bar
       {:title \"Vesting Coverage Ratio\"
        :value 1250000
        :max 1000000
        :color :green
        :details [{:label \"Covered\" :value [1250000 :usd]}
                  {:label \"Required\" :value [1000000 :usd]}
                  {:label \"Excess\" :value [250000 :usd] :highlight? true}]})"
  [{:keys [title value max color show-percentage? details style height]
    :or {color :green
         show-percentage? true
         style :gradient
         height 30}}]

  (let [percentage (min 100 (* (/ value max) 100))
        percentage-str (str (int percentage) "%")
        bar-color (if (= style :gradient)
                    (get-gradient color)
                    (get-color color))]

    (kind/hiccup
     [:div {:style {:background "#ffffff"
                    :padding "20px"
                    :border-radius "8px"
                    :box-shadow "0 1px 3px rgba(0,0,0,0.1)"}}
      [:h4 {:style {:margin "0 0 15px 0"
                    :color (get-color [:text :primary])
                    :font-size (get-typography :heading)}}
       title]

      ;; Progress bar
      [:div {:style {:background "#e5e7eb"
                     :height (str height "px")
                     :border-radius "15px"
                     :overflow "hidden"
                     :position "relative"
                     :margin-bottom (if details "15px" "0")}}
       [:div {:style {:background bar-color
                      :height "100%"
                      :width percentage-str
                      :display "flex"
                      :align-items "center"
                      :justify-content "center"
                      :color "#ffffff"
                      :font-weight "bold"
                      :font-size (get-typography :small)
                      :transition "width 0.3s ease"}}
        (when show-percentage? percentage-str)]]

      ;; Details grid
      (when details
        [:div {:style {:display "grid"
                       :grid-template-columns (str "repeat(" (count details) ", 1fr)")
                       :gap "10px"
                       :margin-top "15px"}}
         (for [{:keys [label value highlight?]} details]
           (let [formatted (if (vector? value)
                             (calc/format value)
                             (calc/with-commas value))]
             [:div {:style {:text-align "center"}}
              [:div {:style {:font-size (get-typography :small)
                             :color (get-color [:text :secondary])
                             :margin-bottom "5px"}}
               label]
              [:div {:style {:font-weight "bold"
                             :color (if highlight?
                                      (get-color color)
                                      (get-color [:text :primary]))}}
               formatted]]))])])))

;; =============================================================================
;; Component 7: Donut Chart (Plotly)
;; =============================================================================

(defn donut-chart
  "Create a donut chart for portfolio breakdown.

   Required:
   - :title - Chart title
   - :data - Vector of data maps [{:label \"...\" :value number}]

   Optional:
   - :colors - Vector of color keywords (default: [:green :blue :orange :purple])
   - :show-legend? - Show legend (default: true)
   - :height - Chart height in px (default: 400)

   Example:
     (donut-chart
       {:title \"Portfolio Distribution by Wallet\"
        :data [{:label \"cold trust\" :value 801}
               {:label \"trading validator\" :value 100}
               {:label \"trading 2\" :value 29}
               {:label \"keplr 1\" :value 49}
               {:label \"keplr 2\" :value 6}
               {:label \"keplr 3\" :value 1}]
        :colors [:green :blue :orange :purple :red :neutral]})"
  [{:keys [title data colors show-legend? height]
    :or {colors [:green :blue :orange :purple :red :neutral]
         show-legend? true
         height 400}}]

  (let [labels (mapv :label data)
        values (mapv :value data)
        resolved-colors (mapv get-color colors)]

    (kind/plotly
     {:data [{:type "pie"
              :labels labels
              :values values
              :hole 0.4
              :marker {:colors resolved-colors}
              :textinfo "label+percent"
              :textposition "outside"
              :hovertemplate "%{label}<br>%{value:,.0f}<br>%{percent}<extra></extra>"}]
      :layout {:title {:text title
                       :font {:size 18}}
               :height height
               :showlegend show-legend?
               :legend {:orientation "v"
                        :x 1.1
                        :y 0.5}}})))

;; =============================================================================
;; Component 8: Grouped Bar Chart (Plotly)
;; =============================================================================

(defn grouped-bar-chart
  "Create a grouped bar chart for comparing metrics across categories.

   Required:
   - :title - Chart title
   - :categories - Vector of category labels
   - :series - Vector of series maps [{:name \"...\" :values [...] :color :green}]

   Optional:
   - :height - Chart height in px (default: 400)
   - :y-axis-label - Y-axis label

   Example:
     (grouped-bar-chart
       {:title \"Wallet Assets Comparison\"
        :categories [\"Wallet 1\" \"Wallet 2\" \"Wallet 3\"]
        :series [{:name \"AUM (USD)\" :values [801 100 29] :color :green}
                 {:name \"Staked HASH\" :values [0 17500000 0] :color :blue}]
        :y-axis-label \"Amount\"})"
  [{:keys [title categories series height y-axis-label]
    :or {height 400}}]

  (let [traces (for [{:keys [name values color]} series]
                 {:type "bar"
                  :name name
                  :x categories
                  :y values
                  :marker {:color (get-color (or color :blue))}})]

    (kind/plotly
     {:data traces
      :layout {:title {:text title
                       :font {:size 18}}
               :height height
               :barmode "group"
               :xaxis {:title ""}
               :yaxis {:title (or y-axis-label "")}
               :showlegend true
               :legend {:orientation "h"
                        :x 0.5
                        :xanchor "center"
                        :y -0.2}}})))

;; =============================================================================
;; Component 9: Time Series Line Chart (Plotly)
;; =============================================================================

(defn time-series-chart
  "Create a time series line chart.

   Required:
   - :title - Chart title
   - :series - Vector of series maps [{:name \"...\" :x [...] :y [...] :color :green}]

   Optional:
   - :height - Chart height in px (default: 400)
   - :x-axis-label - X-axis label (default: \"Time\")
   - :y-axis-label - Y-axis label
   - :show-legend? - Show legend (default: true)

   Example:
     (time-series-chart
       {:title \"HASH Price History\"
        :series [{:name \"Price (USD)\"
                  :x [\"2025-01-01\" \"2025-02-01\" \"2025-03-01\"]
                  :y [0.025 0.027 0.028]
                  :color :green}]
        :y-axis-label \"Price (USD)\"})"
  [{:keys [title series height x-axis-label y-axis-label show-legend?]
    :or {height 400
         x-axis-label "Time"
         show-legend? true}}]

  (let [traces (for [{:keys [name x y color]} series]
                 {:type "scatter"
                  :mode "lines+markers"
                  :name name
                  :x x
                  :y y
                  :line {:color (get-color (or color :blue))
                         :width 3}
                  :marker {:size 6}})]

    (kind/plotly
     {:data traces
      :layout {:title {:text title
                       :font {:size 18}}
               :height height
               :xaxis {:title x-axis-label}
               :yaxis {:title (or y-axis-label "")}
               :showlegend show-legend?
               :legend {:orientation "h"
                        :x 0.5
                        :xanchor "center"
                        :y -0.2}
               :hovermode "x unified"}})))

;; =============================================================================
;; Helper: Component Showcase
;; =============================================================================

(defn showcase-all
  "Generate a showcase of all components for testing and demonstration.
   Returns a vector of all component examples."
  []
  [(kind/md "# Component Library Showcase")
   (kind/md "All components from the design system, using real data examples.")

   (kind/md "## Metric Cards")
   (kind/hiccup
    [:div {:style {:display "grid"
                   :grid-template-columns "repeat(3, 1fr)"
                   :gap "20px"}}
     (metric-card {:title "HASH Price"
                   :value [0.028 :usd]
                   :label "USD per HASH"
                   :color :green})
     (metric-card {:title "Total AUM"
                   :value [177475 :usd]
                   :label "USD"
                   :color :blue})
     (metric-card {:title "Total HASH"
                   :value [29037044 :hash]
                   :label "HASH"
                   :color :orange
                   :style :solid})])

   (kind/md "## Portfolio Summary Card")
   (portfolio-summary-card
    {:title "💎 Total Portfolio Summary"
     :metrics [{:label "Total AUM" :value [177475 :usd]}
               {:label "HASH Price" :value [0.028 :usd]}
               {:label "Total HASH" :value [29037044 :hash]}
               {:label "Wallets" :value 6}]
     :color :purple})

   (kind/md "## Data Table")
   (data-table
    {:columns [{:key :wallet :label "Wallet" :align :left}
               {:key :aum :label "AUM (USD)" :align :right :format :token}
               {:key :staked :label "Staked HASH" :align :right :format :number}
               {:key :type :label "Type" :align :center}]
     :rows [{:wallet "cold trust" :aum [801 :usd] :staked 0 :type "✅ Liquid"}
            {:wallet "trading validator" :aum [100 :usd] :staked 17500000 :type "🔒 Vesting"}
            {:wallet "trading 2" :aum [29 :usd] :staked 0 :type "✅ Liquid"}
            {:wallet "keplr 1" :aum [49 :usd] :staked 0 :type "✅ Liquid"}
            {:wallet "keplr 2" :aum [6 :usd] :staked 0 :type "✅ Liquid"}
            {:wallet "keplr 3" :aum [1 :usd] :staked 0 :type "✅ Liquid"}]})

   (kind/md "## Insight Cards")
   (insight-card
    {:type :info
     :title "Staking Optimization"
     :message "Pending rewards should be claimed within the next 7 days to maintain optimal APY."})

   (insight-card
    {:type :success
     :title "Vesting Compliance"
     :message "All vesting schedules are properly covered. Excess coverage provides safety margin."})

   (insight-card
    {:type :warning
     :title "Market Volatility"
     :message "HASH price fluctuation detected. Consider reviewing portfolio rebalancing strategy."})

   (kind/md "## Action Items List")
   (action-items-list
    {:title "Recommended Actions"
     :items ["Claim Rewards: Harvest 20,730 HASH in pending staking rewards to optimize returns"
             "Review Vesting: Ensure 100% compliance with vesting schedules across all wallets"
             "Monitor Staking: Track validator performance and consider re-delegation if needed"]})

   (kind/md "## Progress Bar")
   (progress-bar
    {:title "Vesting Coverage Ratio"
     :value 1250000
     :max 1000000
     :color :green
     :details [{:label "Covered" :value [1250000 :usd]}
               {:label "Required" :value [1000000 :usd]}
               {:label "Excess" :value [250000 :usd] :highlight? true}]})

   (kind/md "## Donut Chart")
   (donut-chart
    {:title "Portfolio Distribution by Wallet"
     :data [{:label "cold trust" :value 801}
            {:label "trading validator" :value 100}
            {:label "trading 2" :value 29}
            {:label "keplr 1" :value 49}
            {:label "keplr 2" :value 6}
            {:label "keplr 3" :value 1}]})

   (kind/md "## Grouped Bar Chart")
   (grouped-bar-chart
    {:title "Wallet Assets Comparison"
     :categories ["cold trust" "trading validator" "trading 2" "keplr 1"]
     :series [{:name "AUM (USD)" :values [801 100 29 49] :color :green}
              {:name "Staked (Millions)" :values [0 17.5 0 0] :color :blue}]
     :y-axis-label "Amount"})])
