import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import pandas as pd
import numpy as np
import os
import shutil
import glob
import gc

# Enable pandas memory optimizations
pd.options.mode.chained_assignment = None  # default='warn'
pd.options.compute.use_bottleneck = True
pd.options.compute.use_numexpr = True

# Define all directory paths
BASE_DIR = os.path.dirname(__file__)
DATA_DIR = os.path.join(BASE_DIR, "data")
ASSETS_DIR = os.path.join(BASE_DIR, "assets")
IMAGES_DIR = os.path.join(ASSETS_DIR, "images")

# Create necessary directories
os.makedirs(IMAGES_DIR, exist_ok=True)

print(f"BASE_DIR: {BASE_DIR}")
print(f"ASSETS_DIR: {ASSETS_DIR}")
print(f"IMAGES_DIR: {IMAGES_DIR}")
print(f"Current working directory: {os.getcwd()}")
print(f"Files in IMAGES_DIR: {glob.glob(os.path.join(IMAGES_DIR, '*.jpg'))}")

app = FastAPI()

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# Load Data with memory optimizations
dtypes = {
    'UserID': 'int32',
    'MovieID': 'int32',
    'Rating': 'float32',
    'Timestamp': 'int32'
}

ratings = pd.read_csv(os.path.join(DATA_DIR, 'ratings.dat'), 
                     sep='::', 
                     engine='python', 
                     header=None,
                     dtype=dtypes)
ratings.columns = ['UserID', 'MovieID', 'Rating', 'Timestamp']

movies = pd.read_csv(os.path.join(DATA_DIR, 'movies.dat'), 
                    sep='::', 
                    engine='python', 
                    encoding="ISO-8859-1", 
                    header=None)
movies.columns = ['MovieID', 'Title', 'Genres']

# Free up memory
gc.collect()

# Compute popularity scores with memory optimizations
rating_merged = ratings.merge(movies[['MovieID', 'Title']], on='MovieID')
movie_stats = rating_merged.groupby('MovieID').agg(
    num_ratings=('Rating', 'count'),
    avg_rating=('Rating', 'mean')
).reset_index()

movie_stats['normalized_rating'] = (movie_stats['avg_rating'] - 1) / 4
movie_stats['popularity_score'] = movie_stats['num_ratings'] * movie_stats['normalized_rating']

# Clean up memory
del rating_merged
gc.collect()

def get_poster_url(movie_id):
    image_filename = f"{movie_id}.jpg"
    image_path = os.path.join(IMAGES_DIR, image_filename)
    
    if os.path.exists(image_path):
        return f"/assets/images/{image_filename}"
    return None

# Add PosterURL to movies efficiently
movies['PosterURL'] = movies['MovieID'].apply(get_poster_url)

# Create final movie_popularity dataframe with memory optimization
movie_popularity = movie_stats.merge(
    movies[['MovieID', 'Title', 'PosterURL']], 
    on='MovieID',
    copy=False
)
movie_popularity.index = ['m' + str(item) for item in movie_popularity['MovieID'].tolist()]

# Clean up memory
del movie_stats
gc.collect()

# Create rating matrix
rating_matrix = ratings.pivot(index='UserID', columns='MovieID', values='Rating')
rating_matrix.index = ['u' + str(item) for item in rating_matrix.index.tolist()]

# Normalize rating matrix
rating_matrix_norm = rating_matrix.sub(rating_matrix.mean(axis=1, skipna=True), axis=0)

# Load or compute similarity matrix (S)
# For large datasets, it's recommended to precompute s_matrix.csv offline
S_FILE = os.path.join(DATA_DIR, 's_matrix.csv')
if os.path.exists(S_FILE):
    s_matrix_new = pd.read_csv(S_FILE, index_col=0)
    itemID_lst = rating_matrix.columns
    s_matrix_new.columns =  ['m' + str(item) for item in itemID_lst.tolist()]
    s_matrix_new.index = ['m' + str(item) for item in itemID_lst.tolist()]
else:
    # Computing S on the fly is time-consuming. 
    # Ideally, do this offline and load from CSV.
    raise FileNotFoundError("s_matrix.csv not found. Precompute it before running the server.")

# Keep only top 30 values per row
def keep_top_n(df, n):
    row_thresholds = np.sort(df.values, axis=1)[:, -n]
    return df.where(df.ge(row_thresholds, axis=0))

s_matrix_top30 = keep_top_n(s_matrix_new.fillna(0), 30)

def myIBCF(newuser, s_matrix_top30):
    # newuser: array-like, same order as s_matrix_top30 columns
    sigma_s = (s_matrix_top30 * np.where(np.isnan(newuser), 0, 1)).sum(axis=1)
    sigma_sw = (s_matrix_top30.mul(newuser, axis=1)).sum(axis=1)
    pred = sigma_sw / sigma_s
    # remove items already rated by the user
    pred[~np.isnan(newuser)] = np.nan
    pred = pred[~np.isnan(pred)]
    pred_top10 = pd.DataFrame(pred.sort_values(ascending=False).head(10))
    pred_top10.columns = ['predicted_rating']
    if len(pred_top10) < 10:
        # fallback to popularity-based
        non_nan_indices = pred[~np.isnan(newuser)].index.tolist() if not np.isnan(newuser).all() else []
        new_movie_popularity = movie_popularity[~movie_popularity.index.isin(non_nan_indices)]
        needed = 10 - len(pred_top10)
        fallback = new_movie_popularity.sort_values(by='popularity_score', ascending=False).head(needed)
        fallback_df = pd.DataFrame(index=fallback.index, columns=['predicted_rating'], data=np.nan)
        pred_top10 = pd.concat([pred_top10, fallback_df], axis=0)
    return pred_top10

@app.post("/api/recommend")
def recommend(user_ratings: dict):
    """
    user_ratings: 
    {
      "ratings": {
          "m1613": 5,
          "m1755": 4
      }
    }
    
    Returns top 10 recommended movies.
    """
    itemID_lst = ['m' + str(item) for item in rating_matrix.columns.tolist()]
    newuser = pd.DataFrame([[np.nan]*len(itemID_lst)], columns=itemID_lst)
    # Fill in user ratings
    if "ratings" not in user_ratings:
        raise HTTPException(status_code=400, detail="Please provide 'ratings' dictionary in the request body.")
    for k,v in user_ratings["ratings"].items():
        if k in newuser.columns:
            newuser.loc[0, k] = v
    
    newuser_array = newuser.loc[0].to_numpy()
    pred_top10 = myIBCF(newuser_array, s_matrix_top30)
    pred_top10 = pred_top10.join(movie_popularity[['Title','PosterURL']], how='left')
    # Return results
    return pred_top10.reset_index().rename(columns={"index":"MovieID"}).to_dict(orient='records')

@app.get("/api/top10")
async def get_top10_movies():
    result = movie_popularity.nlargest(10, 'popularity_score')[['MovieID', 'Title', 'PosterURL', 'popularity_score']].copy()
    response_data = result.to_dict(orient='records')
    return response_data

@app.get("/api/movies")
async def get_movies(page: int = 1, per_page: int = 24):
    start_idx = (page - 1) * per_page
    end_idx = start_idx + per_page
    
    # Get all movies sorted by popularity
    all_movies = movie_popularity.sort_values(by='popularity_score', ascending=False)
    paginated_movies = all_movies.iloc[start_idx:end_idx]
    
    # Get total pages
    total_movies = len(all_movies)
    total_pages = (total_movies + per_page - 1) // per_page
    
    result = paginated_movies[['MovieID', 'Title', 'PosterURL', 'popularity_score']].copy()
    response = {
        "movies": result.to_dict(orient='records'),
        "page": page,
        "total_pages": total_pages,
        "total_movies": total_movies
    }
    return response

# Mount the assets directory for serving static files
app.mount("/assets", StaticFiles(directory=ASSETS_DIR), name="assets")

# Mount the frontend build - use relative path that works in both Docker and local dev
FRONTEND_DIR = os.path.join(os.path.dirname(BASE_DIR), "ui/resources/public")
app.mount("/", StaticFiles(directory=FRONTEND_DIR, html=True), name="frontend")

if __name__ == '__main__':
    uvicorn.run(app, host="0.0.0.0", port=8008)
