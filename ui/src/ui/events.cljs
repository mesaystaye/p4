(ns ui.events
  (:require
   [re-frame.core :as rf]
   [ajax.core :as ajax]
   [ui.config :as config]
   [ui.db :as db]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   {:movies []
    :ratings {}
    :current-page 1
    :total-pages 0
    :loading? false}))

(rf/reg-event-fx
 ::fetch-movies
 (fn [{:keys [db]} [_ page]]
   {:db (assoc db :loading? true)
    :http-xhrio {:method          :get
                 :uri             (str config/api-url "/movies?page=" page)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [::fetch-movies-success]
                 :on-failure      [::fetch-movies-failure]}}))

(rf/reg-event-db
 ::fetch-movies-success
 (fn [db [_ response]]
   (-> db
       (update :movies #(if (= 1 (:page response))
                         (:movies response)  ; First page: replace movies
                         (concat % (:movies response))))  ; Later pages: append
       (assoc :current-page (:page response))
       (assoc :total-pages (:total_pages response))
       (assoc :loading? false))))

(rf/reg-event-db
 ::fetch-movies-failure
 (fn [db [_ _]]
   (assoc db :loading? false)))

(rf/reg-event-fx
 ::load-more
 (fn [{:keys [db]} _]
   (when (< (:current-page db) (:total-pages db))
     {:dispatch [::fetch-movies (inc (:current-page db))]})))

(rf/reg-event-db
 ::rate-movie
 (fn [db [_ movie-id rating]]
   (if rating
     (assoc-in db [:ratings (str "m" movie-id)] rating)
     (update db :ratings dissoc (str "m" movie-id)))))

(rf/reg-event-fx
 ::fetch-recommendations
 (fn [{:keys [db]} _]
   (when (seq (:ratings db))  ; Only fetch if we have ratings
     {:db (assoc db :loading-recommendations? true)
      :http-xhrio {:method          :post
                   :uri             (str config/api-url "/recommend")
                   :params          {:ratings (:ratings db)}
                   :format          (ajax/json-request-format)
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success      [::fetch-recommendations-success]
                   :on-failure      [::fetch-recommendations-failure]}})))

(rf/reg-event-db
 ::fetch-recommendations-success
 (fn [db [_ response]]
   (-> db
       (assoc :recommendations response)
       (assoc :loading-recommendations? false))))

(rf/reg-event-db
 ::fetch-recommendations-failure
 (fn [db [_ _]]
   (assoc db :loading-recommendations? false)))

(rf/reg-event-fx
 ::fetch-top-movies
 (fn [{:keys [db]} _]
   {:http-xhrio {:method          :get
                 :uri             (str config/api-url "/top10")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [::fetch-top-movies-success]
                 :on-failure      [::fetch-top-movies-failure]}}))

(rf/reg-event-db
 ::fetch-top-movies-success
 (fn [db [_ response]]
   (assoc db :top-movies response)))

(rf/reg-event-db
 ::fetch-top-movies-failure
 (fn [db [_ _]]
   db))
