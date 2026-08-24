# Personal Finance REST API

Spring Boot REST API for the Personal Finance application.

## Local development

The API runs at `http://localhost:8080` by default. All public application endpoints are versioned under:

```text
/api/v1
```

Run unit tests with `./mvnw test`. Run unit and integration tests with `./mvnw verify`.

### Development sample data

Activate the `dev` Spring profile to start the in-memory H2 database with representative sample data:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The profile adds `classpath:dev/db/migration` to Flyway's normal migration locations. Its repeatable seed migration loads deterministic accounts, opening and manual balance history, active and archived categories, a category hierarchy, USD and EUR activity, active and recoverably deleted transactions, an ordered split transaction, and same-currency and cross-currency transfers. The database is still discarded when the application process stops, so every new run starts from the same useful scenario.

Production migrations remain in `db/migration`; sample data must stay under `dev/db/migration`. When a feature adds required tables, relationships, or useful frontend states, update the development seed and `DevelopmentDataIT` together. The fixed seed UUIDs should remain stable unless a relationship is intentionally replaced.

The REST API permits browser requests from `http://localhost:4200` by default for local Angular development. Override the exact, comma-separated origins without changing code:

```bash
APP_CORS_ALLOWED_ORIGINS=https://app.example.com ./mvnw spring-boot:run
```

Do not use wildcard origins in a deployed environment.

## Account contract

### Create an account

`POST /api/v1/accounts`

```json
{
  "name": "Everyday Checking",
  "type": "checking",
  "currency": "USD",
  "openingDate": "2026-08-20",
  "openingBalance": 1250.75
}
```

`openingBalance` is optional and defaults to `0.00`. Supported account types are `checking`, `savings`, `cash`, `credit_card`, and `loan`.

A successful request returns `201 Created`, a `Location` header, and the created account:

```json
{
  "id": "0dfae49e-6765-4f9f-b485-53d17338a106",
  "ownerId": "00000000-0000-0000-0000-000000000001",
  "name": "Everyday Checking",
  "type": "checking",
  "currency": "USD",
  "openingDate": "2026-08-20",
  "openingBalance": 1250.75,
  "currentBalance": 1250.75,
  "status": "active",
  "archivedAt": null,
  "createdAt": "2026-08-22T18:30:00Z",
  "updatedAt": "2026-08-22T18:30:00Z"
}
```

### Retrieve accounts

- `GET /api/v1/accounts` returns the current user's active accounts as a JSON array, ordered by creation time.
- `GET /api/v1/accounts?status=archived` returns archived accounts.
- `GET /api/v1/accounts?status=all` returns active and archived accounts.
- `GET /api/v1/accounts/{accountId}` returns one account owned by the current user.

`currentBalance` initially equals `openingBalance` and changes by the signed impact of active transactions.

### Preserve balance history

Creating an account records an immutable opening snapshot at midnight UTC on its opening date. Record later observed balances with:

`POST /api/v1/accounts/{accountId}/balance-snapshots`

```json
{
  "balance": 1800.00,
  "effectiveAt": "2026-08-21T12:00:00Z"
}
```

- `GET /api/v1/accounts/{accountId}/balance-snapshots` returns the complete history in effective-time order.
- `GET /api/v1/accounts/{accountId}/balance-snapshots/{snapshotId}` returns one snapshot.
- `GET /api/v1/accounts/{accountId}/balance?asOf={instant}` returns the latest balance effective at or before the requested UTC instant.
- Omitting `asOf` returns the latest currently effective balance.

Snapshots are append-only. A backdated snapshot is retained without replacing a later current balance. Duplicate effective timestamps and snapshots for archived accounts return `409 Conflict`. A request before the first snapshot returns `404 Not Found`.

### Maintain an account

`PATCH /api/v1/accounts/{accountId}` updates one or more supplied fields:

```json
{
  "name": "Primary Checking",
  "type": "checking"
}
```

Name and type remain editable after account activity exists. Currency, opening date, and opening balance may only change before financial activity references the account; conflicting changes return `409 Conflict`.

Account lifecycle operations are explicit and idempotent:

- `POST /api/v1/accounts/{accountId}/archive` soft-archives an account.
- `POST /api/v1/accounts/{accountId}/restore` restores an archived account.

There is intentionally no permanent-delete endpoint. Archiving preserves the account identifier and its current or future financial history.

The initial application uses a seeded default user. The ownership boundary remains in the API and persistence model so authentication can replace that user later.

## Category contract

Create a transaction category with `POST /api/v1/categories`:

```json
{
  "name": "Groceries",
  "applicability": "expense",
  "parentId": null
}
```

Supported applicability values are `income`, `expense`, and `both`. A successful request returns `201 Created`, a `Location` header, and the category with its owner, lifecycle status, and timestamps.

- `GET /api/v1/categories` returns active categories alphabetically.
- `GET /api/v1/categories?status=archived` returns archived categories.
- `GET /api/v1/categories?status=all` returns both lifecycle states.
- `GET /api/v1/categories/{categoryId}` retrieves one owned category.
- `PATCH /api/v1/categories/{categoryId}` changes its name, applicability, or both.
- `PATCH /api/v1/categories/{categoryId}/parent` assigns a parent or clears it with a null `parentId`.
- `POST /api/v1/categories/{categoryId}/archive` archives it idempotently.
- `POST /api/v1/categories/{categoryId}/restore` restores it idempotently.

Active category names are unique per owner after trimming, collapsing repeated whitespace, and ignoring case. An archived category releases its active name, but restoring it returns `409 Conflict` if another active category now uses that name. There is intentionally no permanent-delete endpoint, so transaction history can continue to reference archived categories safely.

Categories may form an owner-scoped hierarchy. Every category response includes nullable `parentId`, and create requests may supply one. Self-parenting and indirect cycles return `409 Conflict`. Active categories can only use active parents; archiving a parent with active children or restoring a child beneath an archived parent also returns `409`. Archived relationships remain stored so future reports can aggregate historical activity under a parent consistently.

## Transaction contract

Record income or spending with `POST /api/v1/transactions`:

```json
{
  "accountId": "0dfae49e-6765-4f9f-b485-53d17338a106",
  "amount": 42.50,
  "transactionDate": "2026-08-23",
  "description": "Groceries",
  "type": "expense",
  "categoryId": null,
  "merchantPayee": "Neighborhood Market",
  "notes": null,
  "externalReference": null
}
```

`amount` is always a positive magnitude. Supported types are `income` and `expense`; the response's `balanceImpact` is positive for income and negative for expense. Creating, replacing, deleting, restoring, or moving an active transaction updates the affected account balances by the net change.

- `GET /api/v1/transactions` returns the first page of active owned transactions.
- `GET /api/v1/transactions?status=deleted` searches soft-deleted transactions.
- `GET /api/v1/transactions?status=all` searches both lifecycle states.
- `GET /api/v1/transactions/{transactionId}` retrieves one owned transaction.
- `PUT /api/v1/transactions/{transactionId}` fully replaces its editable fields; nullable optional fields can therefore be cleared.
- `DELETE /api/v1/transactions/{transactionId}` soft-deletes it idempotently and reverses its balance impact.
- `POST /api/v1/transactions/{transactionId}/restore` restores it idempotently and reapplies its balance impact.

Transactions require an active owned account, cannot predate that account's opening date or be future-dated, and may reference an active owned category compatible with their type. A retained archived category remains available to its existing transaction, preserving history. Even deleted transactions count as account activity, so currency and opening terms remain protected.

An income or expense can use either the optional parent `categoryId` above or an ordered `splits` allocation, never both:

```json
{
  "accountId": "0dfae49e-6765-4f9f-b485-53d17338a106",
  "amount": 100.00,
  "transactionDate": "2026-08-23",
  "description": "Household purchase",
  "type": "expense",
  "categoryId": null,
  "splits": [
    { "categoryId": "50ea8ada-6436-4624-bbb9-a33c9a3631e2", "amount": 75.25 },
    { "categoryId": "e9d0ccb5-6362-4bf7-9d8f-fcc9e1bf2f4a", "amount": 24.75 }
  ]
}
```

A split requires at least two positive rows with distinct owned, type-compatible categories. All monetary values currently use the application's fixed two-decimal currency scale, and split amounts must equal the normalized transaction amount exactly; the server never creates a remainder row. Transfers cannot be split.

Create requests omit split IDs. Responses expose each row's stable `id`, zero-based `position`, `categoryId`, and `amount`. Full `PUT` requests retain IDs for unchanged rows, omit IDs for new rows, and delete rows omitted from the replacement allocation. Existing IDs cannot be moved between transactions. New or reassigned rows require active categories; an unchanged row may retain an archived category. Supplying no splits returns the transaction to the single-category contract. Soft deletion and restoration retain the allocation.

Allocation validation uses indexed field keys such as `splits[0].id`, `splits[0].categoryId`, and `splits[0].amount`; row-count, duplicate-category, and total-mismatch failures use `splits`.

Transaction search supports these combinable query parameters:

- `accountId`, inclusive `from` and `to` dates, `categoryId`, and `type`.
- Inclusive `minAmount` and `maxAmount` positive-magnitude boundaries.
- Case-insensitive `text` matching description, merchant/payee, notes, or external reference.
- Zero-based `page` (default `0`) and `size` from 1 through 100 (default `25`).
- `sort=date|amount` and `direction=asc|desc` (defaults `date,desc`).

The response is a stable page envelope:

```json
{
  "items": [],
  "page": 0,
  "size": 25,
  "totalElements": 0,
  "totalPages": 0,
  "sortBy": "date",
  "sortDirection": "desc"
}
```

Date and amount boundaries are inclusive. Filters combine with AND. Date sorting uses creation time and ID tie-breakers; amount sorting uses transaction date and ID tie-breakers. Invalid ranges, page sizes, types, or sort values use the shared field-error response.

## Transfer contract

Record an atomic transfer with `POST /api/v1/transfers`:

```json
{
  "sourceAccountId": "0dfae49e-6765-4f9f-b485-53d17338a106",
  "destinationAccountId": "70dbce4a-1e87-43cf-9fac-4ffeb0185690",
  "sourceAmount": 100.00,
  "destinationAmount": 92.00,
  "transactionDate": "2026-08-23",
  "description": "Travel cash exchange",
  "notes": null,
  "externalReference": null
}
```

The source and destination accounts must be distinct, active, owned accounts. Same-currency transfers require equal amounts; cross-currency transfers require explicit source and destination amounts and do not infer an exchange rate. Each transfer is stored as linked `transfer_out` and `transfer_in` ledger entries sharing a `transferId`. Those legs appear in the transaction ledger but must be changed only through the aggregate transfer endpoints.

- `GET /api/v1/transfers` lists active transfers; `status=deleted|all` selects other lifecycle views.
- `GET /api/v1/transfers/{transferId}` retrieves one owned transfer.
- `PUT /api/v1/transfers/{transferId}` atomically replaces both linked legs.
- `DELETE /api/v1/transfers/{transferId}` soft-deletes both legs and reverses both balance impacts.
- `POST /api/v1/transfers/{transferId}/restore` restores both legs and reapplies both impacts.

Retrieve active transaction totals grouped by account currency with:

`GET /api/v1/transactions/summary?from=2026-08-01&to=2026-08-31`

```json
[
  {
    "currency": "USD",
    "income": 2416.00,
    "spending": 24.36,
    "netImpact": 2391.64,
    "transactionCount": 2
  }
]
```

`income` and `spending` are positive fixed-decimal totals, and `netImpact` is income minus spending. Date boundaries are inclusive. Either boundary may be omitted for an open-ended range; omitting both returns an all-time summary. Optional `accountId`, `categoryId`, and `type` filters match the paged ledger semantics. A category filter matches either an unsplit parent category or an individual split row; split amounts are aggregated without also counting the parent amount. `transactionCount` remains the distinct number of matching transactions. Only active income and expense transactions owned by the current user are included; transfer legs are excluded from every total and from `transactionCount`. Results are ordered by currency, and a range with no qualifying activity returns an empty array. A `from` date after `to` returns `400 Validation failed` with a `dateRange` field error.

Balance snapshots and transaction-driven balance changes currently share the account's `currentBalance` projection. A newly effective snapshot sets the observed balance; subsequent transaction changes apply deltas. Full automatic reconciliation between the ledger and observed snapshots is intentionally deferred to the dedicated reconciliation story.

## Error contract

All documented API errors use this shape:

```json
{
  "timestamp": "2026-08-22T18:30:00Z",
  "status": 400,
  "error": "Validation failed",
  "fieldErrors": {
    "currency": "must be a three-letter currency code"
  }
}
```

- Invalid fields return `400` with `error` set to `Validation failed` and entries in `fieldErrors`.
- Malformed JSON or unsupported enum values return `400` with `error` set to `Request body is malformed`.
- A valid but unknown account ID returns `404` with `error` set to `Financial account not found: {accountId}` and an empty `fieldErrors` object.
- Attempts to change currency or opening terms after financial activity exists return `409` with an empty `fieldErrors` object.
- Duplicate active category names, including restore collisions, return `409`.
- Duplicate balance timestamps and attempts to record balances on archived accounts return `409`.
- Archived accounts, incompatible or archived category assignments, and invalid transaction dates return `409`.
- An as-of request before the first recorded balance returns `404`.

Clients should use `status` and `fieldErrors` for behavior rather than parsing the human-readable `error` text.
