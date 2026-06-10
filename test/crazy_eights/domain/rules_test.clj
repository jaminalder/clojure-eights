(ns crazy_eights.domain.rules-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.domain.rules :as rules]))

(deftest card-and-playability-rules
  (let [top-card (model/card :queen :hearts)
        hand [(model/card :queen :clubs)
              (model/card :eight :spades)]
        matching-rank (model/card :queen :clubs)
        wild-eight (model/card :eight :spades)
        wrong-card (model/card :ace :clubs)]
    (testing "basic value predicates"
      (is (rules/valid-suit? :hearts))
      (is (rules/valid-rank? :queen))
      (is (rules/card? top-card))
      (is (rules/card-in-hand? hand matching-rank)))
    (testing "playability"
      (is (rules/playable-card? {:active-suit :hearts
                                 :discard-pile [top-card]}
                                matching-rank))
      (is (rules/playable-card? {:active-suit :clubs
                                 :discard-pile [top-card]}
                                wild-eight))
      (is (not (rules/playable-card? {:active-suit :hearts
                                      :discard-pile [top-card]}
                                     wrong-card))))
    (testing "declared suit requirements"
      (is (rules/requires-declared-suit? wild-eight))
      (is (not (rules/requires-declared-suit? matching-rank)))
      (is (rules/valid-declared-suit? wild-eight :spades))
      (is (not (rules/valid-declared-suit? wild-eight nil))))))
