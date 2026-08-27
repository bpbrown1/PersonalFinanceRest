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

The profile adds `classpath:dev/db/migration` to Flyway's normal migration locations. Its repeatable seed migration loads deterministic accounts, opening and manual balance history, active and archived categories, a category hierarchy, USD and EUR activity, active and recoverably deleted transactions, an ordered split transaction, same-currency and cross-currency transfers, and a monthly budget with active and archived lines. The database is still discarded when the application process stops, so every new run starts from the same useful scenario.

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

Supported types are `income` and `expense`. Income amounts and ordinary expense amounts are positive. An expense refund or credit is recorded as a negative expense amount, which reduces spending and produces a positive `balanceImpact`; zero and negative income amounts are invalid. Creating, replacing, deleting, restoring, or moving an active transaction updates the affected account balances by the net change.

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

A split requires at least two non-zero rows with distinct owned, type-compatible categories. Every split must use the transaction amount's sign, so refund allocations are all negative. All monetary values use the application's fixed two-decimal currency scale, and split amounts must equal the normalized transaction amount exactly; the server never creates a remainder row. Transfers cannot be split.

Create requests omit split IDs. Responses expose each row's stable `id`, zero-based `position`, `categoryId`, and `amount`. Full `PUT` requests retain IDs for unchanged rows, omit IDs for new rows, and delete rows omitted from the replacement allocation. Existing IDs cannot be moved between transactions. New or reassigned rows require active categories; an unchanged row may retain an archived category. Supplying no splits returns the transaction to the single-category contract. Soft deletion and restoration retain the allocation.

Allocation validation uses indexed field keys such as `splits[0].id`, `splits[0].categoryId`, and `splits[0].amount`; row-count, duplicate-category, and total-mismatch failures use `splits`.

Transaction search supports these combinable query parameters:

- `accountId`, inclusive `from` and `to` dates, `categoryId`, and `type`.
- Inclusive `minAmount` and `maxAmount` signed boundaries.
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

`income` and `spending` are fixed-decimal totals, and `netImpact` is income minus spending. Negative expense refunds reduce `spending`, which may become negative when credits exceed purchases. Date boundaries are inclusive. Either boundary may be omitted for an open-ended range; omitting both returns an all-time summary. Optional `accountId`, `categoryId`, and `type` filters match the paged ledger semantics. A category filter matches either an unsplit parent category or an individual split row; split amounts are aggregated without also counting the parent amount. `transactionCount` remains the distinct number of matching transactions. Only active income and expense transactions owned by the current user are included; transfer legs are excluded from every total and from `transactionCount`. Results are ordered by currency, and a range with no qualifying activity returns an empty array. A `from` date after `to` returns `400 Validation failed` with a `dateRange` field error.

Balance snapshots and transaction-driven balance changes currently share the account's `currentBalance` projection. A newly effective snapshot sets the observed balance; subsequent transaction changes apply deltas. Full automatic reconciliation between the ledger and observed snapshots is intentionally deferred to the dedicated reconciliation story.

## Budget contract

Create a monthly budget with `POST /api/v1/budgets`:

```json
{
  "name": "August Spending Plan",
  "currency": "USD",
  "startDate": "2026-08-01",
  "endDate": "2026-08-31",
  "lines": [
    { "categoryId": "50ea8ada-6436-4624-bbb9-a33c9a3631e2", "plannedAmount": 600.00 }
  ]
}
```

The dates must span one complete calendar month. Multiple owned budgets may cover the same month, including historical months. Lines use active, owned categories applicable to expenses or both transaction types, and a category can occur only once among all retained lines in a budget. Monetary amounts are non-negative and normalized to two decimals. `totalPlanned` is the sum of active lines only.

- `GET /api/v1/budgets?status=active|archived|all` lists owned budgets; active is the default.
- `GET /api/v1/budgets/{budgetId}` retrieves one owned budget.
- `GET /api/v1/budgets/{budgetId}/progress` calculates live planned-versus-actual progress. Optional `accountId` and `categoryId` filters restrict contributing activity.
- `GET /api/v1/budgets/{budgetId}/progress/transactions` pages through the exact transactions behind an overall, line, or unbudgeted progress total.
- `PUT /api/v1/budgets/{budgetId}` fully replaces its name, currency, and monthly period.
- `POST /api/v1/budgets/{budgetId}/archive` and `/restore` change lifecycle state idempotently.
- `POST /api/v1/budgets/{budgetId}/lines` appends a line.
- `PUT /api/v1/budgets/{budgetId}/lines/{lineId}` replaces its category and amount.
- `PUT /api/v1/budgets/{budgetId}/lines/reorder` accepts every retained line ID in the desired order.
- `POST /api/v1/budgets/{budgetId}/lines/{lineId}/archive` and `/restore` preserve line history.

Budget and line IDs remain stable, and responses include lifecycle timestamps plus the budget's optimistic-lock `version`. Archived budgets are immutable until restored; active historical budgets remain correctable. There are intentionally no permanent-delete endpoints.

An abridged progress response is shaped for both summary cards and line-item drill-down:

```json
{
  "budgetId": "70000000-0000-0000-0000-000000000001",
  "currency": "USD",
  "startDate": "2026-08-01",
  "endDate": "2026-08-31",
  "planned": 800.00,
  "budgetedActual": 153.65,
  "unbudgetedActual": 35.00,
  "totalActual": 188.65,
  "remaining": 611.35,
  "percentageUsed": 23.58,
  "lines": [
    {
      "lineId": "71000000-0000-0000-0000-000000000001",
      "categoryId": "30000000-0000-0000-0000-000000000001",
      "planned": 600.00,
      "actual": 153.65,
      "remaining": 446.35,
      "percentageUsed": 25.61,
      "drillDown": {
        "from": "2026-08-01",
        "to": "2026-08-31",
        "type": "expense",
        "status": "active",
        "transactionIds": [
          "40000000-0000-0000-0000-000000000002",
          "40000000-0000-0000-0000-000000000003",
          "40000000-0000-0000-0000-000000000011"
        ],
        "transactionsPath": "/api/v1/budgets/70000000-0000-0000-0000-000000000001/progress/transactions?scope=line&lineId=71000000-0000-0000-0000-000000000001"
      }
    }
  ],
  "unbudgeted": [
    {
      "categoryId": null,
      "actual": 35.00,
      "drillDown": {
        "transactionIds": ["40000000-0000-0000-0000-000000000012"],
        "transactionsPath": "/api/v1/budgets/70000000-0000-0000-0000-000000000001/progress/transactions?scope=unbudgeted&uncategorized=true"
      }
    }
  ]
}
```

Budget progress is recalculated from active expense transactions in the inclusive budget period and matching account currency. Positive expenses increase actual spending; negative expense refunds reduce it. Income, transfers, soft-deleted transactions, foreign owners, other currencies, and out-of-period activity are excluded. Split rows contribute their allocated amounts rather than the parent amount.

Only active budget lines receive progress. A line includes its category and descendants. When parent and child lines overlap, each allocation goes to the closest matching line (then line position), preventing double counting. Unmatched and uncategorized spending is returned in separate `unbudgeted` rows. Overall fields distinguish `budgetedActual`, `unbudgetedActual`, and `totalActual`; overall remaining is planned minus total actual. Negative remaining and percentages above 100 are preserved, while a zero planned amount returns a null percentage. Every line and unbudgeted row includes stable contributing transaction IDs plus its date, account, category, type, and lifecycle drill-down filters.

Each drill-down now also supplies a bookmarkable `transactionsPath`. The endpoint returns the established transaction page response and accepts `page`, `size`, `sort`, and `direction` just like transaction search. Supported scopes are:

- `scope=overall`, with optional `accountId` and hierarchical `categoryId` filters.
- `scope=line&lineId={lineId}`, with optional `accountId`.
- `scope=unbudgeted&categoryId={categoryId}` for one unbudgeted category.
- `scope=unbudgeted&uncategorized=true` for uncategorized spending.

`overall` is the default scope. Every scope accepts an optional owned `accountId`; dates and currency come from the budget. Line scope requires an active `lineId` and rejects category selectors. Unbudgeted scope requires exactly one of an exact category or `uncategorized=true`.

The server recomputes the selected scope from the owned budget instead of accepting transaction IDs from the client. It therefore preserves the same inclusive dates, currency, active-expense, hierarchy, split-allocation, refund, deletion, and most-specific-line rules as the progress total. A transaction appears once even when several matching allocations belong to it. Unknown or foreign budgets, lines, accounts, and categories return the established not-found errors; incompatible scope parameters return the standard validation response.

Returned split transactions retain their full allocation. Reconcile a line or category total using only the contributing split amounts, not by summing whole parent transaction amounts. The links resolve live data, not frozen snapshots. HTTP results are paginated, but progress calculation currently materializes matching allocations and transaction IDs before the database page query.

Known limitation identified on 2026-08-27: a line link from a category-filtered progress response does not retain that category filter. Line scope recomputes the full line, optionally restricted by account, and can therefore return more transactions than contributed to the filtered line total. Overall scope does preserve its category filter. A follow-up fix and regression test are needed before category-filtered line links can be treated as exact reconciliation.

US-049 and US-056 were moved to Done on 2026-08-27. Their finalized designs document the implementation and the limitation above: [budget progress](https://app.notion.com/p/3c6308cf6b5381f0a220ed0e77e63fb5) and [transaction drill-downs](https://app.notion.com/p/3c7308cf6b5381be8524f1c1cc58166f).

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
