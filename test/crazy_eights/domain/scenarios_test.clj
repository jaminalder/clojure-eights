(ns crazy_eights.domain.scenarios-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.scenarios :as scenarios]))

(deftest bundled-scenarios-pass
  (doseq [path ["domain/scenarios/play_matching_rank.edn"
                "domain/scenarios/play_eight_declares_suit.edn"
                "domain/scenarios/cannot_play_invalid_card.edn"
                "domain/scenarios/draws_when_no_playable_card.edn"
                "domain/scenarios/starts_and_plays_opening_round.edn"
                "domain/scenarios/redraws_until_playable.edn"
                "domain/scenarios/reshuffles_draw_pile.edn"
                "domain/scenarios/passes_when_no_draw_possible.edn"
                "domain/scenarios/blocked_game_after_full_round_of_passes.edn"
                "domain/scenarios/wins_after_passes.edn"]]
    (testing path
      (is (true? (:pass? (scenarios/run-scenario-resource path)))))))
