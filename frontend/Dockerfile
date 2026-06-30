# syntax=docker/dockerfile:1
#
# Frontend image for the isolated E2E stack (SPEC-0028 / DL-0101): builds the Angular bundle and serves
# it with Nginx, proxying /api to the backend so the SPA and the API share an origin (port 4201).

# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app
# .npmrc carries legacy-peer-deps=true (PrimeNG 21 declares Angular-21 peers while we run Angular 22 —
# documented in DL-0090); without it `npm ci` aborts on ERESOLVE. Copy it before installing.
COPY package*.json .npmrc ./
RUN npm ci
COPY . .
RUN npm run build

# --- Serve stage ---
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
