(ns crazy_eights.web.page
  (:require [hiccup2.core :as h]))

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
       [:button {:type "submit"} "start simulation"]]
      [:pre {:id "log"}]
      [:script
       "const form = document.getElementById('simulation-form');\n"
       "const log = document.getElementById('log');\n"
       "let source = null;\n"
       "form.addEventListener('submit', async (event) => {\n"
       "  event.preventDefault();\n"
       "  log.textContent = '';\n"
       "  if (source) source.close();\n"
       "  const playerCount = document.getElementById('player-count').value;\n"
       "  const response = await fetch('/simulations', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({ 'player-count': playerCount })});\n"
       "  const data = await response.json();\n"
       "  source = new EventSource(`/simulations/${data.simulation-id}/events`);\n"
       "  source.addEventListener('log', (e) => { const msg = JSON.parse(e.data); log.textContent += msg.message + '\\n'; });\n"
       "});"]]])))
