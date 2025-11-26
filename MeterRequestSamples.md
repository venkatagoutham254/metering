# MeterRequest Sample JSON Payloads

`MeterRequest` needs only the time window and (optionally) the identifiers that scope the query.  Usage quantity is auto-derived from `ingestion_event`, and pricing rules come from the rate-plan service, so there are far fewer fields than `EstimateRequest`.

---

## 1. Whole-tenant usage (no filters)
```json
{
  "from": "2025-10-01T00:00:00Z",
  "to":   "2025-11-01T00:00:00Z"
}
```

---

## 2. Single subscription
```json
{
  "from": "2025-10-15T00:00:00Z",
  "to":   "2025-10-31T23:59:59Z",
  "subscriptionId": 9101
}
```

---

## 3. Specific product
```json
{
  "from": "2025-09-01T00:00:00Z",
  "to":   "2025-10-01T00:00:00Z",
  "productId": 42
}
```

---

## 4. Particular rate-plan (most common)
```json
{
  "from": "2025-10-01T00:00:00Z",
  "to":   "2025-11-01T00:00:00Z",
  "ratePlanId": 777
}
```

---

## 5. Usage for one metric inside a rate-plan
```json
{
  "from": "2025-10-20T00:00:00Z",
  "to":   "2025-10-27T00:00:00Z",
  "ratePlanId": 777,
  "billableMetricId": 5555
}
```

---

## 6. Combined filters (subscription + rate-plan)
```json
{
  "from": "2025-10-01T00:00:00Z",
  "to":   "2025-10-31T23:59:59Z",
  "subscriptionId": 9101,
  "ratePlanId": 777
}
```

Feel free to mix and match filters; any omitted field is ignored in the SQL `WHERE` clause.
