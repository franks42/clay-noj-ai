(ns claude-service
  "Multi-instance Claude Code subprocess service with async messaging.

   Architecture:
   - Multiple named Claude instances (spawn, list, kill)
   - Async request/response with message queues
   - Request correlation via UUIDs
   - Registry for Claude discovery
   - Router for Claude-to-Claude communication

   Usage:
   1. Load: (local-load-file \"src/claude_service.clj\")
   2. Spawn: (claude-service/spawn! \"researcher\")
   3. Ask sync: (claude-service/ask \"researcher\" \"Find issues\")
   4. Ask async: (claude-service/ask-async \"researcher\" \"Analyze code\")
   5. Poll: (claude-service/poll-response request-id)
   6. List: (claude-service/list-services)
   7. Kill: (claude-service/kill! \"researcher\")"
  (:require [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [telemere-lite.core :as tel]))

;; =============================================================================
;; State - Multi-service registry with message queues
;; =============================================================================

(defonce registry
  (atom {}))

(defonce response-queues
  ;; Map of request-id -> {:status :pending/:complete/:error, :result ..., :claude ...}
  (atom {}))

(defonce request-counter
  (atom 0))

;; =============================================================================
;; Logging Configuration
;; =============================================================================

#_{:clj-kondo/ignore [:unresolved-var]}
(defonce logging-initialized
  (do
    ;; Enable the logging runtime
    (tel/set-enabled! true)

    ;; Add file handler for structured JSON logging
    (tel/add-file-handler!
     :agent-log
     "logs/agent-communication.log"
     {:async {:mode :dropping :buffer-size 1024 :n-threads 1}})

    ;; Add stdout handler for development visibility
    (tel/add-stdout-handler! :stdout {})

    true))

;; =============================================================================
;; Configuration
;; =============================================================================

(def claude-path
  "/Users/franksiebenlist/.claude/local/claude")

(def claude-base-args
  ["-p" "--verbose" "--input-format" "stream-json" "--output-format" "stream-json"])

(def model-ids
  "Available Claude models with their identifiers."
  {:haiku  "claude-3-5-haiku-20241022"
   :sonnet "claude-sonnet-4-20250514"
   :opus   "claude-opus-4-20250514"})

(defn build-claude-args
  "Build command args, optionally with model selection."
  [model]
  (if model
    (let [model-id (get model-ids model model)] ; allow keyword or string
      (into claude-base-args ["--model" model-id]))
    claude-base-args))

;; =============================================================================
;; Internal helpers
;; =============================================================================

(defn generate-request-id
  "Generate unique request ID with counter for ordering."
  [claude-name]
  (let [n (swap! request-counter inc)]
    (format "%s-%06d-%s" claude-name n (subs (str (random-uuid)) 0 8))))

(defn read-jsonl-response
  "Read JSONL lines from reader until we get a result message."
  [reader]
  (loop [responses {}]
    (if-let [line (.readLine reader)]
      (let [parsed (json/parse-string line true)
            msg-type (:type parsed)]
        (case msg-type
          "system" (recur (assoc responses :init parsed))
          "assistant" (recur (assoc responses :assistant parsed))
          "result" (assoc responses :result parsed)
          (recur responses)))
      responses)))

(defn send-message!
  "Send a user message to Claude via stdin."
  [writer content]
  (let [msg {:type "user"
             :message {:role "user"
                       :content content}}]
    (.write writer (json/generate-string msg))
    (.write writer "\n")
    (.flush writer)))

(defn get-service
  "Get service by name, throw if not found."
  [name]
  (if-let [svc (get @registry name)]
    svc
    (throw (ex-info (str "Claude service '" name "' not found")
                    {:name name
                     :available (keys @registry)}))))

;; =============================================================================
;; Service Lifecycle
;; =============================================================================

(defn spawn!
  "Spawn a new named Claude instance.

   Options:
     :model - :haiku, :sonnet, :opus (or full model string)

   Examples:
     (spawn! \"worker\")
     (spawn! \"fetcher\" :model :haiku)
     (spawn! \"analyzer\" :model :opus)"
  [name & {:keys [model]}]
  (when (get @registry name)
    (throw (ex-info (str "Claude service '" name "' already exists")
                    {:name name})))

  (let [model-name (when model (get model-ids model (str model)))]
    (tel/log! {:level :info
               :id :claude-service/spawning
               :data {:instance-name name :model model-name}})

    (let [args (build-claude-args model)
          proc (p/process (into [claude-path] args)
                          {:shutdown p/destroy-tree})
          stdin (:in proc)
          stdout (:out proc)
          writer (io/writer stdin)
          reader (io/reader stdout)
          service {:name name
                   :process proc
                   :stdin stdin
                   :stdout stdout
                   :writer writer
                   :reader reader
                   :model model
                   :status :running
                   :created-at (System/currentTimeMillis)
                   :session-id nil
                   :request-count 0}]

      (swap! registry assoc name service)
      (tel/log! {:level :info
                 :id :claude-service/spawned
                 :data {:instance-name name :model model-name}})

      {:name name
       :status :running
       :model model
       :pid (try (.pid (:proc proc)) (catch Exception _ nil))})))

(defn kill!
  "Kill a named Claude instance."
  [name]
  (let [{:keys [process writer reader]} (get-service name)]
    (tel/log! {:level :info
               :id :claude-service/killing
               :data {:instance-name name}})

    ;; Close streams
    (when writer
      (try (.close writer) (catch Exception _)))
    (when reader
      (try (.close reader) (catch Exception _)))

    ;; Destroy process
    (when process
      (try (p/destroy-tree process) (catch Exception _)))

    (swap! registry dissoc name)
    (tel/log! {:level :info
               :id :claude-service/killed
               :data {:instance-name name}})

    {:name name :status :killed}))

(defn kill-all!
  "Kill all Claude instances."
  []
  (let [names (keys @registry)]
    (doseq [name names]
      (try (kill! name) (catch Exception e
                          (tel/log! {:level :error
                                     :id :claude-service/kill-error
                                     :data {:instance-name name :error (.getMessage e)}}))))
    {:killed names}))

(defn spawn-from-session!
  "Spawn a new Claude instance that resumes from an existing session.

   The new instance inherits all conversation history from that session,
   then diverges independently. Useful for:
   - Checkpoint/fork pattern (build context once, fork workers)
   - Federated workflows (laptop builds context, cloud computes)

   Args:
     name       - Unique name for this instance
     session-id - Session ID to resume from

   Options:
     :model - :haiku, :sonnet, :opus (or full model string)

   Returns:
     Service info map with :forked-from metadata"
  [name session-id & {:keys [model]}]
  (when (get @registry name)
    (throw (ex-info (str "Claude service '" name "' already exists")
                    {:name name})))

  (when (str/blank? session-id)
    (throw (ex-info "session-id is required for spawn-from-session!"
                    {:name name})))

  (let [model-name (when model (get model-ids model (str model)))]
    (tel/log! {:level :info
               :id :claude-service/spawning-from-session
               :data {:instance-name name :session-id session-id :model model-name}})

    ;; Add --resume to load existing session history
    (let [base-args (build-claude-args model)
          resume-args (vec (concat [claude-path] base-args ["--resume" session-id]))
          proc (p/process resume-args
                          {:shutdown p/destroy-tree})
          stdin (:in proc)
          stdout (:out proc)
          writer (io/writer stdin)
          reader (io/reader stdout)
          service {:name name
                   :process proc
                   :stdin stdin
                   :stdout stdout
                   :writer writer
                   :reader reader
                   :status :running
                   :created-at (System/currentTimeMillis)
                   :session-id session-id  ;; Starts with parent's session-id
                   :forked-from session-id ;; Track lineage
                   :model model
                   :request-count 0}]

      (swap! registry assoc name service)
      (tel/log! {:level :info
                 :id :claude-service/forked
                 :data {:instance-name name :session-id session-id :model model}})

      {:name name
       :status :running
       :model model
       :forked-from session-id
       :pid (try (.pid (:proc proc)) (catch Exception _ nil))})))

(defn fork!
  "Fork a running Claude instance into a new named instance.
   The new instance inherits all conversation context from the source.

   Options:
     :model - :haiku, :sonnet, :opus (or full model string)

   Examples:
     (spawn! \"base\")
     (ask \"base\" \"Learn about project X...\")
     (fork! \"base\" \"worker-1\")
     (fork! \"base\" \"worker-2\" :model :haiku)"
  [source-name new-name & {:keys [model]}]
  (let [source (get-service source-name)
        session-id (:session-id source)]
    (when-not session-id
      (throw (ex-info (str "Source '" source-name "' has no session-id yet. Send at least one message first.")
                      {:source source-name})))
    (spawn-from-session! new-name session-id :model model)))

(defn get-lineage
  "Get the fork lineage for an instance."
  [name]
  (let [svc (get-service name)]
    {:name name
     :session-id (:session-id svc)
     :forked-from (:forked-from svc)
     :created-at (:created-at svc)}))

(defn list-forks
  "List all instances forked from a given session-id."
  [session-id]
  (into {}
        (filter (fn [[_ svc]] (= session-id (:forked-from svc)))
                @registry)))

;; =============================================================================
;; Registry / Directory Service
;; =============================================================================

(defn list-services
  "List all running Claude instances."
  []
  (into {}
        (for [[name svc] @registry]
          [name {:status (:status svc)
                 :created-at (:created-at svc)
                 :session-id (:session-id svc)
                 :request-count (:request-count svc)
                 :pid (try (.pid (:proc (:process svc))) (catch Exception _ nil))}])))

(defn service-info
  "Get info about a specific Claude instance."
  [name]
  (let [svc (get-service name)]
    {:name name
     :status (:status svc)
     :model (:model svc)
     :created-at (:created-at svc)
     :session-id (:session-id svc)
     :request-count (:request-count svc)
     :pid (try (.pid (:proc (:process svc))) (catch Exception _ nil))}))

(defn service-exists?
  "Check if a named service exists."
  [name]
  (boolean (get @registry name)))

;; =============================================================================
;; Synchronous API
;; =============================================================================

(defn ask
  "Send a prompt to a named Claude and wait for response.
   Returns the result string."
  ([name prompt] (ask name prompt {}))
  ([name prompt opts]
   (let [{:keys [writer reader]} (get-service name)
         _ (send-message! writer prompt)
         response (read-jsonl-response reader)
         session-id (get-in response [:result :session_id])]

     ;; Update service state
     (swap! registry update name
            (fn [s]
              (-> s
                  (update :request-count inc)
                  (assoc :session-id session-id))))

     (if (:full? opts)
       response
       (get-in response [:result :result])))))

;; =============================================================================
;; Asynchronous API
;; =============================================================================

(defn ask-async
  "Send a prompt to a named Claude asynchronously.
   Returns request-id immediately. Use poll-response to get result."
  [name prompt]
  (let [_ (get-service name) ;; validate exists
        request-id (generate-request-id name)]

    ;; Initialize response queue entry
    (swap! response-queues assoc request-id
           {:status :pending
            :claude name
            :prompt prompt
            :submitted-at (System/currentTimeMillis)})

    ;; Process in background
    (future
      (try
        (let [result (ask name prompt)]
          (swap! response-queues assoc request-id
                 {:status :complete
                  :claude name
                  :result result
                  :completed-at (System/currentTimeMillis)}))
        (catch Exception e
          (swap! response-queues assoc request-id
                 {:status :error
                  :claude name
                  :error (.getMessage e)
                  :completed-at (System/currentTimeMillis)}))))

    request-id))

(defn poll-response
  "Poll for a response by request-id.
   Returns {:status :pending/:complete/:error, ...}"
  [request-id]
  (if-let [response (get @response-queues request-id)]
    response
    {:status :not-found :request-id request-id}))

(defn wait-response
  "Wait for a response by request-id with timeout.
   Returns response when complete or :timeout."
  ([request-id] (wait-response request-id 60000))
  ([request-id timeout-ms]
   (let [start (System/currentTimeMillis)]
     (loop []
       (let [response (poll-response request-id)]
         (cond
           ;; Complete or error - return
           (#{:complete :error :not-found} (:status response))
           response

           ;; Timeout
           (> (- (System/currentTimeMillis) start) timeout-ms)
           {:status :timeout :request-id request-id :timeout-ms timeout-ms}

           ;; Still pending - wait and retry
           :else
           (do
             (Thread/sleep 100)
             (recur))))))))

(defn list-pending
  "List all pending requests."
  []
  (into {}
        (filter (fn [[_ v]] (= :pending (:status v)))
                @response-queues)))

(defn list-responses
  "List all responses (optionally filter by status)."
  ([] @response-queues)
  ([status]
   (into {}
         (filter (fn [[_ v]] (= status (:status v)))
                 @response-queues))))

(defn clear-responses
  "Clear completed/error responses from queue."
  []
  (let [pending (list-pending)]
    (reset! response-queues pending)
    {:cleared (- (count @response-queues) (count pending))}))

;; =============================================================================
;; Claude Router - Claude-to-Claude Communication
;; =============================================================================

(defn relay
  "Relay a message from one Claude to another.
   Returns request-id for async tracking."
  [from-name to-name prompt]
  (tel/log! {:level :debug
             :id :claude-service/relay
             :data {:from from-name :to to-name}})
  (ask-async to-name prompt))

(defn broadcast
  "Send a message to all Claude instances (except sender).
   Returns map of {name -> request-id}."
  ([prompt] (broadcast nil prompt))
  ([exclude-name prompt]
   (let [targets (if exclude-name
                   (remove #(= exclude-name %) (keys @registry))
                   (keys @registry))]
     (into {}
           (for [name targets]
             [name (ask-async name prompt)])))))

;; =============================================================================
;; Convenience / Backward Compatibility
;; =============================================================================

(defn start!
  "Start a default Claude instance (backward compatibility).
   Same as (spawn! \"default\")."
  []
  (spawn! "default"))

(defn stop!
  "Stop the default Claude instance (backward compatibility).
   Same as (kill! \"default\")."
  []
  (kill! "default"))

(defn status
  "Get status of default instance or all services."
  ([]
   (if (service-exists? "default")
     (service-info "default")
     {:services (list-services)
      :pending-requests (count (list-pending))
      :total-responses (count @response-queues)}))
  ([name]
   (service-info name)))

(defn restart!
  "Restart the default Claude instance."
  []
  (when (service-exists? "default")
    (kill! "default"))
  (spawn! "default"))

;; =============================================================================
;; MCP Server Context - For spawned Claudes to discover their environment
;; =============================================================================

(defn get-context
  "Get context info that a spawned Claude needs to know about its environment.
   This can be passed to a Claude or queried by it."
  []
  {:mcp-server {:host "localhost"
                :nrepl-port 7888
                :type "nrepl-mcp-server"}
   :services (list-services)
   :router {:relay-fn "claude-service/relay"
            :broadcast-fn "claude-service/broadcast"}
   :self-discovery "Use (claude-service/list-services) to find other Claudes"})

;; =============================================================================
;; Examples
;; =============================================================================

(comment
  ;; ===========================================
  ;; Basic: Spawn and ask named instances
  ;; ===========================================
  (spawn! "researcher")
  (spawn! "reviewer")
  (list-services)
  (ask "researcher" "What security patterns should I look for?")

  ;; ===========================================
  ;; Checkpoint & Fork Pattern (token saver!)
  ;; ===========================================

  ;; 1. Build shared context in base instance
  (spawn! "base")
  (ask "base" "You are analyzing project clay-noj-ai")
  (ask "base" "Key files: src/claude_service.clj, scripts/ask-claude")
  (ask "base" "Standards: functional style, no side effects")

  ;; 2. Fork workers that inherit all context
  (fork! "base" "security-worker")
  (fork! "base" "perf-worker")
  (fork! "base" "doc-worker")

  ;; 3. Each worker has full context, now specialize
  (ask-async "security-worker" "Focus on security vulnerabilities only")
  (ask-async "perf-worker" "Focus on performance issues only")
  (ask-async "doc-worker" "Generate API documentation")

  ;; 4. Check lineage
  (get-lineage "security-worker")
  ;; => {:name "security-worker", :forked-from "abc-123-...", ...}

  ;; 5. List all forks from a session
  (list-forks (:session-id (service-info "base")))

  ;; ===========================================
  ;; Async workflow
  ;; ===========================================
  (def req-id (ask-async "researcher" "Analyze the codebase"))
  (poll-response req-id)  ;; {:status :pending ...}
  (poll-response req-id)  ;; {:status :complete :result "..."}
  (wait-response req-id 30000)

  ;; ===========================================
  ;; Claude-to-Claude routing
  ;; ===========================================
  (relay "researcher" "reviewer" "Please review these findings: ...")
  (broadcast "New context: we're focusing on Python security")

  ;; ===========================================
  ;; Spawn from session-id (federated/cross-machine)
  ;; ===========================================
  ;; On machine A:
  (spawn! "local")
  (ask "local" "Build context...")
  (:session-id (service-info "local"))
  ;; => "xyz-789-..."

  ;; On machine B (with access to session files):
  (spawn-from-session! "remote-worker" "xyz-789-...")

  ;; ===========================================
  ;; Discovery and context
  ;; ===========================================
  (get-context)
  (service-info "researcher")

  ;; ===========================================
  ;; Cleanup
  ;; ===========================================
  (kill! "researcher")
  (kill-all!)
  (clear-responses))
