(ns crazy_eights.web.page
  (:require [hiccup.util :as u]
            [hiccup2.core :as h]))

(defn observer-page []
  (str
   (h/html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:title "Crazy Eights Simulation Observer"]]
     [:body
      [:h1 "Crazy Eights Simulation Observer"]
      [:form {:id "simulation-form"}
       [:label {:for "player-count"} "Players"]
       [:input {:id "player-count"
                :name "player-count"
                :type "number"
                :min 2
                :max 10
                :value 4}]
       [:button {:id "start-simulation"
                 :type "button"}
        "start simulation"]]
      [:p {:id "status"} "idle"]
      [:pre {:id "log"}]
      [:script
       (u/raw-string
        "const form = document.getElementById('simulation-form');\n"
        "const button = document.getElementById('start-simulation');\n"
        "const log = document.getElementById('log');\n"
        "const status = document.getElementById('status');\n"
        "let source = null;\n"
        "async function startSimulation(event) {\n"
        "  if (event) event.preventDefault();\n"
        "  log.textContent = '';\n"
        "  status.textContent = 'starting';\n"
        "  if (source) source.close();\n"
        "  const playerCount = document.getElementById('player-count').value;\n"
        "  try {\n"
        "    const response = await fetch('/simulations', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({ 'player-count': playerCount })});\n"
        "    const data = await response.json();\n"
        "    if (!response.ok) { status.textContent = 'error'; log.textContent = data.error || 'failed to start simulation'; return; }\n"
        "    status.textContent = 'running';\n"
        "    source = new EventSource(`/simulations/${data['simulation-id']}/events`);\n"
        "    source.addEventListener('log', (e) => { const msg = JSON.parse(e.data); log.textContent += msg.message + '\\n'; });\n"
        "    source.onerror = () => { status.textContent = 'stream closed'; log.textContent += '[stream closed or failed]\\n'; };\n"
        "  } catch (error) {\n"
        "    status.textContent = 'error';\n"
        "    log.textContent = String(error);\n"
        "  }\n"
        "}\n"
        "form.addEventListener('submit', startSimulation);\n"
        "button.addEventListener('click', startSimulation);")]]])))
