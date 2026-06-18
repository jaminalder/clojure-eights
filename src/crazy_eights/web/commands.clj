(ns crazy_eights.web.commands
  "Parses web requests into command maps. Pure: params in, command or error out."
  (:require [clojure.string :as str]
            [crazy_eights.domain.model :as model]
            [crazy_eights.web.cards :as cards]))

(defn- clean-name [params]
  (let [value (some-> (get params "name") str/trim)]
    (when-not (str/blank? value)
      value)))

(defn create-game-command [params]
  {:type :create-game
   :name (clean-name params)})

(defn join-game-command [game-id params]
  {:type :join-game
   :game-id game-id
   :name (clean-name params)})

(defn start-game-command [game-id player-id _params]
  (if player-id
    {:type :start-game :game-id game-id :player-id player-id}
    {:error :not-a-player}))

(defn play-card-command [game-id player-id params]
  (let [card (cards/code->card (get params "card"))
        suit-param (get params "declared-suit")
        declared-suit (some-> suit-param keyword)]
    (cond
      (nil? player-id) {:error :not-a-player}
      (nil? card) {:error :invalid-card}
      (and suit-param (not (model/valid-suit? declared-suit))) {:error :invalid-suit}
      :else {:type :play-card
             :game-id game-id
             :player-id player-id
             :card card
             :declared-suit declared-suit})))

(defn draw-card-command [game-id player-id _params]
  (if player-id
    {:type :draw-card :game-id game-id :player-id player-id}
    {:error :not-a-player}))

(defn pass-turn-command [game-id player-id _params]
  (if player-id
    {:type :pass-turn :game-id game-id :player-id player-id}
    {:error :not-a-player}))
