(ns crazy_eights.app.core
  (:require [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]))

(defn create-store []
  (atom {:next-game-id 0
         :games {}}))

(defn- next-game-id [store-state]
  (str "game-" (:next-game-id store-state)))

(defn- next-player-id [game]
  (str (:game-id game) "-player-" (count (:players game))))

(defn- emit! [store game-id event]
  (let [subscribers (get-in @store [:games game-id :subscribers] {})]
    (pubsub/publish subscribers event)))

(defn create-game! [store]
  (let [created (atom nil)]
    (swap! store
           (fn [state]
             (let [game-id (next-game-id state)
                   game {:game-id game-id
                         :state nil
                         :players {}
                         :subscribers {}}]
               (reset! created {:game-id game-id})
               (-> state
                   (update :next-game-id inc)
                   (assoc-in [:games game-id] game)))))
    @created))

(defn join-game! [store game-id]
  (let [joined (atom nil)]
    (swap! store
           (fn [state]
             (let [game (get-in state [:games game-id])
                   seat (count (:players game))
                   player-id (next-player-id game)]
               (reset! joined {:player-id player-id :seat seat})
               (assoc-in state [:games game-id :players player-id] {:seat seat}))))
    @joined))

(defn get-game [store game-id]
  (get-in @store [:games game-id]))

(defn subscribe! [store game-id subscriber-id handler]
  (swap! store update-in [:games game-id :subscribers]
         #(pubsub/subscribe (or % {}) subscriber-id handler))
  subscriber-id)

(defn unsubscribe! [store game-id subscriber-id]
  (swap! store update-in [:games game-id :subscribers]
         #(pubsub/unsubscribe (or % {}) subscriber-id))
  subscriber-id)

(defn submit-action! [store game-id command]
  (let [result (atom nil)]
    (swap! store
           (fn [state]
             (let [game (get-in state [:games game-id])
                   current-state (:state game)
                   decision (commands/decide current-state command)]
               (reset! result decision)
               (if (= :domain-error (:type decision))
                 state
                 (assoc-in state
                           [:games game-id :state]
                           (events/apply-events current-state decision))))))
    (when (vector? @result)
      (let [next-state (get-in @store [:games game-id :state])]
        (emit! store game-id {:type (if (= :start-game (:type command))
                                      :game-started
                                      :move-made)
                            :game-id game-id
                            :command command
                            :events @result
                            :state next-state})
        (emit! store game-id {:type :turn-changed
                              :game-id game-id
                              :current-player (:current-player next-state)
                              :state next-state})
        (when (= :finished (:status next-state))
          (emit! store game-id {:type :game-finished
                                :game-id game-id
                                :winner (:winner next-state)
                                :state next-state}))))
    {:events @result}))

(defn submit-player-action! [store game-id player-id action]
  (let [seat (get-in @store [:games game-id :players player-id :seat])]
    (submit-action! store game-id (assoc action :player seat))))
