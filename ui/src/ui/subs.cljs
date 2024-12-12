(ns ui.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::movies
 (fn [db]
   (:movies db)))

(rf/reg-sub
 ::loading?
 (fn [db]
   (:loading? db)))

(rf/reg-sub
 ::movie-rating
 (fn [db [_ movie-id]]
   (let [rating (get-in db [:ratings (str "m" movie-id)])]
     (js/console.log "Getting rating for movie" movie-id ":" rating)
     rating)))

(rf/reg-sub
 ::recommendations
 (fn [db]
   (:recommendations db)))

(rf/reg-sub
 ::loading-recommendations?
 (fn [db]
   (:loading-recommendations? db)))

(rf/reg-sub
 ::current-page
 (fn [db]
   (:current-page db)))

(rf/reg-sub
 ::total-pages
 (fn [db]
   (:total-pages db)))

(rf/reg-sub
 ::can-load-more?
 (fn [db]
   (let [current-page (:current-page db)
         total-pages (:total-pages db)]
     (js/console.log "Can load more check:" 
                    "current-page:" current-page 
                    "total-pages:" total-pages)
     (< current-page total-pages))))

(rf/reg-sub
 ::has-ratings?
 (fn [db]
   (-> db :ratings count pos?)))

(rf/reg-sub
 ::top-movies
 (fn [db]
   (:top-movies db)))
