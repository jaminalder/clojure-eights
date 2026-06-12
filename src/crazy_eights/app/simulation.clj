(ns crazy_eights.app.simulation
  (:require [crazy_eights.app.core :as app]
            [crazy_eights.app.logging :as logging]
            [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.commands :as commands]
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

(defn shuffle-deck [deck]
  (vec (shuffle deck)))

(defn valid-start-deck [player-count]
  (if (<= 2 player-count model/max-player-count)
    (loop []
      (let [deck (shuffle-deck model/full-deck)
            result (commands/decide nil {:type :start-game
                                         :player-count player-count
                                         :deck deck})]
        (if (= :domain-error (:type result))
          (recur)
          deck)))
    (throw (ex-info "invalid player-count for single deck"
                    {:player-count player-count
                     :max-player-count model/max-player-count}))))

(defn playable-card [state player]
  (first (filter #(model/playable-card? state %)
                 (get-in state [:players player :hand]))))

(defn choose-player-action [state]
  (let [player (:current-player state)]
    (cond
      (playable-card state player)
      (let [card (playable-card state player)]
        (cond-> {:type :play-card :card card}
          (model/requires-declared-suit? card)
          (assoc :declared-suit :spades)))

      (seq (:draw-pile state))
      {:type :draw-card}

      (model/reshuffleable? state)
      {:type :reshuffle-draw-pile
       :cards (model/reshuffle-cards (:discard-pile state))}

      :else
      {:type :pass-turn})))

(defn- log-event [service simulation-id app-event]
  (emit! service simulation-id {:type :log
                                :message (pr-str (logging/event->log-entry app-event))
                                :app-event app-event}))

(defn start! [service player-count]
  (let [simulation-id (atom nil)]
    (swap! service
           (fn [state]
             (let [id (next-simulation-id state)
                   store (app/create-store)
                   {:keys [game-id]} (app/create-game! store)
                   players (vec (repeatedly player-count #(app/join-game! store game-id)))
                   sim {:simulation-id id
                        :game-id game-id
                        :player-count player-count
                        :store store
                        :players players
                        :status :ready
                        :started? false
                        :subscribers {}}]
               (reset! simulation-id id)
               (-> state
                   (update :next-simulation-id inc)
                   (assoc-in [:simulations id] sim)))))
    {:simulation-id @simulation-id}))

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

(defn run-to-completion! [service simulation-id]
  (when-let [{:keys [store game-id player-count players]} (claim-run! service simulation-id)]
    (app/subscribe! store game-id :simulation-logger #(log-event service simulation-id %))
    (app/submit-action! store game-id {:type :start-game
                                       :player-count player-count
                                       :deck (valid-start-deck player-count)})
    (loop [steps-left 500]
      (let [state (:state (app/get-game store game-id))]
        (if (or (= :finished (:status state)) (zero? steps-left))
          (swap! service assoc-in [:simulations simulation-id :status] (:status state))
          (let [player-index (:current-player state)
                player-id (:player-id (nth players player-index))
                action (choose-player-action state)]
            ((:delay-fn @service))
            (app/submit-player-action! store game-id player-id action)
            (recur (dec steps-left))))))))
