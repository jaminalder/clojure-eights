(ns crazy_eights.app.simulation
  (:require [clojure.data.json :as json]
            [crazy_eights.app.core :as app]
            [crazy_eights.app.logging :as logging]
            [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.model :as model]
            [org.httpkit.server :as http]))

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
            store (app/create-store)
            {:keys [game-id]} (app/create-game! store)]
        (dotimes [_ player-count]
          (app/join-game! store game-id))
        (let [result (app/submit-action! store game-id {:type :start-game
                                                        :player-count player-count
                                                        :deck deck})]
          (if (= :domain-error (:type (:events result)))
            (recur)
            deck))))
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

(defn run-to-completion! [service simulation-id]
  (let [{:keys [store game-id player-count players started?]} (get-simulation service simulation-id)
        deck (valid-start-deck player-count)]
    (when-not started?
      (swap! service assoc-in [:simulations simulation-id :started?] true)
      (app/subscribe! store game-id :simulation-logger #(log-event service simulation-id %))
      (app/submit-action! store game-id {:type :start-game
                                         :player-count player-count
                                         :deck deck})
      (loop [steps-left 500]
        (let [state (:state (app/get-game store game-id))]
          (if (or (= :finished (:status state)) (zero? steps-left))
            (swap! service assoc-in [:simulations simulation-id :status] (:status state))
            (let [player-index (:current-player state)
                  player-id (:player-id (nth players player-index))
                  action (choose-player-action state)]
              ((:delay-fn @service))
              (app/submit-player-action! store game-id player-id action)
              (recur (dec steps-left)))))))))

(defn sse-response [service simulation-id request]
  (http/with-channel request [channel]
    (http/on-close channel (fn [_] (unsubscribe! service simulation-id channel)))
    (http/send! channel {:status 200
                         :headers {"Content-Type" "text/event-stream"
                                   "Cache-Control" "no-cache"
                                   "Connection" "keep-alive"}
                         :body "event: open\ndata: {\"status\":\"connected\"}\n\n"}
                false)
    (subscribe! service simulation-id channel
                (fn [event]
                  (http/send! channel
                              (str "event: log\n"
                                   "data: "
                                   (json/write-str {:message (:message event)})
                                   "\n\n")
                              false)))))
