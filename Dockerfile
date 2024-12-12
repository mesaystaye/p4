# Use multi-stage build
FROM node:16 AS frontend-builder

# Install Java and Clojure
RUN apt-get update && apt-get install -y \
    curl \
    default-jdk \
    rlwrap \
    && rm -rf /var/lib/apt/lists/*

# Install Clojure tools
RUN curl -O https://download.clojure.org/install/linux-install-1.11.1.1273.sh && \
    chmod +x linux-install-1.11.1.1273.sh && \
    ./linux-install-1.11.1.1273.sh

# Install shadow-cljs globally
RUN npm install -g shadow-cljs

# Set working directory
WORKDIR /app

# Copy dependency files first
COPY ui/package.json ui/package-lock.json ui/shadow-cljs.edn ./ui/

# Install dependencies
WORKDIR /app/ui
RUN npm install
# Cache Clojure dependencies
RUN npx shadow-cljs classpath

# Copy the rest of the application
WORKDIR /app
COPY . .

# Build frontend
WORKDIR /app/ui
RUN npx shadow-cljs release app

# Final stage
FROM python:3.9-slim

# Install required system packages
RUN apt-get update && apt-get install -y \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy and install Python requirements first
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Create directory structure
RUN mkdir -p /app/ui/resources/public \
    && mkdir -p /app/assets/images \
    && mkdir -p /app/app/data

# Copy application code and data after installing dependencies
COPY app/ ./app/
COPY assets/ ./assets/
COPY app/data/*.dat app/data/*.csv ./app/data/

# Copy frontend build from previous stage
COPY --from=frontend-builder /app/ui/resources/public/ /app/ui/resources/public/

# Expose port
EXPOSE 8008

# Set environment variables
ENV PORT=8008
ENV HOST=0.0.0.0
ENV WORKERS=1

# Command to run the application with gunicorn - single worker to reduce memory usage
CMD ["gunicorn", "app.main:app", "--workers", "1", "--worker-class", "uvicorn.workers.UvicornWorker", "--bind", "0.0.0.0:8008", "--access-logfile", "-"]
