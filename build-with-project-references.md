# Build Flow: Project References (No Packages)

Complete build process for WebApp, Backend, and Console using project references instead of NuGet packages.

---

## Prerequisites

- .NET SDK 10.0 or later
- Docker installed
- All PayrollEngine repositories cloned in a common parent directory

---

## Directory Structure

```
payroll-engine-workspace/
├── PayrollEngine.Core/
├── PayrollEngine.Client.Core/
├── PayrollEngine.Client.Scripting/
├── PayrollEngine.Client.Services/
├── PayrollEngine.Serilog/
├── PayrollEngine.Document/
├── payroll-engine-backend/
├── payroll-engine-webapp/
└── payroll-engine-console/
```

---

## Step 1: Update Project References

### 1.1 Update Backend Project References

Replace `<PackageReference>` with `<ProjectReference>` in backend projects:

**File**: `payroll-engine-backend/Domain/Domain.Model/PayrollEngine.Domain.Model.csproj`

```xml
<!-- Remove: -->
<PackageReference Include="PayrollEngine.Core" Version="0.9.0-beta.13" />

<!-- Add: -->
<ProjectReference Include="../../../../PayrollEngine.Core/Core/PayrollEngine.Core.csproj" />
```

Repeat for all projects that reference PayrollEngine packages.

### 1.2 Update WebApp Project References

**File**: `payroll-engine-webapp/Server/PayrollEngine.WebApp.Server.csproj`

```xml
<!-- Replace PackageReference with ProjectReference -->
<ProjectReference Include="../../PayrollEngine.Client.Core/Client.Core/PayrollEngine.Client.Core.csproj" />
<ProjectReference Include="../../PayrollEngine.Client.Services/Client.Services/PayrollEngine.Client.Services.csproj" />
```

### 1.3 Update Console Project References

**File**: `payroll-engine-console/PayrollConsole/PayrollEngine.PayrollConsole.csproj`

```xml
<ProjectReference Include="../../PayrollEngine.Client.Services/Client.Services/PayrollEngine.Client.Services.csproj" />
<ProjectReference Include="../../PayrollEngine.Serilog/Serilog/PayrollEngine.Serilog.csproj" />
```

---

## Step 2: Build Docker Images with Project References

### 2.1 Build Backend Docker Image

```bash
cd payroll-engine-backend

# Create unified Dockerfile
cat > Dockerfile.projectref << 'EOF'
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy all PayrollEngine source repositories
COPY PayrollEngine.Core/ ./PayrollEngine.Core/
COPY PayrollEngine.Client.Core/ ./PayrollEngine.Client.Core/
COPY PayrollEngine.Client.Scripting/ ./PayrollEngine.Client.Scripting/
COPY PayrollEngine.Client.Services/ ./PayrollEngine.Client.Services/
COPY PayrollEngine.Serilog/ ./PayrollEngine.Serilog/
COPY PayrollEngine.Document/ ./PayrollEngine.Document/
COPY payroll-engine-backend/ ./payroll-engine-backend/

# Add nuget.org for third-party packages
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Build backend (project references will resolve automatically)
WORKDIR /src/payroll-engine-backend
RUN dotnet restore PayrollEngine.Backend.sln --source nuget-org
RUN dotnet build PayrollEngine.Backend.sln -c Release --no-restore

WORKDIR /src/payroll-engine-backend/Backend.Server
RUN dotnet publish PayrollEngine.Backend.Server.csproj -c Release -o /app/publish --no-restore

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app

RUN apt-get update && apt-get install -y \
    libgdiplus \
    fonts-liberation \
    fonts-dejavu-core \
    fonts-freefont-ttf \
    fontconfig \
    libc6-dev \
    libx11-dev \
    libxext-dev \
    libxrender-dev \
    libxtst-dev \
    libxrandr-dev \
    libasound2-dev \
    libcairo2-dev \
    libpango1.0-dev \
    libatk1.0-dev \
    libgtk-3-dev \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/publish .
COPY --from=build /src/payroll-engine-backend/Database ./Database

ENV DISPLAY=:99
ENV FONTCONFIG_PATH=/etc/fonts

ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
EOF

# Build from parent directory
cd ..
docker build -f payroll-engine-backend/Dockerfile.projectref -t payroll-engine-backend:latest .
```

### 2.2 Build WebApp Docker Image

```bash
# Create unified Dockerfile
cat > payroll-engine-webapp/Dockerfile.projectref << 'EOF'
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy all PayrollEngine source repositories
COPY PayrollEngine.Core/ ./PayrollEngine.Core/
COPY PayrollEngine.Client.Core/ ./PayrollEngine.Client.Core/
COPY PayrollEngine.Client.Scripting/ ./PayrollEngine.Client.Scripting/
COPY PayrollEngine.Client.Services/ ./PayrollEngine.Client.Services/
COPY PayrollEngine.Serilog/ ./PayrollEngine.Serilog/
COPY PayrollEngine.Document/ ./PayrollEngine.Document/
COPY payroll-engine-webapp/ ./payroll-engine-webapp/

# Add nuget.org for third-party packages
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Build webapp
WORKDIR /src/payroll-engine-webapp
RUN dotnet restore PayrollEngine.WebApp.sln --source nuget-org
RUN dotnet build PayrollEngine.WebApp.sln -c Release --no-restore

WORKDIR /src/payroll-engine-webapp/Server
RUN dotnet publish PayrollEngine.WebApp.Server.csproj -c Release -o /app/publish --no-restore

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "PayrollEngine.WebApp.Server.dll"]
EOF

# Build from parent directory
docker build -f payroll-engine-webapp/Dockerfile.projectref -t payroll-engine-webapp:latest .
```

### 2.3 Build Console Docker Image

```bash
# Create unified Dockerfile
cat > payroll-engine-console/Dockerfile.projectref << 'EOF'
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy all PayrollEngine source repositories
COPY PayrollEngine.Core/ ./PayrollEngine.Core/
COPY PayrollEngine.Client.Core/ ./PayrollEngine.Client.Core/
COPY PayrollEngine.Client.Scripting/ ./PayrollEngine.Client.Scripting/
COPY PayrollEngine.Client.Services/ ./PayrollEngine.Client.Services/
COPY PayrollEngine.Serilog/ ./PayrollEngine.Serilog/
COPY PayrollEngine.Document/ ./PayrollEngine.Document/
COPY payroll-engine-console/ ./payroll-engine-console/

# Add nuget.org for third-party packages
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Build console
WORKDIR /src/payroll-engine-console
RUN dotnet restore PayrollEngine.PayrollConsole.sln --source nuget-org
RUN dotnet build PayrollEngine.PayrollConsole.sln -c Release --no-restore

WORKDIR /src/payroll-engine-console/PayrollConsole
RUN dotnet publish PayrollEngine.PayrollConsole.csproj -c Release -o /app/publish --no-restore

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
COPY --from=build /src/payroll-engine-console/consoleScripts/ /app/scripts/
ENTRYPOINT ["dotnet", "PayrollEngine.PayrollConsole.dll"]
EOF

# Build from parent directory
docker build -f payroll-engine-console/Dockerfile.projectref -t payroll-engine-console:latest .
```

---

## Step 3: Alternative - Build Script Approach

Create a build script that creates a unified build context:

```bash
#!/bin/bash
# build-with-projectrefs.sh

BUILD_DIR="docker-build-context"
rm -rf $BUILD_DIR
mkdir -p $BUILD_DIR

# Copy all repositories
echo "Copying repositories..."
cp -r PayrollEngine.Core $BUILD_DIR/
cp -r PayrollEngine.Client.Core $BUILD_DIR/
cp -r PayrollEngine.Client.Scripting $BUILD_DIR/
cp -r PayrollEngine.Client.Services $BUILD_DIR/
cp -r PayrollEngine.Serilog $BUILD_DIR/
cp -r PayrollEngine.Document $BUILD_DIR/
cp -r payroll-engine-backend $BUILD_DIR/
cp -r payroll-engine-webapp $BUILD_DIR/
cp -r payroll-engine-console $BUILD_DIR/

# Create .dockerignore
cat > $BUILD_DIR/.dockerignore << EOF
**/bin/
**/obj/
**/.git/
**/node_modules/
**/.vs/
**/.idea/
**/*.user
**/*.suo
EOF

# Build backend
echo "Building backend..."
docker build -f $BUILD_DIR/payroll-engine-backend/Dockerfile.projectref \
    -t payroll-engine-backend:latest \
    $BUILD_DIR

# Build webapp
echo "Building webapp..."
docker build -f $BUILD_DIR/payroll-engine-webapp/Dockerfile.projectref \
    -t payroll-engine-webapp:latest \
    $BUILD_DIR

# Build console
echo "Building console..."
docker build -f $BUILD_DIR/payroll-engine-console/Dockerfile.projectref \
    -t payroll-engine-console:latest \
    $BUILD_DIR

# Cleanup
rm -rf $BUILD_DIR

echo "Build complete!"
```

---

## Step 4: Verify Images

```bash
# List built images
docker images | grep payroll-engine

# Test backend
docker run -p 8080:8080 payroll-engine-backend:latest

# Test webapp
docker run -p 8081:8080 payroll-engine-webapp:latest

# Test console
docker run payroll-engine-console:latest --help
```

---

## Advantages

- ✅ No NuGet feed required for PayrollEngine packages
- ✅ Always uses latest source code
- ✅ Faster iteration during development
- ✅ No package version management

## Disadvantages

- ❌ Larger Docker build context
- ❌ Longer build times
- ❌ Requires all repos in one location
- ❌ Still needs nuget.org for third-party packages

---

## Notes

- Project references use relative paths - ensure directory structure matches
- Docker build context must include all referenced projects
- Use `.dockerignore` to reduce build context size
- Consider using BuildKit for better caching: `DOCKER_BUILDKIT=1 docker build ...`

