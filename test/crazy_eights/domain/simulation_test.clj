(ns crazy_eights.domain.simulation-test
  (:require [clojure.test :refer [deftest is]]
            [crazy_eights.domain.commands :as commands]
            [crazy_eights.domain.events :as events]
            [crazy_eights.domain.model :as model]))

(def ^:dynamic *log-simulation* false)

(defn shuffle-deck [deck]
  (vec (shuffle deck)))

(defn valid-start-deck [player-count]
  (loop []
    (let [deck (shuffle-deck model/full-deck)
          result (commands/decide nil {:type :start-game
                                       :player-count player-count
                                       :deck deck})]
      (if (= :domain-error (:type result))
        (recur)
        deck))))

(defn playable-card [state player]
  (first (filter #(model/playable-card? state %)
                 (get-in state [:players player :hand]))))

(defn choose-command [state]
  (let [player (:current-player state)]
    (cond
      (= :finished (:status state))
      nil

      (playable-card state player)
      (let [card (playable-card state player)]
        (cond-> {:type :play-card
                 :player player
                 :card card}
          (model/requires-declared-suit? card)
          (assoc :declared-suit :spades)))

      (seq (:draw-pile state))
      {:type :draw-card
       :player player}

      (model/reshuffleable? state)
      {:type :reshuffle-draw-pile
       :cards (model/reshuffle-cards (:discard-pile state))}

      :else
      {:type :pass-turn
       :player player})))

(defn transcript-entry [command result next-state]
  {:command command
   :events result
   :summary {:current-player (:current-player next-state)
             :status (:status next-state)
             :winner (:winner next-state)
             :draw-count (count (:draw-pile next-state))
             :passes-in-row (:passes-in-row next-state)}})

(defn transcript-text [transcript]
  (with-out-str
    (doseq [entry transcript]
      (prn entry))))

(defn run-simulated-game [player-count]
  (loop [state (events/apply-events nil
                                    (commands/decide nil {:type :start-game
                                                          :player-count player-count
                                                          :deck (valid-start-deck player-count)}))
         transcript []
         steps-left 500]
    (if (or (= :finished (:status state))
            (zero? steps-left))
      {:state state
       :transcript transcript
       :steps-left steps-left}
      (let [command (choose-command state)
            result (commands/decide state command)
            next-state (events/apply-events state result)
            entry (transcript-entry command result next-state)]
        (when *log-simulation*
          (prn entry))
        (recur next-state
               (conj transcript entry)
               (dec steps-left))))))

(deftest transcript-text-includes-command-and-events
  (let [text (transcript-text [{:command {:type :play-card}
                                :events [{:type :card-played}]
                                :summary {:status :in-progress}}])]
    (is (.contains text ":command"))
    (is (.contains text ":events"))
    (is (.contains text ":card-played"))))

(deftest shuffled-games-reach-an-end-state
  (doseq [player-count [2 3 4]]
    (let [{:keys [state transcript steps-left]} (run-simulated-game player-count)
          log-text (transcript-text transcript)]
      (is (= :finished (:status state))
          (str "simulation did not finish for " player-count
               " players\n"
               log-text))
      (is (pos? steps-left)
          (str "simulation exhausted step budget for " player-count
               " players\n"
               log-text)))))
