(ns crazy_eights.web.view_model
  "Builds a per-viewer view of an application game. Pure: game map and
  viewer id in, view model out. Other players' cards never enter the view."
  (:require [crazy_eights.domain.model :as model]
            [crazy_eights.web.cards :as cards]))

(defn- phase [{:keys [state]}]
  (cond
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
          {:card card
           :code (cards/card->code card)
           :eight? (model/requires-declared-suit? card)
           :playable? (boolean (and your-turn? (model/playable-card? state card)))})
        (get-in state [:players seat :hand])))

(defn game-view [game viewer-id]
  (let [state (:state game)
        viewer (get-in game [:players viewer-id])
        viewer-seat (:seat viewer)
        names (seat->name game)
        in-progress? (= :in-progress (:status state))
        your-turn? (boolean (and in-progress?
                                 viewer-seat
                                 (= viewer-seat (:current-player state))))
        hand (when (and state viewer-seat) (hand-view state viewer-seat your-turn?))
        playable? (some :playable? hand)
        draw-pile (:draw-pile state)]
    {:game-id (:game-id game)
     :phase (phase game)
     :players (players-view game viewer-seat)
     :player-count (count (:players game))
     :viewer-seat viewer-seat
     :viewer-name (:name viewer)
     :host? (boolean (and viewer (= 0 viewer-seat)))
     :can-start? (boolean (and (nil? state)
                               viewer
                               (= 0 viewer-seat)
                               (<= 2 (count (:players game)))))
     :top-card (when state (model/top-discard (:discard-pile state)))
     :top-code (when state (cards/card->code (model/top-discard (:discard-pile state))))
     :active-suit (:active-suit state)
     :draw-count (when state (count draw-pile))
     :current-name (when in-progress? (names (:current-player state)))
     :your-turn? your-turn?
     :hand hand
     :can-draw? (boolean (and your-turn?
                              (not playable?)
                              (or (seq draw-pile) (model/reshuffleable? state))))
     :can-pass? (boolean (and your-turn?
                              (not playable?)
                              (empty? draw-pile)
                              (not (model/reshuffleable? state))))
     :winner-name (when (= :finished (:status state)) (names (:winner state)))
     :blocked? (boolean (and (= :finished (:status state)) (nil? (:winner state))))}))
