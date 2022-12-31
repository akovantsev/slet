(ns com.akovantsev.slet.core
  #?(:cljs (:require-macros [com.akovantsev.slet.core :refer [slet slet!]]))
  (:require [clojure.spec.alpha :as s]))


(defn -keys-keys [skeys-form]
  (let [m   (->> skeys-form (rest) (apply hash-map))
        skw #(-> % name keyword)]
    (-> #{}
      (into (->> m :req))
      (into (->> m :req-un (map skw)))
      (into (->> m :opt))
      (into (->> m :opt-un (map skw))))))




(declare -spec-keys)
(defn -collect-keys [root-spec coll]
  (->> coll (mapcat #(-spec-keys root-spec %)) (into #{})))

(defmulti  -seq-form-keys           (fn [root-spec form err] (first form)))
(defmethod -seq-form-keys      :default [root-spec form err] (throw (ex-info err {'root-spec root-spec 'form form})))
(defmethod -seq-form-keys       `s/keys [root-spec form err] (->> form -keys-keys))
(defmethod -seq-form-keys         `s/or [root-spec form err] (->> form rest (partition 2) (map second) (-collect-keys root-spec)))
(defmethod -seq-form-keys        `s/and [root-spec form err] (->> form rest (-collect-keys root-spec)))
(defmethod -seq-form-keys      `s/merge [root-spec form err] (->> form rest (-collect-keys root-spec)))
(defmethod -seq-form-keys `s/multi-spec [root-spec form err] (->> form second resolve deref methods keys (-collect-keys root-spec)))
(defmethod -seq-form-keys    `s/nilable [root-spec form err] (->> form second (-spec-keys root-spec)))


(defn -spec-keys [root-spec form]
  (let [not-key-msg  (str "in:\n  " root-spec "\nnot an s/keys spec:\n  " form)
        not-spec-msg (str "in:\n  " root-spec "\nis not a spec:\n  " form)]
    (cond
      (= :clojure.spec.alpha/unknown form)
      (throw (ex-info not-spec-msg {}))

      (qualified-keyword? form) ;;alias
      (recur root-spec (s/form form))

      (seq? form)
      (-seq-form-keys root-spec form not-key-msg)

      :else
      (throw (ex-info not-key-msg {})))))


(defn -expand [validate? [sym expr :as no-change]]
  (if-not (map? sym)
    {::slet-pair no-change}
    (let [[k spec] (find sym :spec)]
      (cond
        (nil? k)    {::slet-pair no-change}
        (nil? spec) {::slet-pair no-change}
        :else       (let [form (s/form spec)]
                      (if (= form :clojure.spec.alpha/unknown)
                        (throw (ex-info (str "not a spec: " spec) {}))
                        (let [sym-     (dissoc sym :spec)
                              pair     (if validate?
                                         [sym- (list `s/assert* spec expr)]
                                         [sym- expr])]
                          {::slet-pair      pair
                           ::slet-spec      spec
                           ::slet-spec-keys (-spec-keys spec form)
                           ::slet-let-keys  (get sym :keys)})))))))


(defn -slet [validate? bindings bodies]
  (let [expand#   (partial -expand validate?)
        expanded# (->> bindings (partition-all 2) (map expand#))
        bindings# (->> expanded# (mapcat ::slet-pair) (vec))]
    (doseq [{:keys [::slet-spec ::slet-spec-keys ::slet-let-keys]} expanded#]
      (doseq [k slet-let-keys]
        (assert
          (contains? slet-spec-keys k)
          (str
            "\nno key:\n  " k "\nin spec:\n  " slet-spec
            "\nspec keys:\n  " (pr-str slet-spec-keys)
            "\nspec form:\n  " (pr-str (s/form slet-spec))
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
#_(macroexpand-1 '(slet [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
#_(macroexpand-1 '(slet! [{:keys [::a ::b] :spec ::bar} {::a 1 ::b 2}] [a b]))
