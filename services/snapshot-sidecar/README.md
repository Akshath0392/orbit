# orbit-snapshot-sidecar

Playwright-backed render service for Orbit snapshots.

## Endpoints
- `GET /healthz` → `{ ok, active }`
- `POST /render` → `{ png?: base64, pdf?: base64, renderMs }`

Body:
```json
{
  "targetUrl": "http://frontend/radar?snapshot=1&lens=PJM&portfolio=7&project=11",
  "jwt": "...",
  "viewport": { "w": 1440, "h": 900 },
  "formats": ["png", "pdf"],
  "waitForSelector": "[data-snapshot-ready=\"true\"]",
  "timeoutMs": 20000
}
```

## Run locally
```bash
npm install
npm start            # PORT=3001
```

## Run via Docker
```bash
docker compose up snapshot-sidecar
```

## Contract
The frontend MUST set `data-snapshot-ready="true"` on the outermost div once
the target page has finished its async data loads. The sidecar refuses to
capture until that selector appears (or `timeoutMs` elapses).
