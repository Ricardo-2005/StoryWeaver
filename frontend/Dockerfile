FROM node:24-alpine AS build

ARG NPM_REGISTRY=https://registry.npmjs.org
ENV COREPACK_NPM_REGISTRY=$NPM_REGISTRY

ENV PNPM_HOME=/pnpm
ENV PATH=$PNPM_HOME:$PATH

WORKDIR /app

RUN corepack enable
RUN --mount=type=cache,id=storyweaver-corepack,target=/root/.cache/node/corepack \
    attempts=0; \
    until corepack prepare pnpm@10.30.0 --activate; do \
      attempts=$((attempts + 1)); \
      [ "$attempts" -ge 5 ] && exit 1; \
      sleep 3; \
    done

COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,id=storyweaver-pnpm,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store && \
    pnpm config set registry "$NPM_REGISTRY" && \
    pnpm config set fetch-retries 5 && \
    pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.29-alpine AS runtime

ENV BACKEND_UPSTREAM=http://host.docker.internal:8080

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
