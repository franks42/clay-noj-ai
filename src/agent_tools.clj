(ns agent-tools
  "MCP tools for inter-agent communication.

   Provides tools for Claude instances to spawn, manage, and communicate
   with other Claude instances. Built on top of claude-service.

   Tools:
   - agent-list-workers: List all active worker instances
   - agent-spawn-worker: Spawn a new worker instance
   - agent-send-task: Send a task to a worker
   - agent-kill-worker: Terminate a worker instance"
  (:require [claude-service :as cs]
            [telemere-lite.core :as tel]))

;; =============================================================================
;; Safeguards - Limits and tracking
;; =============================================================================

(defonce max-workers (atom 5))
(defonce max-requests-per-worker (atom 20))
(defonce max-total-requests (atom 100))

(defonce session-stats
  (atom {:spawns 0
         :requests 0
         :kills 0}))

;; =============================================================================
;; Helper functions
;; =============================================================================

(defn count-active-workers
  "Count currently active Claude instances."
  []
  (count (cs/list-services)))

(defn check-worker-limit!
  "Check if we can spawn another worker, throw if limit exceeded."
  []
  (when (>= (count-active-workers) @max-workers)
    (tel/log! {:level :warn
               :id :agent-tools/limit-exceeded
               :data {:current (count-active-workers)
                      :limit @max-workers}})
    (throw (ex-info "Worker limit exceeded"
                    {:limit @max-workers
                     :current (count-active-workers)}))))

(defn track-spawn!
  "Track a spawn action in session stats."
  []
  (swap! session-stats update :spawns inc))

(defn track-request!
  "Track a request action in session stats."
  []
  (swap! session-stats update :requests inc))

(defn track-kill!
  "Track a kill action in session stats."
  []
  (swap! session-stats update :kills inc))

(defn get-session-stats
  "Get current session statistics."
  []
  @session-stats)

(defn reset-session-stats!
  "Reset session statistics."
  []
  (reset! session-stats {:spawns 0 :requests 0 :kills 0}))

;; =============================================================================
;; Tool Registration Helper
;; =============================================================================

(defn register-tool!
  "Register an MCP tool. This is a placeholder - actual registration
   depends on how the MCP server handles dynamic tools."
  [tool-name handler metadata]
  (tel/log! {:level :info
             :id :agent-tools/tool-registered
             :data {:tool-name tool-name}})
  ;; Store in a registry atom for now
  ;; Actual MCP registration TBD based on server capabilities
  {:tool-name tool-name
   :handler handler
   :metadata metadata})

;; =============================================================================
;; Initialization
;; =============================================================================

(defn init!
  "Initialize agent tools system."
  []
  (tel/log! {:level :info
             :id :agent-tools/initialized
             :data {:max-workers @max-workers
                    :max-requests @max-total-requests}})
  (reset-session-stats!)
  :initialized)

;; Auto-initialize on load
(init!)
