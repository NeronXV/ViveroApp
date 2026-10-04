FROM node:24-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force
COPY --chown=node:node src ./src
COPY --chown=node:node test ./test
COPY --chown=node:node scripts ./scripts
RUN mkdir -p /data/catalog-images && chown node:node /data/catalog-images
USER node
EXPOSE 3001
CMD ["node", "src/server.js"]
