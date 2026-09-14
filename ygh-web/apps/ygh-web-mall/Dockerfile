FROM node:24.4.1-alpine3.22 AS build
RUN corepack enable && corepack prepare pnpm@10.13.1 --activate
WORKDIR /workspace
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml tsconfig.base.json ./
COPY packages/ygh-web-shared/package.json packages/ygh-web-shared/package.json
COPY apps/ygh-web-mall/package.json apps/ygh-web-mall/package.json
COPY apps/ygh-web-admin/package.json apps/ygh-web-admin/package.json
RUN pnpm install --frozen-lockfile
COPY packages/ygh-web-shared packages/ygh-web-shared
COPY apps/ygh-web-mall apps/ygh-web-mall
RUN pnpm --filter @ygh/web-mall build

FROM nginxinc/nginx-unprivileged:1.29.0-alpine3.22
ARG APP_VERSION=dev
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
LABEL org.opencontainers.image.version="${APP_VERSION}" org.opencontainers.image.revision="${GIT_SHA}" org.opencontainers.image.created="${BUILD_TIME}"
COPY apps/ygh-web-mall/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/ygh-web-mall/dist /usr/share/nginx/html
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
