## Install 

```clojure
;; in deps.edn
{:deps {github-akovantsev/slet
        {:git/url "https://github.com/akovantsev/slet"
         :sha     "9af2a74c3aaa723808ad33d4760238c3f01c0127"}}} ;; actual sha
```

## Usage

```clojure
(ns example
  (:require
   [clojure.spec.alpha :as s]
   [com.akovantsev.slet.core :refer [slet slet!]]))


(s/def ::a pos-int?)
(s/def ::b pos-int?)
(s/def ::foo (s/keys :req [::a]))
(s/def ::bar (s/and ::foo (s/keys :req [::b])))
(s/def ::not-keys (s/merge ::foo (s/every-kv any? any?)))

(slet [{:keys [::a ::c] :spec ::not-spec} {}] [a c])
;Syntax error macroexpanding slet at (src/example.cljc:11:3).
;Unable to resolve spec: :example/not-spec


(slet [{:keys [::a ::c] :spec ::not-keys} {}] [a c])
;Syntax error macroexpanding slet at (src/example.cljc:12:3).
;in:
;  :example/not-keys
;not an s/keys spec:
;  (clojure.spec.alpha/every-kv clojure.core/any? clojure.core/any?)


(slet [{:keys [::a ::c] :spec ::bar} {}] [a c])
;Unexpected error (AssertionError) macroexpanding slet at (src/example.cljc:13:3).
;Assert failed:
;no key:
;  :example/c
;in spec:
;  :example/bar
;spec keys:
;  #{:example/a :example/b}
;spec form:
;  (clojure.spec.alpha/and :example/foo (clojure.spec.alpha/keys :req [:example/b]))
;assert:
;(contains? slet-spec-keys k)


(slet [{:keys [::a ::b] :spec ::bar} {}] [a b])
;=> [nil nil]

(slet  [{:keys [::a ::b] :spec ::bar} {::a -1 ::b 2}] [a b])
;=> [-1 2]


(slet! [{:keys [::a ::b] :spec ::bar} {::a -1 ::b 2}] [a b])
;Execution error - invalid arguments to example/eval2480 at (example.cljc:16).
;-1 - failed: pos-int? at: [:example/a] spec: :example/a


(slet [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b])
;=> [1 2]


(macroexpand-1 '(slet [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
;=> (let [{:keys [:example/a :example/b]} {:example/a 1, :example/b 2}]
;     [a b])

(macroexpand-1 '(slet! [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
;=> (let [{:keys [:example/a :example/b]} (clojure.spec.alpha/assert*
;                                           :example/bar
;                                           {:example/a 1, :example/b 2})]
;     [a b])
```