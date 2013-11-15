(ns modern-cljs.shopping
  (:require-macros [hiccups.core :refer [html]]
                   [cljs.core.async.macros :refer [go-loop]])
  (:require [domina :refer [by-id value by-class set-value! append! destroy!
                            add-class! remove-class! text set-text!]]
            [domina.events :refer [listen! prevent-default]]
            [domina.xpath :refer [xpath]]
            [hiccups.runtime :as hiccupsrt]
            [shoreleave.remotes.http-rpc :refer [remote-callback]]
            [modern-cljs.shopping.validators :refer [validate-field]]
            [cljs.core.async :refer [put! chan >! <! map<]]))

(defn calculate [evt]
  (let [quantity (value (by-id "quantity"))
        price (value (by-id "price"))
        tax (value (by-id "tax"))
        discount (value (by-id "discount"))]
    (remote-callback :calculate
                     [quantity price tax discount]
                     #(set-value! (by-id "total") (.toFixed % 2)))
    (prevent-default evt)))

(defn add-help! []
  (append! (by-id "shoppingForm")
               (html [:div.help "Click to calculate"])))

(defn remove-help![]
  ;;(destroy! (by-class "help")))
  (destroy! (.getElementsByClassName js/document "help")))

(defn listen [el type]
  (let [out (chan)]
    (listen! el type (partial put! out))
    out))

(defn check-field [field]
  (let [errs-chan (map< (fn [evt]
                          (validate-field (keyword field) (.-value (:target evt))))
                        (listen (by-id field) :change))
        label (xpath (str "//label[@for='" field "']"))
        title (text label)]
    (go-loop
      []
      (let [errs (<! errs-chan)]
        (if errs
          (-> label (add-class! "error") (set-text! (first errs)))
          (-> label (remove-class! "error") (set-text! title)))
        (recur)))))

(defn ^:export init []
  (when (and js/document
             (aget js/document "getElementById"))
    (loop [fields '("quantity" "price" "tax" "discount")]
      (when (not (empty? fields))
        (check-field (first fields))
        (recur (rest fields))))
    (listen! (by-id "calc") :click (fn [evt] (calculate evt)))
    (listen! (by-id "calc") :mouseover add-help!)
    (listen! (by-id "calc") :mouseout remove-help!)))
