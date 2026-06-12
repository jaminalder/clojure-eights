(ns crazy_eights.web.cards
  (:require [crazy_eights.domain.model :as model]))

(def ^:private rank-codes
  {:ace "A" :two "2" :three "3" :four "4" :five "5" :six "6" :seven "7"
   :eight "8" :nine "9" :ten "10" :jack "J" :queen "Q" :king "K"})

(def ^:private suit-codes
  {:clubs "C" :diamonds "D" :hearts "H" :spades "S"})

(defn card->code [{:keys [rank suit]}]
  (str (rank-codes rank) (suit-codes suit)))

(def ^:private code->card-map
  (into {} (map (juxt card->code identity)) model/full-deck))

(defn code->card [code]
  (get code->card-map code))

(defn card->image [card]
  (str "/assets/cards/" (card->code card) ".svg"))

(def back-image "/assets/cards/BACK.svg")

(def suit-glyph
  {:clubs "♣" :diamonds "♦" :hearts "♥" :spades "♠"})

(def suit-color
  {:clubs "black" :spades "black" :diamonds "red" :hearts "red"})
