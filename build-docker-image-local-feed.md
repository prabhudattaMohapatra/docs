# Building Docker Image for Payroll Engine Backend Using Local NuGet Feed

Complete guide to build the Docker image for `payroll-engine-backend` with all PayrollEngine packages sourced from a local NuGet feed.

---

## Prerequisites

1. **Docker** installed and running:
   ```bash
   docker --version
   docker info
   ```

2. **Local NuGet Feed** set up and configured:
   - Location: `~/local-nuget-feed`
   - Contains all required PayrollEngine packages (see [Required Packages](#required-packages))

3. **Required PayrollEngine Packages** published to local feed:
   - `PayrollEngine.Core` (version 0.9.0-beta.13)
   - `PayrollEngine.Client.Core` (version 0.9.0-beta.13)
   - `PayrollEngine.Client.Scripting` (version 0.9.0-beta.13)
   - `PayrollEngine.Serilog` (version 0.9.0-beta.13)

4. **Build Script** available at `shellScripts/build.sh`

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

### Third-Party Packages (from nuget.org)

These packages will be automatically restored from nuget.org during Docker build:

- `Microsoft.AspNetCore.OpenApi` (10.0.1)
- `Serilog.AspNetCore` (10.0.0)
- `Serilog.Settings.Configuration` (10.0.0)
- `Serilog.Sinks.*` (various versions)
- `Swashbuckle.AspNetCore` (10.1.0)
- `AWSSDK.SecretsManager` (4.0.1.2)
- `Microsoft.CodeAnalysis.Compilers` (5.0.0)
- `Microsoft.Data.SqlClient` (6.1.3)
- `System.Data.SqlClient` (4.9.0)
- `Asp.Versioning.Mvc` (8.1.1)
- And other Microsoft/third-party dependencies

---

## Dockerfile Overview

The Dockerfile uses a **multi-stage build** process:

1. **Build Stage**: Compiles the application using .NET SDK
2. **Runtime Stage**: Creates a minimal runtime image using .NET ASP.NET runtime

### Key Dockerfile Features for Local Feed

The Dockerfile is configured to:
- Copy local NuGet feed into the build container (`COPY packages/ /packages/`)
- Configure NuGet sources inside the container (local feed first, then nuget.org)
- Restore packages from local feed for PayrollEngine packages
- Restore third-party packages from nuget.org

---

## Build Steps

### Step 1: Navigate to Backend Directory

```bash
cd /path/to/payroll-engine-backend
```

### Step 2: Verify Local NuGet Feed

Ensure your local feed contains the required packages:

```bash
# List all packages in local feed
ls -la ~/local-nuget-feed/*.nupkg

# Verify specific packages exist
ls -la ~/local-nuget-feed/PayrollEngine.*.nupkg
```

Expected packages:
- `PayrollEngine.Core.0.9.0-beta.13.nupkg`
- `PayrollEngine.Client.Core.0.9.0-beta.13.nupkg`
- `PayrollEngine.Client.Scripting.0.9.0-beta.13.nupkg`
- `PayrollEngine.Serilog.0.9.0-beta.13.nupkg`

### Step 3: Build Using Build Script (Recommended)

The `shellScripts/build.sh` script automates the entire process:

```bash
# Make script executable (if needed)
chmod +x shellScripts/build.sh

# Run build script
sh shellScripts/build.sh
```

**What the script does**:
1. Copies local NuGet feed to `./packages/` directory
2. Builds Docker image with `--platform linux/amd64`
3. Tags image as `payroll-engine/backend:latest`
4. Cleans up temporary `./packages/` directory

### Step 4: Build Using Docker Directly (Alternative)

If you prefer to build manually:

```bash
# Copy local NuGet feed to build context
cp -r ~/local-nuget-feed ./packages

# Build Docker image
docker build --platform linux/amd64 -t payroll-engine/backend:latest .

# Cleanup
rm -rf ./packages
```

---

## Dockerfile Configuration Details

### Build Stage Configuration

The Dockerfile configures NuGet sources inside the container:

```dockerfile
# Copy local NuGet feed into container
COPY packages/ /packages/

# Configure NuGet sources (local feed first, then nuget.org)
RUN dotnet nuget remove source nuget.org 2>/dev/null || true
RUN dotnet nuget add source /packages --name local-feed
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Verify sources are configured correctly
RUN dotnet nuget list source

# Restore with architecture-specific runtime using local feed
RUN if [ "$TARGETARCH" = "arm64" ]; then \
      dotnet restore "PayrollEngine.Backend.sln" \
        --source /packages \
        --source https://api.nuget.org/v3/index.json \
        --runtime linux-arm64; \
    else \
      dotnet restore "PayrollEngine.Backend.sln" \
        --source /packages \
        --source https://api.nuget.org/v3/index.json \
        --runtime linux-x64; \
    fi
```

**Important Notes**:
- `/packages` is used as the first source (checked first)
- `https://api.nuget.org/v3/index.json` is used as the second source (for third-party packages)
- Absolute paths are used to avoid path resolution issues

### Multi-Stage Build Process

1. **First Restore** (before copying source files):
   - Restores packages based on `.csproj` files
   - Leverages Docker layer caching (if only source changes, packages don't re-download)

2. **Copy Source Files**:
   - Copies all source code into container

3. **Second Restore** (after copying source files):
   - Ensures `project.assets.json` files are correctly generated with runtime targets
   - Required for `dotnet publish` to work correctly

4. **Publish**:
   - Publishes application to `/app/publish`
   - Uses `--no-restore` flag (restore already completed)

5. **Runtime Stage**:
   - Copies published output to minimal runtime image
   - Installs system dependencies (libgdiplus, fonts, etc.)

---

## Verification

### Verify Local Feed Usage During Build

#### Method 1: Check Build Logs

```bash
# Build and capture logs
docker build --platform linux/amd64 -t payroll-engine/backend:latest . 2>&1 | tee build.log

# Search for /packages references
grep -i "/packages" build.log | head -10

# Search for PayrollEngine package installations
grep -iE "(installed|restoring).*payrollengine" build.log | head -10
```

#### Method 2: Inspect Build Stage (Most Reliable)

```bash
# Build only the build stage
docker build --platform linux/amd64 --target build -t payroll-engine/backend:build-stage .

# Check package metadata in build stage
docker run --rm --entrypoint sh payroll-engine/backend:build-stage -c "
echo '=== Verifying Local Feed Usage ==='
for pkg in payrollengine.core payrollengine.client.core payrollengine.client.scripting payrollengine.serilog; do
  if [ -f /root/.nuget/packages/\$pkg/0.9.0-beta.13/.nupkg.metadata ]; then
    SOURCE=\$(cat /root/.nuget/packages/\$pkg/0.9.0-beta.13/.nupkg.metadata 2>/dev/null | grep -o '\"source\": \"[^\"]*\"' | cut -d'\"' -f4)
    PKG_NAME=\$(echo \$pkg | sed 's/payrollengine\./PayrollEngine./' | sed 's/\.\(.\)/\U\1/g')
    if [ \"\$SOURCE\" = \"/packages\" ]; then
      echo \"✅ \$PKG_NAME: Local feed confirmed (\$SOURCE)\"
    else
      echo \"⚠️  \$PKG_NAME: Source is \$SOURCE (expected /packages)\"
    fi
  else
    echo \"❌ \$pkg: Package metadata not found\"
  fi
done
"
```

**Expected Output**:
```
=== Verifying Local Feed Usage ===
✅ PayrollEngineCore: Local feed confirmed (/packages)
✅ PayrollEngineClientCore: Local feed confirmed (/packages)
✅ PayrollEngineClientScripting: Local feed confirmed (/packages)
✅ PayrollEngineSerilog: Local feed confirmed (/packages)
```

#### Method 3: Check NuGet Sources in Container

```bash
# Build build stage
docker build --platform linux/amd64 --target build -t payroll-engine/backend:build-stage .

# Check sources inside container
docker run --rm --entrypoint sh payroll-engine/backend:build-stage -c "dotnet nuget list source"
```

Should show `/packages` as a source.

### Verify Final Image

```bash
# List built images
docker images | grep payroll-engine/backend

# Test run (optional - requires database connection)
docker run --rm payroll-engine/backend:latest --help
```

---

## Complete Build Command Sequence

Here's a complete sequence you can run:

```bash
# Navigate to backend directory
cd /path/to/payroll-engine-backend

# Verify local feed has required packages
ls -la ~/local-nuget-feed/PayrollEngine.*.nupkg

# Build using script (recommended)
sh shellScripts/build.sh

# OR build manually
cp -r ~/local-nuget-feed ./packages
docker build --platform linux/amd64 -t payroll-engine/backend:latest .
rm -rf ./packages

# Verify build stage (optional)
docker build --platform linux/amd64 --target build -t payroll-engine/backend:build-stage .
docker run --rm --entrypoint sh payroll-engine/backend:build-stage -c "
for pkg in payrollengine.core payrollengine.client.core payrollengine.client.scripting payrollengine.serilog; do
  SOURCE=\$(cat /root/.nuget/packages/\$pkg/0.9.0-beta.13/.nupkg.metadata 2>/dev/null | grep -o '\"source\": \"[^\"]*\"' | cut -d'\"' -f4)
  echo \"\$pkg: \$SOURCE\"
done
"
```

---

## Troubleshooting

### Issue: `packages/` Directory Not Found During Build

**Error**: `ERROR: failed to calculate checksum of ref ... "/packages": not found`

**Solution**:
- Ensure `shellScripts/build.sh` copies local feed before `docker build`
- Or manually copy: `cp -r ~/local-nuget-feed ./packages` before building

### Issue: Packages Not Found in Local Feed

**Error**: `error NU1101: Unable to find package PayrollEngine.Core. No packages exist with this id in source(s): local-feed`

**Solution**:
1. Verify packages are in local feed:
   ```bash
   ls -la ~/local-nuget-feed/*.nupkg
   ```
2. Ensure `shellScripts/build.sh` copies feed correctly
3. Check package names match exactly (case-sensitive)

### Issue: NuGet Source Resolution Errors

**Error**: `error NU1301: The local source '/src/nuget-org' doesn't exist`

**Solution**:
- The Dockerfile uses absolute paths (`/packages` and `https://api.nuget.org/v3/index.json`)
- Ensure Dockerfile has the correct source configuration (see [Dockerfile Configuration Details](#dockerfile-configuration-details))

### Issue: Build Fails with Missing Runtime Target

**Error**: `error NETSDK1047: Assets file doesn't have a target for 'net10.0/linux-x64'`

**Solution**:
- The Dockerfile includes a second restore after copying source files
- This ensures `project.assets.json` files include runtime targets
- Verify Dockerfile has both restore steps (before and after `COPY . .`)

### Issue: Packages Coming from nuget.org Instead of Local Feed

**Solution**:
1. Verify build stage uses local feed:
   ```bash
   docker build --platform linux/amd64 --target build -t payroll-engine/backend:build-stage .
   docker run --rm --entrypoint sh payroll-engine/backend:build-stage -c "dotnet nuget list source"
   ```
2. Check package metadata (see [Verification - Method 2](#method-2-inspect-build-stage-most-reliable))
3. Ensure `/packages` is listed first in NuGet sources inside container

---

## Build Script Details

The `shellScripts/build.sh` script contains:

```bash
# Copy local NuGet feed to build context
cp -r ~/local-nuget-feed ./packages

# Build Docker image
docker build --platform linux/amd64 -t payroll-engine/backend:latest .

# Cleanup
rm -rf ./packages
```

**Why this approach?**
- Docker build context must include all files needed during build
- Local feed is copied into build context as `packages/`
- Dockerfile then copies `packages/` into container as `/packages/`
- After build, temporary `./packages/` is cleaned up

---

## Platform-Specific Builds

### Build for AMD64 (x86_64)

```bash
docker build --platform linux/amd64 -t payroll-engine/backend:latest .
```

### Build for ARM64

```bash
docker build --platform linux/arm64 -t payroll-engine/backend:latest .
```

The Dockerfile automatically handles platform-specific runtime identifiers:
- `linux-x64` for AMD64
- `linux-arm64` for ARM64

---

## Verification Checklist

- [ ] Docker installed and running
- [ ] Local NuGet feed contains all required PayrollEngine packages
- [ ] `shellScripts/build.sh` script is executable
- [ ] Docker build completes successfully
- [ ] Build stage verification shows packages from `/packages`
- [ ] Final image is created: `payroll-engine/backend:latest`
- [ ] Image size is reasonable (~1.2GB for full image)

---

## Next Steps

After successful Docker build:

1. **Test the image locally**:
   ```bash
   docker run -p 8080:8080 \
     -e DB_HOST=your-db-host \
     -e DB_SECRET_NAME=your-secret \
     payroll-engine/backend:latest
   ```

2. **Push to container registry**:
   ```bash
   docker tag payroll-engine/backend:latest your-registry/payroll-engine/backend:latest
   docker push your-registry/payroll-engine/backend:latest
   ```

3. **Deploy to ECS/ECS Fargate** (see deployment documentation)

---

## Related Documentation

- [Build with dotnet build and Local Feed](./build-with-dotnet-local-feed.md)
- [Setup Local NuGet Feed](../../payroll-engine-core/docs/setup-local-nuget-feed.md)
- [Build with Project References](./build-with-project-references.md)

