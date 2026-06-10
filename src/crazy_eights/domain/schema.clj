(ns crazy_eights.domain.schema
  (:require [crazy_eights.domain.model :as model]
            [malli.core :as m]))

(def suit-schema
  (into [:enum {:error/message "valid suit required"}] model/suits))

(def rank-schema
  (into [:enum {:error/message "valid rank required"}] model/ranks))

(def card-schema
  [:map
   [:rank rank-schema]
   [:suit suit-schema]])

(def player-schema
  [:map
   [:hand [:vector card-schema]]])

(def game-state-schema
  [:map
   [:players [:vector player-schema]]
   [:draw-pile [:vector card-schema]]
   [:discard-pile [:vector card-schema]]
   [:active-suit [:maybe suit-schema]]
   [:current-player [:maybe :int]]
   [:status [:enum :waiting-for-start :in-progress :finished]]
   [:winner [:maybe :int]]])

(def command-schema
  [:multi {:dispatch :type}
   [:start-game [:map [:type [:= :start-game]] [:player-count pos-int?] [:deck [:vector card-schema]]]]
   [:play-card [:map [:type [:= :play-card]] [:player :int] [:card card-schema] [:declared-suit {:optional true} [:maybe suit-schema]]]]])

(def event-schema
  [:multi {:dispatch :type}
   [:game-started [:map [:type [:= :game-started]] [:players [:vector player-schema]] [:draw-pile [:vector card-schema]] [:discard-pile [:vector card-schema]] [:active-suit suit-schema] [:current-player :int] [:status [:= :in-progress]] [:winner nil?]]]
   [:card-played [:map [:type [:= :card-played]] [:player :int] [:card card-schema]]]
   [:suit-declared [:map [:type [:= :suit-declared]] [:suit suit-schema]]]
   [:turn-advanced [:map [:type [:= :turn-advanced]] [:player :int]]]
   [:game-won [:map [:type [:= :game-won]] [:player :int]]]])

(def domain-error-schema
  [:map [:type [:= :domain-error]] [:reason keyword?]])

(defn valid? [schema value]
  (m/validate schema value))
