^:kindly/hide-code
(ns components-specification
  "Visual specification of the component library design system.

   This notebook renders all design tokens, color palettes, typography scales,
   and component examples so they can be reviewed and discussed visually."
  (:require [scicloj.kindly.v4.kind :as kind]))

;; # 🎨 Component Library Design System
;; *Visual Specification - Version 0.2.0*

;; ---
;; ## Overview

(kind/md "This notebook provides a **visual reference** for all design tokens and component specifications.
Each section shows actual rendered examples of colors, typography, spacing, and component layouts.")

;; ---
;; ## Color Palette

;; ### Primary Colors

^:kindly/hide-code
(def primary-colors
  {:green "#10b981"
   :blue "#3b82f6"
   :orange "#f59e0b"
   :purple "#8b5cf6"
   :red "#ef4444"})

^:kindly/hide-code
(def primary-color-descriptions
  {:green "Success, positive metrics, growth"
   :blue "Informational, neutral metrics"
   :orange "Warning, attention needed"
   :purple "Premium, aggregate metrics"
   :red "Error, negative metrics, danger"})

(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(5, 1fr)"
                :gap "20px"
                :margin "20px 0"}}
  (for [[name color] primary-colors]
    [:div {:style {:text-align "center"}}
     [:div {:style {:background color
                    :height "100px"
                    :border-radius "8px"
                    :box-shadow "0 2px 4px rgba(0,0,0,0.1)"}}]
     [:div {:style {:margin-top "10px" :font-weight "bold"}}
      (clojure.string/capitalize (clojure.core/name name))]
     [:div {:style {:font-size "0.9em" :color "#6b7280"}}
      color]
     [:div {:style {:font-size "0.85em" :color "#9ca3af" :margin-top "5px"}}
      (get primary-color-descriptions name)]])])

;; ### Semantic Colors

^:kindly/hide-code
(def semantic-colors
  {:success "#10b981"
   :info "#3b82f6"
   :warning "#fbbf24"
   :danger "#ef4444"
   :neutral "#6b7280"})

(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(5, 1fr)"
                :gap "20px"
                :margin "20px 0"}}
  (for [[name color] semantic-colors]
    [:div {:style {:text-align "center"}}
     [:div {:style {:background color
                    :height "80px"
                    :border-radius "8px"
                    :box-shadow "0 2px 4px rgba(0,0,0,0.1)"}}]
     [:div {:style {:margin-top "10px" :font-weight "bold"}}
      (clojure.string/capitalize (clojure.core/name name))]
     [:div {:style {:font-size "0.9em" :color "#6b7280"}}
      color]])])

;; ### Background & Text Colors

^:kindly/hide-code
(def background-colors
  {:light "#f9fafb"
   :white "#ffffff"
   :dark "#1f2937"})

^:kindly/hide-code
(def text-colors
  {:primary "#1f2937"
   :secondary "#6b7280"
   :white "#ffffff"})

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:h3 "Background Colors"]
  [:div {:style {:display "grid"
                 :grid-template-columns "repeat(3, 1fr)"
                 :gap "20px"
                 :margin "10px 0"}}
   (for [[name color] background-colors]
     [:div {:style {:text-align "center"}}
      [:div {:style {:background color
                     :height "60px"
                     :border-radius "8px"
                     :border "1px solid #e5e7eb"
                     :box-shadow "0 2px 4px rgba(0,0,0,0.1)"}}]
      [:div {:style {:margin-top "10px" :font-weight "bold"}}
       (clojure.string/capitalize (clojure.core/name name))]
      [:div {:style {:font-size "0.9em" :color "#6b7280"}}
       color]])]

  [:h3 {:style {:margin-top "30px"}} "Text Colors"]
  [:div {:style {:display "grid"
                 :grid-template-columns "repeat(3, 1fr)"
                 :gap "20px"
                 :margin "10px 0"}}
   (for [[name color] text-colors]
     [:div {:style {:text-align "center"}}
      [:div {:style {:background color
                     :height "60px"
                     :border-radius "8px"
                     :border "1px solid #e5e7eb"
                     :box-shadow "0 2px 4px rgba(0,0,0,0.1)"}}]
      [:div {:style {:margin-top "10px" :font-weight "bold"}}
       (clojure.string/capitalize (clojure.core/name name))]
      [:div {:style {:font-size "0.9em" :color "#6b7280"}}
       color]])]])

(kind/md "---")

;; ## Typography Scale

^:kindly/hide-code
(def typography-scale
  {:display "2.5em"
   :title "1.8em"
   :heading "1.2em"
   :body "1em"
   :small "0.9em"
   :tiny "0.85em"})

^:kindly/hide-code
(def typography-usage
  {:display "Large metric values"
   :title "Component titles"
   :heading "Section headings"
   :body "Regular text"
   :small "Labels, secondary info"
   :tiny "Tertiary info"})

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:table {:style {:width "100%"
                   :border-collapse "collapse"}}
   [:thead
    [:tr {:style {:background "#f9fafb" :border-bottom "2px solid #e5e7eb"}}
     [:th {:style {:padding "12px" :text-align "left"}} "Scale"]
     [:th {:style {:padding "12px" :text-align "left"}} "Size"]
     [:th {:style {:padding "12px" :text-align "left"}} "Example"]
     [:th {:style {:padding "12px" :text-align "left"}} "Usage"]]]
   [:tbody
    (for [[scale size] typography-scale]
      [:tr {:style {:border-bottom "1px solid #e5e7eb"}}
       [:td {:style {:padding "12px" :font-weight "bold"}}
        (clojure.string/capitalize (clojure.core/name scale))]
       [:td {:style {:padding "12px" :font-family "monospace" :color "#6b7280"}}
        size]
       [:td {:style {:padding "12px"}}
        [:span {:style {:font-size size}} "The quick brown fox"]]
       [:td {:style {:padding "12px" :color "#6b7280" :font-size "0.9em"}}
        (get typography-usage scale)]])]]])

(kind/md "---")

;; ## Spacing System

^:kindly/hide-code
(def spacing-scale
  {:xs "5px"
   :sm "10px"
   :md "20px"
   :lg "30px"
   :xl "40px"})

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:table {:style {:width "100%"
                   :border-collapse "collapse"}}
   [:thead
    [:tr {:style {:background "#f9fafb" :border-bottom "2px solid #e5e7eb"}}
     [:th {:style {:padding "12px" :text-align "left"}} "Token"]
     [:th {:style {:padding "12px" :text-align "left"}} "Size"]
     [:th {:style {:padding "12px" :text-align "left"}} "Visual Example"]]]
   [:tbody
    (for [[token size] spacing-scale]
      [:tr {:style {:border-bottom "1px solid #e5e7eb"}}
       [:td {:style {:padding "12px" :font-weight "bold"}}
        (str ":" (clojure.core/name token))]
       [:td {:style {:padding "12px" :font-family "monospace" :color "#6b7280"}}
        size]
       [:td {:style {:padding "12px"}}
        [:div {:style {:background "#3b82f6"
                       :width size
                       :height "20px"
                       :border-radius "4px"}}]]])]]])

(kind/md "---")

;; ## Gradients

(kind/md "### Standard Gradient Pattern")

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:p {:style {:color "#6b7280" :margin-bottom "20px"}}
   "All gradient backgrounds use a 135-degree angle with the base color transitioning to a slightly darker shade."]

  [:div {:style {:display "grid"
                 :grid-template-columns "repeat(3, 1fr)"
                 :gap "20px"}}
   ;; Green gradient
   [:div {:style {:background "linear-gradient(135deg, #10b981 0%, #059669 100%)"
                  :padding "40px"
                  :border-radius "15px"
                  :text-align "center"
                  :color "white"
                  :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Green Gradient"]
    [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} "$0 USD"]
    [:div {:style {:font-size "0.85em" :opacity "0.8"}} "Success / Positive"]]

   ;; Blue gradient
   [:div {:style {:background "linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)"
                  :padding "40px"
                  :border-radius "15px"
                  :text-align "center"
                  :color "white"
                  :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Blue Gradient"]
    [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} "51.2B"]
    [:div {:style {:font-size "0.85em" :opacity "0.8"}} "Informational"]]

   ;; Orange gradient
   [:div {:style {:background "linear-gradient(135deg, #f59e0b 0%, #d97706 100%)"
                  :padding "40px"
                  :border-radius "15px"
                  :text-align "center"
                  :color "white"
                  :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Orange Gradient"]
    [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} "16.0B"]
    [:div {:style {:font-size "0.85em" :opacity "0.8"}} "Warning / Attention"]]]])

(kind/md "---")

;; ## Component Examples

(kind/md "### Metric Card")

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
   [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} "$0 USD"]
   [:div {:style {:font-size "0.85em" :opacity "0.8"}} "USD per HASH"]]

  [:div {:style {:background "linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)"
                 :padding "30px"
                 :border-radius "15px"
                 :color "white"
                 :text-align "center"
                 :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
   [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Total Circulation"]
   [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} "51.2B"]
   [:div {:style {:font-size "0.85em" :opacity "0.8"}} "HASH tokens"]]

  [:div {:style {:background "linear-gradient(135deg, #f59e0b 0%, #d97706 100%)"
                 :padding "30px"
                 :border-radius "15px"
                 :color "white"
                 :text-align "center"
                 :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}
   [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Bonded (Staked)"]
   [:div {:style {:font-size "2.5em" :font-weight "bold" :margin "10px 0"}} "16.0B"]
   [:div {:style {:font-size "0.85em" :opacity "0.8"}} "31.2% of circulation"]]])

(kind/md "---")

(kind/md "### Portfolio Summary Card")

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
    [:div {:style {:font-size "2em" :font-weight "bold"}} "$177,475 USD"]]
   [:div
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Staked HASH"]
    [:div {:style {:font-size "2em" :font-weight "bold"}} "2,967"]]
   [:div
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Staking Value"]
    [:div {:style {:font-size "2em" :font-weight "bold"}} "$812,000 USD"]]
   [:div
    [:div {:style {:font-size "0.9em" :opacity "0.9"}} "Pending Rewards"]
    [:div {:style {:font-size "2em" :font-weight "bold"}} "37,044"]]]])

(kind/md "---")

(kind/md "### Insight Cards")

(kind/md "Semantic color variations for different message types:")

(kind/hiccup
 [:div {:style {:display "grid" :grid-template-columns "1fr" :gap "15px" :margin "20px 0"}}
  ;; Info (Blue)
  [:div {:style {:background "#dbeafe" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #3b82f6"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#1e40af"}} "💡 Info: Staking Optimization"]
   [:p {:style {:margin "0" :color "#1e3a8a"}}
    "Pending rewards of 37,044 HASH (~$1,037 value) should be claimed and restaked. Rewards do NOT earn additional rewards until claimed and restaked."]]

  ;; Success (Green)
  [:div {:style {:background "#dcfce7" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #10b981"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#065f46"}} "✅ Success: Diversification Status"]
   [:p {:style {:margin "0" :color "#064e3b"}}
    "Staking across multiple validators provides good network diversification. Consider reviewing validator performance and commission rates."]]

  ;; Warning (Yellow/Orange)
  [:div {:style {:background "#fef3c7" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #f59e0b"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#92400e"}} "🔒 Warning: Vesting Accounts"]
   [:p {:style {:margin "0" :color "#78350f"}}
    "Both staking wallets are Continuous Vesting Accounts. Delegation serves dual purpose: earning rewards AND satisfying vesting coverage requirements."]]

  ;; Premium (Purple)
  [:div {:style {:background "#f3e8ff" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #a855f7"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#6b21a8"}} "📈 Premium: Staking Performance"]
   [:p {:style {:margin "0" :color "#581c87"}}
    "Estimated staking APY: 68.7% APY (based on 7-day observation). Current staking value: $812.00 USD (including pending rewards)."]]

  ;; Danger (Red)
  [:div {:style {:background "#fee2e2" :padding "20px" :border-radius "8px"
                 :border-left "4px solid #ef4444"}}
   [:h4 {:style {:margin "0 0 10px 0" :color "#991b1b"}} "⚠️ Danger: Critical Alert"]
   [:p {:style {:margin "0" :color "#7f1d1d"}}
    "Account balance below minimum threshold. Immediate action required to avoid liquidation."]]])

(kind/md "---")

(kind/md "### Data Table")

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:h4 "Wallet Breakdown Table"]
  [:table {:style {:width "100%"
                   :border-collapse "collapse"
                   :margin-top "10px"}}
   [:thead
    [:tr {:style {:background "#f9fafb" :border-bottom "2px solid #e5e7eb"}}
     [:th {:style {:padding "12px" :text-align "left"}} "Wallet"]
     [:th {:style {:padding "12px" :text-align "right"}} "AUM (USD)"]
     [:th {:style {:padding "12px" :text-align "right"}} "Staked HASH"]
     [:th {:style {:padding "12px" :text-align "right"}} "Rewards HASH"]
     [:th {:style {:padding "12px" :text-align "center"}} "Type"]]]
   [:tbody
    [:tr {:style {:border-bottom "1px solid #e5e7eb"}}
     [:td {:style {:padding "12px"}} "cold trust"]
     [:td {:style {:padding "12px" :text-align "right"}} "$801"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "center"}} "✅ Liquid"]]

    [:tr {:style {:border-bottom "1px solid #e5e7eb" :background "#f9fafb"}}
     [:td {:style {:padding "12px"}} "staking individual 1"]
     [:td {:style {:padding "12px" :text-align "right"}} "$60,336"]
     [:td {:style {:padding "12px" :text-align "right"}} "17,500,000"]
     [:td {:style {:padding "12px" :text-align "right"}} "20,730"]
     [:td {:style {:padding "12px" :text-align "center"}} "🔒 Vesting"]]

    [:tr {:style {:border-bottom "1px solid #e5e7eb"}}
     [:td {:style {:padding "12px"}} "staking individual 2"]
     [:td {:style {:padding "12px" :text-align "right"}} "$16,282"]
     [:td {:style {:padding "12px" :text-align "right"}} "11,500,000"]
     [:td {:style {:padding "12px" :text-align "right"}} "16,314"]
     [:td {:style {:padding "12px" :text-align "center"}} "🔒 Vesting"]]

    [:tr {:style {:border-bottom "1px solid #e5e7eb" :background "#f9fafb"}}
     [:td {:style {:padding "12px"}} "staking trust"]
     [:td {:style {:padding "12px" :text-align "right"}} "$0"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "center"}} "✅ Liquid"]]

    [:tr {:style {:border-bottom "1px solid #e5e7eb"}}
     [:td {:style {:padding "12px"}} "trading trust"]
     [:td {:style {:padding "12px" :text-align "right"}} "$100,057"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "center"}} "✅ Liquid"]]

    [:tr {:style {:border-bottom "1px solid #e5e7eb" :background "#f9fafb"}}
     [:td {:style {:padding "12px"}} "unused trust"]
     [:td {:style {:padding "12px" :text-align "right"}} "$0"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "right"}} "0"]
     [:td {:style {:padding "12px" :text-align "center"}} "✅ Liquid"]]]]])

(kind/md "---")

(kind/md "### Action Items List")

(kind/hiccup
 [:div {:style {:background "#ffffff" :padding "25px" :border-radius "12px"
                :border "2px solid #e5e7eb" :margin "20px 0"}}
  [:h3 {:style {:color "#1f2937" :margin-top "0"}} "Recommended Actions"]
  [:ol {:style {:color "#374151" :line-height "1.8"}}
   [:li [:strong "Claim Staking Rewards"] " - Immediately claim 37,044 HASH in pending rewards (~$1,037 value)"]
   [:li [:strong "Restake Rewards"] " - Restake claimed rewards to maximize APY (rewards don't compound automatically)"]
   [:li [:strong "Review Validator Performance"] " - Audit all validator delegations for commission rates and uptime"]
   [:li [:strong "Monitor Vesting Schedule"] " - Track vesting progress and coverage ratios for both vesting accounts"]
   [:li [:strong "Consider Rebalancing"] " - Trading Trust wallet ($100,057) could potentially stake for additional yield"]]])

(kind/md "---")

(kind/md "### Progress Bar / Gauge")

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:h4 "Vesting Coverage Ratio"]
  [:div {:style {:background "#e5e7eb"
                 :height "30px"
                 :border-radius "15px"
                 :overflow "hidden"
                 :position "relative"
                 :margin "10px 0"}}
   [:div {:style {:background "linear-gradient(90deg, #10b981 0%, #059669 100%)"
                  :height "100%"
                  :width "125%"
                  :max-width "100%"
                  :border-radius "15px"
                  :display "flex"
                  :align-items "center"
                  :justify-content "center"
                  :color "white"
                  :font-weight "bold"}}
    "125%"]]
  [:div {:style {:display "grid" :grid-template-columns "repeat(3, 1fr)" :gap "10px" :margin-top "15px"}}
   [:div
    [:div {:style {:font-size "0.85em" :color "#6b7280"}} "Covered"]
    [:div {:style {:font-weight "bold"}} "$1,250,000"]]
   [:div
    [:div {:style {:font-size "0.85em" :color "#6b7280"}} "Required"]
    [:div {:style {:font-weight "bold"}} "$1,000,000"]]
   [:div
    [:div {:style {:font-size "0.85em" :color "#6b7280"}} "Excess"]
    [:div {:style {:font-weight "bold" :color "#10b981"}} "$250,000"]]]])

(kind/md "---")

(kind/md "## Responsive Grid Examples")

(kind/md "### Three-Column Grid (Desktop)")

(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(3, 1fr)"
                :gap "20px"
                :margin "20px 0"}}
  [:div {:style {:background "#f9fafb" :padding "30px" :border-radius "8px" :text-align "center"}}
   [:div {:style {:font-size "2em"}} "📊"]
   [:div {:style {:margin-top "10px" :font-weight "bold"}} "Column 1"]
   [:div {:style {:color "#6b7280" :font-size "0.9em"}} "Content here"]]
  [:div {:style {:background "#f9fafb" :padding "30px" :border-radius "8px" :text-align "center"}}
   [:div {:style {:font-size "2em"}} "📈"]
   [:div {:style {:margin-top "10px" :font-weight "bold"}} "Column 2"]
   [:div {:style {:color "#6b7280" :font-size "0.9em"}} "Content here"]]
  [:div {:style {:background "#f9fafb" :padding "30px" :border-radius "8px" :text-align "center"}}
   [:div {:style {:font-size "2em"}} "💰"]
   [:div {:style {:margin-top "10px" :font-weight "bold"}} "Column 3"]
   [:div {:style {:color "#6b7280" :font-size "0.9em"}} "Content here"]]])

(kind/md "### Four-Column Grid (Wide Desktop)")

(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(4, 1fr)"
                :gap "15px"
                :margin "20px 0"}}
  [:div {:style {:background "#dbeafe" :padding "20px" :border-radius "8px" :text-align "center"}}
   [:div {:style {:font-weight "bold"}} "Metric 1"]
   [:div {:style {:font-size "1.5em" :margin "5px 0"}} "123"]
   [:div {:style {:font-size "0.85em" :color "#6b7280"}} "units"]]
  [:div {:style {:background "#dcfce7" :padding "20px" :border-radius "8px" :text-align "center"}}
   [:div {:style {:font-weight "bold"}} "Metric 2"]
   [:div {:style {:font-size "1.5em" :margin "5px 0"}} "456"]
   [:div {:style {:font-size "0.85em" :color "#6b7280"}} "units"]]
  [:div {:style {:background "#fef3c7" :padding "20px" :border-radius "8px" :text-align "center"}}
   [:div {:style {:font-weight "bold"}} "Metric 3"]
   [:div {:style {:font-size "1.5em" :margin "5px 0"}} "789"]
   [:div {:style {:font-size "0.85em" :color "#6b7280"}} "units"]]
  [:div {:style {:background "#f3e8ff" :padding "20px" :border-radius "8px" :text-align "center"}}
   [:div {:style {:font-weight "bold"}} "Metric 4"]
   [:div {:style {:font-size "1.5em" :margin "5px 0"}} "321"]
   [:div {:style {:font-size "0.85em" :color "#6b7280"}} "units"]]])

(kind/md "---")

(kind/md "## Summary")

(kind/hiccup
 [:div {:style {:background "#f9fafb"
                :padding "30px"
                :border-radius "12px"
                :border-left "4px solid #3b82f6"
                :margin "20px 0"}}
  [:h3 {:style {:margin-top "0" :color "#1f2937"}} "Design System Complete"]
  [:p {:style {:color "#374151" :line-height "1.6"}}
   "This visual specification shows all design tokens and component patterns. "
   "Use these examples as reference when implementing the component library in " [:code "components.clj"] "."]
  [:p {:style {:color "#374151" :line-height "1.6" :margin-bottom "0"}}
   "All examples are rendered using the actual colors, typography, and spacing defined in the design system. "
   "This ensures visual consistency across all components."]])

(kind/md "---")

(kind/hiccup
 [:div {:style {:text-align "center" :color "#6b7280" :padding "20px"}}
  [:p {:style {:margin "0" :font-size "0.9em"}}
   "Component Library Design System v0.2.0"]
  [:p {:style {:margin "5px 0 0 0" :font-size "0.85em"}}
   "Visual specification rendered with Clay/Noj"]])
