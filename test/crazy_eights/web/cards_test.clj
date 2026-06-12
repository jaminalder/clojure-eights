(ns crazy_eights.web.cards-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.cards :as cards]))

(deftest codes-roundtrip-for-the-full-deck
  (doseq [card model/full-deck]
    (is (= card (cards/code->card (cards/card->code card))))))

(deftest code->card-rejects-garbage
  (is (nil? (cards/code->card "ZZ")))
  (is (nil? (cards/code->card nil)))
  (is (nil? (cards/code->card "")))
  (is (nil? (cards/code->card "11H"))))

(deftest card-image-paths
  (is (= "/assets/cards/10H.svg"
         (cards/card->image (model/card :ten :hearts))))
  (is (= "/assets/cards/QS.svg"
         (cards/card->image (model/card :queen :spades))))
  (is (= "/assets/cards/BACK.svg" cards/back-image)))

(deftest suit-display
  (is (= "♥" (cards/suit-glyph :hearts)))
  (is (= "♣" (cards/suit-glyph :clubs)))
  (is (= "red" (cards/suit-color :diamonds)))
  (is (= "black" (cards/suit-color :spades))))
