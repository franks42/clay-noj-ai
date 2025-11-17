(ns start-clay
  (:require [scicloj.clay.v2.api :as clay]
            [nrepl.server :as nrepl-server]))

;; Start nREPL server for future AI agent connectivity
(def nrepl-port 7890)
(defonce nrepl-server
  (do
    (println (str "Starting nREPL server on port " nrepl-port "..."))
    (nrepl-server/start-server :port nrepl-port)
    (println (str "nREPL server started on port " nrepl-port))))

;; Start Clay with live-reload (auto file watching + browser refresh)
(println "Starting Clay with live-reload on notebooks/index.clj...")
(clay/make! {:source-path "notebooks/index.clj"
             :live-reload true
             :browse false
             :show true
             :kindly/options {:kinds-that-hide-code #{:kind/md
                                                      :kind/hiccup
                                                      :kind/plotly
                                                      :kind/table}}})

(println "\n===========================================")
(println "Clay/Noj instance is running!")
(println "===========================================")
(println (str "📊 Notebook viewer: http://localhost:1971"))
(println (str "🔌 nREPL server:    localhost:" nrepl-port))
(println "👁️  File watching:   notebooks/index.clj")
(println "===========================================")
(println "\nEdit notebooks/index.clj to see live updates in browser!")
(println "Press Ctrl+C to stop.\n")

;; Keep the process running
@(promise)
