# Build Flow: Local NuGet Feed to Docker Images

Complete build process for WebApp, Backend, and Console using a local NuGet feed.

---

## Prerequisites

- .NET SDK 10.0 or later
- Docker installed
- All PayrollEngine repositories cloned
- Local NuGet feed directory created

---

## Step 1: Setup Local NuGet Feed

```bash
# Create local feed directory
mkdir -p ~/local-nuget-feed

# Create nuget.config in workspace root
cat > ~/nuget.config << EOF
<?xml version="1.0" encoding="utf-8"?>
<configuration>
  <packageSources>
    <clear />
    <add key="local" value="~/local-nuget-feed" />
    <add key="nuget.org" value="https://api.nuget.org/v3/index.json" />
  </packageSources>
</configuration>
EOF
```

---

## Step 2: Build All Packages

Build packages in dependency order:

### 2.1 Build PayrollEngine.Core

```bash
cd PayrollEngine.Core
dotnet restore PayrollEngine.Core.sln
dotnet build PayrollEngine.Core.sln -c Release
dotnet pack PayrollEngine.Core.sln -c Release --no-build --output ./nupkg
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

### 2.2 Build PayrollEngine.Client.Core

```bash
cd PayrollEngine.Client.Core
dotnet restore PayrollEngine.Client.Core.sln
dotnet build PayrollEngine.Client.Core.sln -c Release
dotnet pack PayrollEngine.Client.Core.sln -c Release --no-build --output ./nupkg
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

### 2.3 Build PayrollEngine.Client.Scripting

```bash
cd PayrollEngine.Client.Scripting
dotnet restore PayrollEngine.Client.Scripting.sln
dotnet build PayrollEngine.Client.Scripting.sln -c Release
dotnet pack PayrollEngine.Client.Scripting.sln -c Release --no-build --output ./nupkg
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

### 2.4 Build PayrollEngine.Client.Services

```bash
cd PayrollEngine.Client.Services
dotnet restore PayrollEngine.Client.Services.sln
dotnet build PayrollEngine.Client.Services.sln -c Release
dotnet pack PayrollEngine.Client.Services.sln -c Release --no-build --output ./nupkg
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

### 2.5 Build PayrollEngine.Serilog

```bash
cd PayrollEngine.Serilog
dotnet restore PayrollEngine.Serilog.sln
dotnet build PayrollEngine.Serilog.sln -c Release
dotnet pack PayrollEngine.Serilog.sln -c Release --no-build --output ./nupkg
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

### 2.6 Build PayrollEngine.Document

```bash
cd PayrollEngine.Document
dotnet restore PayrollEngine.Document.sln
dotnet build PayrollEngine.Document.sln -c Release
dotnet pack PayrollEngine.Document.sln -c Release --no-build --output ./nupkg
cp ./nupkg/*.nupkg ~/local-nuget-feed/
```

---

## Step 3: Build Docker Images

### 3.1 Build Backend Docker Image

```bash
cd payroll-engine-backend

# Create Dockerfile with local feed support
cat > Dockerfile.local << 'EOF'
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy solution and project files
COPY ["PayrollEngine.Backend.sln", "./"]
COPY ["Api/Api.Controller/PayrollEngine.Api.Controller.csproj", "Api/Api.Controller/"]
COPY ["Api/Api.Core/PayrollEngine.Api.Core.csproj", "Api/Api.Core/"]
COPY ["Api/Api.Map/PayrollEngine.Api.Map.csproj", "Api/Api.Map/"]
COPY ["Api/Api.Model/PayrollEngine.Api.Model.csproj", "Api/Api.Model/"]
COPY ["Backend.Controller/PayrollEngine.Backend.Controller.csproj", "Backend.Controller/"]
COPY ["Backend.Server/PayrollEngine.Backend.Server.csproj", "Backend.Server/"]
COPY ["Domain/Domain.Application/PayrollEngine.Domain.Application.csproj", "Domain/Domain.Application/"]
COPY ["Domain/Domain.Model/PayrollEngine.Domain.Model.csproj", "Domain/Domain.Model/"]
COPY ["Domain/Domain.Model.Tests/PayrollEngine.Domain.Model.Tests.csproj", "Domain/Domain.Model.Tests/"]
COPY ["Domain/Domain.Scripting/PayrollEngine.Domain.Scripting.csproj", "Domain/Domain.Scripting/"]
COPY ["Persistence/Persistence/PayrollEngine.Persistence.csproj", "Persistence/Persistence/"]
COPY ["Persistence/Persistence.SqlServer/PayrollEngine.Persistence.SqlServer.csproj", "Persistence/Persistence.SqlServer/"]
COPY ["Directory.Build.props", "./"]

# Copy local NuGet feed
COPY --from=local-feed /packages /packages

# Configure NuGet sources
RUN dotnet nuget add source /packages --name local
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Restore
RUN dotnet restore "PayrollEngine.Backend.sln" --source local --source nuget-org

# Copy everything else
COPY . .
COPY ["Database/", "Database/"]

WORKDIR "/src/Backend.Server"

# Publish
RUN dotnet publish "PayrollEngine.Backend.Server.csproj" -c Release -o /app/publish --no-restore

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
COPY --from=build /src/Database ./Database

ENV DISPLAY=:99
ENV FONTCONFIG_PATH=/etc/fonts

ENTRYPOINT ["dotnet", "PayrollEngine.Backend.Server.dll"]
EOF

# Build with local feed
docker build \
    --build-arg BUILDKIT_INLINE_CACHE=1 \
    --build-context local-feed=~/local-nuget-feed \
    -f Dockerfile.local \
    -t payroll-engine-backend:latest .
```

### 3.2 Build WebApp Docker Image

```bash
cd payroll-engine-webapp

# Create Dockerfile with local feed support
cat > Dockerfile.local << 'EOF'
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy solution and project files
COPY ["PayrollEngine.WebApp.sln", "./"]
COPY ["Core/PayrollEngine.WebApp.Core.csproj", "Core/"]
COPY ["Presentation/PayrollEngine.WebApp.Presentation.csproj", "Presentation/"]
COPY ["Server/PayrollEngine.WebApp.Server.csproj", "Server/"]
COPY ["Shared/PayrollEngine.WebApp.Shared.csproj", "Shared/"]
COPY ["ViewModel/PayrollEngine.WebApp.ViewModel.csproj", "ViewModel/"]
COPY ["Directory.Build.props", "./"]

# Copy local NuGet feed
COPY --from=local-feed /packages /packages

# Configure NuGet sources
RUN dotnet nuget add source /packages --name local
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Restore
RUN dotnet restore "PayrollEngine.WebApp.sln" --source local --source nuget-org

# Copy everything else
COPY . .
WORKDIR "/src/Server"

# Publish
RUN dotnet publish "PayrollEngine.WebApp.Server.csproj" -c Release -o /app/publish --no-restore

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENTRYPOINT ["dotnet", "PayrollEngine.WebApp.Server.dll"]
EOF

# Build with local feed
docker build \
    --build-context local-feed=~/local-nuget-feed \
    -f Dockerfile.local \
    -t payroll-engine-webapp:latest .
```

### 3.3 Build Console Docker Image

```bash
cd payroll-engine-console

# Create Dockerfile with local feed support
cat > Dockerfile.local << 'EOF'
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy solution and project files
COPY ["PayrollEngine.PayrollConsole.sln", "./"]
COPY ["PayrollConsole/PayrollEngine.PayrollConsole.csproj", "PayrollConsole/"]
COPY ["Commands/PayrollEngine.PayrollConsole.Commands.csproj", "Commands/"]
COPY ["Directory.Build.props", "./"]

# Copy local NuGet feed
COPY --from=local-feed /packages /packages

# Configure NuGet sources
RUN dotnet nuget add source /packages --name local
RUN dotnet nuget add source https://api.nuget.org/v3/index.json --name nuget-org

# Restore
RUN dotnet restore "PayrollEngine.PayrollConsole.sln" --source local --source nuget-org

# Copy everything else
COPY . .
WORKDIR "/src/PayrollConsole"

# Publish
RUN dotnet publish "PayrollEngine.PayrollConsole.csproj" -c Release -o /app/publish --no-restore

# Runtime stage
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
COPY consoleScripts/ /app/scripts/
ENTRYPOINT ["dotnet", "PayrollEngine.PayrollConsole.dll"]
EOF

# Build with local feed
docker build \
    --build-context local-feed=~/local-nuget-feed \
    -f Dockerfile.local \
    -t payroll-engine-console:latest .
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

## Quick Build Script

```bash
#!/bin/bash
# build-all-local.sh

FEED_DIR=~/local-nuget-feed
mkdir -p $FEED_DIR

echo "Building PayrollEngine packages..."
for repo in PayrollEngine.Core PayrollEngine.Client.Core PayrollEngine.Client.Scripting \
            PayrollEngine.Client.Services PayrollEngine.Serilog PayrollEngine.Document; do
    echo "Building $repo..."
    cd $repo
    dotnet pack -c Release -o ./nupkg
    cp ./nupkg/*.nupkg $FEED_DIR/
    cd ..
done

echo "Building Docker images..."
docker build --build-context local-feed=$FEED_DIR -f payroll-engine-backend/Dockerfile.local -t payroll-engine-backend:latest payroll-engine-backend/
docker build --build-context local-feed=$FEED_DIR -f payroll-engine-webapp/Dockerfile.local -t payroll-engine-webapp:latest payroll-engine-webapp/
docker build --build-context local-feed=$FEED_DIR -f payroll-engine-console/Dockerfile.local -t payroll-engine-console:latest payroll-engine-console/

echo "Build complete!"
```

---

## Notes

- Local feed must be rebuilt when package versions change
- Docker build context includes the feed directory
- Third-party packages still come from nuget.org
- Use `--build-context` to mount the local feed into Docker builds

