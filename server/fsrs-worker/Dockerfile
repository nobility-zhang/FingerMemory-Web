FROM node:24.19.0-bookworm-slim AS build

WORKDIR /build/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/src ./src
COPY web/worker ./worker
COPY web/worker.vite.config.ts ./
RUN npm run worker:build
RUN node --check dist-worker/server.mjs

FROM node:24.19.0-bookworm-slim

RUN useradd --create-home --uid 10002 fsrs-worker

WORKDIR /app
COPY --from=build /build/web/dist-worker/server.mjs /app/server.mjs

USER fsrs-worker
EXPOSE 8090
ENTRYPOINT ["node", "/app/server.mjs"]
