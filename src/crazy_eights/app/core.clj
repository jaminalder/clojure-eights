(ns crazy_eights.app.core
  (:require [crazy_eights.app.pubsub :as pubsub]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))

(defn create-store []
  (atom {:next-game-id 0
         :games {}}))

(defn swap-result!
  "Apply (f state) -> [next-state result]; commit next-state, return result.
  Lets a use case stay a pure state->[state result] fn instead of smuggling a
  value out of swap! through an atom."
  [a f]
  (let [out (volatile! nil)]
    (swap! a (fn [state]
               (let [[next-state result] (f state)]
                 (vreset! out result)
                 next-state)))
    @out))

(defn- next-game-id [store-state]
  (str "game-" (:next-game-id store-state)))

(defn- next-player-id [game]
  (str (:game-id game) "-player-" (count (:players game))))

(defn- next-seat [game]
  (count (:players game)))

(defn- game-started? [game]
  (some? (:state game)))

(defn- game-full? [game]
  (<= model/max-player-count (next-seat game)))

(defn- default-player-name [seat]
  (str "player " (inc seat)))

(defn- player-seat [game player-name]
  (let [seat (next-seat game)
        player-id (next-player-id game)
        player {:seat seat
                :name (or player-name (default-player-name seat))}]
    [(assoc-in game [:players player-id] player)
     (assoc player :player-id player-id)]))

(defn- emit! [store game-id event]
  (let [subscribers (get-in @store [:games game-id :subscribers] {})]
    (pubsub/publish subscribers event)))

(defn- player-joined [game-id result]
  (assoc result
         :type :player-joined
         :game-id game-id))

(defn- game-started [command next-state domain-events]
  {:type :game-started
   :command command
   :events domain-events
   :state next-state})

(defn- move-made [command next-state domain-events]
  {:type :move-made
   :command command
   :events domain-events
   :state next-state})

(defn- turn-changed [next-state]
  {:type :turn-changed
   :current-player (:current-player next-state)
   :state next-state})

(defn- game-finished [next-state]
  {:type :game-finished
   :winner (:winner next-state)
   :state next-state})

(defn create-game! [store]
  (swap-result! store
                (fn [state]
                  (let [game-id (next-game-id state)
                        game {:game-id game-id
                              :state nil
                              :players {}
                              :subscribers {}}]
                    [(-> state
                         (update :next-game-id inc)
                         (assoc-in [:games game-id] game))
                     {:game-id game-id}]))))

(defn- join-game-state [state game-id player-name]
  (if-let [game (get-in state [:games game-id])]
    (cond
      (game-started? game)
      [state {:error :game-already-started}]

      (game-full? game)
      [state {:error :game-full}]

      :else
      (let [[next-game result] (player-seat game player-name)]
        [(assoc-in state [:games game-id] next-game) result]))
    [state {:error :unknown-game}]))

(defn join-game!
  ([store game-id]
   (join-game! store game-id nil))
  ([store game-id player-name]
   (let [result (swap-result! store
                              #(join-game-state % game-id player-name))]
     (when (:player-id result)
       (emit! store game-id (player-joined game-id result)))
     result)))

(defn host-game! [store player-name]
  (let [{:keys [game-id]} (create-game! store)]
    (assoc (join-game! store game-id player-name)
           :game-id game-id)))

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

(defn- action-events
  "App events to emit after a successful command, given the command and the
  resulting game state. The caller adds :game-id at emit time."
  [command next-state domain-events]
  (cond-> [(if (= :start-game (:type command))
             (game-started command next-state domain-events)
             (move-made command next-state domain-events))
           (turn-changed next-state)]
    (= :finished (:status next-state))
    (conj (game-finished next-state))))

(defn- apply-command [game command]
  (let [current-state (:state game)
        decision (commands/decide current-state command)]
    (if (= :domain-error (:type decision))
      [game decision]
      [(assoc game :state (events/apply-events current-state decision))
       decision])))

(defn- submit-action-state [state game-id command]
  (if-let [game (get-in state [:games game-id])]
    (let [[next-game decision] (apply-command game command)]
      [(assoc-in state [:games game-id] next-game) decision])
    [state {:type :domain-error :reason :unknown-game}]))

(defn- emit-action-events! [store game-id command domain-events]
  (let [next-state (get-in @store [:games game-id :state])]
    (run! #(emit! store game-id (assoc % :game-id game-id))
          (action-events command next-state domain-events))))

(defn submit-action! [store game-id command]
  (let [decision (swap-result! store
                               #(submit-action-state % game-id command))]
    (when (vector? decision)
      (emit-action-events! store game-id command decision))
    {:events decision}))

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
