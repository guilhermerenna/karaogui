# Build stage
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

ARG APP_VERSION=dev
ARG GIT_SHA=local
ARG BUILD_TIME=unknown
RUN node -e 'const fs = require("fs"); \
  const info = { version: process.env.APP_VERSION || "dev", commit: process.env.GIT_SHA || "local", builtAt: process.env.BUILD_TIME || "unknown" }; \
  const content = [ \
    "export const APP_VERSION = " + JSON.stringify(info, null, 2) + ";", \
    "", \
    "export function formatAppVersion(): string {", \
    "  const { version, commit } = APP_VERSION;", \
    "  if (!commit || commit === \"local\" || version.includes(commit)) {", \
    "    return version;", \
    "  }", \
    "  return `${version} · ${commit}`;", \
    "}", \
    "", \
  ].join("\n"); \
  fs.writeFileSync("projects/ui/src/lib/app-version.ts", content);'

# Build both apps. The TV app is served under /tv/, so its base-href must match.
RUN npm run build -- --project phone
RUN npm run build -- --project tv --base-href /tv/

# Runtime stage
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/phone/browser /usr/share/nginx/html
COPY --from=build /app/dist/tv/browser /usr/share/nginx/html/tv
EXPOSE 80
