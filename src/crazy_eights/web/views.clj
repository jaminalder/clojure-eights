(ns crazy_eights.web.views
  "Hiccup rendering of view models. Pure: view model in, HTML string out.
  Fragment functions return single-line HTML so they can travel as SSE data."
  (:require [crazy_eights.web.cards :as cards]
            [crazy_eights.web.paths :as paths]
            [hiccup2.core :as h]))

(defn- html [content]
  (str (h/html content)))

;; status fragment

(def ^:private error-messages
  {:not-a-player "you are not in this game"
   :not-current-player "not your turn"
   :card-not-in-hand "that card is not in your hand"
   :card-not-playable "that card cannot be played"
   :declared-suit-required "pick a suit for the eight"
   :must-play-before-drawing "you have a playable card"
   :cannot-pass-while-playable "you have a playable card"
   :draw-pile-empty "the draw pile is empty"
   :must-reshuffle-before-passing "the draw pile must be reshuffled first"
   :game-already-finished "the game is over"
   :not-host "only the host can start the game"
   :not-enough-players "at least two players are needed"
   :invalid-start-game "the game cannot be started"
   :game-in-progress "the game is in progress"
   :unknown-game "no such game"
   :game-full "the game is full"
   :game-already-started "the game has already started"
   :invalid-card "that is not a card"
   :invalid-suit "that is not a suit"})

(defn error-status-html [reason]
  (html [:p.status.error (get error-messages reason (name reason))]))

(defn status-html [{:keys [phase player-count your-turn? current-name winner-name blocked?]}]
  (html
   (case phase
     :waiting [:p.status (str "waiting for players — " player-count " joined")]
     :between-games [:p.status (str "between games — " player-count " seated")]
     :playing (if your-turn?
                [:p.status.your-turn "your turn"]
                [:p.status (str current-name "'s turn")])
     :finished (if blocked?
                 [:p.status "game blocked — no winner"]
                 [:p.status.your-turn (str winner-name " wins")]))))

;; game-board fragment

(defn- leave-form [game-id]
  [:form.table-action {:action (paths/leave game-id) :method "post"}
   [:button {:type "submit"} "leave table"]])

(defn- start-button [{:keys [game-id start-label]}]
  [:button {:hx-post (paths/start game-id)
            :hx-target "#status"}
   start-label])

(defn- table-actions [{:keys [can-start? can-leave?] :as vm}]
  (when (or can-start? can-leave?)
    [:div.actions.table-actions
     (when can-start?
       (start-button vm))
     (when can-leave?
       (leave-form (:game-id vm)))]))

(defn- waiting-room [{:keys [phase players] :as vm}]
  [:div.panel
   [:h2 (if (= :between-games phase) "between games" "waiting room")]
   [:ul.player-list
    (for [player players]
      [:li (:name player) (when (:host? player) [:span.host-mark "host"])])]
   (table-actions vm)
   (when (= :waiting phase)
     [:p.share
      "share this page's link to invite players"
      [:button {:onclick "navigator.clipboard.writeText(window.location.href)"}
       "copy link"]])])

(defn- opponent [{:keys [card-count current?] :as player}]
  [:div.opponent {:class (when current? "current")}
   [:div.name (:name player)]
   [:div.backs
    (for [_ (range (min card-count 8))]
      [:img {:src cards/back-image :alt "card back"}])]
   [:div.count (str card-count " cards")]])

(defn- table [{:keys [players viewer-seat top-card top-code active-suit draw-count
                      winner-name blocked? phase] :as vm}]
  [:div.table
   [:div.opponents
    (for [player (remove :you? players)]
      (opponent player))]
   [:div.center
    [:div.pile
     [:img {:src cards/back-image :alt "draw pile"}]
     [:div.label (str draw-count " to draw")]]
    [:div.pile
     [:img {:src (cards/card->image top-card) :alt top-code}]
     [:div.label "discard"]]
    [:div.suit-chip "suit "
     [:span {:class (cards/suit-color active-suit)}
      (cards/suit-glyph active-suit)]]]
   (when (= :finished phase)
     [:p.winner (if blocked? "game blocked — no winner" (str winner-name " wins"))])
   (when (and (= :finished phase) viewer-seat)
     (table-actions vm))])

(defn board-html [vm]
  (html
   (if (contains? #{:waiting :between-games} (:phase vm))
     (waiting-room vm)
     (table vm))))

;; player-hand fragment

(defn- join-form [{:keys [game-id]}]
  [:div.panel
   [:h2 "take a seat"]
   [:form {:action (paths/join game-id) :method "post"}
    [:label {:for "name"} "your name"]
    [:input {:type "text" :id "name" :name "name" :maxlength 24 :autofocus true}]
    [:button {:type "submit"} "sit down"]]])

(defn- card-button [game-id {:keys [code playable? eight?]}]
  (let [image [:img {:src (cards/card->image (cards/code->card code)) :alt code}]]
    (cond
      (and playable? eight?)
      [:button.card-btn.playable {:hx-get (str (paths/hand game-id) "?declare=" code)
                                  :hx-target "#player-hand"}
       image]

      playable?
      [:button.card-btn.playable {:hx-post (str (paths/play game-id) "?card=" code)
                                  :hx-target "#status"}
       image]

      :else
      [:button.card-btn.unplayable {:disabled true} image])))

(defn- suit-picker [game-id declare-code]
  [:div.suit-picker
   [:p "the eight changes the suit — pick one"]
   [:div.suits
    (for [suit [:clubs :diamonds :hearts :spades]]
      [:button.suit {:class (cards/suit-color suit)
                     :hx-post (str (paths/play game-id) "?card=" declare-code
                                   "&declared-suit=" (name suit))
                     :hx-target "#status"}
       (cards/suit-glyph suit)])]
   [:button {:hx-get (paths/hand game-id)
             :hx-target "#player-hand"}
    "back"]])

(defn- hand-cards [{:keys [game-id hand can-draw? can-pass? draw-count]}]
  [:div.hand-area
   [:div.hand-label "your hand"]
   [:div.hand
    (for [card hand]
      (card-button game-id card))]
   (when (or can-draw? can-pass?)
     [:div.actions
      (when can-draw?
        [:button {:hx-post (paths/draw game-id)
                  :hx-target "#status"}
         (if (zero? draw-count) "reshuffle and draw" "draw a card")])
      (when can-pass?
        [:button {:hx-post (paths/pass game-id)
                  :hx-target "#status"}
         "pass"])])])

(defn- declarable? [vm declare-code]
  (some #(and (= declare-code (:code %)) (:declarable? %))
        (:hand vm)))

(defn hand-html
  ([vm] (hand-html vm {}))
  ([{:keys [phase viewer-seat game-id] :as vm} {:keys [declare-code]}]
   (html
    (cond
      (and (= :waiting phase) (nil? viewer-seat)) (join-form vm)
      (contains? #{:waiting :between-games} phase) [:p.spectator-note "waiting for the host to deal"]
      (nil? viewer-seat) [:p.spectator-note "spectating — you watch, they play"]
      (declarable? vm declare-code) (suit-picker game-id declare-code)
      :else (hand-cards vm)))))

;; full pages

(defn- layout [title & body]
  (str "<!DOCTYPE html>"
       (h/html
        [:html
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:link {:rel "icon" :href "data:,"}]
          [:link {:rel "stylesheet" :href "/assets/style.css"}]
          [:title title]
          [:script {:src "/assets/vendor/htmx.min.js"}]
          [:script {:src "/assets/vendor/htmx-sse.js"}]]
         [:body
          [:h1 [:a {:href "/"} "crazy eights"]]
          [:main body]
          [:footer "cards: public-domain vector playing cards by byron knoll"]]])))

(defn start-page []
  (layout "Crazy Eights"
          [:p.tagline "first to an empty hand wins"]
          [:div.panel
           [:h2 "host a game"]
           [:form {:action "/games" :method "post"}
            [:label {:for "name"} "your name"]
            [:input {:type "text" :id "name" :name "name" :maxlength 24 :autofocus true}]
            [:button {:type "submit"} "create game"]]]))

(defn game-page [vm]
  (layout "Crazy Eights"
          [:div {:hx-ext "sse"
                 :sse-connect (paths/events (:game-id vm))
                 :sse-close "table-ended"}
           [:div {:id "status" :sse-swap "status"}
            (h/raw (status-html vm))]
           [:div {:id "game-board" :sse-swap "game-board"}
            (h/raw (board-html vm))]
           [:div {:id "player-hand" :sse-swap "player-hand"}
            (h/raw (hand-html vm))]
           [:div {:id "table-ended" :sse-swap "table-ended" :hidden true}]]))

(defn not-found-page []
  (layout "Crazy Eights"
          [:div.panel
           [:h2 "no such game"]
           [:p.spectator-note [:a.button {:href "/"} "back to the start"]]]))
