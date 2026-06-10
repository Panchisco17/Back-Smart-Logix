$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Start-SmartLogixService {
    param(
        [string]$Module,
        [string]$Title
    )

    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-Command",
        "Set-Location '$root'; .\\mvnw.cmd -pl $Module spring-boot:run"
    ) -WindowStyle Normal

    Start-Sleep -Seconds 2
    Write-Host "Iniciado: $Title ($Module)"
}

Start-SmartLogixService -Module "discovery-service" -Title "Eureka Discovery"
Start-SmartLogixService -Module "inventory-service" -Title "Inventory Service"
Start-SmartLogixService -Module "shipment-service" -Title "Shipment Service"
Start-SmartLogixService -Module "order-service" -Title "Order Service"
Start-SmartLogixService -Module "api-gateway" -Title "API Gateway"

Write-Host "Servicios SmartLogix iniciados."
Write-Host "Gateway: http://localhost:8080"
Write-Host "Eureka: http://localhost:8761"
