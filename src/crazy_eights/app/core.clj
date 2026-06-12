(ns crazy_eights.app.core
  (:require [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))

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

(defn join-game!
  ([store game-id]
   (join-game! store game-id nil))
  ([store game-id player-name]
   (let [joined (atom nil)]
     (swap! store
            (fn [state]
              (let [game (get-in state [:games game-id])
                    seat (count (:players game))]
                (cond
                  (some? (:state game))
                  (do (reset! joined {:error :game-already-started})
                      state)

                  (<= model/max-player-count seat)
                  (do (reset! joined {:error :game-full})
                      state)

                  :else
                  (let [player-id (next-player-id game)
                        seat-name (or player-name (str "player " (inc seat)))]
                    (reset! joined {:player-id player-id :seat seat :name seat-name})
                    (assoc-in state [:games game-id :players player-id]
                              {:seat seat :name seat-name}))))))
     (when (:player-id @joined)
       (emit! store game-id (assoc @joined
                                   :type :player-joined
                                   :game-id game-id)))
     @joined)))

(defn get-game [store game-id]
  (get-in @store [:games game-id]))

(defn host? [game player-id]
  (= 0 (get-in game [:players player-id :seat])))

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

;; deck preparation

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

;; game-play use cases

(defn- normalize [{:keys [events]}]
  (if (= :domain-error (:type events))
    {:error (:reason events)}
    {:events events}))

(defn start-game! [store game-id player-id {:keys [deck]}]
  (let [game (get-game store game-id)]
    (cond
      (nil? game) {:error :unknown-game}
      (not (host? game player-id)) {:error :not-host}
      (< (count (:players game)) 2) {:error :not-enough-players}
      :else
      (let [player-count (count (:players game))]
        (normalize (submit-action! store game-id
                                   {:type :start-game
                                    :player-count player-count
                                    :deck (or deck (valid-start-deck player-count))}))))))

(defn play-card! [store game-id player-id card declared-suit]
  (normalize (submit-player-action! store game-id player-id
                                    (cond-> {:type :play-card :card card}
                                      declared-suit (assoc :declared-suit declared-suit)))))

(defn draw-card! [store game-id player-id]
  (let [state (:state (get-game store game-id))]
    (when (model/reshuffleable? state)
      (submit-action! store game-id
                      {:type :reshuffle-draw-pile
                       :cards (model/reshuffle-cards (:discard-pile state))}))
    (normalize (submit-player-action! store game-id player-id {:type :draw-card}))))

(defn pass-turn! [store game-id player-id]
  (normalize (submit-player-action! store game-id player-id {:type :pass-turn})))
