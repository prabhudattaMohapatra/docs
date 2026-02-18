# Payroll Engine – Workspace Architecture

This document describes the architecture of the Payroll Engine as reflected in the workspace: the backend, the console, and all **payroll-engine-xx** packages that the backend (and related apps) depend on. These packages are present in the workspace as separate repositories and are consumed by the backend and console as NuGet packages (e.g. `PayrollEngine.Core`, `PayrollEngine.Client.Scripting`).

---

## 1. High-Level View

- **payroll-engine-backend** – REST API server and payroll engine core (domain, API, persistence). It is the central service that runs payruns, compiles and executes C# payroll scripts, and stores data in SQL Server.
- **payroll-engine-console** – CLI that talks to the backend (or file-based workflows) for import/export, payrun execution, regulation import, reporting, and diagnostics.
- **payroll-engine-webapp** – Blazor web application that uses the backend API (and shares types with it via NuGet).
- **payroll-engine-adminApp** – Blazor admin application.

The backend and console do **not** use project references to the other payroll-engine-xx repos; they reference **NuGet packages** (e.g. `PayrollEngine.Core`, `PayrollEngine.Client.Scripting`). The corresponding source lives in the workspace as:

| NuGet Package                  | Workspace Repository        |
|--------------------------------|-----------------------------|
| PayrollEngine.Core             | payroll-engine-core         |
| PayrollEngine.Client.Core      | payroll-engine-client-core  |
| PayrollEngine.Client.Scripting | payroll-engine-client-scripting |
| PayrollEngine.Client.Services  | payroll-engine-client-services  |
| PayrollEngine.Client.Test      | payroll-engine-client-test  |
| PayrollEngine.Serilog          | payroll-engine-serilog     |
| PayrollEngine.Document         | payroll-engine-document    |

Additional workspace repos used for tooling, regulations, or documentation:

- **payroll-engine-jsonSchemaBuilder** – JSON schema generation (e.g. referenced from client-core build).
- **payroll-engine-dbQueryTool** – Database query tooling.
- **payroll-engine** – Root/orchestration repo: commands, examples, tests, docker-compose, docs (not a single .NET app).
- **payroll-engine-template** – Template for building a regulation (scripts + JSON).
- **payroll-engine-regulations-INDIA**, **payroll-engine-regulations-swiss**, **payroll-engine-regulation-France** – Country-specific regulations and scripts.
- **payroll-engine-rules** – Rules (separate repo path in workspace).

---

## 2. Payroll Engine Backend (payroll-engine-backend)

The backend is a .NET ASP.NET Core web application that exposes a REST API and hosts the payroll engine (domain logic, script compilation, persistence).

### 2.1 Solution Structure

The solution `PayrollEngine.Backend.sln` contains only projects inside the backend repo (no project references to other payroll-engine-xx folders). Dependencies on payroll-engine-xx are via **NuGet** only.

| Project | Type | Description |
|---------|------|-------------|
| PayrollEngine.Domain.Model | Library | Domain entities and repository contracts. Depends on **PayrollEngine.Core**, **PayrollEngine.Client.Scripting** (NuGet). |
| PayrollEngine.Domain.Scripting | Library | Script compilation and execution (Roslyn). Depends on Domain.Model, **PayrollEngine.Client.Scripting**, Microsoft.CodeAnalysis.Compilers. |
| PayrollEngine.Domain.Application | Library | Application/orchestration services. Depends on Domain.Scripting. |
| PayrollEngine.Persistence | Library | Repository implementations, query building (SqlKata, OData). Depends on Domain.Scripting. |
| PayrollEngine.Persistence.SqlServer | Library | SQL Server–specific persistence. Depends on Persistence. |
| PayrollEngine.Api.Model | Library | REST DTOs and API models. Depends on Domain.Model, **PayrollEngine.Core**. |
| PayrollEngine.Api.Core | Library | API core services, versioning, Swagger. Depends on Domain.Application, Persistence, Api.Model. |
| PayrollEngine.Api.Map | Library | Mapping between API and domain (Mapperly). Depends on Domain.Model, Api.Core. |
| PayrollEngine.Api.Controller | Library | REST controllers. Depends on Api.Map. |
| PayrollEngine.Backend.Controller | Library | Routing and controller assembly. Depends on Api.Controller. |
| PayrollEngine.Backend.Server | Web/Exe | ASP.NET Core host. Depends on Backend.Controller, Persistence.SqlServer; **PayrollEngine.Serilog** (NuGet). |

### 2.2 Backend Dependency Flow (Internal + NuGet)

```
Backend.Server
  ├── Backend.Controller
  │     └── Api.Controller
  │           └── Api.Map
  │                 ├── Domain.Model  ──► PayrollEngine.Core (NuGet)
  │                 │                    PayrollEngine.Client.Scripting (NuGet)
  │                 └── Api.Core
  │                       ├── Domain.Application
  │                       │     └── Domain.Scripting
  │                       │           ├── Domain.Model
  │                       │           └── PayrollEngine.Client.Scripting (NuGet)
  │                       ├── Persistence
  │                       │     └── Domain.Scripting
  │                       └── Api.Model
  │                             ├── Domain.Model
  │                             └── PayrollEngine.Core (NuGet)
  └── Persistence.SqlServer
        └── Persistence
              └── Domain.Scripting
```

Backend NuGet usage:

- **PayrollEngine.Core** – Shared types, configuration, logging abstraction, serialization (used by Domain.Model, Api.Model).
- **PayrollEngine.Client.Scripting** – Script runtime types and base classes that the backend’s Domain.Scripting layer uses when compiling and executing regulation C# scripts (Roslyn compiles user code that inherits from/invokes client scripting types).
- **PayrollEngine.Serilog** – Serilog integration (Backend.Server).

### 2.3 Backend Responsibilities

- Host REST API (OpenAPI/Swagger).
- Run payruns: load regulation, employees, cases; execute payrun lifecycle and C# scripts.
- Compile C# regulation scripts with Roslyn; cache compiled assemblies.
- Persist tenants, regulations, payruns, results, etc. to SQL Server.
- Optional API key, audit trail, webhooks, OData query support.

---

## 3. Payroll Engine Console (payroll-engine-console)

The console is a .NET executable that provides CLI commands for working with the Payroll Engine (backend or file-based).

### 3.1 Structure

- **PayrollConsole** – Main executable project.
  - NuGet: **PayrollEngine.Client.Services**, **PayrollEngine.Serilog**, Serilog sinks.
  - Project refs: Commands, payroll-engine-console-dsl, SwissExtension (Regulation.CH.Swissdec.Console).
- **Commands** (PayrollEngine.PayrollConsole.Commands) – Command implementations.
  - NuGet: **PayrollEngine.Client.Services**, **PayrollEngine.Document**, NPOI, AWS SDK (S3, SSO).
- **payroll-engine-console-dsl** – DSL support for the console.
- **SwissExtension** – Swiss regulation/tax conversion extension.

### 3.2 Console Dependency Chain (NuGet)

- **PayrollEngine.Client.Services** – High-level API client and scripting helpers (used by Console and Commands).
- **PayrollEngine.Client.Scripting** – (Transitive via Client.Services) Script types and runtime used when interacting with backend or running script-related commands.
- **PayrollEngine.Client.Test** – (Transitive via Client.Services) Test utilities.
- **PayrollEngine.Client.Core** – (Transitive via Client.Scripting) API client, exchange format, query builders, model classes.
- **PayrollEngine.Document** – Report/document generation (e.g. Excel, PDF via NPOI/FastReport).
- **PayrollEngine.Serilog** – Logging.

So the console depends on the same “client stack” (Core → Scripting → Services) plus Document and Serilog; all of these have corresponding **payroll-engine-xx** repos in the workspace.

---

## 4. Shared Packages (payroll-engine-xx) and Their Roles

These are the packages that the backend and/or console (and webapp) consume and that exist in the workspace as separate repos.

### 4.1 payroll-engine-core (PayrollEngine.Core)

- **Role**: Foundation used by almost all other Payroll Engine components.
- **Contents**: Exceptions, logger abstraction, document/report abstraction (`IDataMerge`), value conversion, common types and extensions, JSON/CSV serialization, payroll `DataSet`, configuration (e.g. HTTP client, database connection).
- **Dependencies**: .NET (e.g. `Microsoft.AspNetCore.App`); no other PayrollEngine packages.
- **Consumers**: Domain.Model, Api.Model (backend); Client.Core, Serilog, Document; WebApp Shared.

### 4.2 payroll-engine-client-core (PayrollEngine.Client.Core)

- **Role**: Client-side foundation for talking to the Payroll Engine REST API and for exchange/import/export.
- **Contents**: HTTP client and configuration, API endpoint definitions, exchange format (import/export), OData query builders, service clients, model classes, script templates (embedded).
- **Dependencies**: **PayrollEngine.Core** (NuGet).
- **Consumers**: Client.Scripting, and thus backend (Domain) and console (via Client.Services).

### 4.3 payroll-engine-client-scripting (PayrollEngine.Client.Scripting)

- **Role**: C# scripting runtime for payroll rules: base classes and types that regulation scripts use (case, collector, wage type, payrun, report functions, etc.).
- **Contents**: Embedded script templates and function base classes; used by the backend’s Roslyn compiler to build the script environment.
- **Dependencies**: **PayrollEngine.Client.Core**, Microsoft.CodeAnalysis.CSharp.
- **Consumers**: Backend (Domain.Model, Domain.Scripting); Client.Services (and thus console).

### 4.4 payroll-engine-client-services (PayrollEngine.Client.Services)

- **Role**: High-level client API and scripting helpers (e.g. week pay cycle).
- **Dependencies**: **PayrollEngine.Client.Scripting**, **PayrollEngine.Client.Test**, Microsoft.Extensions.Configuration.Json.
- **Consumers**: Console (PayrollConsole + Commands), and any app that uses the client as an SDK.

### 4.5 payroll-engine-client-test (PayrollEngine.Client.Test)

- **Role**: Test utilities and helpers for payroll tests.
- **Consumers**: Client.Services (and thus console and tests that use the client).

### 4.6 payroll-engine-serilog (PayrollEngine.Serilog)

- **Role**: Serilog integration for Payroll Engine (sinks, configuration).
- **Dependencies**: **PayrollEngine.Core**, Serilog packages.
- **Consumers**: Backend.Server, Console, WebApp.Server.

### 4.7 payroll-engine-document (PayrollEngine.Document)

- **Role**: Document/report generation (e.g. Excel, PDF).
- **Dependencies**: **PayrollEngine.Core**, FastReport.OpenSource, NPOI.
- **Consumers**: Console (Commands), WebApp (Server, Presentation).

### 4.8 payroll-engine-jsonSchemaBuilder

- **Role**: Tool to build JSON schemas (e.g. from Client.Core for exchange format). Referenced from client-core build when schema output is produced.

### 4.9 payroll-engine-dbQueryTool

- **Role**: Database query tooling (separate from the main backend persistence layer).

---

## 5. Package Dependency Graph (Conceptual)

```
PayrollEngine.Core
  ├── PayrollEngine.Client.Core
  │     └── PayrollEngine.Client.Scripting
  │           └── PayrollEngine.Client.Services
  │                 └── PayrollEngine.Client.Test
  ├── PayrollEngine.Serilog
  └── PayrollEngine.Document
```

Backend uses: **Core**, **Client.Scripting** (and thus Client.Core), **Serilog**.  
Console uses: **Client.Services** (and thus Client.Scripting, Client.Core, Client.Test), **Document**, **Serilog**.  
WebApp uses: **Core**, **Serilog**, **Document**.

---

## 6. Applications Summary

| Application | Repo | Main NuGet Dependencies |
|-------------|------|--------------------------|
| Backend API | payroll-engine-backend | PayrollEngine.Core, PayrollEngine.Client.Scripting, PayrollEngine.Serilog |
| Console CLI | payroll-engine-console | PayrollEngine.Client.Services, PayrollEngine.Serilog, PayrollEngine.Document (Commands) |
| Web App | payroll-engine-webapp | PayrollEngine.Core, PayrollEngine.Serilog, PayrollEngine.Document |
| Admin App | payroll-engine-adminApp | (consumes webapp or shared packages as needed) |

---

## 7. Workspace Layout (Relevant Folders)

- **Backend / API / Host**: payroll-engine-backend  
- **CLI**: payroll-engine-console  
- **Web / Admin**: payroll-engine-webapp, payroll-engine-adminApp  
- **Shared libraries (as NuGet, source in workspace)**: payroll-engine-core, payroll-engine-client-core, payroll-engine-client-scripting, payroll-engine-client-services, payroll-engine-client-test, payroll-engine-serilog, payroll-engine-document  
- **Tooling**: payroll-engine-jsonSchemaBuilder, payroll-engine-dbQueryTool  
- **Orchestration / examples / tests**: payroll-engine  
- **Regulations / templates**: payroll-engine-template, payroll-engine-regulations-INDIA, payroll-engine-regulations-swiss, payroll-engine-regulation-France, payroll-engine-rules  

---

## 8. Build and Consumption Note

The backend and console are built against **published NuGet packages** (e.g. 0.9.0-beta.14), not against project references to the other payroll-engine-xx repos. To use local changes from payroll-engine-core or payroll-engine-client-scripting, you would typically:

1. Build and pack the desired payroll-engine-xx projects.
2. Publish to a local NuGet feed or copy packages to a local folder.
3. Point the backend/console to that feed/folder and restore.

The workspace groups all these repos for convenience; the actual compile-time dependency is NuGet.

---

*Document derived from workspace structure, solution files, and .csproj references in payroll-engine-backend, payroll-engine-console, and the payroll-engine-xx repositories.*
