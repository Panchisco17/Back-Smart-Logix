$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

try {
    docker info | Out-Null
} catch {
    Write-Error "Docker daemon no esta disponible. Abre Docker Desktop y vuelve a ejecutar este script."
    exit 1
}

docker compose up --build -d
docker compose ps

Write-Host "Plataforma SmartLogix desplegada en Docker Compose."
Write-Host "Gateway: http://localhost:8080"
Write-Host "Eureka: http://localhost:8761"
Write-Host "Para ver logs: docker compose logs -f api-gateway"
