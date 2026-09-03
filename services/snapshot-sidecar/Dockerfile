# Playwright ships a Node image with browsers + system deps pre-installed.
# Pin to a known good tag matching the playwright npm version above.
FROM mcr.microsoft.com/playwright:v1.47.0-jammy

WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev --ignore-scripts \
 && npx playwright install chromium

COPY server.js ./

ENV PORT=3001
EXPOSE 3001

# Run as the non-root user shipped in the Playwright image so the Chromium
# sandbox can stay enabled (audit H4 — no --no-sandbox needed).
USER pwuser

CMD ["node", "server.js"]
