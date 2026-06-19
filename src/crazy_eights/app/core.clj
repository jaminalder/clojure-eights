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
  (str (:game-id game) "-player-" (:next-player-number game)))

(defn- next-seat [game]
  (count (:players game)))

(defn- table-started? [game]
  (:started-once? game))

(defn- game-in-progress? [game]
  (= :in-progress (get-in game [:state :status])))

(defn- table-open-to-joins? [game]
  (not (table-started? game)))

(defn- table-leavable? [game]
  (not (game-in-progress? game)))

(defn- game-full? [game]
  (<= model/max-player-count (next-seat game)))

(defn- default-player-name [seat]
  (str "player " (inc seat)))

(defn- player-seat [game player-name]
  (let [seat (next-seat game)
        player-id (next-player-id game)
        player {:seat seat
                :name (or player-name (default-player-name seat))}]
    [(-> game
         (update :next-player-number inc)
         (assoc-in [:players player-id] player))
     (assoc player :player-id player-id)]))

(defn- reseat-players [players]
  (->> players
       (sort-by (comp :seat val))
       (map-indexed (fn [seat [player-id player]]
                      [player-id (assoc player :seat seat)]))
       (into {})))

(defn- remove-player [game player-id]
  (-> game
      (update :players #(reseat-players (dissoc % player-id)))
      (assoc :state nil)))

(defn- emit! [store game-id event]
  (let [subscribers (get-in @store [:games game-id :subscribers] {})]
    (pubsub/publish subscribers event)))

(defn- player-joined [game-id result]
  (assoc result
         :type :player-joined
         :game-id game-id))

(defn- player-left [game-id result]
  {:type :player-left
   :game-id game-id
   :player-id (:player-id result)})

(defn- table-ended [game-id]
  {:type :table-ended
   :game-id game-id})

(defn- domain-error [reason]
  {:type :domain-error
   :reason reason})

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
                              :started-once? false
                              :next-player-number 0
                              :players {}
                              :subscribers {}}]
                    [(-> state
                         (update :next-game-id inc)
                         (assoc-in [:games game-id] game))
                     {:game-id game-id}]))))

(defn- join-game-state [state game-id player-name]
  (if-let [game (get-in state [:games game-id])]
    (cond
      (not (table-open-to-joins? game))
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

(defn host? [game player-id]
  (= 0 (get-in game [:players player-id :seat])))

(defn- leave-table-state [state game-id player-id]
  (if-let [game (get-in state [:games game-id])]
    (let [player (get-in game [:players player-id])]
      (cond
        (nil? player)
        [state {:error :not-a-player}]

        (not (table-leavable? game))
        [state {:error :game-in-progress}]

        (host? game player-id)
        [(update state :games dissoc game-id)
         {:ended? true
          :subscribers (:subscribers game)
          :event (table-ended game-id)}]

        :else
        [(assoc-in state [:games game-id] (remove-player game player-id))
         {:left? true
          :player-id player-id}]))
    [state {:error :unknown-game}]))

(defn leave-table! [store game-id player-id]
  (let [result (swap-result! store
                             #(leave-table-state % game-id player-id))]
    (cond
      (:ended? result)
      (pubsub/publish (:subscribers result) (:event result))

      (:left? result)
      (emit! store game-id (player-left game-id result)))
    (dissoc result :subscribers :event)))

(defn get-game [store game-id]
  (get-in @store [:games game-id]))

(defn subscribe! [store game-id subscriber-id handler]
  (swap! store (fn [state]
                 (if (get-in state [:games game-id])
                   (update-in state [:games game-id :subscribers]
                              #(pubsub/subscribe (or % {}) subscriber-id handler))
                   state)))
  subscriber-id)

(defn unsubscribe! [store game-id subscriber-id]
  (swap! store (fn [state]
                 (if (get-in state [:games game-id])
                   (update-in state [:games game-id :subscribers]
                              #(pubsub/unsubscribe (or % {}) subscriber-id))
                   state)))
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

(defn- start-game-command [player-count deck]
  {:type :start-game
   :player-count player-count
   :deck deck})

(defn- start-table [game deck]
  (let [player-count (count (:players game))
        command (start-game-command player-count deck)
        decision (commands/decide nil command)]
    (if (= :domain-error (:type decision))
      [game decision command]
      [(assoc game
              :state (events/apply-events nil decision)
              :started-once? true)
       decision
       command])))

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

(defn- start-game-error [game player-id]
  (cond
    (nil? game) :unknown-game
    (not (host? game player-id)) :not-host
    (< (count (:players game)) 2) :not-enough-players
    (game-in-progress? game) :invalid-start-game))

(defn- start-game-state [state game-id player-id deck]
  (let [game (get-in state [:games game-id])]
    (if-let [reason (start-game-error game player-id)]
      [state {:events (domain-error reason)}]
      (let [[next-game decision command] (start-table game deck)]
        [(assoc-in state [:games game-id] next-game)
         {:events decision
          :command command}]))))

(defn start-game! [store game-id player-id {:keys [deck]}]
  (let [game (get-game store game-id)]
    (if-let [reason (start-game-error game player-id)]
      {:error reason}
      (let [deck (or deck (valid-start-deck (count (:players game))))
            result (swap-result! store
                                 #(start-game-state % game-id player-id deck))]
        (when (vector? (:events result))
          (emit-action-events! store game-id (:command result) (:events result)))
        (normalize result)))))

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
