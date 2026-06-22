(ns crazy_eights.web.fragments-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.fragments :as fragments]))

(deftest game-fragments-renders-the-three-swap-targets
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        {:keys [player-id]} (app/join-game! store game-id "ben")
        frags (fragments/game-fragments store game-id player-id)]
    (is (= #{:status :game-board :player-hand} (set (keys frags))))
    (doseq [html (vals frags)]
      (is (string? html))
      (is (not (str/includes? html "\n")) "fragments are single-line for SSE"))))

(def fixed-deck
  (vec (concat [(model/card :queen :clubs)
                (model/card :two :clubs)
                (model/card :six :diamonds)
                (model/card :king :diamonds)
                (model/card :ace :diamonds)
                (model/card :eight :diamonds)
                (model/card :two :hearts)
                (model/card :ace :clubs)
                (model/card :king :spades)
                (model/card :jack :clubs)
                (model/card :queen :spades)
                (model/card :four :clubs)]
               (drop 12 model/full-deck))))

(deftest observer-fragments-render-open-table
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id "anna")
        _p2 (app/join-game! store game-id "ben")]
    (app/start-game! store game-id (:player-id p1) {:deck fixed-deck})
    (let [frags (fragments/observer-fragments store game-id)]
      (is (= #{:observer-table} (set (keys frags))))
      (is (str/includes? (:observer-table frags) "/assets/cards/QC.svg"))
      (is (str/includes? (:observer-table frags) "/assets/cards/8D.svg"))
      (is (str/includes? (:observer-table frags) "/assets/cards/2H.svg"))
      (is (not (str/includes? (:observer-table frags) "\n"))))))
