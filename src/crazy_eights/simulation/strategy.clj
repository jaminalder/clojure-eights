(ns crazy_eights.simulation.strategy
  (:require [crazy_eights.domain.model :as model]))

(defn observation [state]
  (let [player (:current-player state)]
    {:player player
     :hand (model/current-hand state player)
     :top-card (model/top-discard (:discard-pile state))
     :active-suit (:active-suit state)
     :draw-count (count (:draw-pile state))
     :discard-count (count (:discard-pile state))
     :other-card-counts (->> (:players state)
                             (map-indexed vector)
                             (remove #(= player (first %)))
                             (mapv #(count (:hand (second %)))))
     :passes-in-row (:passes-in-row state)
     :status (:status state)}))

(defn- playable? [{:keys [active-suit top-card]} candidate-card]
  (or (= :eight (:rank candidate-card))
      (= active-suit (:suit candidate-card))
      (= (:rank top-card) (:rank candidate-card))))

(defn- play-candidate [hand card declared-suit]
  (let [remaining (model/remove-card hand card)]
    (cond-> {:type :play-card
             :card card
             :resulting-hand remaining
             :resulting-active-suit (or declared-suit (:suit card))}
      declared-suit (assoc :declared-suit declared-suit))))

(defn- play-candidates [{:keys [hand] :as obs}]
  (mapcat (fn [card]
            (when (playable? obs card)
              (if (model/requires-declared-suit? card)
                (map #(play-candidate hand card %) model/suits)
                [(play-candidate hand card nil)])))
          hand))

(defn legal-actions [obs]
  (let [plays (vec (play-candidates obs))]
    (cond
      (seq plays) plays
      (or (pos? (:draw-count obs)) (< 1 (:discard-count obs))) [{:type :draw-card}]
      :else [{:type :pass-turn}])))

(defn action [candidate]
  (dissoc candidate :resulting-hand :resulting-active-suit))

(defn- score [rules obs candidate]
  (reduce + (map #(% obs candidate) rules)))

(defn from-rules [id rules]
  {:id id
   :choose (fn [obs]
             (->> (legal-actions obs)
                  (map-indexed (fn [index candidate]
                                 {:index index
                                  :candidate candidate
                                  :score (score rules obs candidate)}))
                  (sort-by (juxt (comp - :score) :index))
                  first
                  :candidate
                  action))})

(defn- play-card? [candidate]
  (= :play-card (:type candidate)))

(defn- eight? [candidate]
  (= :eight (get-in candidate [:card :rank])))

(defn- suit-counts [hand]
  (frequencies (map :suit hand)))

(defn- non-eight-play-available? [obs]
  (some #(and (play-card? %) (not (eight? %)))
        (legal-actions obs)))

(defn win-now [_obs candidate]
  (if (and (play-card? candidate)
           (empty? (:resulting-hand candidate)))
    1000
    0))

(defn avoid-eight [obs candidate]
  (if (and (eight? candidate)
           (non-eight-play-available? obs))
    -100
    0))

(defn declare-most-held-suit [_obs candidate]
  (if (and (eight? candidate) (:declared-suit candidate))
    (* 10 (get (suit-counts (:resulting-hand candidate))
               (:declared-suit candidate)
               0))
    0))

(defn prefer-rank-switch-to-held-suit [obs candidate]
  (if (and (play-card? candidate)
           (= (:rank (:top-card obs)) (:rank (:card candidate)))
           (not= (:active-suit obs) (:resulting-active-suit candidate)))
    (* 5 (get (suit-counts (:resulting-hand candidate))
              (:resulting-active-suit candidate)
              0))
    0))

(defn prefer-strong-suit [_obs candidate]
  (if (play-card? candidate)
    (* 2 (get (suit-counts (:resulting-hand candidate))
              (:resulting-active-suit candidate)
              0))
    0))

(defn prefer-play-over-draw [_obs candidate]
  (case (:type candidate)
    :play-card 20
    :draw-card 0
    :pass-turn -10))

(defn playable-card [state player]
  (first (filter #(model/playable-card? state %)
                 (model/current-hand state player))))

(defn- play-card-action [card]
  (cond-> {:type :play-card
           :card card}
    (model/requires-declared-suit? card)
    (assoc :declared-suit :spades)))

(defn choose-action [state]
  (let [player (:current-player state)]
    (if-let [card (playable-card state player)]
      (play-card-action card)
      (if (or (seq (:draw-pile state))
              (model/reshuffleable? state))
        {:type :draw-card}
        {:type :pass-turn}))))

(def first-playable
  {:id :first-playable
   :choose (fn [obs]
             (action (first (legal-actions obs))))})

(def careful
  (from-rules :careful [win-now
                        avoid-eight
                        declare-most-held-suit
                        prefer-rank-switch-to-held-suit
                        prefer-strong-suit
                        prefer-play-over-draw]))
