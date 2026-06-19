(ns crazy_eights.web.view_model
  "Builds a per-viewer view of an application game. Pure: game map and
  viewer id in, view model out. Other players' cards never enter the view."
  (:require [crazy_eights.domain.model :as model]
            [crazy_eights.web.cards :as cards]))

(defn- phase [{:keys [state started-once?]}]
  (cond
    (and (nil? state) started-once?) :between-games
    (nil? state) :waiting
    (= :finished (:status state)) :finished
    :else :playing))

(defn- seat->name [game]
  (into {} (map (fn [[_ player]] [(:seat player) (:name player)])) (:players game)))

(defn- players-view [game viewer-seat]
  (let [state (:state game)
        current (:current-player state)]
    (->> (:players game)
         (map val)
         (sort-by :seat)
         (mapv (fn [{:keys [seat] :as player}]
                 {:seat seat
                  :name (:name player)
                  :host? (= 0 seat)
                  :you? (= seat viewer-seat)
                  :current? (and (= :in-progress (:status state)) (= seat current))
                  :card-count (when state
                                (count (get-in state [:players seat :hand])))})))))

(defn- hand-view [state seat your-turn?]
  (mapv (fn [card]
          (let [playable? (boolean (and your-turn? (model/playable-card? state card)))
                eight? (model/requires-declared-suit? card)]
            {:card card
             :code (cards/card->code card)
             :eight? eight?
             :playable? playable?
             :declarable? (boolean (and playable? eight?))}))
        (get-in state [:players seat :hand])))

(defn- in-progress? [state]
  (= :in-progress (:status state)))

(defn- viewer-turn? [state viewer-seat]
  (boolean (and (in-progress? state)
                viewer-seat
                (model/current-player? state viewer-seat))))

(defn- playable-hand? [hand]
  (boolean (some :playable? hand)))

(defn- can-start? [phase-value viewer player-count]
  (boolean (and (contains? #{:waiting :between-games :finished} phase-value)
                viewer
                (= 0 (:seat viewer))
                (<= 2 player-count))))

(defn- start-label [phase-value]
  (if (= :waiting phase-value)
    "start game"
    "start new game"))

(defn- can-leave? [phase-value viewer]
  (boolean (and viewer
                (contains? #{:waiting :between-games :finished} phase-value))))

(defn- can-draw? [state your-turn? playable?]
  (boolean (and your-turn?
                (not playable?)
                (or (seq (:draw-pile state))
                    (model/reshuffleable? state)))))

(defn- can-pass? [state your-turn? playable?]
  (boolean (and your-turn?
                (not playable?)
                (empty? (:draw-pile state))
                (not (model/reshuffleable? state)))))

(defn- top-card [state]
  (when state
    (model/top-discard (:discard-pile state))))

(defn game-view [game viewer-id]
  (let [state (:state game)
        viewer (get-in game [:players viewer-id])
        viewer-seat (:seat viewer)
        names (seat->name game)
        player-count (count (:players game))
        phase-value (phase game)
        your-turn? (viewer-turn? state viewer-seat)
        hand (when (and state viewer-seat) (hand-view state viewer-seat your-turn?))
        playable? (playable-hand? hand)
        draw-pile (:draw-pile state)]
    {:game-id (:game-id game)
     :phase phase-value
     :players (players-view game viewer-seat)
     :player-count player-count
     :viewer-seat viewer-seat
     :viewer-name (:name viewer)
     :host? (boolean (and viewer (= 0 viewer-seat)))
     :can-start? (can-start? phase-value viewer player-count)
     :start-label (start-label phase-value)
     :can-leave? (can-leave? phase-value viewer)
     :top-card (top-card state)
     :top-code (some-> state top-card cards/card->code)
     :active-suit (:active-suit state)
     :draw-count (when state (count draw-pile))
     :current-name (when (in-progress? state) (names (:current-player state)))
     :your-turn? your-turn?
     :hand hand
     :can-draw? (can-draw? state your-turn? playable?)
     :can-pass? (can-pass? state your-turn? playable?)
     :winner-name (when (= :finished (:status state)) (names (:winner state)))
     :blocked? (boolean (and (= :finished (:status state)) (nil? (:winner state))))}))
