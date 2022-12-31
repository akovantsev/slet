## What

clj/cljs `let`-like macro to avoid typos in keys destructuring at macroexpansion time.
<br>
<br>Specs must be loaded first, before `slet` macro expansion.
<br>Checks only `:keys` (not `::keys`, `::foo/keys`, `syms`, `strs`, etc.), and only when `:spec` or `:conf` is present.
<br>`slet` only checks destructuring keys against `:spec`.
<br>`slet!` does what `slet` does, plus at runtime validates against `:spec` or `:conf` spec, plus at runtime conforms valid value to `:conf` spec.
<br>`:spec` spec must be one `s/keys` spec, or several `s/keys` specs with `s/and`, `s/merge`, `s/multi-spec`, `s/or`.
<br>`:conf` spec must be one `s/keys` spec, or several `s/keys` specs with `s/and`, `s/merge`, `s/multi-spec`, `s/cat`.
<br> both can be wrapped in `s/spec`, `s/nilable`, `s/nonconforming`.

## Install 

```clojure
;; in deps.edn
{:deps {github-akovantsev/slet
        {:git/url "https://github.com/akovantsev/slet"
         :sha     "5b1b118ffadb30f401a78ed2ee14d9b5701a2f06"}}} ;; actual sha
```

## Usage

### :spec

Checks that desturcturing uses only data keys mentioned in spec.
<br>`slet!` validates form at runtime against `:spec` spec.
<br>`slet` does not modify form.

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


(slet [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2} x 3] [a b x])
;=> [1 2 3]


(macroexpand-1 '(slet [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
;=> (let [{:keys [:example/a :example/b]} {:example/a 1, :example/b 2}]
;     [a b])

(macroexpand-1 '(slet! [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
;=> (let [{:keys [:example/a :example/b]} (clojure.spec.alpha/assert* :example/bar
;                                           {:example/a 1, :example/b 2})]
;     [a b])
```

### :conf

Checks that desturcturing uses only conformed keys mentioned in spec.
<br>`slet!` validates and conforms form at runtime against `:conf` spec.
<br>`slet` does not modify form.

```clojure
(macroexpand-1 '(slet  [{:keys [::a ::b] :conf ::bar} {::a 1 ::b 2}] [a b]))
;=> (let [{:keys [:example/a :example/b]} {:example/a 1, :example/b 2}]
;     [a b])

(macroexpand-1 '(slet! [{:keys [::a ::b] :conf ::bar} {::a 1 ::b 2}] [a b]))
;=> (let [{:keys [:example/a :example/b]} (clojure.spec.alpha/conform :example/bar
;                                         (clojure.spec.alpha/assert* :example/bar
;                                           {:example/a 1, :example/b 2}))]
;     [a b])


(slet [{:keys [::a ::c] :conf ::foo} []] [a c])
;Unexpected error (AssertionError) macroexpanding slet at (src/example.cljc:26:1).
;Assert failed:
;no tag:
;  :example/c
;in spec:
;  :example/foo
;spec form:
;  (clojure.spec.alpha/keys :req [:example/a])
;conformed spec tags:
;  #{:example/a}
;assert:
;(contains? slet-spec-keys k)



(s/def ::baz (s/cat :foo (s/? ::foo) :bar (s/* ::bar)))

(slet [{:keys [:foo :bar] :conf ::baz2} []] [foo bar])
;Syntax error macroexpanding slet at (src/example.cljc:25:1).
;Unable to resolve spec: :example/baz2

(slet [{:keys [::a :bar] :conf ::baz} []] [a bar])
;Unexpected error (AssertionError) macroexpanding slet at (src/example.cljc:26:1).
;Assert failed:
;no tag:
;  :example/a
;in spec:
;  :example/baz
;spec form:
;  (clojure.spec.alpha/cat :foo (clojure.spec.alpha/? :example/foo) :bar (clojure.spec.alpha/* :example/bar))
;conformed spec tags:
;  #{:bar :foo}
;assert:
;(contains? slet-spec-keys k)

(slet [x 1 {:keys [:foo :bar] :conf ::baz} (s/conform ::baz [])] [x foo bar])
;=> [1 nil nil]

(slet! [x 1 {:keys [:foo :bar] :conf ::baz} []] [x foo bar])
;=> [1 nil nil]


(s/conform ::baz [{::a 1} {::b 2 ::a 3}])
;=> {:foo {:example/a 1}, :bar [{:example/b 2, :example/a 3}]}

(slet! [x 1 {:keys [:foo :bar] :conf ::baz} [{::a 1} {::b 2 ::a 3}]] [x foo bar])
;=> [1 {:example/a 1} [{:example/b 2, :example/a 3}]]
```