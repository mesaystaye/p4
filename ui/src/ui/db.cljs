(ns ui.db)

(def default-db
  {:movies []
   :ratings {}  ; Empty map for ratings
   :current-page 1
   :total-pages 1  ; Make sure this is initialized
   :loading? false})
