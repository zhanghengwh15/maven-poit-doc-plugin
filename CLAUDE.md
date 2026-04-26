# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Compile only
mvn compile

# Build fat JAR (for JVM execution)
mvn clean package

# Build GraalVM native image (requires GraalVM for JDK 21)
mvn clean package -Pnative

# Run tests
mvn test

# Run single test
mvn test -Dtest=SourceRootDiscoveryTest

# Skip tests during build
mvn clean package -DskipTests
```

## Project Overview

This is a **standalone Java CLI tool** that scans Java source code using a built-in QDox/ASM-based scanner and synchronizes API documentation to MySQL 8. It recursively discovers `src/main/java` directories, extracts Controller endpoints and model definitions from Spring-based projects, and stores them in a structured database format with JSON fields.

**Key Characteristics:**
- **Java Version:** JDK 21
- **CLI Framework:** picocli 4.7.5
- **Scanner:** QDox (source) + ASM (bytecode fallback) — no Smart-doc dependency
- **Database:** MySQL 8 with HikariCP connection pool
- **JSON:** fastjson2 + Gson
- **Native Image:** GraalVM support via `native` profile
- **Exit Codes:** 0=success, 1=DB error, 2=configuration error

## Architecture

```
PoitApiScannerCli (CLI Entry Point)
    ↓
SourceRootDiscovery (Finds src/main/java directories)
    ↓
MavenPomArtifactIdResolver (Reads pom.xml for artifactId)
    ↓
ApiScannerEngine (QDox/ASM scanner)
    ├→ QDoxClassMetaProvider (source parsing)
    ├→ AsmClassMetaProvider (bytecode fallback)
    ├→ ReflectionClassMetaProvider (last resort)
    └→ CompositeClassMetaProvider (chains above with caching)
    ↓
SyncInput (direct output, no intermediate format)
    ↓
DocSyncService (Database upsert coordination)
    ├→ ApiDocSupport (Model extraction from ApiMethod/ApiField)
    ├→ ModelDefinitionSync (Model persistence with topological sort)
    └→ Interface upsert/delete logic
```

**Key Components:**

- **`PoitApiScannerCli`**: Main CLI entry point with picocli annotations. Handles parameter parsing, validation, builds QDox `JavaProjectBuilder`, invokes `ApiScannerEngine`, and calls `DocSyncService.sync()`.
- **`SourceRootDiscovery`**: Recursively discovers `src/main/java` directories, skipping `target/`, `.git/`, `.idea/`, `node_modules/`, `build/`, and hidden directories.
- **`MavenPomArtifactIdResolver`**: XML parser that extracts `artifactId` from `<project>` element (not from `<parent>`).
- **`ApiScannerEngine`**: Main scanner entry point. Scans source roots, identifies `@Controller`/`@RestController` classes, parses `@RequestMapping` series annotations, extracts parameters and response types. Returns `SyncInput`.
- **`SyncInput`**: Direct output data structure containing `List<SyncController>`, each with `ApiMethod` lists. No Smart-doc intermediate format.
- **`ApiDocSupport`**: Extracts model definitions from `ApiMethod` trees. Flattens nested `ApiField` structures into flat JSON with `ref` references for circular reference handling.
- **`DocSyncService`**: Orchestrates database synchronization with transaction management, model sync via `ModelDefinitionSync`, interface upsert with MD5-based change detection, and soft deletion of stale interfaces.
- **`ModelDefinitionSync`**: Handles model persistence with topological sort to respect dependencies, converts `ref` (class names) to `ref_model_id` (database foreign keys), and uses MD5-based change detection.

## Scanner Components (`com.poit.doc.scanner`)

- **`provider/`**: `ClassMetaProvider` interface with three implementations:
  - `QDoxClassMetaProvider` — source code parsing (primary)
  - `AsmClassMetaProvider` — bytecode analysis (fallback for classes without source)
  - `ReflectionClassMetaProvider` — JVM reflection (last resort, uses `Class.forName(fqn, false, loader)`)
  - `CompositeClassMetaProvider` — chains QDox → ASM → Reflection with in-process caching
- **`resolver/`**: `SourceLoader`, `ClassFilter`, `MethodParser`, `TypeResolver` (recursive type resolution with generics, circular ref guard, inheritance, enum support)
- **`merger/`**: `DocMerger` — annotation priority chains for descriptions (`@ApiModelProperty` → `@Schema` → `@JsonPropertyDescription` → JavaDoc) and required fields

## Database Schema

Three tables defined in `src/main/resources/schema.sql`:

- **`api_interface`**: Stores endpoint metadata with unique key `(service_name, env, path, method)`. JSON columns for `path_params`, `query_params`, and `raw_info`. MD5 signatures for change detection: `req_params_md5`, `res_params_md5`. Foreign keys `req_body_model_id`, `res_body_model_id` reference `api_model_definition.id`.
- **`api_model_definition`**: Stores model definitions with unique key `(service_name, full_name, model_md5)`. `fields` JSON column with `ref_model_id` references for nested objects.
- **`api_dictionary`**: API terminology dictionary with global and per-service scope.

## Model Reference Pattern

To handle circular references and nested objects, models are stored with references instead of inline expansion:

```json
// Example: UserDTO fields stored in api_model_definition.fields
[
  {"name": "id", "type": "long", "desc": "User ID"},
  {"name": "role", "type": "object", "ref": "com.example.RoleDTO", "ref_model_id": 123}
]
```

Frontend loads models recursively by following `ref_model_id` values.

## CLI Parameters

**Required:**
- `--scan-dir`: Scan root directory; auto-discovers `**/src/main/java`
- `--db-url`: MySQL JDBC URL
- `--db-user` / `--db-password`: Database credentials
- `--service-version`: API version (e.g., `v1`)
- `--env`: Environment (e.g., `dev` / `test` / `prod`)

**Conditionally Required:**
- `--service-name` / `--artifact-id`: If not provided, they are read from `--scan-dir/pom.xml` `<artifactId>`

**Optional:**
- `--package-filters` / `--package-exclude-filters`: Package inclusion/exclusion filters
- `--project-name`: Display project name, defaults to last directory name of `--scan-dir`
- `--source-path`: Explicit source roots (disables auto-discovery)
