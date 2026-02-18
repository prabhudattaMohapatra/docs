# Building Payroll Engine Backend Using `dotnet build` with Local NuGet Feed

Complete guide to build the `payroll-engine-backend` using `dotnet build` with all PayrollEngine packages sourced from a local NuGet feed.

---

## Prerequisites

1. **.NET SDK 10.0** installed and verified:
   ```bash
   dotnet --version
   # Should output: 10.0.x or higher
   ```

2. **Local NuGet Feed** set up and configured (see [Setup Local NuGet Feed](../../payroll-engine-core/docs/setup-local-nuget-feed.md))

3. **Required PayrollEngine Packages** published to local feed:
   - `PayrollEngine.Core` (version 0.9.0-beta.13)
   - `PayrollEngine.Client.Core` (version 0.9.0-beta.13)
   - `PayrollEngine.Client.Scripting` (version 0.9.0-beta.13)
   - `PayrollEngine.Serilog` (version 0.9.0-beta.13)

4. **NuGet Sources** configured correctly:
   ```bash
   dotnet nuget list source
   ```
   
   Expected output (local-feed must be first):
   ```
   Registered Sources:
     1.  local-feed [Enabled]
         ~/local-nuget-feed
     2.  nuget-org [Enabled]
         https://api.nuget.org/v3/index.json
   ```

---

## Required Packages

### PayrollEngine Packages (from Local Feed)

These packages **must** be available in your local NuGet feed (`~/local-nuget-feed`):

| Package Name | Version | Used By |
|-------------|---------|---------|
| `PayrollEngine.Core` | 0.9.0-beta.13 | Domain.Model, Domain.Application |
| `PayrollEngine.Client.Core` | 0.9.0-beta.13 | Domain.Model (transitive) |
| `PayrollEngine.Client.Scripting` | 0.9.0-beta.13 | Domain.Model, Domain.Scripting |
| `PayrollEngine.Serilog` | 0.9.0-beta.13 | Backend.Server |

**Note**: `PayrollEngine.Document` is not required for the backend build.

### Third-Party Packages (from nuget.org)

These packages will be automatically restored from nuget.org:

| Package Name | Version | Purpose |
|-------------|---------|---------|
| `Microsoft.AspNetCore.OpenApi` | 10.0.1 | OpenAPI support |
| `Serilog.AspNetCore` | 10.0.0 | ASP.NET Core logging |
| `Serilog.Settings.Configuration` | 10.0.0 | Configuration-based logging |
| `Serilog.Sinks.Async` | 2.1.0 | Async logging sinks |
| `Serilog.Sinks.Console` | 6.1.1 | Console logging |
| `Serilog.Sinks.File` | 7.0.0 | File logging |
| `Serilog.Sinks.MSSqlServer` | 9.0.2 | SQL Server logging |
| `Serilog.Sinks.PeriodicBatching` | 5.0.0 | Periodic batching |
| `Swashbuckle.AspNetCore` | 10.1.0 | Swagger/OpenAPI documentation |
| `AWSSDK.SecretsManager` | 4.0.1.2 | AWS Secrets Manager integration |
| `Microsoft.CodeAnalysis.Compilers` | 5.0.0 | Roslyn compiler (for scripting) |
| `Microsoft.Data.SqlClient` | 6.1.3 | SQL Server data access |
| `System.Data.SqlClient` | 4.9.0 | Legacy SQL Server support |
| `Asp.Versioning.Mvc` | 8.1.1 | API versioning |
| `Microsoft.NETCore.Platforms` | 7.0.4 | .NET Core platform support |
| `System.Configuration.ConfigurationManager` | 10.0.1 | Configuration management |

---

## Build Steps

### Step 1: Navigate to Backend Directory

```bash
cd /path/to/payroll-engine-backend
```

### Step 2: Verify NuGet Sources

Ensure local-feed is listed first:

```bash
dotnet nuget list source
```

If sources are not in the correct order, fix them:

```bash
# Remove and re-add in correct order
dotnet nuget remove source nuget.org 2>/dev/null || true
dotnet nuget remove source local-feed 2>/dev/null || true

# Add local-feed FIRST
dotnet nuget add source ~/local-nuget-feed --name local-feed

# Add nuget.org SECOND
dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Verify
dotnet nuget list source
```

### Step 3: Clear NuGet Cache (Optional but Recommended)

To ensure fresh restore from local feed:

```bash
# Clear all NuGet caches
dotnet nuget locals all --clear

# Verify cache is cleared
dotnet nuget locals all --list
```

### Step 4: Restore Dependencies

Restore all project dependencies:

```bash
dotnet restore PayrollEngine.Backend.sln
```

**Expected behavior**:
- PayrollEngine packages are restored from `~/local-nuget-feed`
- Third-party packages are restored from `https://api.nuget.org/v3/index.json`

### Step 5: Verify Local Feed Usage

Verify that PayrollEngine packages came from the local feed:

```bash
# Check package metadata for PayrollEngine.Core
cat ~/.nuget/packages/payrollengine.core/0.9.0-beta.13/.nupkg.metadata | grep -o '"source": "[^"]*"'

# Should output: "source": "~/local-nuget-feed" or absolute path to local feed
```

**Quick verification for all PayrollEngine packages**:

```bash
for pkg in payrollengine.core payrollengine.client.core payrollengine.client.scripting payrollengine.serilog; do
  SOURCE=$(cat ~/.nuget/packages/$pkg/0.9.0-beta.13/.nupkg.metadata 2>/dev/null | grep -o '"source": "[^"]*"' | cut -d'"' -f4)
  if [ -n "$SOURCE" ]; then
    echo "✅ $pkg: $SOURCE"
  else
    echo "❌ $pkg: Not found"
  fi
done
```

### Step 6: Build the Solution

Build the entire solution:

```bash
dotnet build PayrollEngine.Backend.sln --configuration Release
```

**Alternative**: Build only the server project:

```bash
dotnet build Backend.Server/PayrollEngine.Backend.Server.csproj --configuration Release
```

### Step 7: Verify Build Output

Check that the build succeeded and output was generated:

```bash
# Check for compiled DLL
ls -lh Backend.Server/bin/Release/net10.0/PayrollEngine.Backend.Server.dll

# Verify build artifacts
find . -name "*.dll" -path "*/bin/Release/*" | head -10
```

---

## Complete Build Command Sequence

Here's a complete sequence you can run:

```bash
# Navigate to backend directory
cd /path/to/payroll-engine-backend

# Verify sources
dotnet nuget list source

# Clear cache (optional)
dotnet nuget locals all --clear

# Restore dependencies
dotnet restore PayrollEngine.Backend.sln

# Verify local feed usage
for pkg in payrollengine.core payrollengine.client.core payrollengine.client.scripting payrollengine.serilog; do
  SOURCE=$(cat ~/.nuget/packages/$pkg/0.9.0-beta.13/.nupkg.metadata 2>/dev/null | grep -o '"source": "[^"]*"' | cut -d'"' -f4)
  echo "$pkg: $SOURCE"
done

# Build solution
dotnet build PayrollEngine.Backend.sln --configuration Release

# Verify output
ls -lh Backend.Server/bin/Release/net10.0/PayrollEngine.Backend.Server.dll
```

---

## Troubleshooting

### Issue: Packages Not Found in Local Feed

**Error**: `error NU1101: Unable to find package PayrollEngine.Core. No packages exist with this id in source(s): local-feed`

**Solution**:
1. Verify packages are published to local feed:
   ```bash
   ls -la ~/local-nuget-feed/*.nupkg
   ```
2. Ensure package names match exactly (case-sensitive)
3. Check package version matches (0.9.0-beta.13)

### Issue: Packages Coming from nuget.org Instead of Local Feed

**Error**: Packages are restored but source shows nuget.org

**Solution**:
1. Ensure local-feed is listed **first** in NuGet sources:
   ```bash
   dotnet nuget list source
   ```
2. Clear NuGet cache:
   ```bash
   dotnet nuget locals all --clear
   ```
3. Remove and re-add sources in correct order (see Step 2)

### Issue: Build Fails with Missing Dependencies

**Error**: `error CS0246: The type or namespace name 'PayrollEngine' could not be found`

**Solution**:
1. Verify restore completed successfully:
   ```bash
   dotnet restore PayrollEngine.Backend.sln --verbosity detailed
   ```
2. Check that all PayrollEngine packages are in local feed
3. Ensure `Directory.Build.props` has correct version (0.9.0-beta.13)

### Issue: Third-Party Packages Not Found

**Error**: `error NU1101: Unable to find package Microsoft.AspNetCore.OpenApi`

**Solution**:
- Ensure nuget.org is configured as a source:
  ```bash
  dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org
  ```
- Verify internet connectivity (nuget.org must be accessible)

---

## Verification Checklist

- [ ] .NET SDK 10.0 installed
- [ ] Local NuGet feed configured at `~/local-nuget-feed`
- [ ] All required PayrollEngine packages published to local feed
- [ ] NuGet sources configured with local-feed first
- [ ] `dotnet restore` completes successfully
- [ ] PayrollEngine packages verified from local feed (via `.nupkg.metadata`)
- [ ] `dotnet build` completes successfully
- [ ] Build output DLL exists at `Backend.Server/bin/Release/net10.0/PayrollEngine.Backend.Server.dll`

---

## Next Steps

After successful build:

1. **Run the application locally**:
   ```bash
   dotnet run --project Backend.Server/PayrollEngine.Backend.Server.csproj
   ```

2. **Publish for deployment**:
   ```bash
   dotnet publish Backend.Server/PayrollEngine.Backend.Server.csproj \
     --configuration Release \
     --output ./publish \
     --runtime linux-x64 \
     --self-contained false
   ```

3. **Build Docker image** (see [Build Docker Image with Local Feed](./build-docker-image-local-feed.md))

---

## Related Documentation

- [Setup Local NuGet Feed](../../payroll-engine-core/docs/setup-local-nuget-feed.md)
- [Build Docker Image with Local Feed](./build-docker-image-local-feed.md)
- [Build with Project References](./build-with-project-references.md)

