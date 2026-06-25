(ns crazy_eights.simulation.strategy
  (:require [crazy_eights.domain.model :as model]))

(defn playable-card [state player]
  (first (filter #(model/playable-card? state %)
                 (model/current-hand state player))))

(defn- play-card-action [card]
  (cond-> {:type :play-card
           :card card}
    (model/requires-declared-suit? card)
    (assoc :declared-suit :spades)))

(defn choose-action [state]
  (let [player (:current-player state)]
    (if-let [card (playable-card state player)]
      (play-card-action card)
      (if (or (seq (:draw-pile state))
              (model/reshuffleable? state))
        {:type :draw-card}
        {:type :pass-turn}))))

(def first-playable choose-action)
