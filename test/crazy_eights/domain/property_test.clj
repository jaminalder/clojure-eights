(ns crazy_eights.domain.property-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [crazy_eights.domain.generators :as generators]
            [crazy_eights.domain.model :as model]))

(defspec generated-cards-are-valid 50
  (prop/for-all [card generators/card-gen]
    (model/card? card)))

(deftest generated-hands-contain-only-valid-cards
  (let [hand (first (clojure.test.check.generators/sample generators/hand-gen 1))]
    (is (every? model/card? hand))))
