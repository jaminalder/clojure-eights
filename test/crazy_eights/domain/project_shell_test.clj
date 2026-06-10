(ns crazy_eights.domain.project-shell-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

;; Kept for later task coverage once classpath resources exist.
(defn resource-exists? [path]
  (boolean (io/resource path)))

(deftest project-shell-files-exist
  (testing "documentation files exist"
    (is (.exists (io/file "README.md")))
    (is (.exists (io/file "docs/domain.md")))
    (is (.exists (io/file "docs/adr/0001-domain-first-clojure.md")))
    (is (.exists (io/file "docs/adr/0002-no-database-initially.md")))))
