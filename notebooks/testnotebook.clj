^:kindly/hide-code
(ns testnotebook
  "Test notebook for E2E testing of stateful parameterized notebooks.
   Displays current params and provides forms/links to test param flow."
  (:require [scicloj.kindly.v4.kind :as kind]
            [scicloj.clay.v2.api :as clay]))

;; =============================================================================
;; Read current parameters
;; =============================================================================

^:kindly/hide-code
(def current-params clay/*url-params*)

^:kindly/hide-code
(def wallet (get current-params "wallet" "not-set"))

^:kindly/hide-code
(def filter-value (get current-params "filter" "not-set"))

^:kindly/hide-code
(def sort-value (get current-params "sort" "not-set"))

;; =============================================================================
;; Display current state
;; =============================================================================

(kind/md "# Test Notebook - Stateful Parameters")

(kind/md "## Current Parameters")

;; Display params in a testable format with data-testid attributes
(kind/hiccup
 [:div {:style {:background "#f0f9ff"
                :padding "20px"
                :border-radius "8px"
                :margin "10px 0"}}
  [:table {:style {:width "100%"}}
   [:tbody
    [:tr
     [:td {:style {:font-weight "bold"}} "wallet:"]
     [:td {:data-testid "param-wallet"} wallet]]
    [:tr
     [:td {:style {:font-weight "bold"}} "filter:"]
     [:td {:data-testid "param-filter"} filter-value]]
    [:tr
     [:td {:style {:font-weight "bold"}} "sort:"]
     [:td {:data-testid "param-sort"} sort-value]]
    [:tr
     [:td {:style {:font-weight "bold"}} "all params:"]
     [:td {:data-testid "param-all"} (pr-str current-params)]]]]])

;; =============================================================================
;; Forms for testing POST submissions
;; =============================================================================

(kind/md "## Test Forms")

;; Form to set wallet
(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:h4 "Set Wallet"]
  [:form {:method "POST" :action "/testnotebook.html" :data-testid "form-wallet"}
   [:input {:type "text" :name "wallet" :placeholder "Enter wallet address"
            :data-testid "input-wallet"
            :style {:padding "8px" :margin-right "10px"}}]
   [:button {:type "submit" :data-testid "submit-wallet"
             :style {:padding "8px 16px"}} "Set Wallet"]]])

;; Form to set filter
(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:h4 "Set Filter"]
  [:form {:method "POST" :action "/testnotebook.html" :data-testid "form-filter"}
   [:select {:name "filter" :data-testid "select-filter"
             :style {:padding "8px" :margin-right "10px"}}
    [:option {:value "all"} "All"]
    [:option {:value "active"} "Active"]
    [:option {:value "staked"} "Staked"]]
   [:button {:type "submit" :data-testid "submit-filter"
             :style {:padding "8px 16px"}} "Set Filter"]]])

;; Form with multiple params
(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:h4 "Set Multiple Params"]
  [:form {:method "POST" :action "/testnotebook.html" :data-testid "form-multi"}
   [:div {:style {:margin-bottom "10px"}}
    [:input {:type "text" :name "wallet" :placeholder "Wallet"
             :data-testid "input-multi-wallet"
             :style {:padding "8px" :margin-right "10px"}}]]
   [:div {:style {:margin-bottom "10px"}}
    [:input {:type "text" :name "filter" :placeholder "Filter"
             :data-testid "input-multi-filter"
             :style {:padding "8px" :margin-right "10px"}}]]
   [:div {:style {:margin-bottom "10px"}}
    [:input {:type "text" :name "sort" :placeholder "Sort"
             :data-testid "input-multi-sort"
             :style {:padding "8px" :margin-right "10px"}}]]
   [:button {:type "submit" :data-testid "submit-multi"
             :style {:padding "8px 16px"}} "Set All"]]])

;; =============================================================================
;; Links for testing GET→POST conversion
;; =============================================================================

(kind/md "## Test Links")

(kind/md "These links have query parameters and should be converted to POST by the injected JavaScript:")

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:ul
   ;; Link that adds/changes filter
   [:li {:style {:margin "10px 0"}}
    [:a {:href "/testnotebook.html?filter=active"
         :data-testid "link-filter-active"}
     "Set filter=active"]]

   ;; Link that adds/changes sort
   [:li {:style {:margin "10px 0"}}
    [:a {:href "/testnotebook.html?sort=desc"
         :data-testid "link-sort-desc"}
     "Set sort=desc"]]

   ;; Link with multiple params
   [:li {:style {:margin "10px 0"}}
    [:a {:href "/testnotebook.html?wallet=pb1test&filter=staked"
         :data-testid "link-multi-params"}
     "Set wallet=pb1test & filter=staked"]]

   ;; Link to clear/reset (only keeps new param)
   [:li {:style {:margin "10px 0"}}
    [:a {:href "/testnotebook.html?reset=true"
         :data-testid "link-reset"}
     "Reset with reset=true"]]]])

;; =============================================================================
;; External links (should NOT be converted)
;; =============================================================================

(kind/md "## External Links (should NOT convert)")

(kind/hiccup
 [:div {:style {:margin "20px 0"}}
  [:ul
   [:li {:style {:margin "10px 0"}}
    [:a {:href "https://example.com?foo=bar"
         :data-testid "link-external"
         :target "_blank"}
     "External link (example.com)"]]]])

;; =============================================================================
;; Debug info
;; =============================================================================

(kind/md "## Debug Info")

(kind/hiccup
 [:div {:style {:background "#fef3c7"
                :padding "15px"
                :border-radius "8px"
                :font-family "monospace"
                :font-size "12px"}}
  [:div {:data-testid "debug-url"}
   "Check browser URL bar for /app/testnotebook.html/{state-id} pattern"]])
