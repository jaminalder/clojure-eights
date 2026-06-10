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
