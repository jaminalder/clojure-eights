(ns crazy_eights.web.paths
  "Single source of truth for game URL paths. Pure: game id in, path out.
  Query strings (?card= etc.) are appended by callers on top of these bases.")

(defn game [id] (str "/games/" id))
(defn join [id] (str (game id) "/join"))
(defn start [id] (str (game id) "/start"))
(defn play [id] (str (game id) "/play"))
(defn draw [id] (str (game id) "/draw"))
(defn pass [id] (str (game id) "/pass"))
(defn leave [id] (str (game id) "/leave"))
(defn hand [id] (str (game id) "/hand"))
(defn events [id] (str (game id) "/events"))
