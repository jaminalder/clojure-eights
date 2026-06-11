(ns crazy_eights.app.core-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.app.core :as app]
            [crazy_eights.domain.model :as model]))

(deftest create-and-join-game
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id)
        p2 (app/join-game! store game-id)
        game (app/get-game store game-id)]
    (is (string? game-id))
    (is (string? (:player-id p1)))
    (is (= 0 (:seat p1)))
    (is (= 1 (:seat p2)))
    (is (= #{(:player-id p1) (:player-id p2)}
           (set (keys (:players game)))))))

(deftest submit-action-starts-game-and-stores-state
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        deck model/full-deck
        result (app/submit-action! store game-id {:type :start-game
                                                  :player-count 2
                                                  :deck deck})
        game (app/get-game store game-id)]
    (is (vector? (:events result)))
    (is (= :in-progress (get-in game [:state :status])))))

(deftest player-action-translates-player-id-to-seat
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        deck (vec (concat [(model/card :queen :clubs)
                           (model/card :ace :diamonds)
                           (model/card :queen :spades)
                           (model/card :queen :diamonds)
                           (model/card :eight :diamonds)
                           (model/card :two :hearts)
                           (model/card :ace :clubs)
                           (model/card :king :spades)
                           (model/card :queen :hearts)
                           (model/card :jack :clubs)
                           (model/card :three :clubs)
                           (model/card :four :clubs)]
                          (drop 12 model/full-deck)))]
    (app/submit-action! store game-id {:type :start-game
                                       :player-count 2
                                       :deck deck})
    (let [result (app/submit-player-action! store game-id (:player-id p1)
                                            {:type :play-card
                                             :card (model/card :queen :clubs)})]
      (is (= :card-played (:type (first (:events result))))))))

(deftest subscribers-receive-app-events-in-order
  (let [store (app/create-store)
        {:keys [game-id]} (app/create-game! store)
        _p1 (app/join-game! store game-id)
        _p2 (app/join-game! store game-id)
        sink (atom [])]
    (app/subscribe! store game-id :test-subscriber #(swap! sink conj %))
    (app/submit-action! store game-id {:type :start-game
                                       :player-count 2
                                       :deck model/full-deck})
    (is (= [:game-started :turn-changed]
           (map :type @sink)))))
