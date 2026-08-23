# Personal Finance REST API

Spring Boot REST API for the Personal Finance application.

## Local development

The API runs at `http://localhost:8080` by default. All public application endpoints are versioned under:

```text
/api/v1
```

Run unit tests with `./mvnw test`. Run unit and integration tests with `./mvnw verify`.

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

`currentBalance` initially equals `openingBalance`. The response field remains stable when transaction activity later becomes part of the calculation.

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
- An as-of request before the first recorded balance returns `404`.

Clients should use `status` and `fieldErrors` for behavior rather than parsing the human-readable `error` text.
