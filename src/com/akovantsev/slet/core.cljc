(ns com.akovantsev.slet.core
  #?(:cljs (:require-macros [com.akovantsev.slet.core :refer [slet slet!]]))
  (:require [clojure.spec.alpha :as s]))

;;todo: handle and or (s/keys :req [(or ::a ::b)])

(defn -keys-keys [skeys-form]
  (let [m   (->> skeys-form (rest) (apply hash-map))
        skw #(-> % name keyword)]
    (-> #{}
      (into (->> m :req))
      (into (->> m :req-un (map skw)))
      (into (->> m :opt))
      (into (->> m :opt-un (map skw))))))




(declare -spec-keys)
(declare -conf-keys)
(defn -collect-spec-keys [root-spec coll] (->> coll (mapcat #(-spec-keys root-spec %)) (into #{})))
(defn -collect-conf-keys [root-spec coll] (->> coll (mapcat #(-conf-keys root-spec %)) (into #{})))


(defmulti  -seq-form-keys              (fn [root-spec form err] (first form)))
(defmethod -seq-form-keys         :default [root-spec form err] (throw (ex-info err {'root-spec root-spec 'form form})))
(defmethod -seq-form-keys          `s/keys [root-spec form err] (->> form -keys-keys))
(defmethod -seq-form-keys            `s/or [root-spec form err] (->> form rest (partition 2) (map second) (-collect-spec-keys root-spec)))
(defmethod -seq-form-keys           `s/and [root-spec form err] (->> form rest (-collect-spec-keys root-spec)))
(defmethod -seq-form-keys         `s/merge [root-spec form err] (->> form rest (-collect-spec-keys root-spec)))
(defmethod -seq-form-keys    `s/multi-spec [root-spec form err] (->> form second resolve deref methods keys (-collect-spec-keys root-spec)))
(defmethod -seq-form-keys          `s/spec [root-spec form err] (->> form second (-spec-keys root-spec)))
(defmethod -seq-form-keys       `s/nilable [root-spec form err] (->> form second (-spec-keys root-spec)))
(defmethod -seq-form-keys `s/nonconforming [root-spec form err] (->> form second (-spec-keys root-spec)))


(defmulti  -seq-conf-keys              (fn [root-spec form err] (first form)))
(defmethod -seq-conf-keys         :default [root-spec form err] (throw (ex-info err {'root-spec root-spec 'form form})))
(defmethod -seq-conf-keys          `s/keys [root-spec form err] (->> form -keys-keys))
(defmethod -seq-conf-keys           `s/and [root-spec form err] (->> form rest (-collect-conf-keys root-spec)))
(defmethod -seq-conf-keys         `s/merge [root-spec form err] (->> form rest (-collect-conf-keys root-spec)))
(defmethod -seq-conf-keys    `s/multi-spec [root-spec form err] (->> form second resolve deref methods keys (-collect-conf-keys root-spec)))
(defmethod -seq-conf-keys          `s/spec [root-spec form err] (->> form second (-conf-keys root-spec)))
(defmethod -seq-conf-keys       `s/nilable [root-spec form err] (->> form second (-conf-keys root-spec)))
(defmethod -seq-conf-keys           `s/cat [root-spec form err] (->> form rest (partition 2) (map first) set))
(defmethod -seq-conf-keys             `s/? [root-spec form err] (->> form second (-conf-keys root-spec)))
(defmethod -seq-conf-keys             `s/+ [root-spec form err] (->> form second (-conf-keys root-spec)))
(defmethod -seq-conf-keys             `s/* [root-spec form err] (->> form second (-conf-keys root-spec)))
(defmethod -seq-conf-keys `s/nonconforming [root-spec form err] (->> form second (-spec-keys root-spec)))
;; s/alt and s/or conform to vec [:tag value], which is not a map

(defn -spec-keys [root-spec form]
  (let [not-key-msg  (str "in:\n  " root-spec "\nnot an s/keys spec:\n  " form)]
    (cond
      (qualified-keyword? form) ;;alias
      (recur root-spec (s/form form))

      (seq? form)
      (-seq-form-keys root-spec form not-key-msg)

      :else
      (throw (ex-info not-key-msg {})))))


(defn -conf-keys [root-spec form]
  (let [not-key-msg  (str "in:\n  " root-spec "\nnot an s/keys spec:\n  " form)]
    (cond
      (qualified-keyword? form) ;;alias
      (recur root-spec (s/form form))

      (seq? form)
      (-seq-conf-keys root-spec form not-key-msg)

      :else
      (throw (ex-info not-key-msg {})))))


(defn -get-form [spec]
  (let [form (s/form spec)]
    (if (= form ::s/unknown)
      (throw (ex-info (str "not a spec: " spec) {}))
      form)))

(defn -expand [validate? [sym expr :as no-change]]
  (if-not (map? sym)
    {::slet-pair no-change}
    (let [[k spec] (find sym :spec)
          [k conf] (find sym :conf)
          let-keys (get sym :keys)
          both     (and spec conf)
          both-err (str "use :spec or :conf, but not both:\n  " sym)
          sym-     (dissoc sym :spec :conf)]
      (cond
        both  (throw (ex-info both-err {}))

        spec  {::slet-spec      spec
               ::slet-mode      :spec
               ::slet-let-keys  let-keys
               ::slet-spec-keys (-spec-keys spec (-get-form spec))
               ::slet-pair      (if validate?
                                  [sym- (list `s/assert* spec expr)]
                                  [sym- expr])}

        conf  {::slet-spec      conf
               ::slet-mode      :conf
               ::slet-let-keys  let-keys
               ::slet-spec-keys (-conf-keys conf (-get-form conf))
               ::slet-pair      (if validate?
                                  [sym- (list `s/conform conf (list `s/assert* conf expr))]
                                  [sym- expr])}

        :else {::slet-pair no-change}))))


(defn -slet [validate? bindings bodies]
  (let [expand#   (partial -expand validate?)
        expanded# (->> bindings (partition-all 2) (map expand#))
        bindings# (->> expanded# (mapcat ::slet-pair) (vec))]
    (doseq [{:keys [::slet-spec ::slet-spec-keys ::slet-let-keys ::slet-mode]} expanded#]
      (doseq [k slet-let-keys]
        (assert
          (contains? slet-spec-keys k)
          (str
            (case slet-mode
              :spec "\nno key:\n  "
              :conf "\nno tag:\n  ") k
            "\nin spec:\n  " slet-spec
            "\nspec form:\n  " (pr-str (s/form slet-spec))
            (case slet-mode
              :spec "\nspec keys:\n  "
              :conf "\nconformed spec tags:\n  ") (pr-str slet-spec-keys)
            "\nassert:"))))
    (apply list 'let bindings# bodies)))



(defmacro slet  [bindings & bodies] (-slet false bindings bodies))
(defmacro slet! [bindings & bodies] (-slet true  bindings bodies))


#_(s/def ::a pos-int?)
#_(s/def ::b pos-int?)
#_(s/def ::foo (s/keys :req [::a]))
#_(s/def ::bar (s/and ::foo (s/keys :req [::b])))
#_(s/def ::not-keys (s/merge ::foo (s/every-kv any? any?)))
#_(slet [{:keys [::a ::c] :spec ::not-spec} {}] [a c])
#_(slet [{:keys [::a ::c] :spec ::not-keys} {}] [a c])
#_(slet [{:keys [::a ::c] :spec ::bar} {}] [a c])
#_(slet [{:keys [::a ::b] :spec ::bar} {}] [a b])
#_(slet  [{:keys [::a ::b] :spec ::bar} {::a -1 ::b 2}] [a b])
#_(slet! [{:keys [::a ::b] :spec ::bar} {::a -1 ::b 2}] [a b])
#_(slet [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b])
#_(slet [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2} x 3] [a b x])
#_(macroexpand-1 '(slet  [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
#_(macroexpand-1 '(slet! [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
#_(macroexpand-1 '(slet  [{:keys [::a ::b] :conf ::bar} {::a 1 ::b 2}] [a b]))
#_(macroexpand-1 '(slet! [{:keys [::a ::b] :conf ::bar} {::a 1 ::b 2}] [a b]))

#_(s/def ::baz (s/cat :foo (s/? ::foo) :bar (s/* ::bar)))
#_(slet [{:keys [:foo :bar] :conf ::baz2} []] [foo bar])
#_(slet [{:keys [::a :bar] :conf ::baz} []] [a bar])
#_(slet [x 1 {:keys [:foo :bar] :conf ::baz} (s/conform ::baz [])] [x foo bar])
#_(slet! [x 1 {:keys [:foo :bar] :conf ::baz} []] [x foo bar])
#_(slet! [x 1 {:keys [:foo :bar] :conf ::baz} [{::a 1} {::b 2 ::a 3}]] [x foo bar])