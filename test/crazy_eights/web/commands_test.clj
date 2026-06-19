(ns crazy_eights.web.commands-test
  (:require [clojure.test :refer [deftest is testing]]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.commands :as commands]))

(deftest create-game-command-takes-trimmed-name
  (is (= {:type :create-game :name "anna"}
         (commands/create-game-command {"name" "  anna "})))
  (is (= {:type :create-game :name nil}
         (commands/create-game-command {"name" "   "})))
  (is (= {:type :create-game :name nil}
         (commands/create-game-command {}))))

(deftest join-game-command-carries-game-and-name
  (is (= {:type :join-game :game-id "game-0" :name "ben"}
         (commands/join-game-command "game-0" {"name" "ben"}))))

(deftest start-game-command-requires-a-player
  (is (= {:type :start-game :game-id "game-0" :player-id "game-0-player-0"}
         (commands/start-game-command "game-0" "game-0-player-0" {})))
  (is (= {:error :not-a-player}
         (commands/start-game-command "game-0" nil {}))))

(deftest play-card-command-parses-card-and-suit
  (testing "plain card"
    (is (= {:type :play-card
            :game-id "game-0"
            :player-id "p"
            :card (model/card :queen :clubs)
            :declared-suit nil}
           (commands/play-card-command "game-0" "p" {"card" "QC"}))))
  (testing "eight with declared suit"
    (is (= {:type :play-card
            :game-id "game-0"
            :player-id "p"
            :card (model/card :eight :diamonds)
            :declared-suit :hearts}
           (commands/play-card-command "game-0" "p" {"card" "8D"
                                                     "declared-suit" "hearts"}))))
  (testing "errors"
    (is (= {:error :invalid-card}
           (commands/play-card-command "game-0" "p" {"card" "XX"})))
    (is (= {:error :invalid-suit}
           (commands/play-card-command "game-0" "p" {"card" "8D"
                                                     "declared-suit" "stars"})))
    (is (= {:error :not-a-player}
           (commands/play-card-command "game-0" nil {"card" "QC"})))))

(deftest draw-and-pass-commands
  (is (= {:type :draw-card :game-id "g" :player-id "p"}
         (commands/draw-card-command "g" "p" {})))
  (is (= {:type :pass-turn :game-id "g" :player-id "p"}
         (commands/pass-turn-command "g" "p" {})))
  (is (= {:error :not-a-player} (commands/draw-card-command "g" nil {})))
  (is (= {:error :not-a-player} (commands/pass-turn-command "g" nil {}))))

(deftest leave-table-command-requires-a-player
  (is (= {:type :leave-table :game-id "g" :player-id "p"}
         (commands/leave-table-command "g" "p" {})))
  (is (= {:error :not-a-player}
         (commands/leave-table-command "g" nil {}))))
