(ns crazy_eights.domain.scenarios-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.scenarios :as scenarios]))

(deftest bundled-scenarios-pass
  (doseq [path ["domain/scenarios/play_matching_rank.edn"
                "domain/scenarios/play_eight_declares_suit.edn"
                "domain/scenarios/cannot_play_invalid_card.edn"
                "domain/scenarios/draws_when_no_playable_card.edn"]]
    (testing path
      (is (true? (:pass? (scenarios/run-scenario-resource path)))))))
