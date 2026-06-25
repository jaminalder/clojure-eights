(ns crazy_eights.simulation.app
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]
            [crazy_eights.simulation.strategy :as strategy]))

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
   :player-count player-count
   :simulation-name simulation-name
   :players (->> (:players game)
                 vals
                 (sort-by :seat)
                 vec)})

(defn start!
  [store {:keys [player-count simulation-name]}]
  (let [simulation-name (or simulation-name (random-simulation-name))
        {:keys [game-id]} (app/create-game! store)
        players (join-players! store game-id simulation-name player-count)
        host-id (:player-id (first players))]
    (app/start-game! store game-id host-id {:deck (app/valid-start-deck player-count)})
    (game-summary (app/get-game store game-id) simulation-name player-count)))

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

(defn- strategy-id [strategy]
  (if (map? strategy)
    (:id strategy)
    :anonymous))

(defn- choose-fn [strategy]
  (if (map? strategy)
    (:choose strategy)
    strategy))

(defn- seat-strategies [player-count {:keys [strategy strategies]}]
  (let [choices (or (seq strategies)
                    (when strategy [strategy])
                    [strategy/first-playable])]
    (mapv #(or % strategy/first-playable)
          (take player-count (cycle choices)))))

(defn- choose-action [strategies state]
  (let [seat (:current-player state)
        choose (choose-fn (nth strategies seat))]
    (choose (strategy/observation state))))

(defn- record-action [metrics action]
  (-> metrics
      (update :steps inc)
      (update (case (:type action)
                :play-card :plays
                :draw-card :draws
                :pass-turn :passes)
              inc)))

(defn- run-result [simulation game strategies metrics steps-left status]
  (let [winner (get-in game [:state :winner])]
    (cond-> (assoc simulation
                   :status status
                   :steps-left steps-left
                   :steps (:steps metrics)
                   :draws (:draws metrics)
                   :plays (:plays metrics)
                   :passes (:passes metrics)
                   :winner winner
                   :seat-strategies (mapv strategy-id strategies))
      (some? winner) (assoc :winner-strategy (strategy-id (nth strategies winner))))))

(defn- finished? [game]
  (model/game-over? (:state game)))

(defn- done-status [game steps-left]
  (cond
    (finished? game) (get-in game [:state :status])
    (zero? steps-left) :step-budget-exhausted))

(defn run-to-completion!
  ([store simulation] (run-to-completion! store simulation {}))
  ([store {:keys [game-id player-count] :as simulation} {:keys [step-budget]
                                                         wait-fn :delay-fn
                                                         :as opts
                                                         :or {wait-fn (fn [] nil)
                                                              step-budget default-step-budget}}]
   (let [strategies (seat-strategies player-count opts)]
     (loop [steps-left step-budget
            metrics {:steps 0 :draws 0 :plays 0 :passes 0}]
      (let [game (app/get-game store game-id)]
        (cond
          (or (nil? game) (nil? (:state game)))
          (run-result simulation game strategies metrics steps-left :missing-game)

          (or (finished? game) (zero? steps-left))
          (run-result simulation game strategies metrics steps-left (done-status game steps-left))

          :else
          (let [player-id (current-player-id game)
                action (choose-action strategies (:state game))]
            (wait-fn)
            (submit-action! store game-id player-id action)
            (recur (dec steps-left) (record-action metrics action)))))))))

(defn start-background!
  [store {:keys [player-count delay-seconds step-budget strategy strategies simulation-name]
          supplied-delay-fn :delay-fn
          :or {delay-seconds 0}}]
  (let [started (start! store {:player-count player-count
                               :simulation-name simulation-name})
        wait-fn (or supplied-delay-fn (delay-fn delay-seconds))
        run-options (cond-> {:delay-fn wait-fn}
                      step-budget (assoc :step-budget step-budget)
                      strategy (assoc :strategy strategy)
                      strategies (assoc :strategies strategies))
        result (assoc started :delay-seconds delay-seconds)
        running (future (run-to-completion! store started run-options))]
    (assoc result :future running)))
