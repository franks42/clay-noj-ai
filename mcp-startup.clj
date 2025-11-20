;; MCP Server Startup File for clay-noj-ai
;; Loaded automatically by mcp-nrepl-joyride before accepting connections
;;
;; This file registers custom MCP tools for inter-agent communication.
;; Tools registered here will be available to Claude Code immediately.

(binding [*out* *err*]
  (println "🔧 clay-noj-ai: Registering inter-agent MCP tools..."))

;; Load claude service first (provides spawn!/ask/fork!/kill! etc.)
(load-file "src/claude_service.clj")

;; Load agent tools (auto-registers MCP tools via init!)
(load-file "src/agent_tools.clj")

(binding [*out* *err*]
  (println "✅ clay-noj-ai: Inter-agent tools registered"))
