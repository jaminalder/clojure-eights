(ns crazy_eights.domain.generators
  (:require [clojure.test.check.generators :as gen]
            [crazy_eights.domain.model :as model]))

(def suit-gen
  (gen/elements model/suits))

(def rank-gen
  (gen/elements model/ranks))

(def card-gen
  (gen/fmap (fn [[rank suit]]
              (model/card rank suit))
            (gen/tuple rank-gen suit-gen)))

(def hand-gen
  (gen/vector card-gen 0 5))

(def player-gen
  (gen/fmap model/player hand-gen))

(def game-state-gen
  (gen/let [players (gen/vector player-gen 2 4)
            top-card card-gen
            draw-pile (gen/vector card-gen 0 10)]
    {:players players
     :draw-pile draw-pile
     :discard-pile [top-card]
     :active-suit (:suit top-card)
     :current-player 0
     :status :in-progress
     :winner nil
     :passes-in-row 0}))
