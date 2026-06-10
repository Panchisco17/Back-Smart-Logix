# SmartLogix - Plataforma Inteligente para Gestion Logistica (Microservicios)

Proyecto de referencia para el caso semestral de Informatica.
Incluye una arquitectura realista para PYMEs eCommerce con estos modulos:

- Gestion de Inventario (`inventory-service`)
- Procesamiento de Pedidos (`order-service`)
- Coordinacion de Envios (`shipment-service`)

Y componentes de infraestructura:

- Descubrimiento de servicios (`discovery-service` con Eureka)
- API Gateway (`api-gateway`)
- Autenticacion JWT (`auth-service`)

## Patrones de arquitectura implementados

- `Service Discovery`: registro dinamico con Eureka.
- `API Gateway`: punto unico de entrada para frontend o clientes.
- `Database per Service`: cada microservicio usa su propia base H2.
- `Factory Method`: en `shipment-service` para crear planes de envio por zona.
- `Circuit Breaker`: en `order-service` para llamadas a `shipment-service`.
- `Synchronous orchestration`: `order-service` coordina inventario + envio.

## Estructura del repositorio

- `discovery-service` (puerto `8761`)
- `api-gateway` (puerto `8080`)
- `auth-service` (puerto interno `8084`)
- `inventory-service` (puerto interno `8081`)
- `order-service` (puerto interno `8082`)
- `shipment-service` (puerto interno `8083`)

## Requisitos

- Java 17
- Maven Wrapper (`mvnw.cmd` ya incluido)

## Compilar y validar

```powershell
.\mvnw.cmd clean test
```

## Docker

Todas las imagenes de los microservicios usan multi-stage build con Java 17:

```dockerfile
FROM eclipse-temurin:17-jdk AS build
```

Para levantar toda la plataforma con Docker Compose:

```powershell
docker compose up --build -d
docker compose ps
```

Para detenerla:

```powershell
docker compose down
```

Si Docker Desktop no esta ejecutandose, `docker compose` devolvera un error de conexion al daemon.

Tambien puedes usar:

```powershell
.\run-docker.ps1
```

## Ejecutar (opcion 1: manual)

Iniciar en este orden (cada comando en terminal distinta):

```powershell
.\mvnw.cmd -pl discovery-service spring-boot:run
.\mvnw.cmd -pl inventory-service spring-boot:run
.\mvnw.cmd -pl shipment-service spring-boot:run
.\mvnw.cmd -pl order-service spring-boot:run
.\mvnw.cmd -pl api-gateway spring-boot:run
```

## Ejecutar (opcion 2: script)

```powershell
.\run-services.ps1
```

## URLs principales

- Eureka Dashboard: `http://localhost:8761`
- API Gateway: `http://localhost:8080`

En Docker Compose solo quedan publicados `8761` y `8080`. Los microservicios internos no se exponen al host; deben consumirse por el gateway.

## Pruebas rapidas por Gateway

### 1) Obtener token JWT

```powershell
$login = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"credential":"admin","password":"admin123"}'

$token = $login.token
```

Usuarios seed de desarrollo:

- `admin` / `admin123`
- `usuario` / `user123`
- `bodeguero` / `bodega123`

Para produccion cambia esas claves con variables de entorno:

- `SMARTLOGIX_SEED_ADMIN_PASSWORD`
- `SMARTLOGIX_SEED_USER_PASSWORD`
- `SMARTLOGIX_SEED_WAREHOUSE_PASSWORD`
- `JWT_SECRET`

### 2) Listar inventario inicial

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/inventory/items `
  -Headers @{ Authorization = "Bearer $token" }
```

### 3) Crear un pedido

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/orders `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer $token" } `
  -Body '{
    "customerName": "Ana Torres",
    "customerEmail": "ana@cliente.cl",
    "shippingAddress": "Av. Providencia 1234, Santiago",
    "lines": [
      { "sku": "SKU-1001", "quantity": 2, "unitPrice": 29990 },
      { "sku": "SKU-2001", "quantity": 1, "unitPrice": 14990 }
    ]
  }'
```

### 4) Ver pedidos

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/orders `
  -Headers @{ Authorization = "Bearer $token" }
```

### 5) Ver envios

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/shipments `
  -Headers @{ Authorization = "Bearer $token" }
```

## Endpoints clave

### Auth Service

- `POST /api/auth/register` publico
- `POST /api/auth/login` publico
- `GET /api/auth/validate` protegido con `Authorization: Bearer <token>`

### Inventory Service

- `GET /api/inventory/items`
- `POST /api/inventory/items`
- `GET /api/inventory/items/{sku}`
- `GET /api/inventory/items/{sku}/availability?quantity=...`
- `PATCH|POST /api/inventory/items/{sku}/reserve?quantity=...`
- `PATCH|POST /api/inventory/items/{sku}/release?quantity=...`
- `PATCH|POST /api/inventory/items/{sku}/dispatch?quantity=...`

### Order Service

- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{orderNumber}`

### Shipment Service

- `POST /api/shipments`
- `GET /api/shipments`
- `GET /api/shipments/{trackingCode}`
- `PATCH /api/shipments/{trackingCode}/status?value=IN_TRANSIT`

## Flujo funcional implementado

1. Se crea orden en `order-service`.
2. `order-service` valida disponibilidad en `inventory-service`.
3. Si hay stock, reserva unidades en inventario.
4. Solicita planificacion de envio en `shipment-service`.
5. Devuelve orden con `trackingCode` y estado final.

## Nota para evaluacion parcial

Este proyecto ya cubre la base del Parcial 1 (arquitectura y microservicios).
Se puede extender en Parcial 2/3 con:

- trazabilidad distribuida,
- mensajeria asincrona (Kafka/RabbitMQ),
- frontend React/Vue,
- despliegue con Docker Compose/Kubernetes.

## Documento tecnico sugerido

Se incluye una base de informe en:

- `docs/INFORME-TECNICO.md`
