(ns crazy_eights.web.style-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(def css (slurp "resources/public/style.css"))

(deftest observer-hands-use-compact-overlapping-cards
  (is (str/includes? css "--observer-card-w: 64px;"))
  (is (str/includes? css "width: var(--observer-card-w);"))
  (is (str/includes? css "margin-left: calc(var(--observer-card-w) * -0.62);")))
