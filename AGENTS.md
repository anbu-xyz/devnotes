# Agent Guidelines for devnotes

## Project Overview

**devnotes** is a personal knowledge-base tool for developers in corporate environments. It serves
markdown files as a web application and extends standard markdown with executable code blocks
(Groovy, SQL, data blocks, PlantUML, Mermaid), Git integration, and a Pomodoro timer.

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
        code/              # Code-block translators (Groovy, SQL, data, Mermaid, parameter)
        image/             # Local-image path rewriting
        link/              # Link transformers
      module/              # Business-logic modules (search, SQL executor, Groovy renderer …)
      service/             # Spring services (config, git, pomodoro, scheduler, datasource)
      types/               # Value types (MarkdownFile, CashAmount, …)
      util/                # Helpers (FileUtil, DateTimeUtil, FileBasedCache)
      cash/                # Currency / exchange-rate support
      scheduled/           # Quartz-scheduled jobs
    jte/                   # jte view templates
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
output:
  templateType: jte
  template: |
    @param List<Map<String, Object>> rows
    ...
```

Results are cached to `<filename>.<checksum>.output` files alongside the markdown source.
The checksum covers the query, parameters, and shared parameter registry state.

### Parameter Registry

`ParameterBlockTranslator` processes `` ```parameter `` blocks first and stores named values in a
request-scoped `ParameterRegistry`. These shared parameters are automatically merged into every
subsequent `DataBlockTranslator` execution on the same page (shared params **override** YAML-local
params).

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

---

## Style Conventions

- Java source uses **Lombok** (`@Slf4j`, `@RequiredArgsConstructor`, `@SneakyThrows`, etc.).
- Prefer `Optional<Node>` return types for markdown transformers.
- HTML fragments are built with **j2html** (`TagCreator.*` static imports).
- Avoid Spring `@Autowired` field injection; use constructor injection (Lombok `@RequiredArgsConstructor`).
- Spock feature method names are written as plain-English sentences in string form.

