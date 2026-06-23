(ns crazy_eights.simulation.app
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]
            [crazy_eights.simulation.strategy :as strategy]
            [crazy_eights.web.paths :as paths]))

(def default-step-budget 500)

(defn random-simulation-name []
  (str "sim-" (subs (str (random-uuid)) 0 8)))

(defn delay-fn
  ([seconds]
   #(Thread/sleep (long (* seconds 1000))))
  ([_seconds sleep-fn]
   sleep-fn))

(defn- player-name [simulation-name seat]
  (str simulation-name "-p" (inc seat)))

(defn- join-players! [store game-id simulation-name player-count]
  (mapv (fn [seat]
          (app/join-game! store game-id (player-name simulation-name seat)))
        (range player-count)))

(defn- game-summary [game simulation-name player-count]
  {:game-id (:game-id game)
   :observer-id (:observer-id game)
   :observer-path (paths/observer (:game-id game) (:observer-id game))
   :player-count player-count
   :simulation-name simulation-name})

(defn start-game!
  ([store player-count] (start-game! store player-count {}))
  ([store player-count {:keys [simulation-name]}]
   (let [simulation-name (or simulation-name (random-simulation-name))
         {:keys [game-id]} (app/create-game! store)
         players (join-players! store game-id simulation-name player-count)
         host-id (:player-id (first players))]
     (app/start-game! store game-id host-id {:deck (app/valid-start-deck player-count)})
     (game-summary (app/get-game store game-id) simulation-name player-count))))

(defn- current-player-id [game]
  (let [seat (get-in game [:state :current-player])]
    (some (fn [[player-id player]]
            (when (= seat (:seat player))
              player-id))
          (:players game))))

(defn- submit-action! [store game-id player-id action]
  (case (:type action)
    :play-card (app/play-card! store game-id player-id (:card action) (:declared-suit action))
    :draw-card (app/draw-card! store game-id player-id)
    :pass-turn (app/pass-turn! store game-id player-id)))

(defn- finished? [game]
  (model/game-over? (:state game)))

(defn run-to-completion!
  ([store simulation] (run-to-completion! store simulation {}))
  ([store {:keys [game-id] :as simulation} {:keys [step-budget]
                                            wait-fn :delay-fn
                                            :or {wait-fn (fn [] nil)
                                                 step-budget default-step-budget}}]
   (loop [steps-left step-budget]
     (let [game (app/get-game store game-id)]
       (cond
         (or (nil? game) (nil? (:state game)))
         (assoc simulation :status :missing-game :steps-left steps-left)

         (or (finished? game) (zero? steps-left))
         (assoc simulation
                :status (get-in game [:state :status])
                :steps-left steps-left)

         :else
         (let [player-id (current-player-id game)
               action (strategy/choose-action (:state game))]
           (wait-fn)
           (submit-action! store game-id player-id action)
           (recur (dec steps-left))))))))
