(ns crazy_eights.domain.rules
  (:require [crazy_eights.domain.model :as model]))

(def valid-suit? model/valid-suit?)

(def valid-rank? model/valid-rank?)

(def card? model/card?)

(defn card-in-hand? [hand card]
  (some #(= card %) hand))

(defn requires-declared-suit? [card]
  (= :eight (:rank card)))

(defn valid-declared-suit? [card declared-suit]
  (if (requires-declared-suit? card)
    (valid-suit? declared-suit)
    (nil? declared-suit)))

(defn playable-card? [{:keys [active-suit discard-pile]} card]
  (let [top-card (peek (vec discard-pile))]
    (or (= :eight (:rank card))
        (= active-suit (:suit card))
        (= (:rank top-card) (:rank card)))))
