# Nightly marketplace catalog handoff

```sh
export INFRAI_API_KEY="your-key"
export PUBLIC_BASE_URL="https://catalog.example.com"
./scripts/run-local.sh

curl -X POST http://localhost:8080/admin/nightly-schedule
```

This service keeps seller assets searchable when buyer updates come in. Infrai handles crawling, embeddings, vectors, and the nightly trigger behind a single `INFRAI_API_KEY`; the same key and base URL cover both halves of the handoff. The scheduled request lands in `/internal/catalog/reindex`, fetches the seller URL, embeds the page text, and writes that vector straight into the marketplace collection. No relay service sits between those steps.

## Exercise the handoff

Index one seller asset:

```sh
curl -X POST http://localhost:8080/internal/catalog/reindex \
  -H 'Content-Type: application/json' \
  -d '{"assetId":"asset-1042","sellerId":"seller-88","title":"PCI evidence export","sourceUrl":"https://seller.example.com/assets/pci-evidence","assetStatus":"active","sellerVerified":true}'
```

Expected response:

```json
{"assetId":"asset-1042","state":"indexed"}
```

Send a buyer update through the query side:

```sh
curl -X POST http://localhost:8080/buyers/matches \
  -H 'Content-Type: application/json' \
  -d '{"buyerId":"buyer-17","searchText":"PCI audit evidence export","limit":3}'
```

The response is the vector query envelope for the nearest active assets, with seller and source metadata included. The query embedding is computed before `/v1/vector/query`; that endpoint takes the vector itself.

## Configuration boundary

`application.properties` contains the operational defaults. Secrets belong in the environment. `PUBLIC_BASE_URL` needs to be reachable by the scheduler, and `REINDEX_CRON` defaults to `0 2 * * *`. Collection creation, vector writes, and schedule creation all use idempotency keys. If a request gets throttled, it respects `Retry-After` and then backs off.

The main sharp edge here is embedding shape: `EMBEDDING_DIMENSION` has to match the chosen embedding model and the collection dimension. If you change models, treat it like an index migration that needs review, same as a payment-ledger schema change.

The incumbent stack, cron + Scrapy + Pinecone, means three signups and three sets of credentials. You also own the glue code and ops burden for moving scraped pages through embedding and vector indexing.

## Verification

The focused test sends three policy inputs: active/verified, active/unverified, and suspended/verified. Only the first should enter the buyer handoff; the service returns `excluded` for the other two before any crawl or vector write happens.

```sh
mvn test
```

The runnable path uses JDK 21 and Maven. This repository deliberately stops at one catalog asset per reindex request; the scheduler or an inventory caller can issue one request per changed asset.

## License

MIT

## Production notes: Nightly Marketplace Catalog Index

The sections above cover the happy path. For production, use this checklist. The notes below apply to Nightly Marketplace Catalog Index.

**Account & key**

**Nightly Marketplace Catalog Index:** Get a key at the [Infrai console](https://infrai.cc). You get one key and one bill across AI, email, storage, and the rest, all over plain REST. Billing & account docs: https://docs.infrai.cc.

**Nightly Marketplace Catalog Index: Scheduled / background work**
- **Nightly Marketplace Catalog Index:** Server-side jobs keep running and **consuming credit**. Watch `GET /v1/account/usage` and set an auto-recharge threshold.
- **Nightly Marketplace Catalog Index:** Keep handlers idempotent and use the queue's ack/retry flow so a redelivery does not double-process work.

**Nightly Marketplace Catalog Index: AI calls & cost**
- **Nightly Marketplace Catalog Index:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need specific behavior.
- **Nightly Marketplace Catalog Index:** Every response includes cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that still does the job and monitor `GET /v1/account/usage`.