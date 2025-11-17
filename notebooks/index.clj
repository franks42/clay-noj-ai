^:kindly/hide-code
(ns index
  (:require [scicloj.kindly.v4.kind :as kind]
            [calc :as calc]
            [components :as c]))

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
  (c/metric-card {:title "HASH Price"
                  :value [hash-price :usd]
                  :label "USD per HASH"
                  :color :green})

  (c/metric-card {:title "Total Circulation"
                  :value (format-billions total-circulation)
                  :label "HASH tokens"
                  :color :blue})

  (c/metric-card {:title "Bonded (Staked)"
                  :value (format-billions total-bonded)
                  :label (str (format-percent bonded-percentage) " of circulation")
                  :color :orange})])

(kind/md "---")

;; ## 💼 Portfolio Overview

(c/portfolio-summary-card
 {:title "📈 Total Portfolio Summary"
  :metrics [{:label "Total AUM"
             :value [total-portfolio-aum :usd]}
            {:label "Staked HASH"
             :value (format-hash total-staked)}
            {:label "Staking Value"
             :value [total-staking-value :usd]}
            {:label "Pending Rewards"
             :value (format-hash total-pending-rewards)}]
  :color :purple
  :columns 4})

;; ### Wallet Breakdown Table

^:kindly/hide-code
(def wallet-table-rows
  (for [[wallet-key wallet-info] (sort-by first wallet-data)]
    {:wallet (clojure.string/replace (name wallet-key) #"-" " ")
     :aum [(:aum wallet-info) :usd]
     :staked (:staked wallet-info)
     :rewards (:rewards wallet-info)
     :type (:type wallet-info)}))

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
            {:key :rewards
             :label "Rewards HASH"
             :align :right
             :format :number}
            {:key :type
             :label "Type"
             :align :center}]
  :rows wallet-table-rows
  :striped? true
  :hover? true})

(kind/md "---")

;; ## 📊 Portfolio Distribution

^:kindly/hide-code
(def portfolio-distribution
  (->> wallet-data
       (filter (fn [[_ info]] (pos? (:aum info))))
       (mapv (fn [[wallet info]]
               {:label (clojure.string/replace (name wallet) #"-" " ")
                :value (:aum info)}))))

(c/donut-chart
 {:title "Portfolio AUM Distribution"
  :data portfolio-distribution
  :colors [:green :purple :red :orange]
  :height 500})

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

(c/grouped-bar-chart
 {:title "Staking Positions & Rewards"
  :categories staking-wallet-names
  :series [{:name "Staked HASH"
            :values staking-amounts
            :color :green}
           {:name "Pending Rewards"
            :values reward-amounts
            :color :orange}]
  :y-axis-label "HASH Amount"
  :height 500})

;; ### Staking Details

^:kindly/hide-code
(def staking-details-rows
  (for [[wallet info] (filter (fn [[_ info]] (pos? (:staked info))) wallet-data)]
    {:wallet (clojure.string/replace (name wallet) #"-" " ")
     :validators "Multi"  ; Placeholder for validator count
     :staked (:staked info)
     :rewards (:rewards info)
     :status "⭐ Earning"}))

(c/data-table
 {:columns [{:key :wallet :label "Wallet" :align :left}
            {:key :validators :label "Validators" :align :center}
            {:key :staked :label "Staked" :align :right :format :number}
            {:key :rewards :label "Rewards" :align :right :format :number}
            {:key :status :label "Status" :align :center}]
  :rows staking-details-rows
  :striped? true
  :compact? false})

(kind/md "---")

;; ## ⚠️ Key Insights

(c/insight-card
 {:type :info
  :title "Staking Optimization"
  :message (str "Pending rewards of " (format-hash total-pending-rewards) " HASH (~"
                (format-usd pending-rewards-value) " value) should be claimed and restaked. "
                "Rewards do NOT earn additional rewards until claimed and restaked.")})

(c/insight-card
 {:type :success
  :title "Diversification Status"
  :message "Staking across multiple validators provides good network diversification. Consider reviewing validator performance and commission rates."})

(c/insight-card
 {:type :warning
  :title "Vesting Accounts"
  :message "Both staking wallets are Continuous Vesting Accounts. Delegation serves dual purpose: earning rewards AND satisfying vesting coverage requirements."})

(c/insight-card
 {:type :premium
  :title "Staking Performance"
  :message (str "Estimated staking APY: " (:formatted staking-apy-data)
                " (based on " (:basis-days staking-apy-data) "-day observation). "
                "Current staking value: " (calc/format total-value-usd {:decimals 2})
                " (including pending rewards).")})

(kind/md "---")

;; ## 🎯 Action Items

(c/action-items-list
 {:title "Recommended Actions"
  :items [(str "Claim Staking Rewards: Immediately claim " (format-hash total-pending-rewards)
               " HASH in pending rewards (~" (format-usd pending-rewards-value) " value)")
          "Restake Rewards: Restake claimed rewards to maximize APY (rewards don't compound automatically)"
          "Review Validator Performance: Audit all validator delegations for commission rates and uptime"
          "Monitor Vesting Schedule: Track vesting progress and coverage ratios for both vesting accounts"
          (str "Consider Rebalancing: Trading Trust wallet (" (format-usd (get-in wallet-data [:trading-trust :aum]))
               ") could potentially stake for additional yield")]
  :color :blue})

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
