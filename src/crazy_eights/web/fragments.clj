(ns crazy_eights.web.fragments
  "Builds the per-viewer SSE fragment map for a game. The keys match the
  sse-swap targets in views/game-page; sse/fragment-frames encodes this map."
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.web.view_model :as view-model]
            [crazy_eights.web.views :as views]))

(defn game-fragments [store game-id viewer]
  (let [view (view-model/game-view (app/get-game store game-id) viewer)]
    {:status (views/status-html view)
     :game-board (views/board-html view)
     :player-hand (views/hand-html view)}))
