(ns calc
  "Financial calculation utilities for Clay notebooks.

   Provides type-safe token conversions, portfolio aggregation, formatting,
   and domain-specific financial calculations for blockchain/DeFi use cases.

   Extracted and adapted from mcp-nrepl calculator library."
  (:refer-clojure :exclude [format])
  (:require [clojure.string :as str]))

;;=============================================================================
;; Constants
;;=============================================================================

;; Blockchain/Crypto Decimals
(def eth-decimals 18)
(def btc-decimals 8)
(def hash-decimals 9)
(def usdc-decimals 6)
(def usdt-decimals 6)

;; Crypto Unit Conversions
(def wei-per-eth 1000000000000000000N)
(def gwei-per-eth 1000000000N)
(def sat-per-btc 100000000N)

;; Time Constants
(def year-seconds 31536000)
(def day-seconds 86400)
(def hour-seconds 3600)
(def minute-seconds 60)
(def week-seconds 604800)

;;=============================================================================
;; Number Formatting Utilities
;;=============================================================================

(defn with-commas
  "Add thousands separators to a number.

   Example:
     (with-commas 1234567.89) => \"1,234,567.89\""
  [num]
  (let [s (str num)
        dot-idx (or (first (keep-indexed #(when (= %2 \.) %1) s)) (count s))
        whole (subs s 0 dot-idx)
        decimal (when (< dot-idx (count s)) (subs s dot-idx))
        rev-whole (vec (reverse whole))
        grouped (partition-all 3 rev-whole)
        rev-grouped (map reverse grouped)
        formatted (apply str (reverse (interpose "," (map #(apply str %) rev-grouped))))]
    (str formatted (or decimal ""))))

(defn round-to
  "Round number to specified decimal places.

   Example:
     (round-to 3.14159 2) => 3.14"
  [num decimals]
  (let [factor (Math/pow 10 decimals)]
    (/ (Math/round (* (double num) factor)) factor)))

;;=============================================================================
;; Financial Calculations
;;=============================================================================

(defn percent-change
  "Calculate percentage change between two values.

   Example:
     (percent-change 100 125)
     => {:percent 25.0 :direction :increase :change 25}"
  [old new]
  (let [change (- new old)
        percent (* (/ change old) 100.0)
        direction (cond
                    (pos? change) :increase
                    (neg? change) :decrease
                    :else :unchanged)
        formatted (str (if (pos? change) "+" "") percent "%")]
    {:change change
     :percent percent
     :direction direction
     :formatted formatted
     :old-value old
     :new-value new}))

(defn percent-of
  "Calculate what percentage one value is of another.

   Example:
     (percent-of 25 100) => {:percentage 25.0 :decimal 0.25}"
  [part total]
  (let [percentage (* (/ part total) 100.0)
        decimal (/ part total)]
    {:percentage percentage
     :decimal decimal
     :formatted (str percentage "%")
     :part part
     :total total}))

(defn roi
  "Calculate return on investment.

   Example:
     (roi 1000 1500)
     => {:profit 500 :roi-percent 50.0 :multiplier 1.5}"
  [initial final]
  (let [profit (- final initial)
        percent (* (/ profit initial) 100.0)
        multiplier (/ final initial)]
    {:profit profit
     :roi-percent percent
     :multiplier multiplier
     :formatted (str (if (pos? profit) "+" "") percent "%")
     :initial initial
     :final final}))

(defn token-value
  "Calculate total value of token holdings.

   Example:
     (token-value 0.028 1000000)
     => {:total-value 28000 :price 0.028 :holdings 1000000}"
  [price holdings]
  (let [total-value (* price holdings)]
    {:total-value total-value
     :price price
     :holdings holdings
     :formatted (str "$" total-value)
     :per-token (str "$" price)}))

(defn staking-rewards
  "Calculate staking rewards over a period.

   Example:
     (staking-rewards 10000 12.0 365)
     => {:rewards 1200 :daily-rewards 3.29 :total 11200}"
  [amount apy duration-days]
  (let [daily-rate (/ apy 365.0 100.0)
        rewards (* amount daily-rate duration-days)
        total (+ amount rewards)
        daily-rewards (/ rewards duration-days)]
    {:principal amount
     :apy apy
     :days duration-days
     :rewards rewards
     :total total
     :daily-rewards daily-rewards
     :formatted (str "$" rewards " rewards")}))

(defn apy-to-apr
  "Convert APY to APR.

   Example:
     (apy-to-apr 12.0 365) => {:apr 11.33 :apy 12.0}"
  [apy compounds-per-year]
  (let [apr (* (- (Math/pow (+ 1 (/ apy 100.0)) (/ 1.0 compounds-per-year)) 1)
               compounds-per-year 100.0)
        daily-rate (/ apr 365.0)]
    {:apy apy
     :apr apr
     :compounds compounds-per-year
     :daily-rate daily-rate}))

(defn apr-to-apy
  "Convert APR to APY.

   Example:
     (apr-to-apy 11.33 365) => {:apy 12.0 :apr 11.33}"
  [apr compounds-per-year]
  (let [apy (* (- (Math/pow (+ 1 (/ apr compounds-per-year 100.0)) compounds-per-year) 1) 100.0)
        daily-rate (/ apr 365.0)]
    {:apr apr
     :apy apy
     :compounds compounds-per-year
     :daily-rate daily-rate}))

;;=============================================================================
;; Date/Time Functions (for Vesting)
;;=============================================================================

(defn unix-now
  "Get current Unix timestamp with formatted date.

   Example:
     (unix-now) => {:unix 1700000000 :date \"2023-11-15\"}"
  []
  (let [now (java.time.Instant/now)
        unix (.getEpochSecond now)
        dt (java.time.ZonedDateTime/ofInstant now java.time.ZoneOffset/UTC)]
    {:unix unix
     :iso (str (.toLocalDate dt))
     :date (str (.toLocalDate dt))
     :time (str (.toLocalTime dt))
     :formatted (str dt)}))

(defn unix-to-date
  "Convert Unix timestamp to date.

   Example:
     (unix-to-date 1700000000)
     => {:date \"2023-11-15\" :year 2023 :month 11 :day 15}"
  [timestamp]
  (let [instant (java.time.Instant/ofEpochSecond (long timestamp))
        dt (java.time.ZonedDateTime/ofInstant instant java.time.ZoneOffset/UTC)
        ld (.toLocalDate dt)]
    {:unix timestamp
     :date (str ld)
     :iso (str ld)
     :year (.getYear ld)
     :month (.getMonthValue ld)
     :day (.getDayOfMonth ld)}))

(defn days-until
  "Calculate days until a future date.

   Example:
     (days-until \"2025-12-31\")
     => {:days-until 45 :weeks-until 6.4 :in-past false}"
  [date]
  (let [target-ld (if (number? date)
                    (.toLocalDate (java.time.ZonedDateTime/ofInstant
                                   (java.time.Instant/ofEpochSecond (long date))
                                   java.time.ZoneOffset/UTC))
                    (java.time.LocalDate/parse (str date)))
        now-ld (java.time.LocalDate/now java.time.ZoneOffset/UTC)
        days (.between java.time.temporal.ChronoUnit/DAYS now-ld target-ld)]
    {:target-date (str target-ld)
     :days-until days
     :weeks-until (/ days 7)
     :in-past (neg? days)}))

(defn days-between
  "Calculate days between two dates.

   Example:
     (days-between \"2025-01-01\" \"2025-12-31\")
     => {:days 364 :weeks 52.0}"
  [start end]
  (let [start-ld (if (number? start)
                   (.toLocalDate (java.time.ZonedDateTime/ofInstant
                                  (java.time.Instant/ofEpochSecond (long start))
                                  java.time.ZoneOffset/UTC))
                   (java.time.LocalDate/parse (str start)))
        end-ld (if (number? end)
                 (.toLocalDate (java.time.ZonedDateTime/ofInstant
                                (java.time.Instant/ofEpochSecond (long end))
                                java.time.ZoneOffset/UTC))
                 (java.time.LocalDate/parse (str end)))
        days (.between java.time.temporal.ChronoUnit/DAYS start-ld end-ld)]
    {:start (str start-ld)
     :end (str end-ld)
     :days days
     :weeks (/ days 7)
     :hours (* days 24)}))

(defn lock-period-end
  "Calculate when a lock period ends and days remaining.

   Example:
     (lock-period-end 1700000000 365)
     => {:unlock-date \"2024-11-15\" :days-remaining 45 :unlocked false}"
  [start-unix duration-days]
  (let [start-instant (java.time.Instant/ofEpochSecond (long start-unix))
        end-instant (.plusSeconds start-instant (* duration-days 86400))
        end-unix (.getEpochSecond end-instant)
        start-dt (java.time.ZonedDateTime/ofInstant start-instant java.time.ZoneOffset/UTC)
        end-dt (java.time.ZonedDateTime/ofInstant end-instant java.time.ZoneOffset/UTC)
        now (java.time.Instant/now)
        days-remaining (long (/ (- end-unix (.getEpochSecond now)) 86400))]
    {:locked-at (str (.toLocalDate start-dt))
     :duration-days duration-days
     :unlock-date (str (.toLocalDate end-dt))
     :unlock-unix end-unix
     :days-remaining days-remaining
     :unlocked (neg? days-remaining)}))

(defn is-unlocked
  "Check if a lock period has ended.

   Example:
     (is-unlocked 1700000000)
     => {:unlocked true :days-remaining 0}"
  [lock-end-timestamp]
  (let [end-instant (java.time.Instant/ofEpochSecond (long lock-end-timestamp))
        now (java.time.Instant/now)
        unlocked (.isAfter now end-instant)
        dt (java.time.ZonedDateTime/ofInstant end-instant java.time.ZoneOffset/UTC)
        days-remaining (long (/ (- lock-end-timestamp (.getEpochSecond now)) 86400))]
    {:unlock-date (str (.toLocalDate dt))
     :unlocked unlocked
     :days-remaining (if unlocked 0 days-remaining)}))

;;=============================================================================
;; Type-Safe Token Conversion System
;;=============================================================================

;; Unit Normalization

(defn normalize-unit
  "Convert unit to lowercase keyword for consistent handling.

   Example:
     (normalize-unit :USD) => :usd
     (normalize-unit \"HASH\") => :hash"
  [unit]
  (cond
    (keyword? unit) (keyword (.toLowerCase (name unit)))
    (string? unit) (keyword (.toLowerCase unit))
    :else (throw (ex-info "Unit must be string or keyword"
                          {:unit unit :type (type unit)}))))

;; Token Amount Functions

(defn token-amount?
  "Check if value is a valid token amount [amount unit].

   Example:
     (token-amount? [1000 :hash]) => true"
  [x]
  (and (vector? x)
       (= 2 (count x))
       (number? (first x))
       (or (keyword? (second x)) (string? (second x)))))

(defn get-amount
  "Extract amount from token tuple.

   Example:
     (get-amount [1000 :hash]) => 1000"
  [[amt _]]
  amt)

(defn get-unit
  "Extract unit from token tuple.

   Example:
     (get-unit [1000 :hash]) => :hash"
  [[_ unit]]
  (normalize-unit unit))

(defn token-amount
  "Construct a token amount tuple.

   Example:
     (token-amount 1000 :hash) => [1000 :hash]"
  [amt unit]
  [amt (normalize-unit unit)])

;; Rate Functions

(defn rate
  "Construct an exchange rate with natural syntax.

   Examples:
     (rate 0.032 :usd :per :hash)
     => [:/ [0.032 :usd] [1 :hash]]

     (rate 31.25 :hash :per :usd)
     => [:/ [31.25 :hash] [1 :usd]]"
  [num-amt num-unit per-kw per-unit-or-vec]
  {:pre [(= per-kw :per)]}
  (if (vector? per-unit-or-vec)
    (let [[denom-amt denom-unit] per-unit-or-vec]
      [:/ [num-amt num-unit] [denom-amt denom-unit]])
    [:/ [num-amt num-unit] [1 per-unit-or-vec]]))

(defn normalize-rate
  "Normalize rate to have denominator = 1.

   Example:
     (normalize-rate [:/ [3.2 :usd] [100 :hash]])
     => [:/ [0.032 :usd] [1 :hash]]"
  [[_ [num-amt num-unit] [denom-amt denom-unit]]]
  (if (= 1 denom-amt)
    [:/ [num-amt num-unit] [denom-amt denom-unit]]
    [:/ [(/ num-amt denom-amt) num-unit] [1 denom-unit]]))

(defn invert-rate
  "Invert an exchange rate.

   Example:
     (invert-rate [:/ [0.032 :usd] [1 :hash]])
     => [:/ [31.25 :hash] [1 :usd]]"
  [[_ num denom]]
  [:/ denom num])

;; Token Conversion

(defn token-convert
  "Convert token amounts using exchange rates.

   Examples:
     (token-convert [1000 :hash] :usd (rate 0.032 :usd :per :hash))
     => [32.0 :usd]

     (token-convert [10 :usd] (rate 0.032 :usd :per :hash))
     => [312.5 :hash]  ; Auto-infers target from rate"
  ([amount-tuple rate-val]
   (let [[_ [_ num-unit] [_ denom-unit]] rate-val
         from-unit-norm (get-unit amount-tuple)
         target-unit (if (= from-unit-norm (normalize-unit denom-unit))
                       num-unit
                       denom-unit)]
     (token-convert amount-tuple target-unit rate-val)))

  ([amount-tuple to-unit rate-val]
   (let [[amount from-unit] amount-tuple
         from-unit-norm (normalize-unit from-unit)
         [_ [num-amt num-unit] [denom-amt denom-unit]] rate-val
         num-unit-norm (normalize-unit num-unit)
         denom-unit-norm (normalize-unit denom-unit)
         to-unit-norm (normalize-unit to-unit)]

     (cond
       ;; FROM denominator TO numerator: multiply by (num/denom)
       (and (= from-unit-norm denom-unit-norm) (= to-unit-norm num-unit-norm))
       [(*' amount (/ num-amt denom-amt)) to-unit]

       ;; FROM numerator TO denominator: divide by (num/denom)
       (and (= from-unit-norm num-unit-norm) (= to-unit-norm denom-unit-norm))
       [(*' amount (/ denom-amt num-amt)) to-unit]

       :else
       (throw (ex-info "Units don't match rate"
                       {:from from-unit-norm
                        :to to-unit-norm
                        :rate rate-val}))))))

;; Compatible Units Registry (for same-token conversions)
(def compatible-units
  "Registry of compatible unit conversions."
  {#{:hash :nhash} [:/ [1 :hash] [1000000000 :nhash]]
   #{:btc :sats}   [:/ [1 :btc] [100000000 :sats]]})

(defn- find-compatible-rate
  "Find conversion rate between compatible units."
  [from-unit to-unit]
  (let [from-norm (normalize-unit from-unit)
        to-norm (normalize-unit to-unit)
        unit-set #{from-norm to-norm}]
    (when-let [rate-val (get compatible-units unit-set)]
      (let [[_ [_ num-unit] [_ denom-unit]] rate-val
            num-unit-norm (normalize-unit num-unit)
            denom-unit-norm (normalize-unit denom-unit)]
        (if (and (= from-norm denom-unit-norm) (= to-norm num-unit-norm))
          rate-val
          (invert-rate rate-val))))))

(defn portfolio-value
  "Calculate total portfolio value in target currency.

   Example:
     (portfolio-value
       [[1000 :hash] [5E7 :nhash] [10 :usd]]
       :usd
       [(rate 0.032 :usd :per :hash)])
     => [42.6 :usd]"
  [holdings to-unit rates]
  (let [to-unit-norm (normalize-unit to-unit)
        normalized-rates (map normalize-rate rates)
        inverted-rates (map invert-rate normalized-rates)
        all-rates (concat normalized-rates inverted-rates)

        converted-amounts
        (for [[amount from-unit] holdings]
          (let [from-unit-norm (normalize-unit from-unit)]
            (cond
              ;; Already in target currency
              (= from-unit-norm to-unit-norm)
              amount

              ;; Find matching rate
              :else
              (if-let [matching-rate
                       (first
                        (filter
                         (fn [[_ [_ num-unit] [_ denom-unit]]]
                           (let [num-norm (normalize-unit num-unit)
                                 denom-norm (normalize-unit denom-unit)]
                             (or (and (= from-unit-norm denom-norm)
                                      (= to-unit-norm num-norm))
                                 (and (= from-unit-norm num-norm)
                                      (= to-unit-norm denom-norm)))))
                         all-rates))]
                (first (token-convert [amount from-unit] to-unit matching-rate))

                ;; Try compatible-units registry
                (if-let [compatible-rate (find-compatible-rate from-unit to-unit)]
                  (first (token-convert [amount from-unit] to-unit compatible-rate))

                  (throw (ex-info "No conversion rate found"
                                  {:holding [amount from-unit]
                                   :target to-unit-norm})))))))]

    [(reduce +' 0 converted-amounts) to-unit]))

;;=============================================================================
;; Token Formatting
;;=============================================================================

;; Currency Symbol Registry
(def currency-symbols
  {:usd "$" :eur "€" :gbp "£" :jpy "¥" :btc "₿" :eth "Ξ"})

(defn- auto-decimals
  "Smart decimal place selection based on amount size."
  [amount]
  (cond
    (< amount 0.01) 8
    (< amount 1) 6
    (< amount 1000) 2
    :else
    (let [frac (- amount (long amount))]
      (if (< frac 0.01) 0 2))))

(defn- format-with-separators
  "Format number with thousands and decimal separators."
  [num decimals thousands-sep decimal-sep]
  (let [factor (Math/pow 10 decimals)
        rounded (/ (Math/round (* (double num) factor)) factor)
        num-str (clojure.core/format (str "%." decimals "f") rounded)
        [whole-part frac-part] (str/split num-str #"\.")
        whole-with-sep (let [rev-whole (vec (reverse whole-part))
                             grouped (partition-all 3 rev-whole)
                             rev-grouped (map reverse grouped)]
                         (apply str (reverse (interpose thousands-sep (map #(apply str %) rev-grouped)))))]
    (if (and frac-part (> decimals 0))
      (str whole-with-sep decimal-sep frac-part)
      whole-with-sep)))

(defn format-token
  "Format token amount for display.

   Examples:
     (format-token [17500000 :hash])
     => \"17,500,000 HASH\"

     (format-token [32.156789 :usd])
     => \"$32.16 USD\""
  ([token-tuple]
   (format-token token-tuple {}))
  ([token-tuple options]
   (let [[amount unit] token-tuple
         unit-norm (normalize-unit unit)
         decimals (or (:decimals options) (auto-decimals amount))
         symbol? (get options :symbol true)
         uppercase? (get options :uppercase true)
         thousands-sep (or (:thousands-sep options) ",")
         decimal-sep (or (:decimal-sep options) ".")

         amt-str (format-with-separators amount decimals thousands-sep decimal-sep)
         unit-str (if uppercase?
                    (.toUpperCase (name unit-norm))
                    (name unit-norm))
         symbol-str (when symbol? (get currency-symbols unit-norm))]

     (str (when symbol-str symbol-str) amt-str " " unit-str))))

(defn format-rate
  "Format exchange rate for display.

   Examples:
     (format-rate [:/ [0.032 :usd] [1 :hash]])
     => \"$0.032 per HASH\"

     (format-rate [:/ [0.032 :usd] [1 :hash]] {:style :slash})
     => \"USD/HASH\""
  ([rate-tuple]
   (format-rate rate-tuple {}))
  ([rate-tuple options]
   (let [normalized-rate (normalize-rate rate-tuple)
         [_ [num-amt num-unit] [denom-amt denom-unit]] normalized-rate

         num-unit-norm (normalize-unit num-unit)
         denom-unit-norm (normalize-unit denom-unit)

         decimals (or (:decimals options) (auto-decimals num-amt))
         symbol? (get options :symbol true)
         uppercase? (get options :uppercase true)
         thousands-sep (or (:thousands-sep options) ",")
         decimal-sep (or (:decimal-sep options) ".")
         style (or (:style options) :per)

         num-amt-str (format-with-separators num-amt decimals thousands-sep decimal-sep)
         num-unit-str (if uppercase?
                        (.toUpperCase (name num-unit-norm))
                        (name num-unit-norm))
         num-symbol-str (when symbol? (get currency-symbols num-unit-norm))

         denom-unit-str (if uppercase?
                          (.toUpperCase (name denom-unit-norm))
                          (name denom-unit-norm))]

     (case style
       :slash (str num-unit-str "/" denom-unit-str)
       :per (str (when num-symbol-str num-symbol-str) num-amt-str " " num-unit-str " per " denom-unit-str)
       (str (when num-symbol-str num-symbol-str) num-amt-str " " num-unit-str " per " denom-unit-str)))))

(defn format
  "Format token amount or rate with auto-detection.

   Examples:
     (format [17500000 :hash]) => \"17,500,000 HASH\"
     (format [:/ [0.032 :usd] [1 :hash]]) => \"$0.032 per HASH\""
  ([value]
   (format value {}))
  ([value options]
   (cond
     ;; 3-element vector starting with :/ -> rate
     (and (vector? value)
          (= 3 (count value))
          (#{:/ '/ "/"} (first value)))
     (format-rate value options)

     ;; 2-element vector -> token amount
     (and (vector? value) (= 2 (count value)))
     (format-token value options)

     :else
     (throw (ex-info "Value must be [amount unit] or [:/ [num unit] [denom unit]]"
                     {:provided value})))))

;;=============================================================================
;; Staking & DeFi Analysis
;;=============================================================================

(defn estimate-staking-apy
  "Estimate annualized APY from current staking rewards.

   This estimates APY based on observed rewards over a period, assuming
   rewards will continue at the same rate. Useful for real-time dashboards
   when historical APY data is not available.

   Example:
     (estimate-staking-apy 17500000 20730 7)
     => {:estimated-apy 68.7 :daily-rate 0.000169 :basis-days 7}"
  [staked-amount pending-rewards accumulation-days]
  (if (and (pos? staked-amount) (pos? pending-rewards) (pos? accumulation-days))
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
    {:estimated-apy 0 :daily-rate 0 :annual-rate 0 :formatted "0.0% APY"}))

(defn vesting-coverage-ratio
  "Calculate vesting coverage ratio for compliance checking.

   Vesting accounts often require that unvested tokens be covered by
   delegation (staked) or committed funds. This calculates the coverage ratio.

   Example:
     (vesting-coverage-ratio 1000000 750000 500000)
     => {:coverage-ratio 1.25 :compliant true :excess-coverage 250000}"
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

;;=============================================================================
;; Enhanced Formatting
;;=============================================================================

(defn format-percentage
  "Format percentage for display with smart rounding.

   Example:
     (format-percentage 31.2456) => \"31.2%\"
     (format-percentage 0.123 {:decimals 2}) => \"0.12%\""
  ([pct] (format-percentage pct {}))
  ([pct {:keys [decimals symbol] :or {decimals 1 symbol "%"}}]
   (str (round-to pct decimals) symbol)))

;;=============================================================================
;; Clay-Specific Helpers
;;=============================================================================

(defn metric-card-data
  "Create data structure for metric card visualization.

   Provides a standard format for metric cards with title, value, label, and styling.

   Example:
     (metric-card-data \"HASH Price\" [0.028 :usd]
       {:color \"#10b981\" :label \"USD per HASH\"})
     => {:title \"HASH Price\"
         :value \"$0 USD\"
         :label \"USD per HASH\"
         :color \"#10b981\"}"
  ([title value-tuple] (metric-card-data title value-tuple {}))
  ([title value-tuple {:keys [color label decimals gradient]
                       :or {color "#6b7280" label ""}}]
   (let [formatted-value (format value-tuple (when decimals {:decimals decimals}))]
     {:title title
      :value formatted-value
      :label label
      :color color
      :gradient (or gradient (str "linear-gradient(135deg, " color " 0%, " color " 100%)"))})))

(defn format-metric-value
  "Format a value for metric card display with smart defaults.

   Example:
     (format-metric-value 177475.23 :usd) => \"$177,475\""
  [value unit]
  (format [value unit] {:decimals (if (#{:usd :eur :gbp} (normalize-unit unit)) 0 2)}))

(defn calculate-portfolio-distribution
  "Calculate portfolio distribution percentages for pie charts.

   Example:
     (calculate-portfolio-distribution [[100 :usd] [50 :usd]])
     => [{:amount 100 :percentage 66.67 :formatted \"$100 USD\"}
         {:amount 50 :percentage 33.33 :formatted \"$50 USD\"}]"
  [holdings]
  (let [total (reduce + (map first holdings))]
    (for [[amt unit] holdings]
      {:amount amt
       :unit unit
       :percentage (:percentage (percent-of amt total))
       :formatted (format [amt unit])})))

(defn aggregate-holdings
  "Aggregate multiple token holdings into target currency.
   Wrapper around portfolio-value with better error messages.

   Example:
     (aggregate-holdings
       [[1000 :hash] [5E7 :nhash]]
       :usd
       [(rate 0.032 :usd :per :hash)])
     => {:total-value 42.6 :unit :usd :formatted \"$43 USD\"}"
  [holdings to-unit rates]
  (let [[total-amt unit] (portfolio-value holdings to-unit rates)]
    {:total-value total-amt
     :unit unit
     :formatted (format [total-amt unit])}))
