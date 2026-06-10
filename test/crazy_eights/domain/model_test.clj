(ns crazy_eights.domain.model-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]))

(deftest card-and-playability-rules
  (let [top-card (model/card :queen :hearts)
        hand [(model/card :queen :clubs)
              (model/card :eight :spades)]
        matching-rank (model/card :queen :clubs)
        wild-eight (model/card :eight :spades)
        wrong-card (model/card :ace :clubs)]
    (testing "basic value predicates"
      (is (model/valid-suit? :hearts))
      (is (model/valid-rank? :queen))
      (is (model/card? top-card))
      (is (model/card-in-hand? hand matching-rank)))
    (testing "playability"
      (is (model/playable-card? {:active-suit :hearts
                                 :discard-pile [top-card]}
                                matching-rank))
      (is (model/playable-card? {:active-suit :clubs
                                 :discard-pile [top-card]}
                                wild-eight))
      (is (not (model/playable-card? {:active-suit :hearts
                                      :discard-pile [top-card]}
                                     wrong-card))))
    (testing "declared suit requirements"
      (is (model/requires-declared-suit? wild-eight))
      (is (not (model/requires-declared-suit? matching-rank)))
      (is (model/valid-declared-suit? wild-eight :spades))
      (is (not (model/valid-declared-suit? wild-eight nil))))))
