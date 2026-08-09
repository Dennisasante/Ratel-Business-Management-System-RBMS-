# Deps stage — cached separately so `npm install` only reruns when package
# files actually change, not on every source edit.
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Public env vars must be present at build time — Next.js inlines them into
# the client bundle then, not at container start. Passed in via --build-arg
# from docker-compose's `build.args`.
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
ARG NEXT_PUBLIC_PAYSTACK_PUBLIC_KEY
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID
ENV NEXT_PUBLIC_PAYSTACK_PUBLIC_KEY=$NEXT_PUBLIC_PAYSTACK_PUBLIC_KEY
RUN npm run build

# Runtime stage — just the standalone server output, no node_modules, no
# source, no build tooling.
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production

RUN addgroup -S rbms && adduser -S rbms -G rbms
COPY --from=build /app/public ./public
COPY --from=build --chown=rbms:rbms /app/.next/standalone ./
COPY --from=build --chown=rbms:rbms /app/.next/static ./.next/static
USER rbms

EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
