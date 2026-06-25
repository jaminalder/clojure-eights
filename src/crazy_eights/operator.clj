(ns crazy_eights.operator
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.app.logging :as logging]
            [crazy_eights.domain.model :as model]
            [crazy_eights.runtime :as runtime]
            [crazy_eights.simulation.app :as simulation]
            [crazy_eights.web.paths :as paths]))

(defonce observer-state (atom {}))

(defn- table-status [game]
  (or (get-in game [:state :status])
      (if (:started-once? game)
        :between-games
        :waiting)))

(defn- player-summary [[player-id player]]
  {:player-id player-id
   :seat (:seat player)
   :name (:name player)})

(defn- table-summary [game]
  {:game-id (:game-id game)
   :observer-path (paths/observer (:game-id game) (:observer-id game))
   :status (table-status game)
   :player-count (count (:players game))
   :players (mapv player-summary
                  (sort-by (comp :seat val) (:players game)))})

(defn games []
  (->> (:games @runtime/store)
       vals
       (map table-summary)
       (group-by :status)))

(defn game [game-id]
  (if-let [found-game (app/get-game runtime/store game-id)]
    found-game
    {:error :unknown-game}))

(defn observers []
  (->> @observer-state
       vals
       (sort-by :observer-id)
       vec))

(defn- event-summary [event]
  (:message (logging/event->log-entry event)))

(defn observe! [game-id]
  (if (app/get-game runtime/store game-id)
    (let [observer-id (str "operator-" (random-uuid))
          handler (fn [event]
                    (println (event-summary event)))]
      (app/subscribe! runtime/store game-id observer-id handler)
      (swap! observer-state assoc observer-id {:observer-id observer-id
                                               :game-id game-id})
      {:observer-id observer-id
       :game-id game-id})
    {:error :unknown-game}))

(defn unobserve! [observer-id]
  (if-let [{:keys [game-id]} (get @observer-state observer-id)]
    (do
      (app/unsubscribe! runtime/store game-id observer-id)
      (swap! observer-state dissoc observer-id)
      {:observer-id observer-id
       :game-id game-id
       :status :stopped})
    {:error :unknown-observer}))

(defn unobserve-all! []
  (let [current (observers)]
    (run! #(unobserve! (:observer-id %)) current)
    {:stopped (count current)}))

(defn- valid-simulation-player-count? [player-count]
  (and (integer? player-count)
       (<= 2 player-count model/max-player-count)))

(defn- valid-delay-seconds? [delay-seconds]
  (and (number? delay-seconds)
       (not (neg? delay-seconds))))

(defn- simulation-summary [{:keys [game-id observer-id player-count simulation-name
                                   delay-seconds]
                            running :future}]
  {:game-id game-id
   :observer-id observer-id
   :observer-path (paths/observer game-id observer-id)
   :player-count player-count
   :simulation-name simulation-name
   :delay-seconds delay-seconds
   :future running})

(defn start-sim [player-count delay-seconds]
  (cond
    (not (valid-simulation-player-count? player-count))
    {:error :invalid-player-count}

    (not (valid-delay-seconds? delay-seconds))
    {:error :invalid-delay-seconds}

    :else
    (simulation-summary
     (simulation/start-background! runtime/store {:player-count player-count
                                                  :delay-seconds delay-seconds}))))
