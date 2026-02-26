# Replicating Plugin Artifact Resolution at Runtime in TypeScript

This doc describes how the Java POC resolves regulation plugins at runtime and how to achieve the same pattern—including optional **artifact resolution** (fetch from a registry or S3)—in the TypeScript engine.

---

## 1. Java flow (reference)

| Step | Java | Location |
|------|------|----------|
| **Config** | `regulations.json`: `id`, `version`, `jar` (filename), `evaluatorClass` | Engine resources |
| **Registry** | `RegulationRegistry(pluginsBase)`: maps `id:version` → absolute JAR path + class name | `RegulationRegistry.java` |
| **Artifact resolution** | Optional: entrypoint script fetches JAR from CodeArtifact into `PLUGINS_DIR` before starting the JVM | `entrypoint.sh` |
| **Load** | `RegulationEvaluatorLoader`: `URLClassLoader` with JAR URL → `loadClass(className)` → `getConstructor().newInstance()` | `RegulationEvaluatorLoader.java` |
| **Use** | Engine calls `getEvaluator(id, version)` (cached); can also `newEvaluator()` for a fresh instance (same classloader) | `MinimalPayrun` |

So at **runtime** the JVM only needs: a path to a JAR and a class name. Artifact resolution (downloading the JAR) can happen outside the JVM (e.g. in the container entrypoint).

---

## 2. TypeScript flow today

| Step | TypeScript | Location |
|------|------------|----------|
| **Config** | `regulations.json`: `id`, `version`, `packageDir`, `factoryName` | `packages/engine/src/resources/regulations.json` |
| **Registry** | `RegulationRegistry(pluginsBase)`: maps `id:version` → `packagePath` (dir) + `factoryName` | `RegulationRegistry.ts` |
| **Artifact resolution** | **None**: plugins are assumed to be on disk under `plugins/<packageDir>/` | — |
| **Load** | `RegulationEvaluatorLoader`: `pathToFileURL(join(packagePath, 'dist', 'index.js'))` → `import(url)` → `module[factoryName]()` | `RegulationEvaluatorLoader.ts` |
| **Use** | Engine calls `await loader.getEvaluator(id, version)` (cached) | `main.ts` |

Important: the **lambda handler** does **not** use the loader; it uses a **static import** of the regulation (`import { createEvaluator } from '../../plugins/poc-regulation/src/index.js'`). So runtime plugin resolution is only used in the CLI (`main.ts`).

---

## 3. Replicating the pattern in TypeScript (no remote artifact)

You already have the same **pattern** as Java:

1. **Registry**  
   - Java: `id:version` → JAR path + class name.  
   - TS: `id:version` → package directory + factory name.

2. **Loader**  
   - Java: create a classloader from JAR, load class, instantiate.  
   - TS: dynamic `import(entrypointUrl)`, then call the exported factory.

3. **Entry point**  
   - Java: class implementing `RegulationEvaluator`.  
   - TS: module exporting a factory (e.g. `createEvaluator`) that returns a `RegulationEvaluator`.

To use this consistently at runtime (including in Lambda):

- In **lambda/handler.ts**, replace the static import with the same registry + loader used in **main.ts**:
  - Read `regulations.json` (from packaged resources).
  - Build `RegulationRegistry` with `pluginsBase` (e.g. `path.join(__dirname, '..', 'plugins')` or an env-driven path).
  - Use `RegulationEvaluatorLoader.getEvaluator(regulationId, version)` and run the payrun with that evaluator.

That gives you **runtime plugin resolution** from a local filesystem (bundle or layer the plugin under `plugins/<packageDir>/dist/` in the deployment package).

---

## 4. Adding runtime artifact resolution in TypeScript

“Artifact resolution” here means: **before** loading the plugin, ensure its code is present (e.g. by downloading it from a registry or S3). In Java this is done in the entrypoint (e.g. `aws codeartifact get-package-version-asset ...`). In TypeScript you can do it inside the process.

### Option A: Resolve from filesystem only (current)

- `regulations.json` has `packageDir` (e.g. `"poc-regulation"`).
- `pluginsBase` + `packageDir` → path to the package (e.g. `plugins/poc-regulation`).
- Loader expects `dist/index.js` (or a configurable entry) and calls the named factory.
- No download step.

### Option B: Resolve from npm (or private npm registry)

- Config can support a **package spec** instead of (or in addition to) `packageDir`, e.g.:
  - `"packageSpec": "@payroll/poc-regulation@1.0.0"` or `"package": "@payroll/poc-regulation", "version": "1.0.0"`.
- At runtime, if the package is not already on disk:
  1. Run `npm pack @payroll/poc-regulation@1.0.0` or use a child process to `npm install` into a dedicated directory (e.g. `plugins/.resolved/<id>-<version>`).
  2. Or use a programmatic npm client to fetch the tarball and extract it.
  3. Set `packagePath` to the extracted package root; the loader then uses `require` or `import(pathToFileURL(join(packagePath, 'dist', 'index.js')))` as today.
- Ensure the regulation package’s `package.json` has `"main": "dist/index.js"` and exports the factory (e.g. `createEvaluator`).

### Option C: Resolve from S3 (or URL)

- Config can support `"artifactUrl": "s3://bucket/prefix/poc-regulation-1.0.0.tgz"` or an HTTPS URL.
- At runtime:
  1. If the plugin dir does not exist (or is stale), download the tgz (e.g. with `@aws-sdk/client-s3` + `getObject` or `fetch` for HTTPS).
  2. Extract (e.g. `tar -xzf` via child process, or a library like `tar`) into `plugins/.resolved/<id>-<version>/`.
  3. Optionally run `npm install --production` in that directory if the tarball is a source package.
  4. Use the same loader with `packagePath` = extracted directory.

### Option D: Hybrid (like Java entrypoint)

- **Build/deploy time**: a script or CI step fetches the regulation artifact (e.g. from CodeArtifact npm or S3) into `plugins/<packageDir>/` (or a versioned path).
- **Runtime**: no download; loader only resolves from the filesystem (Option A). This is the closest to the Java model where the entrypoint script does CodeArtifact fetch and the JVM only loads from disk.

---

## 5. Recommended layout for runtime resolution

- **Config** (e.g. `regulations.json`):

```json
{
  "regulations": [
    {
      "id": "france-regulation",
      "version": "1.0.0",
      "packageDir": "poc-regulation",
      "factoryName": "createEvaluator"
    },
    {
      "id": "france-regulation",
      "version": "2.0.0",
      "packageSpec": "@payroll/poc-regulation@2.0.0",
      "factoryName": "createEvaluator"
    }
  ]
}
```

- **Resolver** (new module, e.g. `PluginArtifactResolver.ts`):
  - For each regulation, if `packageDir` is set and the directory exists → use it.
  - Else if `packageSpec` (or `package`+`version`) is set → resolve via npm (install/pack into `plugins/.resolved/<id>-<version>`), then set effective `packagePath`.
  - Else if `artifactUrl` is set → download + extract into `plugins/.resolved/<id>-<version>`, then set effective `packagePath`.
  - Registry then uses this effective path for `getPackagePath(id, version)`.

- **Loader** (existing `RegulationEvaluatorLoader.ts`):
  - Takes the resolved `packagePath` and `factoryName`, builds entry URL (e.g. `join(packagePath, 'dist', 'index.js')`), `import(url)`, then `module[factoryName]()`.
  - No change to the loader interface; only the way `packagePath` is obtained changes (filesystem-only vs resolver).

---

## 6. Summary

| Concern | Java | TypeScript replication |
|--------|------|-------------------------|
| **Registry** | JAR path + class name | Package path + factory name (already in place) |
| **Load** | URLClassLoader + reflection | `import(entrypointUrl)` + `module[factoryName]()` (already in place) |
| **Use loader everywhere** | Engine always uses loader | Use `RegulationEvaluatorLoader` in Lambda as well, not static import |
| **Artifact resolution** | Entrypoint fetches JAR from CodeArtifact | Add resolver: filesystem (current), or npm, or S3/URL; then load from resolved path |

So: **pattern** is already replicated in TS (registry + dynamic load). To replicate **artifact resolution**, add a resolution step (npm/S3/URL or build-time fetch) that produces a package directory, then keep using the existing loader with that path.
