(ns crazy_eights.app.simulation-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]))

(defn shuffle-deck [deck]
  (vec (shuffle deck)))

(defn valid-start-deck [player-count]
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
          deck)))))

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

(defn run-app-simulated-game [player-count]
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        players (vec (repeatedly player-count #(app/join-game! store game-id)))
        _start (app/submit-action! store game-id {:type :start-game
                                                  :player-count player-count
                                                  :deck (valid-start-deck player-count)})
        event-log (atom [])]
    (app/subscribe! store game-id :simulation #(swap! event-log conj %))
    (loop [steps-left 500]
      (let [state (:state (app/get-game store game-id))]
        (if (or (= :finished (:status state)) (zero? steps-left))
          {:state state :events @event-log :steps-left steps-left :players players}
          (let [player-index (:current-player state)
                player-id (:player-id (nth players player-index))
                action (choose-player-action state)]
            (app/submit-player-action! store game-id player-id action)
            (recur (dec steps-left))))))))

(deftest app-layer-simulated-games-finish
  (doseq [player-count [2 3 4]]
    (let [{:keys [state events steps-left]} (run-app-simulated-game player-count)
          event-log (with-out-str (doseq [event events] (prn event)))]
      (is (= :finished (:status state))
          (str "app simulation did not finish for " player-count " players\n" event-log))
      (is (pos? steps-left)
          (str "app simulation exhausted step budget for " player-count " players\n" event-log)))))
