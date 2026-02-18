# Required Repositories for Self-Contained Payroll Engine Setup

This document identifies all repositories from the [Payroll-Engine GitHub organization](https://github.com/orgs/Payroll-Engine/repositories) that must be cloned to have a complete, self-contained setup that can run independently even if the original repositories are deleted.

---

## Critical Repositories (MUST HAVE)

These repositories contain the core libraries and applications required to run the payroll engine.

### 1. Core Libraries (NuGet Package Sources)

#### ✅ **PayrollEngine.Core**
- **Repository**: `PayrollEngine.Core`
- **Purpose**: Base library with core domain models, interfaces, and utilities
- **Used By**: 
  - Backend (Domain.Model)
  - WebApp (Shared)
  - Client.Core
  - All other PayrollEngine packages
- **Critical**: ⭐⭐⭐⭐⭐ (Foundation library - everything depends on it)

#### ✅ **PayrollEngine.Client.Core**
- **Repository**: `PayrollEngine.Client.Core`
- **Purpose**: Client-side core library with API models, exchange format, query builders
- **Used By**:
  - Backend (via Client.Scripting)
  - WebApp (Core)
  - Console (via Client.Services)
- **Critical**: ⭐⭐⭐⭐⭐ (Required for API communication)

#### ✅ **PayrollEngine.Client.Scripting**
- **Repository**: `PayrollEngine.Client.Scripting`
- **Purpose**: Scripting library with embedded C# script templates and function base classes
- **Used By**:
  - Backend (Domain.Scripting) - **CRITICAL for script compilation**
- **Critical**: ⭐⭐⭐⭐⭐ (Required for dynamic script execution)

#### ✅ **PayrollEngine.Client.Services**
- **Repository**: `PayrollEngine.Client.Services`
- **Purpose**: Client services library for API communication
- **Used By**:
  - Console (main application)
  - Console Commands
  - Console DSL
  - Regulation scripts
- **Critical**: ⭐⭐⭐⭐ (Required for Console and client applications)

#### ✅ **PayrollEngine.Serilog**
- **Repository**: `PayrollEngine.Serilog`
- **Purpose**: Serilog logging integration
- **Used By**:
  - Backend Server
  - Console
  - WebApp Server
- **Critical**: ⭐⭐⭐⭐ (Required for logging)

#### ✅ **PayrollEngine.Document**
- **Repository**: `PayrollEngine.Document`
- **Purpose**: Document generation and reporting library
- **Used By**:
  - Console Commands
  - WebApp (Presentation, Server)
- **Critical**: ⭐⭐⭐ (Required for reports and document generation)

---

### 2. Applications

#### ✅ **PayrollEngine.Backend**
- **Repository**: `PayrollEngine.Backend`
- **Purpose**: Backend REST API server
- **Dependencies**:
  - PayrollEngine.Core
  - PayrollEngine.Client.Scripting
  - PayrollEngine.Serilog
- **Critical**: ⭐⭐⭐⭐⭐ (Main backend server - required for API)

#### ✅ **PayrollEngine.PayrollConsole**
- **Repository**: `PayrollEngine.PayrollConsole`
- **Purpose**: Console CLI application for payroll operations
- **Dependencies**:
  - PayrollEngine.Client.Services
  - PayrollEngine.Serilog
  - PayrollEngine.Document
- **Critical**: ⭐⭐⭐⭐ (Required for CLI operations, DSL conversion, data import/export)

#### ✅ **PayrollEngine.WebApp**
- **Repository**: `PayrollEngine.WebApp`
- **Purpose**: Web application (Blazor/MudBlazor UI)
- **Dependencies**:
  - PayrollEngine.Core
  - PayrollEngine.Client.Core
  - PayrollEngine.Serilog
  - PayrollEngine.Document
- **Critical**: ⭐⭐⭐ (Optional - web UI, but useful for administration)

---

## Supporting Repositories (RECOMMENDED)

These repositories provide additional tooling and utilities.

### ✅ **PayrollEngine.JsonSchemaBuilder**
- **Repository**: `PayrollEngine.JsonSchemaBuilder`
- **Purpose**: JSON schema generation tool
- **Used By**: Build process for generating exchange schemas
- **Critical**: ⭐⭐ (Useful for schema generation, but not required for runtime)

### ✅ **PayrollEngine.SqlServer.DbQuery**
- **Repository**: `PayrollEngine.SqlServer.DbQuery`
- **Purpose**: SQL Server query tool
- **Critical**: ⭐⭐ (Useful for database queries, but not required for runtime)

### ⚠️ **PayrollEngine.AdminApp**
- **Repository**: `PayrollEngine.AdminApp`
- **Purpose**: Administration tool
- **Critical**: ⭐ (Optional - may not be actively maintained)

---

## Not Required (Can Skip)

These repositories are not needed for running the payroll engine:

### ❌ **.github**
- **Repository**: `.github`
- **Reason**: Organization configuration only (workflows, templates)
- **Action**: Skip

### ❌ **PayrollEngine.Client.Tutorials**
- **Repository**: `PayrollEngine.Client.Tutorials`
- **Reason**: Tutorials and examples only
- **Action**: Skip (unless you want documentation)

### ❌ **PayrollEngine.Client.Test**
- **Repository**: `PayrollEngine.Client.Test`
- **Reason**: Test library only
- **Action**: Skip (unless you want to run tests)

### ❓ **PayrollEngine** (Main Framework)
- **Repository**: `PayrollEngine`
- **Status**: **NEEDS VERIFICATION**
- **Note**: This appears to be the main framework repository. Check if it contains:
  - Documentation
  - Setup scripts
  - Main README
  - Build configurations
- **Recommendation**: **Clone it** - likely contains important setup information

---

## Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                    PayrollEngine.Core                        │
│                  (Foundation Library)                        │
└────────────┬────────────────────────────────────────────────┘
             │
             ├─────────────────────────────────────┐
             │                                     │
             ▼                                     ▼
┌──────────────────────────┐         ┌──────────────────────────┐
│ PayrollEngine.Client.   │         │ PayrollEngine.Client.    │
│        Core              │         │      Scripting           │
└──────────┬───────────────┘         └──────────┬───────────────┘
           │                                    │
           │                                    │
           ▼                                    ▼
┌──────────────────────────┐         ┌──────────────────────────┐
│ PayrollEngine.Client.    │         │ PayrollEngine.Backend    │
│      Services            │         │    (Backend Server)      │
└──────────┬───────────────┘         └─────────────────────────┘
           │
           │
           ▼
┌──────────────────────────┐
│ PayrollEngine.Payroll    │
│      Console             │
└──────────────────────────┘

┌──────────────────────────┐
│ PayrollEngine.Serilog    │
│  (Used by all apps)      │
└──────────────────────────┘

┌──────────────────────────┐
│ PayrollEngine.Document   │
│  (Used by Console/WebApp)│
└──────────────────────────┘
```

---

## Clone Script

Here's a script to clone all required repositories:

```bash
#!/bin/bash

# Set your GitHub username/organization
GITHUB_ORG="Payroll-Engine"
BASE_DIR="payroll-engine-repos"

mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

# Core Libraries (CRITICAL)
echo "Cloning Core Libraries..."
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.Core.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.Client.Core.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.Client.Scripting.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.Client.Services.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.Serilog.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.Document.git"

# Applications (CRITICAL)
echo "Cloning Applications..."
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.Backend.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.PayrollConsole.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.WebApp.git"

# Supporting Tools (RECOMMENDED)
echo "Cloning Supporting Tools..."
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.JsonSchemaBuilder.git"
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.SqlServer.DbQuery.git"

# Main Framework (VERIFY FIRST)
echo "Cloning Main Framework..."
git clone "https://github.com/${GITHUB_ORG}/PayrollEngine.git"

echo "✅ All repositories cloned!"
```

---

## Build Order

After cloning, build in this order:

1. **PayrollEngine.Core** (foundation)
2. **PayrollEngine.Client.Core** (depends on Core)
3. **PayrollEngine.Client.Scripting** (depends on Client.Core)
4. **PayrollEngine.Client.Services** (depends on Client.Core)
5. **PayrollEngine.Serilog** (standalone)
6. **PayrollEngine.Document** (standalone)
7. **PayrollEngine.Backend** (depends on Core, Client.Scripting, Serilog)
8. **PayrollEngine.PayrollConsole** (depends on Client.Services, Serilog, Document)
9. **PayrollEngine.WebApp** (depends on Core, Client.Core, Serilog, Document)

---

## NuGet Package Configuration

**Important**: The projects reference PayrollEngine packages as **NuGet packages** (e.g., `Version="0.9.0-beta.12"`). To make this self-contained, you have two options:

### Option 1: Use Project References (Recommended for Development)

Modify `.csproj` files to use `<ProjectReference>` instead of `<PackageReference>`:

```xml
<!-- Change from: -->
<PackageReference Include="PayrollEngine.Core" Version="0.9.0-beta.12" />

<!-- To: -->
<ProjectReference Include="../../PayrollEngine.Core/Path/To/PayrollEngine.Core.csproj" />
```

### Option 2: Build and Publish to Local NuGet Feed

1. Build each library project
2. Pack as NuGet packages: `dotnet pack`
3. Create a local NuGet feed (folder or NuGet.Server)
4. Configure `nuget.config` to use local feed

---

## Minimum Required Set (If Space is Limited)

If you must minimize, the **absolute minimum** is:

1. ✅ **PayrollEngine.Core**
2. ✅ **PayrollEngine.Client.Core**
3. ✅ **PayrollEngine.Client.Scripting**
4. ✅ **PayrollEngine.Client.Services**
5. ✅ **PayrollEngine.Serilog**
6. ✅ **PayrollEngine.Backend**
7. ✅ **PayrollEngine.PayrollConsole**

**Total: 7 repositories** (can skip WebApp, Document, and tools if not needed)

---

## Verification Checklist

After cloning, verify you have:

- [ ] All 6 core library repositories
- [ ] Backend repository
- [ ] Console repository
- [ ] WebApp repository (optional)
- [ ] Supporting tools (optional)
- [ ] All repositories build successfully
- [ ] Project references or NuGet feed configured
- [ ] Database connection strings configured
- [ ] Application settings configured

---

## Summary

### Must Clone (9 repositories):
1. PayrollEngine.Core
2. PayrollEngine.Client.Core
3. PayrollEngine.Client.Scripting
4. PayrollEngine.Client.Services
5. PayrollEngine.Serilog
6. PayrollEngine.Document
7. PayrollEngine.Backend
8. PayrollEngine.PayrollConsole
9. PayrollEngine.WebApp

### Recommended (3 repositories):
10. PayrollEngine.JsonSchemaBuilder
11. PayrollEngine.SqlServer.DbQuery
12. PayrollEngine (main framework - verify contents)

### Skip (3 repositories):
- .github
- PayrollEngine.Client.Tutorials
- PayrollEngine.Client.Test

**Total: 12 repositories to clone for complete self-contained setup**

---

*Document Generated: 2025-01-05*  
*Based on analysis of Payroll-Engine GitHub organization repositories*

