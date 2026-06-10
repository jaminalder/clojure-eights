(ns crazy_eights.domain.invariants
  (:require [crazy_eights.domain.model :as model]))

(defn- valid-card-collection? [cards]
  (every? model/card? cards))

(defn- winner-empty-hand? [{:keys [players winner]}]
  (or (nil? winner)
      (empty? (get-in players [winner :hand]))))

(defn check [{:keys [players draw-pile discard-pile active-suit current-player status winner] :as state}]
  (cond-> []
    (not (vector? players))
    (conj {:reason :players-not-vector})

    (some #(not (vector? (:hand %))) players)
    (conj {:reason :player-hand-not-vector})

    (not (valid-card-collection? (mapcat :hand players)))
    (conj {:reason :invalid-player-card})

    (not (valid-card-collection? draw-pile))
    (conj {:reason :invalid-draw-pile-card})

    (not (valid-card-collection? discard-pile))
    (conj {:reason :invalid-discard-pile-card})

    (and (= :in-progress status)
         (not (model/valid-suit? active-suit)))
    (conj {:reason :invalid-active-suit})

    (and (= :in-progress status)
         (or (neg? current-player)
             (<= (count players) current-player)))
    (conj {:reason :invalid-current-player})

    (and (= :in-progress status)
         (empty? discard-pile))
    (conj {:reason :empty-discard-pile})

    (and (= :in-progress status)
         winner)
    (conj {:reason :winner-present-while-in-progress})

    (not (winner-empty-hand? state))
    (conj {:reason :winner-hand-not-empty})))
