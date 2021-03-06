(ns darkexchange.controller.main.search.search-tab
  (:require [clojure.contrib.logging :as logging]
            [darkexchange.controller.actions.utils :as action-utils]
            [darkexchange.controller.offer.has-panel :as offer-has-panel]
            [darkexchange.controller.offer.wants-panel :as offer-wants-panel]
            [darkexchange.controller.offer.view :as offer-view-controller]
            [darkexchange.controller.utils :as controller-utils]
            [darkexchange.controller.widgets.utils :as widgets-utils]
            [darkexchange.model.calls.search-offers :as search-offers-call]
            [darkexchange.model.offer :as offer-model]
            [darkexchange.model.terms :as terms]
            [darkexchange.view.main.search.search-tab :as search-tab-view]
            [seesaw.core :as seesaw-core]
            [seesaw.table :as seesaw-table]))

(def search-futures (atom nil))

(defn find-search-offer-table [parent-component]
  (seesaw-core/select parent-component ["#search-offer-table"]))

(defn find-view-offer-button [parent-component]
  (seesaw-core/select parent-component ["#view-offer-button"]))

(defn find-search-button [parent-component]
  (seesaw-core/select parent-component ["#search-button"]))

(defn insert-offer-into-table [parent-component offer]
  (let [search-offer-table (find-search-offer-table parent-component)]
    (seesaw-table/insert-at! search-offer-table (seesaw-table/row-count search-offer-table) offer)))

(defn load-search-offer-table [parent-component offers]
  (when offers
    (doseq [offer offers]
      (seesaw.core/invoke-later (insert-offer-into-table parent-component offer)))))

(defn convert-offer [offer]
  { :id (:id offer)
    :public-key (:public-key offer)
    :public-key-algorithm (:public-key-algorithm offer)
    :name (:name offer)
    :has (offer-model/has-amount-str offer)
    :to_send_by (offer-model/has-payment-type-str offer)
    :wants (offer-model/wants-amount-str offer)
    :to_receive_by (offer-model/wants-payment-type-str offer)
    :has_div_wants (offer-model/calculate-has-div-wants offer)
    :wants_div_has (offer-model/calculate-wants-div-has offer) })

(defn search-call-back [parent-component found-offers]
  (when found-offers
    (load-search-offer-table parent-component (map convert-offer found-offers))))

(declare attach-search-action)

(defn set-search-mode [parent-component]
  (reset! search-futures nil)
  (seesaw-core/config! (find-search-button parent-component) :text (terms/search))
  (controller-utils/enable-widget (offer-has-panel/find-has-panel parent-component))
  (controller-utils/enable-widget (offer-wants-panel/find-wants-panel parent-component)))

(defn set-cancel-search-mode [parent-component]
  (seesaw-core/config! (find-search-button parent-component) :text (terms/cancel))
  (controller-utils/disable-widget (offer-has-panel/find-has-panel parent-component))
  (controller-utils/disable-widget (offer-wants-panel/find-wants-panel parent-component))
  (reset! search-futures
    (search-offers-call/call
      (offer-has-panel/i-have-currency parent-component)
      (offer-has-panel/i-have-payment-type parent-component)
      (offer-wants-panel/i-want-currency parent-component)
      (offer-wants-panel/i-want-payment-type parent-component)
      #(search-call-back parent-component %))))

(defn search-mode? [parent-component]
  (not @search-futures))

(defn run-cancel [parent-component]
  (doseq [search-future @search-futures]
    (when (and (not (future-done? search-future)) (not (future-cancelled? search-future)))
      (future-cancel search-future)))
  (set-search-mode parent-component))

(defn search-done [parent-component]
  (future
    (doseq [search-future @search-futures]
      @search-future)
    (seesaw-core/invoke-later
      (set-search-mode parent-component))))

(defn run-search [parent-component]
  (seesaw-table/clear! (find-search-offer-table parent-component))
  (set-cancel-search-mode parent-component)
  (search-done parent-component))

(defn run-search-button-action [parent-component]
  (let [search-button (find-search-button parent-component)]
    (if (search-mode? parent-component)
      (run-search parent-component)
      (run-cancel parent-component))))

(defn attach-search-action [parent-component]
  (let [search-button (find-search-button parent-component)]
    (seesaw-core/listen search-button :action (fn [e] (run-search-button-action parent-component)))))

(defn view-offer-listener [parent-component]
  (let [search-offer-table (find-search-offer-table parent-component)
        selected-row (seesaw-table/value-at search-offer-table (seesaw-core/selection search-offer-table))]
    (offer-view-controller/show parent-component selected-row)))

(defn attach-view-offer-action [parent-component]
  (action-utils/attach-listener parent-component "#view-offer-button"
    (fn [e] (view-offer-listener parent-component))))

(defn view-offer-if-enabled [main-frame]
  (widgets-utils/do-click-if-enabled (find-view-offer-button main-frame)))

(defn attach-view-offer-table-action [main-frame]
  (widgets-utils/add-table-action (find-search-offer-table main-frame)
    #(view-offer-if-enabled main-frame))
  main-frame)

(defn attach-view-offer-enable-listener [main-frame]
  (widgets-utils/single-select-table-button (find-view-offer-button main-frame)
    (find-search-offer-table main-frame))
  main-frame)

(defn load-data [main-frame]
  (offer-wants-panel/load-data (offer-has-panel/load-data main-frame)))

(defn attach [main-frame]
  (attach-search-action main-frame)
  (attach-view-offer-action main-frame)
  (offer-has-panel/attach main-frame)
  (offer-wants-panel/attach main-frame)
  (attach-view-offer-table-action main-frame)
  (attach-view-offer-enable-listener main-frame))

(defn init [main-frame]
  (attach (load-data main-frame)))