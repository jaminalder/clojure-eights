(ns crazy_eights.app.logging)

(defn- event-level [event]
  (case (:type event)
    :game-finished :info
    :game-started :info
    :player-joined :info
    :game-created :info
    :turn-changed :debug
    :move-made :info
    :info))

(defn- compact-state-summary [state]
  {:status (:status state)
   :winner (:winner state)
   :current-player (:current-player state)
   :draw-count (count (:draw-pile state))
   :passes-in-row (:passes-in-row state)
   :hand-sizes (mapv #(count (:hand %)) (:players state))})

(defn- event-message [event]
  (case (:type event)
    :game-created (str "game created: " (:game-id event))
    :player-joined (str "player joined: " (:player-id event) " seat " (:seat event))
    :game-started (str "game started: " (:game-id event))
    :turn-changed (str "turn changed: player " (:current-player event))
    :game-finished (str "game finished: winner " (:winner event))
    :move-made (str "move made: " (pr-str (:command event)))
    (str "event: " (:type event))))

(defn event->log-entry [event]
  {:level (event-level event)
   :event (:type event)
   :game-id (:game-id event)
   :message (event-message event)
   :data (if-let [state (:state event)]
           (assoc (dissoc event :state)
                  :state-summary (compact-state-summary state))
           event)})

(defn stdout-subscriber []
  (fn [event]
    (prn (event->log-entry event))))
