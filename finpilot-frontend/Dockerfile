# Build stage: Vite reads VITE_API_URL=/api from .env.production
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Runtime stage: nginx serves the static build and proxies /api to the backend
FROM nginx:1.27-alpine

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

ENV BACKEND_URL=http://backend:8080
EXPOSE 80
