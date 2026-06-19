(ns crazy_eights.domain.model)

(def suits [:clubs :diamonds :hearts :spades])

(def ranks [:ace :two :three :four :five :six :seven :eight :nine :ten :jack :queen :king])

(defn valid-suit? [suit]
  (contains? (set suits) suit))

(defn valid-rank? [rank]
  (contains? (set ranks) rank))

(defn card [rank suit]
  {:rank rank
   :suit suit})

(def full-deck
  (vec (for [suit suits
             rank ranks]
         (card rank suit))))

(def cards-per-player 5)

(def max-player-count
  (quot (dec (count full-deck)) cards-per-player))

(defn player [hand]
  {:hand (vec hand)})

(defn game-over? [state]
  (= :finished (:status state)))

(defn current-hand [state player-index]
  (get-in state [:players player-index :hand]))

(defn current-player? [state player-index]
  (= player-index (:current-player state)))

(defn next-player [state]
  (mod (inc (:current-player state))
       (count (:players state))))

(defn card? [value]
  (and (map? value)
       (valid-rank? (:rank value))
       (valid-suit? (:suit value))))

(defn card-in-hand? [hand candidate-card]
  (boolean (some #(= candidate-card %) hand)))

(defn remove-card [hand candidate-card]
  (let [[before after] (split-with #(not= % candidate-card) hand)]
    (vec (concat before (rest after)))))

(defn requires-declared-suit? [candidate-card]
  (= :eight (:rank candidate-card)))

(defn valid-declared-suit? [candidate-card declared-suit]
  (if (requires-declared-suit? candidate-card)
    (valid-suit? declared-suit)
    (nil? declared-suit)))

(defn playable-card? [{:keys [active-suit discard-pile]} candidate-card]
  (let [top-card (peek discard-pile)]
    (or (= :eight (:rank candidate-card))
        (= active-suit (:suit candidate-card))
        (= (:rank top-card) (:rank candidate-card)))))

(defn playable-hand? [state hand]
  (boolean (some #(playable-card? state %) hand)))

(defn reshuffleable? [{:keys [draw-pile discard-pile]}]
  (and (empty? draw-pile)
       (< 1 (count discard-pile))))

(defn reshuffle-cards [discard-pile]
  (vec (butlast discard-pile)))

(defn top-discard [discard-pile]
  (peek discard-pile))
