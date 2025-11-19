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
;; MCP Tool: agent-list-workers
;; =============================================================================

(def list-workers-metadata
  {:description "List all active worker Claude instances"
   :inputSchema
   {:type "object"
    :properties {}
    :required []}})

(defn list-workers-handler
  "Handler for agent-list-workers tool."
  [_params]
  (let [services (cs/list-services)
        workers (for [[name info] services]
                  {:name name
                   :model (:model info)
                   :status (:status info)
                   :pid (:pid info)
                   :requests (:request-count info)
                   :created-at (:created-at info)
                   :session-id (:session-id info)})]
    (tel/log! {:level :debug
               :id :agent-tools/list-workers
               :data {:count (count workers)}})
    {:workers (vec workers)
     :count (count workers)
     :limit @max-workers
     :session-stats @session-stats}))

;; =============================================================================
;; MCP Tool: agent-spawn-worker
;; =============================================================================

(def spawn-worker-metadata
  {:description "Spawn a new worker Claude instance for task execution"
   :inputSchema
   {:type "object"
    :properties
    {:name {:type "string"
            :description "Unique name for the worker"}
     :model {:type "string"
             :enum ["haiku" "sonnet" "opus"]
             :description "Model to use (default: haiku)"}}
    :required ["name"]}})

(defn spawn-worker-handler
  "Handler for agent-spawn-worker tool."
  [{:keys [name model] :or {model "haiku"}}]
  ;; Check worker limit
  (check-worker-limit!)

  ;; Spawn the worker
  (let [model-kw (keyword model)
        result (cs/spawn! name :model model-kw)]

    ;; Track stats
    (track-spawn!)

    ;; Log
    (tel/log! {:level :info
               :id :agent-tools/worker-spawned
               :data {:worker-name name
                      :model model
                      :pid (:pid result)}})

    {:status "spawned"
     :worker name
     :model model
     :pid (:pid result)
     :session-stats @session-stats}))

;; =============================================================================
;; MCP Tool: agent-send-task
;; =============================================================================

(def send-task-metadata
  {:description "Send a task prompt to a worker and wait for response"
   :inputSchema
   {:type "object"
    :properties
    {:worker {:type "string"
              :description "Name of worker to send task to"}
     :prompt {:type "string"
              :description "Task prompt for the worker"}}
    :required ["worker" "prompt"]}})

(defn send-task-handler
  "Handler for agent-send-task tool."
  [{:keys [worker prompt]}]
  ;; Log send
  (tel/log! {:level :info
             :id :agent-tools/task-sent
             :data {:worker worker
                    :prompt-length (count prompt)}})

  (let [start-time (System/currentTimeMillis)
        response (cs/ask worker prompt)
        elapsed (- (System/currentTimeMillis) start-time)]

    ;; Track stats
    (track-request!)

    ;; Log receive
    (tel/log! {:level :info
               :id :agent-tools/task-received
               :data {:worker worker
                      :response-length (count response)
                      :elapsed-ms elapsed}})

    {:status "complete"
     :worker worker
     :response response
     :elapsed-ms elapsed
     :session-stats @session-stats}))

;; =============================================================================
;; MCP Tool: agent-kill-worker
;; =============================================================================

(def kill-worker-metadata
  {:description "Terminate a worker Claude instance"
   :inputSchema
   {:type "object"
    :properties
    {:worker {:type "string"
              :description "Name of worker to kill"}}
    :required ["worker"]}})

(defn kill-worker-handler
  "Handler for agent-kill-worker tool."
  [{:keys [worker]}]
  ;; Log kill
  (tel/log! {:level :info
             :id :agent-tools/worker-killing
             :data {:worker worker}})

  (let [_result (cs/kill! worker)]
    ;; Track stats
    (track-kill!)

    ;; Log complete
    (tel/log! {:level :info
               :id :agent-tools/worker-killed
               :data {:worker worker}})

    {:status "killed"
     :worker worker
     :session-stats @session-stats}))

;; =============================================================================
;; Tool Registry
;; =============================================================================

(defonce tool-registry (atom {}))

(defn register-tool!
  "Register an MCP tool in local registry."
  [tool-name handler metadata]
  (swap! tool-registry assoc tool-name {:handler handler :metadata metadata})
  (tel/log! {:level :info
             :id :agent-tools/tool-registered
             :data {:tool-name tool-name}})
  {:tool-name tool-name :status :registered})

(defn get-tool
  "Get a registered tool by name."
  [tool-name]
  (get @tool-registry tool-name))

(defn list-tools
  "List all registered tools."
  []
  (keys @tool-registry))

(defn call-tool
  "Call a registered tool by name with params."
  [tool-name params]
  (if-let [tool (get-tool tool-name)]
    ((:handler tool) params)
    (throw (ex-info (str "Tool not found: " tool-name)
                    {:tool-name tool-name
                     :available (list-tools)}))))

;; =============================================================================
;; Initialization
;; =============================================================================

(defn init!
  "Initialize agent tools system."
  []
  (reset-session-stats!)

  ;; Register tools
  (register-tool! "agent-list-workers" list-workers-handler list-workers-metadata)
  (register-tool! "agent-spawn-worker" spawn-worker-handler spawn-worker-metadata)
  (register-tool! "agent-send-task" send-task-handler send-task-metadata)
  (register-tool! "agent-kill-worker" kill-worker-handler kill-worker-metadata)

  (tel/log! {:level :info
             :id :agent-tools/initialized
             :data {:max-workers @max-workers
                    :max-requests @max-total-requests
                    :tools (vec (list-tools))}})
  :initialized)

;; Auto-initialize on load
(init!)
