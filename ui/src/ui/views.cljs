(ns ui.views
  (:require
   [re-frame.core :as rf]
   [reagent.core :as r]
   [ui.subs :as subs]
   [ui.events :as events]
   [ui.config :as config]))

(def placeholder-img "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='300' height='450' viewBox='0 0 300 450'%3E%3Crect width='300' height='450' fill='%23333'/%3E%3Ctext x='50%25' y='50%25' text-anchor='middle' fill='white'%3ENo Image%3C/text%3E%3C/svg%3E")

(defn star-rating [{:keys [movie-id]}]
  (let [rating @(rf/subscribe [::subs/movie-rating movie-id])]
    [:div.star-rating
     (for [star (range 1 6)]
       ^{:key star}
       [:span.star
        {:style {:color (if (<= star (or rating 0)) "#FFD700" "#DDD")
                 :cursor "pointer"
                 :font-size "24px"}
         :on-click #(rf/dispatch [::events/rate-movie movie-id star])}
        "★"])]))

(defn movie-card [{:keys [MovieID Title PosterURL]}]
  [:div.movie-card
   [:div.poster-container
    [:img.movie-poster 
     {:src PosterURL
      :alt Title
      :on-error #(-> % .-target .-src (set! placeholder-img))}]]
   [:h3.movie-title Title]
   [star-rating {:movie-id MovieID}]])

(defn load-more-button []
  (let [can-load-more? @(rf/subscribe [::subs/can-load-more?])
        loading? @(rf/subscribe [::subs/loading?])]
    [:button.load-more
     {:on-click #(rf/dispatch [::events/load-more])
      :disabled (or loading? (not can-load-more?))}
     (cond
       loading? "Loading..."
       (not can-load-more?) "No More Movies"
       :else "Load More Movies")]))

(defn movies-list []
  (let [movies @(rf/subscribe [::subs/movies])
        loading? @(rf/subscribe [::subs/loading?])]
    [:div.movies-container
     [:div.movies-grid
      (for [movie movies]
        ^{:key (:MovieID movie)}
        [movie-card movie])]]))

(defn get-recommendations-button []
  (let [has-ratings? @(rf/subscribe [::subs/has-ratings?])
        loading? @(rf/subscribe [::subs/loading-recommendations?])]
    [:button.recommend-button
     {:on-click #(rf/dispatch [::events/fetch-recommendations])
      :disabled (or (not has-ratings?) loading?)}
     (cond
       loading? "Getting Recommendations..."
       (not has-ratings?) "Rate some movies first"
       :else "Get Recommendations")]))

(defn recommendations-section []
  (let [recommendations @(rf/subscribe [::subs/recommendations])
        loading? @(rf/subscribe [::subs/loading-recommendations?])]
    (when (or loading? (seq recommendations))
      [:div.recommendations
       [:div.section-header "Recommended Movies"]
       (cond
         loading? [:div.loading "Finding movies you'll love..."]
         (seq recommendations)
         [:div.movies-grid
          (for [movie recommendations]
            ^{:key (:MovieID movie)}
            [movie-card movie])])])))

(defn button-container []
  [:div.button-container
   [load-more-button]
   [get-recommendations-button]])

(defn simple-movie-card [{:keys [MovieID Title PosterURL]}]
  [:div.movie-card
   [:div.poster-container
    [:img.movie-poster 
     {:src PosterURL
      :alt Title
      :on-error #(-> % .-target .-src (set! placeholder-img))}]]
   [:h3.movie-title Title]])

(defn top-10-section []
  (let [top-movies @(rf/subscribe [::subs/top-movies])]
    [:div.top-10-section
     [:div.section-header "Top 10 Movies"]
     [:div.top-movies-grid
      (for [movie top-movies]
        ^{:key (:MovieID movie)}
        [simple-movie-card movie])]]))

(defn main-panel []
  (r/create-class
   {:component-did-mount
    (fn []
      (rf/dispatch [::events/fetch-movies 1])
      (rf/dispatch [::events/fetch-top-movies]))
    
    :reagent-render
    (fn []
      [:div.container
       [:div.main-content
        [:div.rating-section
         [:div.section-header "Rate movies to get recommendations"]
         [movies-list]
         [button-container]]
        [:div.recommendations-section
         [recommendations-section]]]
       [:div.sidebar
        [top-10-section]]])}))
