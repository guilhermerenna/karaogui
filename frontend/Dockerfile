# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Build both apps. The TV app is served under /tv/, so its base-href must match.
RUN npm run build -- --project phone
RUN npm run build -- --project tv --base-href /tv/

# Runtime stage
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/phone/browser /usr/share/nginx/html
COPY --from=build /app/dist/tv/browser /usr/share/nginx/html/tv
EXPOSE 80