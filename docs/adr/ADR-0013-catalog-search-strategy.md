# ADR-0013: Catalog Search Strategy — MySQL Full-Text Search for Phase 1, Elasticsearch Deferred

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Product Catalog  

---

## Context

The platform requires product search across title, description, and attributes. Search
is a critical customer-facing feature with the following characteristics:

- **Query types:** keyword search, category filter, price range filter, sort (price, rating, newest)
- **Latency SLO:** < 200 ms at P99 for a search returning 20 results
- **Scale (Phase 1):** 10K–50K products, < 1K concurrent search requests
- **Relevance:** basic keyword match sufficient for Phase 1; faceted search and ML
  ranking are Phase 2+ requirements

Two options evaluated: MySQL full-text search (same DB as catalog data) and Elasticsearch
(dedicated search cluster).

---

## Decision

**MySQL full-text search for Phase 1.**

A FULLTEXT index on `products(title, description)` combined with MySQL's boolean mode
(`MATCH ... AGAINST ... IN BOOLEAN MODE`) handles keyword search. Category, price, and
sort filters are standard SQL `WHERE` / `ORDER BY` clauses.

```sql
ALTER TABLE products ADD FULLTEXT INDEX ft_products_search (title, description);

-- Example query
SELECT p.*, pv.base_price
FROM products p
JOIN product_variants pv ON pv.product_id = p.id
WHERE MATCH(p.title, p.description) AGAINST (? IN BOOLEAN MODE)
  AND p.category_id = ?
  AND pv.base_price BETWEEN ? AND ?
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20 OFFSET ?;
```

Search results are cached in Redis: key `search:{hashOf(query+filters+page)}` with
5-minute TTL. Cache invalidation on `ProductCreated` and `PriceUpdated` events clears
affected keys (pattern delete: `search:*`).

---

## Consequences

### Positive

- **No additional infrastructure.** MySQL full-text is available in the existing MySQL
  instance. No Elasticsearch cluster, no index synchronisation pipeline, no additional
  operational dependency.
- **Consistent data.** Search results come from the same MySQL instance as the catalog
  data — no eventual consistency lag between product updates and search index updates.
- **Sufficient for Phase 1 scale.** At 50K products and < 1K concurrent searches,
  MySQL full-text with the Redis cache comfortably meets the 200 ms P99 SLO.
- **Simple failure mode.** If search is slow, only the search feature degrades; the
  catalog, order, and cart flows are unaffected (separate queries).

### Negative

- **Limited relevance ranking.** MySQL full-text relevance scoring is basic (term
  frequency). Typo tolerance, synonym expansion, and ML-based personalisation are not
  supported without Elasticsearch.
- **Full-text index adds write overhead.** Every product INSERT/UPDATE rebuilds the
  full-text index. At < 100 product updates/day, this is negligible.
- **Pattern-based cache invalidation is coarse.** `DEL search:*` invalidates all cached
  search pages on any product update. At < 100 updates/day, the cache miss rate is
  acceptable. At higher update rates, a more targeted invalidation strategy is needed.
- **Phase 2 migration required.** When search quality requirements outgrow MySQL, an
  Elasticsearch migration is needed. The Catalog Service already publishes `ProductCreated`
  and `PriceUpdated` events — an Elasticsearch sync consumer can subscribe without
  changing the Catalog Service code.

---

## Migration Path to Elasticsearch (Phase 2 / future)

When Elasticsearch becomes necessary (triggers: typo tolerance required, product count
> 500K, or P99 latency > 200 ms despite Redis caching):

1. Deploy Elasticsearch cluster (AWS OpenSearch in Phase 2)
2. Add a Kafka consumer that subscribes to `catalog.product.*` events and writes to ES index
3. Dual-run: MySQL search and ES search in parallel; compare results for correctness
4. Cut over to ES search; keep MySQL full-text index as fallback
5. Remove MySQL full-text index after ES stability is confirmed

The event-driven architecture (ADR-0004) enables this migration without any producer-
side changes.

---

## Alternatives Rejected

### Elasticsearch from Day 1

Deploy an Elasticsearch cluster alongside MySQL. Product data is indexed into ES
asynchronously via Kafka consumer; search queries go to ES.

Pros: Industry-standard search relevance; typo tolerance; faceted search; scales to
millions of products.

Rejected for Phase 1 because:
- Adds significant operational overhead: ES cluster sizing, index mapping management,
  sync pipeline monitoring, split-brain handling.
- At 50K products and basic keyword search, MySQL full-text is sufficient with no
  additional infra.
- Premature optimisation: the search quality requirements that justify ES are not
  validated by user feedback yet.

Scheduled for Phase 2 evaluation when product count and search complexity grow.

### PostgreSQL full-text search

If MySQL were replaced with PostgreSQL, `tsvector` + `GIN index` provides richer full-
text search than MySQL FULLTEXT (better ranking, stemming, custom dictionaries).

Not applicable — MySQL 8 is the chosen RDBMS (ADR-0008) and is not open for revision
in Phase 1. PostgreSQL full-text can be evaluated if the RDBMS choice is reconsidered.

### Algolia (SaaS search)

Managed search-as-a-service. Excellent developer experience; instant typo tolerance
and faceting; no cluster to operate.

Rejected:
- External SaaS dependency adds cost and a PII/product-data sharing concern (product
  catalogue pushed to a third-party service).
- Vendor lock-in for a core customer-facing feature.
- Not appropriate for a learning/portfolio project where operational ownership of the
  full stack is the goal.
