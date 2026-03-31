# Agent Guidelines for devnotes

## Project Overview

**devnotes** is a personal knowledge-base tool for developers in corporate environments. It serves
markdown files as a web application and extends standard markdown with executable code blocks
(Groovy, SQL, data blocks, PlantUML, Mermaid), Git integration, a Pomodoro timer, and a
spaced-repetition flash-card system.

- **Language / Runtime:** Java 21, Spring Boot 3.x
- **Build tool:** Maven (`mvn`)
- **Test framework:** Spock (Groovy 5) + JUnit 5
- **Templating:** jte (Java Template Engine)
- **Version:** tracked in `pom.xml` (`<version>`)

---

## Build & Run

```bash
# Compile and run all tests
mvn clean test

# Full build (produces the fat jar)
mvn clean package

# Run the application (HTTPS on port 8443 by default)
java -Ddevnotes.docsDirectory=/path/to/your/docs \
     -jar target/devnotes-<version>.jar prod
```

> The app requires TLS certificates at `tls/localtls.crt` and `tls/localtls.key`. See README for
> the `openssl` command that generates them.

### Dev profile (hot-reload of jte templates)

```bash
java -Dspring.profiles.active=dev \
     -Ddevnotes.docsDirectory=/path/to/docs \
     -jar target/devnotes-<version>.jar
```

---

## Project Layout

```
src/
  main/
    java/uk/anbu/devnotes/
      controller/          # Spring MVC controllers (one per feature area)
      markdown/            # Markdown AST visitors / transformers
        code/              # Code-block translators (Groovy, SQL, data, Mermaid, parameter, database-metadata)
          datablock/       # YamlCodeblockConfig POJO + ParameterRegistry
          databasemetadata/ # DatabaseMetadataConfig POJO
        image/             # Local-image path rewriting
        link/              # Link transformers
      module/              # Business-logic modules (search, SQL executor, Groovy renderer …)
      service/             # Spring services (config, git, pomodoro, scheduler, datasource, encryption)
                           #   FlashCardService.java      — load/save/query cards; delegates SM-2 to Sm2Algorithm
                           #   Sm2Algorithm.java          — pure stateless SM-2 computation (no I/O, no Spring)
                           #   ExchangeRateService.java   — load/save exchange-rates.yaml; populates ExchangeRates on startup
                           #   ImageAuditService.java     — walks docsDir to find orphaned images and broken image links
      types/               # Value types (MarkdownFile, CashAmount, FlashCard, FlashCardStats, …)
      util/                # Helpers (FileUtil, DateTimeUtil, FileBasedCache, JdbcTypeMapper)
      cash/                # Currency / exchange-rate support
      scheduled/           # Quartz-scheduled jobs
    jte/                   # jte view templates (fragments + full pages; `database-metadata-diff.jte` for diff results)
      flashcards/          # Flash-card UI templates (summary, review, new-card, edit-card)
    resources/
      application.yaml     # Default config (profiles: prod / dev)
      static/              # CSS, JS, images served statically
  test/
    groovy/uk/anbu/devnotes/  # Spock specifications
    java/uk/anbu/devnotes/    # JUnit tests
    resources/                # Test fixtures
```

---

## Key Configuration (`application.yaml`)

| Property | Default | Purpose |
|---|---|---|
| `devnotes.docsDirectory` | `~/docs` | Root directory for markdown files |
| `devnotes.allowedIps` | `127.0.0.1,…` | IP whitelist for access |
| `devnotes.sshKeyFile` | *(empty)* | SSH key for Git push/pull |
| `devnotes.chromeDriverLocation` | *(empty)* | Path to ChromeDriver for Selenium |
| `devnotes.sql.maxRows` | `1000` | Global default SQL row limit |
| `server.port` | `8443` | HTTPS port |

Datasource connections are configured in a separate file at
`<docsDirectory>/config/datasource.yaml`:

```yaml
myDatasource:
  url: "jdbc:postgresql://host:5432/db"
  username: "user"
  password: "pass"
  driverClassName: "org.postgresql.Driver"
```

When password encryption is active, the `password` field holds an `ENC(…)` token instead
of plain text.  A PBKDF2 salt is stored alongside in `<docsDirectory>/config/encryption.salt`.
The operator supplies the passphrase once at `/config/encryption-key` after each server
start; the derived AES-256-GCM key is kept only in JVM memory.

---

## Architecture Notes

### Markdown Processing Pipeline

1. Raw markdown text is parsed by **commonmark-java**.
2. A series of AST visitors (`NodeVisitor`) walk the tree and replace recognised code fences with
   rendered HTML (`HtmlBlock` nodes):
   - `` ```groovy:<format> `` → `CodeBlockTransformer` → `GroovyRenderer`
   - `` ```sql(datasource:…) `` → `CodeBlockTransformer` → `SqlExecutor`
   - `` ```data `` → `DataBlockTranslator` (YAML config → named-parameter JDBC query → HTML table or jte template)
   - `` ```mermaid `` → `MermaidBlockTranslator`
   - `` ```plantuml `` → `PlantumlController` (rendered server-side to PNG via URL)
   - `` ```parameter `` → `ParameterBlockTranslator` (populates a shared `ParameterRegistry`)
   - `` ```database-metadata `` → `DatabaseMetadataBlockTranslator` (YAML schema doc → HTML card; optional live DB diff via HTMX)
3. The modified AST is rendered back to HTML and injected into the jte page template.

### Data Blocks (`DataBlockTranslator`)

Data blocks use a YAML mini-language inside a `` ```data `` fence:

```yaml
source: myDatasource          # references datasource.yaml key
query: SELECT * FROM users WHERE id = :userId
parameters:
  userId:
    value: 42
    type: integer
options:
  columns: [id, name, email]
  rowLimit: 50
column-formats:               # optional per-column display formats
  amount:
    number-format: "#,##0.00"   # java.text.DecimalFormat pattern
  rate:
    number-format: "0.00000"
output:
  templateType: jte
  template: |
    @param List<Map<String, Object>> rows
    ...
```

Results are cached to `<filename>.<checksum>.output` files alongside the markdown source.
The checksum covers the query, parameters, shared parameter registry state, `column-formats`,
and the current `__exchangeRatesVersion` (last-modified epoch millis of `exchange-rates.yaml`),
so any rate change automatically invalidates the cache.

### Currency-Symbol Column Conversion

When a SQL result column name starts with a recognised currency symbol, every non-null cell value
is automatically converted to the target currency and displayed as a formatted number.

| Column prefix | Unicode | Target currency | Example column name |
|---|---|---|---|
| `$` | U+0024 | USD | `$balance` |
| `£` | U+00A3 | GBP | `£nav` |
| `€` | U+20AC | EUR | `€revenue` |

**Wire format** expected in each cell: `CCC <number>` where `CCC` is an ISO 4217 alphabetic
code (e.g. `GBP 200`, `EUR 1234.56`).  Negative amounts are supported.

**Rendering rules:**

1. Null / blank cell → rendered as `(null)` — no conversion attempted.
2. Malformed value (no space, unknown ISO code, non-numeric amount) → `<td class="data-block-currency-error">` (red).
3. Same currency as target → formatted as number directly — no rate lookup.
4. Different currency → `ExchangeRates.getExchangeRate(srcCurrency, targetCurrency)` called.
   - Rate found: `converted = amount × rate`; rendered as `<td class="data-block-number">`.
   - Rate absent: `<td class="data-block-no-rate" title="No exchange rate configured for …">` (amber, `cursor:help`).
5. `column-formats` `number-format` pattern is applied to the converted result if present;
   otherwise `DecimalFormat("#,##0.00")` is used.

Column headers are displayed as-is, including the symbol prefix.

**Cache invalidation:** `DataBlockTranslator` always injects `__exchangeRatesVersion`
(`ExchangeRateService.getVersion()` — the last-modified epoch millis of `exchange-rates.yaml`)
into the shared-params copy before computing the checksum.  Saving a rate via
`ExchangeRateService.addRate` / `deleteRate` rewrites the file, changing `lastModified`,
which changes the checksum, which causes a cache miss on the next page render.

**Key files:**

| File | Role |
|---|---|
| `markdown/code/DataBlockTranslator.resolveTargetCurrency` | Maps column prefix to target ISO code |
| `markdown/code/DataBlockTranslator.convertAndRenderCurrencyCell` | Performs lookup and formats the cell |
| `cash/ExchangeRates.java` | Static in-memory rate map; `getExchangeRate` derives cross-rates |
| `service/ExchangeRateService.java` | Loads/saves `exchange-rates.yaml`; drives `ExchangeRates` mutations |
| `static/css/style.css` | `.data-block-no-rate` (amber) and `.data-block-currency-error` (red) classes |

### Column Formats (`column-formats`)

`column-formats` is an optional top-level key in a data block that maps column names
(case-insensitive) to a `ColumnFormatConfig`:

| Sub-key | Type | Description |
|---|---|---|
| `number-format` | `java.text.DecimalFormat` pattern | Applied to any `Number` value in that column, e.g. `"#,##0.00"` |

Columns without an entry fall back to the existing defaults (`%,.2f` for float/decimal types,
plain `toString()` for integers). The design is intentionally open for future format types
(`date-format`, `string-transform`, …) by adding fields to `ColumnFormatConfig`.

**Key files:**

| File | Role |
|---|---|
| `markdown/code/datablock/YamlCodeblockConfig.ColumnFormatConfig` | POJO holding format fields |
| `markdown/code/DataBlockTranslator.createTdTag` | Applies formats when rendering each `<td>` |
| `markdown/code/DataBlockTranslator.findFormatConfig` | Case-insensitive column-name lookup |

### Parameter Registry

`ParameterBlockTranslator` processes `` ```parameter `` blocks first and stores named values in a
request-scoped `ParameterRegistry`. These shared parameters are automatically merged into every
subsequent `DataBlockTranslator` execution on the same page (shared params **override** YAML-local
params).

### Database Metadata Blocks (`DatabaseMetadataBlockTranslator`)

Database metadata blocks use a YAML mini-language inside a `` ```database-metadata `` fence to
document a single DB table inline in a wiki page:

```yaml
table:
  name: instrument
  description: Store instruments used in trading.
  datasource: myDatasource      # optional — enables the "Check against DB" button
columns:
  name:
    oracle-type: varchar2(200)
    h2-type: varchar(200)
    java-type: java.lang.String
    description: Instrument name
  type:
    oracle-type: varchar2(20)
    h2-type: varchar(20)
    java-type: java.lang.String
    description: Instrument type
    values:
      PRP: Perpetual bond
      EQU: Equity
  # db-type is used for databases other than H2 / Oracle (e.g. PostgreSQL, MySQL)
  last_updated:
    db-type: timestamptz
    java-type: java.sql.Timestamp
    description: Last update time
```

The three type fields (`oracle-type`, `h2-type`, `db-type`) are mutually exclusive — only the one
matching the live DB is populated; the others are omitted (Jackson `@JsonInclude(NON_NULL)`).

**Rendering** (`DatabaseMetadataBlockTranslator`): Parses the YAML into a `DatabaseMetadataConfig`
POJO and builds a j2html card (title bar + `<table class="db-meta-table">`). The rendered columns
table has seven columns: **Column / Oracle Type / H2 Type / DB Type / Java Type / Description /
Values**. When `table.datasource` is present, an HTMX `<form>` with a "Check against DB ▶"
button is appended.

**Diff endpoint** (`DatabaseMetadataController`, `POST /database-metadata/check`): Accepts
`datasource` + `yamlContent` form params. Opens a JDBC connection via `DriverManagerDataSource`,
calls `DatabaseMetaData.getColumns()`, then computes three sets:

| Set | Meaning |
|---|---|
| **Matched** | Columns in both the wiki and the live DB |
| **Undocumented** | Live DB columns absent from the wiki |
| **Ghost** | Wiki columns absent from the live DB |

The response is rendered by `database-metadata-diff.jte` and swapped into the target `<div>` via
HTMX `hx-swap="innerHTML"`.

**Key files:**

| File | Role |
|---|---|
| `markdown/code/databasemetadata/DatabaseMetadataConfig.java` | Jackson POJO (`@Data`, `@JsonInclude(NON_NULL)`, `@JsonProperty` for hyphenated keys including `db-type`) |
| `markdown/code/DatabaseMetadataBlockTranslator.java` | Translates YAML fence → `HtmlBlock` (7-column table) |
| `controller/DatabaseMetadataController.java` | `POST /database-metadata/check` diff endpoint |
| `src/main/jte/database-metadata-diff.jte` | jte fragment template for the diff result |

### Fetch Metadata Tool (`JdbcDatabaseController`)

`GET /database` serves the fetch-metadata UI page. `POST /database/fetch-metadata` connects to a
configured datasource, introspects all matching tables via JDBC `DatabaseMetaData`, and writes a
single Markdown file containing one fenced `` ```database-metadata `` block per table.

**Request parameters:**

| Parameter | Required | Description |
|---|---|---|
| `configName` | yes | Datasource key from `config/datasource.yaml` |
| `targetName` | yes | Output filename stem — file is saved as `<docsDirectory>/database/<targetName>.md` |
| `schemaPattern` | no | JDBC schema pattern (e.g. `PUBLIC`); defaults to all schemas |
| `tablePattern` | no | JDBC table-name pattern (e.g. `ORD%`); defaults to `%` |

**Output:** One `` ```database-metadata `` block per table, sorted alphabetically, separated by a
blank line, in the file `<docsDirectory>/database/<targetName>.md`. All column names are
lowercased. The type field populated depends on the DB product:

| DB product | Type field written |
|---|---|
| H2 | `h2-type` |
| Oracle | `oracle-type` |
| Any other | `db-type` |

Type-string formatting and JDBC-type → Java-type mapping are handled by `JdbcTypeMapper`:
- Integer/boolean families: no size suffix (e.g. `INTEGER`, `BOOLEAN`)
- Decimal types: `TYPE(size,digits)` when `DECIMAL_DIGITS > 0`
- Other types: `TYPE(size)`
- H2 2.x `CLOB` is reported as `CHARACTER LARGE OBJECT` → `java.lang.String`

**Key files:**

| File | Role |
|---|---|
| `controller/JdbcDatabaseController.java` | `GET /database` page + `POST /database/fetch-metadata` endpoint |
| `util/JdbcTypeMapper.java` | `resolveTypeFieldName`, `formatType`, `toJavaType` static helpers |
| `src/main/jte/tools/database.jte` | UI form (datasource selector, output name, schema/table pattern filters) |

### Password Encryption (`EncryptionService`)

Datasource passwords are encrypted at rest using **AES-256-GCM**.  The AES key is derived
from an operator-supplied passphrase via **PBKDF2WithHmacSHA256** (600 000 iterations, 16-byte
random salt) and held only in JVM memory — it is never persisted.

**Key lifecycle:**

1. On startup `EncryptionService.secretKey` is `null` (key absent).
2. Operator POSTs a passphrase to `/config/encryption-key`.
3. `EncryptionService.setPassphrase(passphrase, saltFile)` derives the key, runs a
   round-trip self-test, and stores the `SecretKey` in a `volatile` field.
4. `ConfigServiceImpl.reEncryptAndSave()` is called: datasources are reloaded from disk
   (decrypting any existing `ENC(…)` tokens), then saved back with all passwords encrypted.

**On-disk token format:**

```
ENC( Base64URL( [12-byte IV] ++ [ciphertext + 16-byte GCM tag] ) )
```

**Backward compatibility:** plain-text passwords in an existing `datasource.yaml` are
loaded as-is when no passphrase is set, and are encrypted on the first save after activation.

**Inline warning when key is absent (`enc-key-needed`):**

When a markdown page contains a `data` or `sql(...)` block that references a datasource whose
password is stored as `ENC(…)` but no passphrase has been entered yet, the render pipeline
detects this early and emits an amber warning `<div class="enc-key-needed">` in place of the
query result.  Detection test (used in `DataBlockTranslator` and `CodeBlockTransformer`):

```java
EncryptionService.isEncrypted(ds.password()) && !configService.isEncryptionKeySet()
```

The warning contains a direct link to `/config/encryption-key?returnTo=<current-page-url>`.
After the passphrase is accepted the controller redirects back to the original page.

`EncryptionKeyController` accepts an optional `returnTo` query/body parameter on both GET and
POST.  `isSafeReturnTo()` validates it (must start with `/`, must not contain `://`) before
using it as the redirect target.  `returnTo` is passed to `encryption-key.jte` via the model
and embedded as a hidden `<input>` in the form — **not** in the `action` URL — to avoid Spring
receiving the parameter twice (once from the URL query-string and once from the POST body) and
joining the values with a comma.

The `POST /datablock/fragment` refresh path (`DataBlockRefreshController`) applies the same guard
and returns `200 text/html` with the warning HTML so HTMX can swap it in.

**Key files:**

| File | Role |
|---|---|
| `service/EncryptionService.java` | AES-256-GCM encrypt/decrypt; PBKDF2 key derivation; salt file management |
| `controller/EncryptionKeyController.java` | `GET/POST /config/encryption-key` — passphrase form and activation; `returnTo` redirect support |
| `jte/tools/encryption-key.jte` | Passphrase entry UI (status banner, confirm input, Alpine.js mismatch guard; hidden `returnTo` input) |
| `<docsDirectory>/config/encryption.salt` | PBKDF2 salt (hex, not secret; generated once; must be backed up) |
| `static/css/style.css` | `.enc-key-needed` amber warning class |

### Flash Cards (`FlashCardService` / `Sm2Algorithm`)

Cards are stored as individual YAML files under `<docsDirectory>/config/flashcards/`.  The
directory hierarchy is the topic tree: a card at `java/streams/lambda-basics.yaml` belongs to
topic `java/streams`.

**Card YAML fields:**

| Field | Type | Default | Description |
|---|---|---|---|
| `question` | `String` | — | Raw CommonMark markdown text |
| `answer` | `String` | — | Raw CommonMark markdown text |
| `lastReviewed` | `LocalDateTime` (ISO-8601) | `null` | Timestamp of most recent review |
| `nextReview` | `LocalDateTime` (ISO-8601) | `null` | Scheduled next review; `null` = due immediately |
| `reviewCount` | `int` | `0` | Consecutive correct reviews (reset to 0 on failure) |
| `correctCount` | `int` | `0` | Cumulative correct reviews |
| `incorrectCount` | `int` | `0` | Cumulative incorrect reviews |
| `easeFactor` | `double` | `2.5` | SM-2 ease factor; floor 1.3 |
| `interval` | `int` | `1` | Days until next review |

`relativePath` and `topic` are populated after loading and annotated `@JsonIgnore` — they are
never written to the YAML file.

**SM-2 scheduling (`Sm2Algorithm.apply`):**

- Quality ≥ 3 (correct): interval advances (`1 → 6 → round(interval × EF)`), EF adjusted, `reviewCount++`, `correctCount++`.
- Quality < 3 (failed): interval resets to 1, `reviewCount` resets to 0, `incorrectCount++`.  EF is unchanged on failure.
- `lastReviewed` ← `now(UTC)`; `nextReview` ← `now + interval days`.

**Quality rating labels** shown to the user:

| Rating | Label | Meaning |
|---|---|---|
| 0 | Blackout | Complete blank |
| 1 | Wrong | Incorrect, remembered after seeing answer |
| 2 | Forgot | Incorrect but easy when shown |
| 3 | Hard | Correct with significant difficulty |
| 4 | Good | Correct after hesitation |
| 5 | Easy | Perfect, no hesitation |

**REST API (`FlashCardController`):**

| Method | Path | Description |
|---|---|---|
| GET | `/flashcards` | Summary page: global stats + per-topic table |
| GET | `/flashcards/review[?topic=…]` | Next due card (Alpine.js show/hide answer; keys 0–5 to rate) |
| POST | `/flashcards/review` | Apply SM-2, save card, redirect to next review |
| GET | `/flashcards/new[?topic=…]` | Blank new-card form |
| POST | `/flashcards/new` | Create card YAML, redirect to summary |
| GET | `/flashcards/edit/{encodedPath}` | Pre-filled edit form |
| POST | `/flashcards/edit/{encodedPath}` | Save updated question/answer, preserve SM-2 metadata |

`encodedPath` = Base64-URL (no padding) of the card's relative path.  Filename generation for
new cards slugifies the first 40 chars of the question; duplicates get `-2`, `-3`, … suffixes.

**Stats aggregation (`FlashCardService.computeStats`):**

- `reviewedToday`: cards whose `lastReviewed.toLocalDate().equals(LocalDate.now(UTC))`.
- `currentStreak`: cards sorted by `lastReviewed` descending; count of the leading consecutive
  run where `reviewCount > 0` (i.e. last review was correct). Computed independently per topic.

**Key files:**

| File | Role |
|---|---|
| `types/FlashCard.java` | `@Data` POJO; all YAML fields + `@JsonIgnore` `relativePath`/`topic` |
| `types/FlashCardStats.java` | Java record: per-topic aggregate (total, dueNow, reviewedToday, accuracyPercent, currentStreak) |
| `service/Sm2Algorithm.java` | Pure stateless SM-2; single public method `FlashCard apply(FlashCard, int)` |
| `service/FlashCardService.java` | All card I/O, query, stats, markdown rendering, path encoding |
| `controller/FlashCardController.java` | 7 HTTP endpoints; renders jte templates |
| `jte/flashcards/summary.jte` | Stats overview + topic tree table |
| `jte/flashcards/review.jte` | Flip-card UI (Alpine.js show/hide; keyboard shortcuts 0–5) |
| `jte/flashcards/new-card.jte` | New card form with topic datalist autocomplete |
| `jte/flashcards/edit-card.jte` | Edit form; preserves SM-2 metadata |

---

### Exchange Rates (`ExchangeRateService`)

Exchange rates are persisted to `<docsDirectory>/config/exchange-rates.yaml` as a flat YAML map:

```yaml
GBP/USD: 1.2700
EUR/USD: 1.0850
JPY/USD: 0.006800
```

**Lifecycle:**

1. On startup `ExchangeRateService.init()` reads the file (if present), calls
   `ExchangeRates.setExchangeRate(pair, rate)` for every entry, and stores explicit pairs in an
   internal `LinkedHashMap` (insertion order preserved).
2. `addRate(pairString, rate)` — validates both ISO 4217 codes, calls `setExchangeRate`, appends
   to the map, and calls `save()`.
3. `deleteRate(pairString)` — removes from the map and calls `save()`.  Reverse-derived entries
   already in `ExchangeRates` static map are NOT removed until the next restart.
4. `save()` — writes the map to `exchange-rates.yaml` via Jackson `YAMLFactory`.
5. `getVersion()` — returns `file.lastModified()` (or `0L` when absent); used as a cache-bust
   key injected into every data-block checksum as `__exchangeRatesVersion`.

**Exchange Rate Manager UI** (`ExchangeRateController`, `GET/POST /tools/exchange-rates`):

| Method | Path | Description |
|---|---|---|
| GET | `/tools/exchange-rates` | Show current rates table + add-rate form |
| POST | `/tools/exchange-rates` | Add / update a rate (validates pair format and ISO codes) |
| POST | `/tools/exchange-rates/delete` | Remove a rate by pair string |

**Key files:**

| File | Role |
|---|---|
| `service/ExchangeRateService.java` | Load/save `exchange-rates.yaml`; drive `ExchangeRates` mutations |
| `cash/ExchangeRates.java` | Static in-memory map; `setExchangeRate` / `getExchangeRate` with cross-rate derivation |
| `cash/CurrencyCodes.java` | ISO 4217 code validation |
| `controller/ExchangeRateController.java` | `GET/POST /tools/exchange-rates` and `/tools/exchange-rates/delete` |
| `jte/tools/exchange-rates.jte` | Rate table + add-rate form UI |

---

### Image Audit Tool (`ImageAuditController`)

`GET /tools/image-audit` scans every `.md` file and every image file under `docsDirectory` and
produces two lists:

| List | Meaning |
|---|---|
| **Orphaned images** | Image files on disk (`.png`, `.jpg`, `.jpeg`, `.gif`, `.svg`, `.bmp`, `.webp`, `.tiff`, `.tif`, `.ico`) not referenced by any markdown file |
| **Broken links** | `![…](…)` image links in markdown files that resolve to a path that does not exist on disk |

**Path resolution rules applied by `ImageAuditService.audit()`:**

| Link form | Resolved as |
|---|---|
| `http://…` / `https://…` | Ignored (external) |
| `/plantumlContent?…` | Ignored (synthetic plantuml link) |
| `/path/to/img.png` | `<docsDir>/path/to/img.png` (absolute from docs root) |
| `relative/img.png` | Relative to the markdown file's own directory |

Results are sorted: orphaned paths alphabetically; broken links by markdown filename then image
link.  `ImageController.isImage()` (widened to `public`) is reused to identify image files by
extension.

**Key files:**

| File | Role |
|---|---|
| `service/ImageAuditService.java` | Walks docs tree; returns `ImageAuditResult` with `orphanedImages` and `brokenLinks` |
| `controller/ImageAuditController.java` | `GET /tools/image-audit` — runs audit and renders the jte page |
| `controller/ImageController.java` | `isImage(String)` — now `public`; shared by both the image-serving endpoint and the audit service |
| `jte/tools/image-audit.jte` | Two-section results page (orphaned table + broken-link table) |

---

### Groovy Execution

Scripts run inside a sandboxed `GroovyShell`. Supported output formats (specified in the code-fence
info string):

| Info string | Rendered as |
|---|---|
| `groovy:csv-table` | HTML table (no header row) |
| `groovy:csv-table-with-header` | HTML table (first row = header) |
| `groovy:html` | Raw HTML |
| `groovy:text` | `<pre>` block |
| `groovy:code-block` | Escaped `<pre>` block |

Append `(cacheEnabled:false)` to the info string to disable output caching.

---

## Testing

- Tests live in `src/test/groovy` (Spock) and `src/test/java` (JUnit).
- Run with `mvn test`; reports land in `target/surefire-reports/`.
- Code coverage via JaCoCo (`target/site/jacoco/`).
- Spock specs follow the `*Spec` naming convention; JUnit tests follow `*Test`.
- Most specs use an embedded H2 database or mock/stub Spring beans — no external services needed.

---

## CI

A `Jenkinsfile` at the project root runs `mvn clean test` and reports status back to GitHub via
the commit-status API. No deployment step is included.

---

## Common Tasks for AI Agents

| Task | Where to look / what to change |
|---|---|
| Add a new code-fence type | `CodeBlockTransformer.java`, then add a translator class |
| Add a new REST endpoint | New class in `controller/`, register as `@RestController` |
| Add a new datasource driver | Add the JDBC dependency in `pom.xml` |
| Change default SQL row limit | `devnotes.sql.maxRows` in `application.yaml` or override in data-block YAML |
| Add a new jte view template | `src/main/jte/` (dev profile) or `src/main/resources/templates/` |
| Change cache key logic | `YamlCodeblockConfig.checksum()` in `markdown/code/datablock/` |
| Add a scheduled job | New class in `scheduled/`, configure via Quartz or `@Scheduled` |
| Add a column field to database-metadata | `DatabaseMetadataConfig.ColumnConfig` + translator `buildHtmlBlock` + `database-metadata-diff.jte` |
| Change the diff output layout | Edit `src/main/jte/database-metadata-diff.jte` |
| Add a new column format type (e.g. date-format) | Add field to `YamlCodeblockConfig.ColumnFormatConfig`, then handle it in `DataBlockTranslator.createTdTag` |
| Change JDBC type → Java type mapping | Edit `JdbcTypeMapper.toJavaType` |
| Change how type strings are formatted (e.g. suppress size) | Edit `JdbcTypeMapper.formatType` and `JdbcTypeMapper.isNoSizeType` |
| Add support for a new DB product in fetch-metadata | Add a `case` to `JdbcTypeMapper.resolveTypeFieldName` and add the new type field to `DatabaseMetadataConfig.ColumnConfig` |
| Change fetch-metadata output path or format | `JdbcDatabaseController.saveAllTablesAsMarkdownFile` |
| Change encryption algorithm | `EncryptionService.ALGORITHM` constant; update `IV_LEN` if switching away from GCM |
| Change PBKDF2 iteration count | `EncryptionService.KDF_ITERS`; higher = slower brute-force, slower activation |
| Rotate the passphrase / re-key | Enter new passphrase at `/config/encryption-key`; `reEncryptAndSave()` is called automatically |
| Disable encryption (revert to plain text) | Do not supply passphrase after restart; `saveDataSourceConfigs()` writes plain text when `isKeySet()` is false |
| Change minimum passphrase length | `EncryptionService.MIN_PASSPHRASE_LEN` constant |
| Change the encrypted-password inline warning style | Edit `.enc-key-needed` in `static/css/style.css` |
| Change where the user lands after entering passphrase | Modify `isSafeReturnTo()` in `EncryptionKeyController` or the `returnUrl` built in `DataBlockTranslator.buildEncKeyNeededBlock()` |
| Add a field to flash cards | Add to `FlashCard.java`; update `Sm2Algorithm` if it affects scheduling; update `edit-card.jte` if it should be user-editable |
| Change the SM-2 scheduling formula | Edit `Sm2Algorithm.apply`; update `Sm2AlgorithmSpec` regression tests |
| Change how flash card stats are computed | Edit `FlashCardService.buildStats` / `computeStreak`; add or update `FlashCardServiceSpec` tests |
| Add a new flash card endpoint | Add handler to `FlashCardController`; add a jte template if needed |
| Change flash card storage location | Edit `FlashCardService.flashcardsRoot()` |
| Change flash card filename generation | Edit `FlashCardController.slugify` |
| Add a new exchange-rate currency symbol | Add a `case` to `DataBlockTranslator.resolveTargetCurrency`; document in README |
| Change exchange-rate storage location | Edit `ExchangeRateService.ratesFile()` |
| Change exchange-rate YAML format | Edit `ExchangeRateService.save()` and `init()`; update tests |
| Add a new currency-error CSS style | Edit `.data-block-no-rate` / `.data-block-currency-error` in `static/css/style.css` |
| Change which file extensions are treated as images | Edit `ImageController.isImage()` — used by both the image-serving endpoint and `ImageAuditService` |
| Change image-audit path-resolution logic | Edit `ImageAuditService.audit()` visitor |
| Add extra columns / detail to the image-audit results page | Edit `jte/tools/image-audit.jte` |

---

## Style Conventions

- Java source uses **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`, `@SneakyThrows`, etc.).
- Prefer `Optional<Node>` return types for markdown transformers.
- HTML fragments are built with **j2html** (`TagCreator.*` static imports).
- Avoid Spring `@Autowired` field injection; use constructor injection (Lombok `@RequiredArgsConstructor`).
- Spock feature method names are written as plain-English sentences in string form.
- Use functional style where it improves readability (e.g. `map`/`filter`/`collect` on streams, `Optional.map`, etc.).
- Use `var` for local variables when the type is obvious from the right-hand side (e.g. `var config = …` when the method name makes it clear that it's a config object).
- Use `final` for method parameters and local variables when it adds clarity or prevents bugs; omit when it adds noise.
- Use `@Slf4j` for logging; prefer parameterized messages (`log.info("… {}", var)`) over string concatenation.
- Inside a method do not modify the input parameters; if you need to transform them, assign to a new `var` (e.g. `var transformedInput = transform(input)`).
- Use pure functions where possible.

