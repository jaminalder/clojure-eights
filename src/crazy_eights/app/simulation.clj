(ns crazy_eights.app.simulation
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.app.logging :as logging]
            [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.model :as model]))

(defn create-service [{:keys [delay-fn]}]
  (atom {:next-simulation-id 0
         :delay-fn (or delay-fn (fn [] (Thread/sleep 500)))
         :simulations {}}))

(defn- next-simulation-id [service-state]
  (str "sim-" (:next-simulation-id service-state)))

(defn get-simulation [service simulation-id]
  (get-in @service [:simulations simulation-id]))

(defn subscribe! [service simulation-id subscriber-id handler]
  (swap! service update-in [:simulations simulation-id :subscribers]
         #(pubsub/subscribe (or % {}) subscriber-id handler))
  subscriber-id)

(defn unsubscribe! [service simulation-id subscriber-id]
  (swap! service update-in [:simulations simulation-id :subscribers]
         #(pubsub/unsubscribe (or % {}) subscriber-id))
  subscriber-id)

(defn- emit! [service simulation-id event]
  (let [subscribers (get-in @service [:simulations simulation-id :subscribers] {})]
    (pubsub/publish subscribers event)))

(defn playable-card [state player]
  (first (filter #(model/playable-card? state %)
                 (model/current-hand state player))))

(defn- play-card-action [card]
  (cond-> {:type :play-card :card card}
    (model/requires-declared-suit? card)
    (assoc :declared-suit :spades)))

(defn- reshuffle-action [state]
  {:type :reshuffle-draw-pile
   :cards (model/reshuffle-cards (:discard-pile state))})

(defn choose-player-action [state]
  (let [player (:current-player state)
        card (playable-card state player)]
    (cond
      card
      (play-card-action card)

      (seq (:draw-pile state))
      {:type :draw-card}

      (model/reshuffleable? state)
      (reshuffle-action state)

      :else
      {:type :pass-turn})))

(defn- log-event [service simulation-id app-event]
  (emit! service simulation-id {:type :log
                                :message (pr-str (logging/event->log-entry app-event))
                                :app-event app-event}))

(defn- joined-players [store game-id player-count]
  (vec (repeatedly player-count #(app/join-game! store game-id))))

(defn- simulation [id store game-id player-count players]
  {:simulation-id id
   :game-id game-id
   :player-count player-count
   :store store
   :players players
   :status :ready
   :started? false
   :subscribers {}})

(defn- create-simulation [id player-count]
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        players (joined-players store game-id player-count)]
    (simulation id store game-id player-count players)))

(defn start! [service player-count]
  (app/swap-result! service
                    (fn [state]
                      (let [id (next-simulation-id state)
                            sim (create-simulation id player-count)]
                        [(-> state
                             (update :next-simulation-id inc)
                             (assoc-in [:simulations id] sim))
                         {:simulation-id id}]))))

(defn- claim-run! [service simulation-id]
  (let [[before _] (swap-vals! service
                               (fn [state]
                                 (cond-> state
                                   (get-in state [:simulations simulation-id])
                                   (update-in [:simulations simulation-id]
                                              assoc :started? true :status :running))))
        sim (get-in before [:simulations simulation-id])]
    (when-not (:started? sim)
      sim)))

(defn- start-game! [store game-id player-count]
  (app/submit-action! store game-id {:type :start-game
                                     :player-count player-count
                                     :deck (app/valid-start-deck player-count)}))

(defn- simulation-done? [state steps-left]
  (or (model/game-over? state)
      (zero? steps-left)))

(defn- current-player-id [players state]
  (:player-id (nth players (:current-player state))))

(defn- submit-next-action! [service store game-id players state]
  ((:delay-fn @service))
  (app/submit-player-action! store
                             game-id
                             (current-player-id players state)
                             (choose-player-action state)))

(defn run-to-completion! [service simulation-id]
  (when-let [{:keys [store game-id player-count players]} (claim-run! service simulation-id)]
    (app/subscribe! store game-id :simulation-logger #(log-event service simulation-id %))
    (start-game! store game-id player-count)
    (loop [steps-left 500]
      (let [state (:state (app/get-game store game-id))]
        (if (simulation-done? state steps-left)
          (swap! service assoc-in [:simulations simulation-id :status] (:status state))
          (do
            (submit-next-action! service store game-id players state)
            (recur (dec steps-left))))))))
