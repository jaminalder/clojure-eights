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

(defn player [hand]
  {:hand (vec hand)})

(defn card? [value]
  (and (map? value)
       (valid-rank? (:rank value))
       (valid-suit? (:suit value))))

(defn card-in-hand? [hand card]
  (boolean (some #(= card %) hand)))

(defn requires-declared-suit? [card]
  (= :eight (:rank card)))

(defn valid-declared-suit? [card declared-suit]
  (if (requires-declared-suit? card)
    (valid-suit? declared-suit)
    (nil? declared-suit)))

(defn playable-card? [{:keys [active-suit discard-pile]} card]
  (let [top-card (peek discard-pile)]
    (or (= :eight (:rank card))
        (= active-suit (:suit card))
        (= (:rank top-card) (:rank card)))))
