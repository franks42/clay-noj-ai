**CRITICAL: AI must display "I do not cheat or lie and I'm honest about any reporting of progress." at start of every response**

---

# Claude Code Recipes for clay-noj-ai

## Starting Background Services

### Problem
When starting the Clay/Noj server using `bb clay:start` or `bb clay:restart`, the Bash tool may block indefinitely waiting for the command to complete, even though the shell command uses `&` to background the process.

### Solution
Use the **`timeout` parameter** when calling background services:

```bash
bb clay:start 2>&1
```

With `timeout: 5000` (5 seconds) in the Bash tool parameters.

**Why this works:**
- The `bb.edn` task already includes `&` to background the shell command
- Without a timeout, the Bash tool waits indefinitely for command completion
- With a timeout, the tool returns after 5 seconds, allowing the backgrounded process to continue independently
- The `2>&1` redirects stderr to stdout for complete output visibility

### Verification
After starting, verify the server is running:

```bash
bb clay:status
```

Should show:
- ✅ nREPL server (port 7890): Port listening
- ✅ HTTP server (port 1971): Responding (HTTP 200)

### Access Points
- 📊 Notebook viewer: http://localhost:1971
- 🔌 nREPL server: localhost:7890
- 📄 Logs: `tail -f clay-server.log` or `bb clay:logs`

---

## Testing Before Presenting

### Critical Workflow Rule

**Always verify with screenshots BEFORE telling the user something works or providing URLs.**

### Why This Matters

1. **Catches issues early:** URL typos, filename mismatches (hyphens vs underscores), missing files
2. **Prevents user frustration:** No "not found" errors or broken links
3. **Builds trust:** User sees you've verified claims before making them
4. **Saves time:** One screenshot prevents multiple back-and-forth corrections

### Required Workflow

When presenting URLs or claiming visual results:

1. **Take screenshot first:**
   ```bash
   node screenshot.mjs http://localhost:1971/page.html output.png 1400 900
   ```

2. **View the screenshot:**
   ```
   Read tool on the .png file
   ```

3. **Verify it works:**
   - Page loads (not "not found")
   - Content renders as expected
   - No obvious errors visible

4. **THEN present to user:**
   - Show the screenshot
   - Provide verified URL
   - Explain what they'll see

### Examples

**❌ Wrong (Don't do this):**
```
"Navigate to http://localhost:1971/components-showcase.html to see all components!"
```
*User gets "not found" because it's actually `components_showcase.html` with underscore*

**✅ Right (Do this):**
```
1. Take screenshot of http://localhost:1971/components_showcase.html
2. Verify it loads and shows components
3. "Here's what the components showcase looks like: [screenshot]
   Navigate to: http://localhost:1971/components_showcase.html"
```

### Clay-Specific Gotchas

- **Filename conversion:** Clay converts `-` to `_` in generated HTML
  - Source: `components-showcase.clj`
  - Output: `components_showcase.html` ← Note the underscore!
- **Default page:** `index.html` serves at root URL automatically
- **No file listing:** Clay doesn't show file browser, must know exact URLs

### When Screenshots Are Required

- ✅ Providing URLs to user
- ✅ Claiming something "works" or "renders correctly"
- ✅ After creating new Clay notebooks
- ✅ After refactoring existing notebooks
- ✅ When debugging rendering issues
- ❌ For simple file operations (Read/Write)
- ❌ For calculations or data processing

---

## Project Architecture Quick Reference

This project implements an AI-powered notebook system where Claude edits living Clojure notebooks:

1. **Babashka MCP Server** - Control plane (file operations)
2. **Clay/Noj JVM Process** - Compute engine (scientific computing)
3. **File Watching** - Auto-renders `notebooks/index.clj` to HTML
4. **nREPL Integration** - Programmatic access for AI agents

See `docs/clay-nrepl-mcp-architecture.md` for complete details.

---

## Code Visibility Control

Clay provides multiple methods to hide code and show only visualizations:

### Global Configuration (Recommended)

Configure code hiding in `start-clay.clj` for automatic hiding of all visualizations:

```clojure
(clay/make! {:source-path "notebooks/index.clj"
             :live-reload true
             :browse false
             :show true
             :kindly/options {:kinds-that-hide-code #{:kind/md
                                                      :kind/hiccup
                                                      :kind/plotly
                                                      :kind/table}}})
```

**Benefits:**
- All visualization code automatically hidden
- Comments and documentation remain visible
- Can use intermediate variables for cleaner code structure
- Easy to toggle by modifying the set

### Per-Form Hiding

Use `^:kindly/hide-code` metadata for specific forms:

```clojure
^:kindly/hide-code
(ns index
  (:require [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(def data (fetch-data))
```

### Toggle Strategy

**To show all code:**
- Remove `:kindly/options` from `start-clay.clj`
- Restart server: `bb clay:restart`

**To hide more kinds:**
- Add to the set: `:kind/code`, `:kind/reagent`, etc.
- Server will auto-reload

---

## Notebook Structure Best Practices

### With Code Hiding Enabled

```clojure
^:kindly/hide-code
(ns index
  (:require [scicloj.kindly.v4.kind :as kind]))

;; Use intermediate variables for clean code structure
(def wallet-data
  (fetch-wallet-summary "pb1..."))

(def total-aum
  (get-in wallet-data [:summary :total-aum]))

(def staking-info
  (get-in wallet-data [:delegation :total-staked]))

;; Title and headers (visible as documentation)
;; # 📊 Portfolio Dashboard
;; *Live Analysis*

(kind/md "---")

;; Clean visualization with readable variable names
(kind/hiccup
 [:div {:style {:background "#667eea"
                :padding "40px"
                :border-radius "15px"
                :color "white"}}
  [:h2 "Total Portfolio Summary"]
  [:div
   [:div "Total AUM"]
   [:div {:style {:font-size "2em"}} total-aum]]])
```

### Without Code Hiding

If you want code visible (for educational/tutorial content):
- Remove `:kindly/options` from config
- Use comments to explain each step
- Consider using `kind/code` to highlight important code blocks

---

## Kindly Visualization Types

### Available Kinds

| Kind | Purpose | Example |
|------|---------|---------|
| `kind/md` | Markdown formatting | Headers, paragraphs, lists |
| `kind/hiccup` | HTML/CSS layouts | Custom cards, grids, styled content |
| `kind/plotly` | Interactive charts | Bar, pie, line, scatter plots |
| `kind/table` | Data tables | Tabular data with columns/rows |
| `kind/code` | Code highlighting | Show formatted code examples |
| `kind/hidden` | Evaluate but don't render | Helper functions, utilities |

### Hiccup Patterns

**Metric Cards:**
```clojure
(kind/hiccup
 [:div {:style {:display "grid"
                :grid-template-columns "repeat(3, 1fr)"
                :gap "20px"}}
  [:div {:style {:background "linear-gradient(135deg, #10b981 0%, #059669 100%)"
                 :padding "30px"
                 :border-radius "15px"
                 :color "white"
                 :text-align "center"}}
   [:div {:style {:font-size "0.9em"}} "Label"]
   [:div {:style {:font-size "2.5em" :font-weight "bold"}} value]
   [:div {:style {:font-size "0.85em"}} "Description"]]])
```

**Info Boxes:**
```clojure
(kind/hiccup
 [:div {:style {:background "#dbeafe"
                :padding "20px"
                :border-radius "8px"
                :border-left "4px solid #3b82f6"}}
  [:h4 {:style {:margin "0 0 10px 0"}} "Title"]
  [:p {:style {:margin "0"}} "Content"]])
```

### Plotly Charts

**Pie Chart:**
```clojure
(kind/plotly
 {:data [{:type "pie"
          :labels ["A" "B" "C"]
          :values [100 200 300]
          :hole 0.4
          :marker {:colors ["#10b981" "#3b82f6" "#f59e0b"]}}]
  :layout {:title {:text "Distribution"
                   :font {:size 24}}
           :width 700
           :height 500}})
```

**Bar Chart:**
```clojure
(kind/plotly
 {:data [{:type "bar"
          :name "Series 1"
          :x ["A" "B"]
          :y [100 200]
          :marker {:color "#10b981"}}]
  :layout {:barmode "group"
           :xaxis {:title "Category"}
           :yaxis {:title "Value"}}})
```

### Tables

```clojure
(kind/table
 {:column-names ["Name" "Value" "Status"]
  :row-vectors [["Item 1" "$100" "✅ Active"]
                ["Item 2" "$200" "🔒 Locked"]]})
```

---

## Live Development Workflow

### File Watching

Clay automatically watches `notebooks/index.clj`:
1. Edit the file
2. Save changes
3. Browser auto-refreshes (if `:live-reload true`)
4. See results immediately

**No manual rebuild needed!**

### Screenshot Verification

Take screenshots to verify rendering:

```bash
npm run screenshot http://localhost:1971 output.png
```

Then view with Read tool to confirm:
- Code visibility settings working
- Styling renders correctly
- No syntax errors visible
- Layout matches expectations

### Iterative Development Pattern

1. Edit `notebooks/index.clj`
2. Save (Clay auto-reloads)
3. Take screenshot
4. Verify rendering
5. Repeat

**Pro tip:** Keep the browser open alongside your editor to see changes in real-time without screenshots.

---

## Common Patterns

### Fetching Live Data

```clojure
;; Fetch once, use multiple times
(def market-data
  (fetch-current-hash-statistics))

(def hash-price
  (get-in market-data [:price :usd]))

(def circulation
  (get-in market-data [:circulation :total]))

;; Use in visualizations
(kind/hiccup
 [:div "HASH: $" hash-price])
```

### Gradient Backgrounds

```clojure
{:background "linear-gradient(135deg, #10b981 0%, #059669 100%)"} ; Green
{:background "linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)"} ; Blue
{:background "linear-gradient(135deg, #f59e0b 0%, #d97706 100%)"} ; Orange
{:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"} ; Purple
```

### Responsive Grids

```clojure
{:display "grid"
 :grid-template-columns "repeat(3, 1fr)"  ; 3 equal columns
 :gap "20px"}
```

---

## Troubleshooting

### Code Still Showing

1. **Check configuration:** Verify `:kindly/options` in `start-clay.clj`
2. **Restart server:** `bb clay:restart` to apply config changes
3. **Add metadata:** Use `^:kindly/hide-code` for namespace and special forms
4. **Check kind type:** Ensure using `:kind/hiccup`, not plain hiccup

### Server Not Responding

1. Check status: `bb clay:status`
2. View logs: `bb clay:logs`
3. Restart: `bb clay:restart`
4. Check ports: `lsof -i :1971` and `lsof -i :7890`

### Live Reload Not Working

1. Ensure `:live-reload true` in config
2. Check file path: Must be `notebooks/index.clj`
3. Verify file saves (some editors need explicit save)
4. Check console for Clay watch messages

---

*Last updated: 2025-11-16*
