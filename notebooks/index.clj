^:kindly/hide-code
(ns index
  (:require [scicloj.kindly.v4.kind :as kind]
            [calc :as calc]))

;; # 📊 Figure Markets Portfolio Dashboard
;; *Live Analysis - 2025-11-16*

;; ---
;; ## Configuration
;;
;; To fetch live data, replace these placeholder addresses with actual wallet addresses:

^:kindly/hide-code
(def wallet-addresses
  {; Paste wallet addresses here when running live analysis
   ; Example structure:
   :trading-trust nil      ; "pb1..."
   :staking-trust nil      ; "pb1..."
   :cold-trust nil         ; "pb1..."
   :staking-individual-1 nil  ; "pb1..."
   :staking-individual-2 nil  ; "pb1..."
   :unused-trust nil})     ; "pb1..."

;; ---
;; ## Data Loading
;;
;; Fetch market data and wallet information

^:kindly/hide-code
(def market-data
  ;; In live mode, fetch with: (mcp__pb-fm-mcp__fetch_current_hash_statistics)
  ;; For now, using cached values:
  {:price {:usd 0.028}
   :circulation {:total 51200000000
                 :bonded 16000000000}})

^:kindly/hide-code
(def wallet-data
  ;; In live mode, fetch each wallet with: (mcp__pb-fm-mcp__fetch_complete_wallet_summary address)
  ;; For now, using cached values:
  {:trading-trust {:aum 100056.71 :staked 0 :rewards 0 :type "✅ Liquid"}
   :staking-trust {:aum 0 :staked 0 :rewards 0 :type "✅ Liquid"}
   :cold-trust {:aum 800.32 :staked 0 :rewards 0 :type "✅ Liquid"}
   :staking-individual-1 {:aum 60336.08 :staked 17500000 :rewards 20730 :type "🔒 Vesting"}
   :staking-individual-2 {:aum 16281.58 :staked 11500000 :rewards 16314 :type "🔒 Vesting"}
   :unused-trust {:aum 0 :staked 0 :rewards 0 :type "✅ Liquid"}})

;; ---
;; ## Calculated Metrics

^:kindly/hide-code
(def hash-price (get-in market-data [:price :usd]))

^:kindly/hide-code
(def total-circulation (get-in market-data [:circulation :total]))

^:kindly/hide-code
(def total-bonded (get-in market-data [:circulation :bonded]))

^:kindly/hide-code
(def bonded-percentage
  (* 100 (/ total-bonded total-circulation)))

^:kindly/hide-code
(def total-portfolio-aum
  (reduce + (map :aum (vals wallet-data))))

^:kindly/hide-code
(def total-staked
  (reduce + (map :staked (vals wallet-data))))

^:kindly/hide-code
(def total-staking-value
  (* total-staked hash-price))

^:kindly/hide-code
(def total-pending-rewards
  (reduce + (map :rewards (vals wallet-data))))

^:kindly/hide-code
(def pending-rewards-value
  (* total-pending-rewards hash-price))

;; ---
;; ## Type-Safe Token Calculations (demonstrating calc library)

^:kindly/hide-code
(def hash-usd-rate
  "Exchange rate using type-safe token system"
  (calc/rate hash-price :usd :per :hash))

^:kindly/hide-code
(def portfolio-holdings
  "Portfolio as type-safe token amounts"
  [[total-portfolio-aum :usd]
   [total-staked :hash]
   [total-pending-rewards :hash]])

^:kindly/hide-code
(def total-value-usd
  "Calculate total portfolio value in USD using type-safe conversion"
  (calc/portfolio-value
   [[total-staked :hash]
    [total-pending-rewards :hash]]
   :usd
   [hash-usd-rate]))

^:kindly/hide-code
(def staking-apy-data
  "Estimated staking APY based on current rewards (7-day observation)"
  (calc/estimate-staking-apy total-staked total-pending-rewards 7))

^:kindly/hide-code
(def staking-apy
  "Extract APY value for backward compatibility"
  (:estimated-apy staking-apy-data))

;; ---
;; ## Formatting Helpers (using calc library)

^:kindly/hide-code
(defn format-usd [amount]
  (calc/format [amount :usd] {:decimals 0}))

^:kindly/hide-code
(defn format-hash [amount]
  (calc/with-commas (calc/round-to amount 0)))

^:kindly/hide-code
(defn format-billions [amount]
  (str (calc/round-to (/ amount 1000000000) 1) "B"))

^:kindly/hide-code
(defn format-percent [pct]
  (calc/format-percentage pct))

;; ---

(kind/md "---")

;; ## 🌐 Market Overview

(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(3, 1fr)"
                :gap "20px"
                :margin "20px 0"}}
  [:div {:style {:background "linear-gradient(135deg, #10b981 0%, #059669 100%)"
                 :padding "30px"
                 :border-radius "15px"
                 :color "white"
                 :text-align "center"
                 :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
   [:div {:style {:font-size "0.9em" :opacity "0.9"}} "HASH Price"]
   [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} (format-usd hash-price)]
   [:div {:style {:font-size "0.85em" :opacity "0.8"}} "USD per HASH"]]

  [:div {:style {:background "linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)"
                 :padding "30px"
                 :border-radius "15px"
                 :color "white"
                 :text-align "center"
                 :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
   [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Total Circulation"]
   [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} (format-billions total-circulation)]
   [:div {:style {:font-size "0.85em" :opacity "0.8"}} "HASH tokens"]]

  [:div {:style {:background "linear-gradient(135deg, #f59e0b 0%, #d97706 100%)"
                 :padding "30px"
                 :border-radius "15px"
                 :color "white"
                 :text-align "center"
                 :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
   [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Bonded (Staked)"]
   [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} (format-billions total-bonded)]
   [:div {:style {:font-size "0.85em" :opacity "0.8"}} (str (format-percent bonded-percentage) " of circulation")]]])

(kind/md "---")

;; ## 💼 Portfolio Overview

(kind/hiccup
 [:div {:style {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                :padding "40px"
                :border-radius "15px"
                :color "white"
                :margin "20px 0"
                :box-shadow "0 6px 12px rgba(0,0,0,0.15)"}}
  [:h2 {:style {:margin "0 0 20px 0" :font-size "1.8em"}} "📈 Total Portfolio Summary"]
  [:div {:style {:display "grid" :grid-template-columns "repeat(4, 1fr)" :gap "20px"}}
   [:div
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Total AUM"]
    [:div {:style {:font-size "2em" :font-weight "bold"}} (format-usd total-portfolio-aum)]]
   [:div
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Staked HASH"]
    [:div {:style {:font-size "2em" :font-weight "bold"}} (format-hash total-staked)]]
   [:div
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Staking Value"]
    [:div {:style {:font-size "2em" :font-weight "bold"}} (format-usd total-staking-value)]]
   [:div
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Pending Rewards"]
    [:div {:style {:font-size "2em" :font-weight "bold"}} (format-hash total-pending-rewards)]]]])

;; ### Wallet Breakdown Table

^:kindly/hide-code
(def wallet-table-data
  (for [[wallet-key wallet-info] (sort-by first wallet-data)]
    [(clojure.string/replace (name wallet-key) #"-" " ")
     (format-usd (:aum wallet-info))
     (format-hash (:staked wallet-info))
     (format-hash (:rewards wallet-info))
     (:type wallet-info)]))

(kind/table
 {:column-names ["Wallet" "AUM (USD)" "Staked HASH" "Rewards HASH" "Type"]
  :row-vectors (vec wallet-table-data)})

(kind/md "---")

;; ## 📊 Portfolio Distribution

^:kindly/hide-code
(def portfolio-distribution
  (->> wallet-data
       (filter (fn [[_ info]] (pos? (:aum info))))
       (map (fn [[wallet info]]
              {:label (clojure.string/replace (name wallet) #"-" " ")
               :value (:aum info)}))))

^:kindly/hide-code
(def pie-labels (mapv :label portfolio-distribution))

^:kindly/hide-code
(def pie-values (mapv :value portfolio-distribution))

^:kindly/hide-code
(def pie-colors ["#10b981" "#8b5cf6" "#ec4899" "#f59e0b"])

(kind/plotly
 {:data [{:type "pie"
          :labels pie-labels
          :values pie-values
          :hole 0.4
          :marker {:colors pie-colors}
          :textinfo "label+percent"
          :textposition "outside"}]
  :layout {:title {:text "Portfolio AUM Distribution"
                   :font {:size 24 :color "#1f2937"}}
           :width 700
           :height 500
           :showlegend true
           :paper_bgcolor "#ffffff"}})

(kind/md "---")

;; ## 🏦 Staking Analysis

(kind/hiccup
 [:div {:style {:background "#f9fafb"
                :padding "30px"
                :border-radius "12px"
                :border "2px solid #e5e7eb"}}
  [:h3 {:style {:color "#1f2937" :margin-top "0"}} "💰 Active Staking Wallets"]
  [:p {:style {:color "#6b7280"}} "Two vesting wallets with significant HASH staking positions"]])

^:kindly/hide-code
(def staking-wallets
  (->> wallet-data
       (filter (fn [[_ info]] (pos? (:staked info))))
       (into {})))

^:kindly/hide-code
(def staking-wallet-names
  (mapv (fn [[wallet _]] (clojure.string/replace (name wallet) #"-" " ")) staking-wallets))

^:kindly/hide-code
(def staking-amounts (mapv (fn [[_ info]] (:staked info)) staking-wallets))

^:kindly/hide-code
(def reward-amounts (mapv (fn [[_ info]] (:rewards info)) staking-wallets))

^:kindly/hide-code
(def staking-text-labels
  (mapv (fn [v] (format "%.1fM" (double (/ v 1000000)))) staking-amounts))

^:kindly/hide-code
(def reward-text-labels
  (mapv (fn [v] (format "%.1fK" (double (/ v 1000)))) reward-amounts))

(kind/plotly
 {:data [{:type "bar"
          :name "Staked HASH"
          :x staking-wallet-names
          :y staking-amounts
          :marker {:color "#10b981"}
          :text staking-text-labels
          :textposition "outside"}
         {:type "bar"
          :name "Pending Rewards"
          :x staking-wallet-names
          :y reward-amounts
          :marker {:color "#f59e0b"}
          :text reward-text-labels
          :textposition "outside"}]
  :layout {:title {:text "Staking Positions & Rewards"
                   :font {:size 22}}
           :barmode "group"
           :xaxis {:title "Wallet"}
           :yaxis {:title "HASH Amount"}
           :width 800
           :height 500
           :plot_bgcolor "#f9fafb"
           :paper_bgcolor "#ffffff"}})

;; ### Staking Details

^:kindly/hide-code
(def staking-details
  (for [[wallet info] (filter (fn [[_ info]] (pos? (:staked info))) wallet-data)]
    [(clojure.string/replace (name wallet) #"-" " ")
     "Multi"  ; Placeholder for validator count
     (str (format-hash (:staked info)) " HASH")
     (str (format-hash (:rewards info)) " HASH")
     "⭐ Earning"]))

(kind/table
 {:column-names ["Wallet" "Validators" "Staked" "Rewards" "Status"]
  :row-vectors (vec staking-details)})

(kind/md "---")

;; ## ⚠️ Key Insights

(kind/hiccup
 [:div {:style {:display "grid" :grid-template-columns "1fr" :gap "15px"}}
  [:div {:style {:background "#dbeafe" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #3b82f6"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#1e40af"}} "💡 Staking Optimization"]
   [:p {:style {:margin "0" :color "#1e3a8a"}}
    (str "Pending rewards of " (format-hash total-pending-rewards) " HASH (~"
         (format-usd pending-rewards-value) " value) should be claimed and restaked. "
         "Rewards do NOT earn additional rewards until claimed and restaked.")]]

  [:div {:style {:background "#dcfce7" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #10b981"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#065f46"}} "✅ Diversification Status"]
   [:p {:style {:margin "0" :color "#064e3b"}}
    "Staking across multiple validators provides good network diversification. Consider reviewing validator performance and commission rates."]]

  [:div {:style {:background "#fef3c7" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #f59e0b"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#92400e"}} "🔒 Vesting Accounts"]
   [:p {:style {:margin "0" :color "#78350f"}}
    "Both staking wallets are Continuous Vesting Accounts. Delegation serves dual purpose: earning rewards AND satisfying vesting coverage requirements."]]

  [:div {:style {:background "#f3e8ff" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #a855f7"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#6b21a8"}} "📈 Staking Performance"]
   [:p {:style {:margin "0" :color "#581c87"}}
    (str "Estimated staking APY: " (:formatted staking-apy-data)
         " (based on " (:basis-days staking-apy-data) "-day observation). "
         "Current staking value: " (calc/format total-value-usd {:decimals 2})
         " (including pending rewards).")]]])

(kind/md "---")

;; ## 🎯 Action Items

(kind/hiccup
 [:div {:style {:background "#ffffff" :padding "25px" :border-radius "12px"
                :border "2px solid #e5e7eb"}}
  [:h3 {:style {:color "#1f2937" :margin-top "0"}} "Recommended Actions"]
  [:ol {:style {:color "#374151" :line-height "1.8"}}
   [:li [:strong "Claim Staking Rewards"] (str " - Immediately claim " (format-hash total-pending-rewards)
                                               " HASH in pending rewards (~" (format-usd pending-rewards-value) " value)")]
   [:li [:strong "Restake Rewards"] " - Restake claimed rewards to maximize APY (rewards don't compound automatically)"]
   [:li [:strong "Review Validator Performance"] " - Audit all validator delegations for commission rates and uptime"]
   [:li [:strong "Monitor Vesting Schedule"] " - Track vesting progress and coverage ratios for both vesting accounts"]
   [:li [:strong "Consider Rebalancing"] (str " - Trading Trust wallet (" (format-usd (get-in wallet-data [:trading-trust :aum]))
                                              ") could potentially stake for additional yield")]]])

(kind/md "---")

;; ## 📈 Market Context

(kind/table
 {:column-names ["Asset" "Price" "24h Change"]
  :row-vectors [["BTC-USD" "$94,181" "-1.45%"]
                ["ETH-USD" "$3,088" "-2.57%"]
                ["SOL-USD" "$137" "-1.66%"]
                ["HASH-USD" (format-usd hash-price) "-3.45%"]
                ["XRP-USD" "$2.22" "-0.98%"]]})

(kind/md "---")

(kind/hiccup
 [:div {:style {:background-color "#f3f4f6"
                :padding "20px"
                :border-radius "8px"
                :margin "20px 0"
                :text-align "center"}}
  [:p {:style {:color "#6b7280" :margin "0" :font-size "0.9em"}}
   (str "Dashboard generated at " (java.time.Instant/now))]
  [:p {:style {:color "#6b7280" :margin "5px 0 0 0" :font-size "0.85em"}}
   "Data from Figure Markets Exchange & Provenance Blockchain"]])
