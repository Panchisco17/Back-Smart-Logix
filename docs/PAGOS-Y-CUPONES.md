# Pasarela de Pago y Sistema de Cupones

Este documento explica cómo funcionan las dos funcionalidades agregadas sobre la
base de SmartLogix: la **pasarela de pago simulada** (`payment-service`) y el
**sistema de cupones de descuento** administrables (`order-service`).

---

## 1. Pasarela de pago (`payment-service`)

### 1.1 Por qué es un microservicio aparte

Al inicio, la simulación de pago vivía dentro de `order-service` (generaba el
HTML de checkout y procesaba la confirmación en el mismo servicio). Se separó
en un microservicio propio, **`payment-service`** (puerto interno `8085`),
para que la responsabilidad de "cobrar" esté aislada de la de "gestionar
pedidos" — el mismo criterio que ya se usaba para separar inventario, envíos y
órdenes.

`payment-service` **no tiene base de datos propia**: es intencionalmente sin
estado (stateless). Toda la información de la orden (productos, total,
estado) la obtiene en tiempo real desde `order-service` vía REST.

### 1.2 Flujo completo

```
Cliente (SPA)                order-service            payment-service          order-service
     |                            |                          |                       |
     | POST /api/orders           |                          |                       |
     |--------------------------->|                          |                       |
     |                            | reserva stock            |                       |
     |                            | crea orden APPROVED      |                       |
     |  <-- paymentUrl ---------- |                          |                       |
     |   (.../api/payments/{orden}/checkout)                 |                       |
     |                                                       |                       |
     | GET paymentUrl (navegador, redirect completo)         |                       |
     |------------------------------------------------------>|                       |
     |                                                       | GET /api/orders/{id}  |
     |                                                       |---------------------->|
     |                                                       |<---- datos de orden --|
     |  <-- HTML de la pasarela (form de tarjeta) -----------|                       |
     |                                                       |                       |
     | (usuario llena tarjeta y hace click en "Continuar")   |                       |
     | GET .../confirm?approved=true|false                   |                       |
     |------------------------------------------------------>|                       |
     |                                                       | PUT .../payment-confirmation?approved=... |
     |                                                       |---------------------->|
     |                                                       |                       | marca PAID o
     |                                                       |                       | libera stock y marca FAILED
     |                                                       |<-- orden actualizada -|
     |  <-- redirect 302 a frontend (success/failure/pending)|                       |
```

Puntos clave:

- El link de pago (`paymentUrl`) que devuelve `order-service` al crear la
  orden **no apunta a sí mismo**: apunta a `payment-service` a través del
  gateway (`http://localhost:8080/api/payments/{orderNumber}/checkout`).
- El navegador llega a `payment-service` con un **redirect normal**, no un
  `fetch` de la SPA — por lo tanto **no lleva el JWT del usuario**. Esto es
  intencional (simula cómo funciona una pasarela real: el banco no conoce el
  token de tu sesión en la tienda).
- Como `payment-service` sí necesita hablar con `order-service` (autenticado),
  firma su **propio JWT de servicio** con el mismo secreto compartido
  (`jwt.secret`) cada vez que no hay un token de usuario en el contexto de la
  petición (`HttpClientConfig.buildServiceAuthorization()`). Ese token tiene
  `role=ROLE_ADMIN` y expira a los 60 segundos.

### 1.3 Endpoints

**`payment-service`** (`PaymentController`, base `/api/payments`):

| Método | Ruta | Quién lo llama | Descripción |
|---|---|---|---|
| `GET` | `/{orderNumber}/checkout` | Navegador (redirect) | Devuelve el HTML de la pasarela simulada (estilo Webpay/Transbank) |
| `GET` | `/{orderNumber}/confirm?approved=true\|false` | Navegador (link del formulario) | Aplica el resultado en `order-service` y redirige (302) al frontend |

**`order-service`** (`OrderController`, base `/api/orders`):

| Método | Ruta | Quién lo llama | Descripción |
|---|---|---|---|
| `PUT` | `/{orderNumber}/payment-confirmation?approved=true\|false` | Solo `payment-service` (interno, protegido con JWT) | Marca la orden `PAID` (aprobado) o libera el stock reservado y marca `FAILED` (rechazado) |

Este último endpoint **no existe para que lo llame el navegador ni la SPA** —
está protegido igual que el resto de `/api/**` (requiere JWT válido), y solo
`payment-service` lo alcanza con su token de servicio.

### 1.4 La pasarela simulada (HTML)

`PaymentService.buildCheckoutPage()` genera una página con estilo similar a
Webpay/Transbank:

- Resumen de la orden con **nombre real del producto** (no el SKU), cantidad
  y precio formateado en pesos chilenos (`$18.990`, no `$18990.00`).
- Formulario de tarjeta con vista previa en vivo (número enmascarado, nombre,
  vencimiento) y detección de marca (VISA/MC/AMEX) según el primer dígito.
- Logo real de RedCompra (`payment-service/src/main/resources/static/assets/redcompra-logo.jpg`,
  servido públicamente vía `/assets/**`).
- **Reglas de simulación**: cualquier tarjeta con datos válidos (16 dígitos,
  vencimiento futuro, CVV de 3 dígitos) aprueba el pago. Una tarjeta que
  **empiece con "4000"** simula un rechazo (fondos insuficientes). El link
  "Anular compra y volver" rechaza directamente.

### 1.5 Configuración (`application.yml`)

```yaml
app:
  gateway:
    base-url: http://localhost:8080
  frontend:
    success-url: http://localhost:5173/?payment=success#/my-orders
    failure-url: http://localhost:5173/?payment=failed#/products
    pending-url: http://localhost:5173/?payment=pending#/my-orders
```

El frontend (`App.jsx`) lee el query param `?payment=...` al cargar, muestra
un banner (aprobado/rechazado/pendiente), y limpia la URL con
`history.replaceState`.

### 1.6 Cómo reemplazar la simulación por un proveedor real

Ver la sección final [«Camino hacia un pago real»](#4-camino-hacia-un-pago-real-mercado-pago--transbank).

---

## 2. Sistema de cupones de descuento

### 2.1 Modelo de datos

Los descuentos **ya no están hardcodeados** en el código — viven en la tabla
`discount_coupons` (`order-service`), administrable desde un panel de admin.

Entidad `DiscountCoupon`:

| Campo | Tipo | Descripción |
|---|---|---|
| `code` | String (único) | Código que el cliente ingresa (ej. `DUOC25`) |
| `description` | String | Texto libre para mostrar en el admin/checkout |
| `type` | `PERCENTAGE` \| `FIXED_AMOUNT` \| `TWO_FOR_ONE` | Cómo se calcula el descuento |
| `amount` | BigDecimal | Porcentaje (ej. `25` = 25%) o monto fijo en CLP. No aplica a `TWO_FOR_ONE` |
| `minSubtotal` | BigDecimal (opcional) | Subtotal mínimo del carrito para que el cupón sea válido |
| `requiredEmailDomain` | String (opcional) | Ej. `@duocuc.cl` — restringe el cupón a ese dominio de correo |
| `firstPurchaseOnly` | boolean | Si es `true`, solo aplica en la primera orden de ese correo |
| `active` | boolean | Interruptor on/off |
| `startDate` / `endDate` | fecha (opcional) | Rango de vigencia. Si ambos son `null`, el cupón no vence |

> Nota técnica: el campo se llama `amount` y no `value` porque `VALUE` es
> palabra reservada en H2 y rompía la creación de la tabla.

### 2.2 Cupones precargados (seed)

Al arrancar `order-service` por primera vez (`DiscountCouponSeedConfig`), si
la tabla está vacía se crean 3 cupones de ejemplo:

| Código | Tipo | Efecto | Condición |
|---|---|---|---|
| `2X1` | `TWO_FOR_ONE` | Descuenta el valor de la línea más barata entre productos con 2+ unidades | Ninguna |
| `DUOC25` | `PERCENTAGE` (25) | 25% de descuento sobre el total | Correo `@duocuc.cl` **y** primera compra de ese correo |
| `SMART5000` | `FIXED_AMOUNT` (5000) | $5.000 de descuento fijo | Carrito ≥ $20.000 |

### 2.3 Cómo se valida y aplica un cupón (backend, autoritativo)

En `OrderService.calculateTotalWithDiscounts()`, al crear o editar una orden:

1. Si no viene `discountCode` en la request, no hay descuento.
2. Se busca el cupón por código (`DiscountCouponRepository.findByCodeIgnoreCase`).
3. Se valida (`isCouponApplicable`): `active`, dentro de `startDate`/`endDate`,
   `minSubtotal` cumplido, dominio de correo (si aplica), y si
   `firstPurchaseOnly` es `true`, que **no existan órdenes previas con ese
   correo** (`countByCustomerEmailIgnoreCase`).
4. Si pasa todas las validaciones, se aplica según `type`:
   - `PERCENTAGE`: `total = subtotal * (1 - amount/100)`
   - `FIXED_AMOUNT`: `total = subtotal - amount`
   - `TWO_FOR_ONE`: `total = subtotal - (precio de la línea más barata con 2+ unidades)`
5. El total nunca queda negativo (se acota en 0).

Si el cupón no existe, está inactivo, vencido, o no cumple las condiciones,
**la orden se crea igual mostrando el subtotal sin descuento** (no es un
error bloqueante).

### 2.4 Panel de administración (frontend, `Coupons.jsx`)

Ruta `#/coupons`, visible solo para `ROLE_ADMIN` en el menú lateral.

- **Tabla** de cupones existentes: código, tipo, valor, condiciones (mínimo,
  dominio, primera compra), vigencia, y un botón para **activar/desactivar**
  sin necesidad de editar todo el cupón.
- **Formulario** de creación/edición: tipo de descuento, valor, subtotal
  mínimo, dominio de correo requerido, checkboxes de "solo primera compra" y
  "activo", **fecha de inicio**, **fecha de término**, y un campo de
  **"duración fija en días"** que calcula automáticamente la fecha de
  término (hoy + N días).
- Editar un cupón reutiliza el mismo formulario (botón "Editar" lo precarga).

### 2.5 Cómo el checkout del cliente conoce los cupones válidos

Como los cupones ahora son dinámicos, `Products.jsx` (la tienda) **consulta
la lista real de cupones** (`GET /api/coupons`) al cargar la página, y valida
el código ingresado contra esos datos (activo, vigencia, dominio, mínimo) —
ya no hay códigos fijos escritos en el frontend. La validación de "primera
compra" es una excepción: eso solo lo puede confirmar el backend al crear la
orden (el frontend no tiene forma de saberlo de antemano), así que se
muestra como una nota informativa ("el backend confirma si es tu primera
compra") en vez de bloquear la aplicación del cupón.

### 2.6 Endpoints (`order-service`, base `/api/coupons`)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/coupons` | Lista todos los cupones |
| `POST` | `/api/coupons` | Crea un cupón (400 si el código ya existe) |
| `PUT` | `/api/coupons/{id}` | Edita un cupón |
| `PATCH` | `/api/coupons/{id}/status?active=true\|false` | Activa/desactiva sin editar el resto |
| `DELETE` | `/api/coupons/{id}` | Elimina un cupón |

Expuestos por el gateway en la misma ruta (`Path=/api/coupons/**` →
`order-service`). No tienen restricción de rol a nivel de backend (igual que
el resto de endpoints administrativos de este proyecto); la protección es a
nivel de UI (solo `ROLE_ADMIN` ve la página).

---

## 3. Arquitectura general actualizada

```
                        ┌───────────────┐
                        │  api-gateway  │  (8080, único puerto público junto a Eureka)
                        └───────┬───────┘
        ┌───────────┬──────────┼──────────┬─────────────┬──────────────┐
        │           │          │          │             │              │
   auth-service  inventory- order-service payment-      shipment-   discovery-
   (8084)        service    (8082)        service        service     service
                 (8081)     - órdenes      (8085)         (8083)      (8761, Eureka)
                             - cupones     - checkout
                                            - confirmación
```

`payment-service` depende de `order-service` (vía Eureka/`RestTemplate`
balanceado) para leer datos de la orden y para notificar el resultado del
pago. No tiene su propia base de datos.

---

## 4. Camino hacia un pago real (Mercado Pago / Transbank)

La arquitectura actual está pensada para que este cambio sea localizado:
solo tocaría `payment-service`, no `order-service` ni el frontend.

Pasos generales (ejemplo con **Transbank Webpay Plus**, que es el más común
en proyectos académicos chilenos):

1. Agregar el SDK del proveedor a `payment-service/pom.xml`.
2. En vez de que `PaymentController.checkoutPage()` devuelva HTML propio,
   `PaymentService` llamaría a la API del proveedor para **crear una
   transacción real** (con el monto de la orden) y haría un `redirect` a la
   URL de checkout que el proveedor entrega — el usuario paga en el sitio
   real de Transbank/Mercado Pago, no en nuestro HTML.
3. El proveedor redirige de vuelta a una URL de retorno propia (ej.
   `/api/payments/{orderNumber}/webpay-return`) con un token de transacción.
4. Ese endpoint llama al SDK para **confirmar/"commit"** la transacción,
   obtiene el resultado real (aprobado/rechazado), y llama a
   `orderClient.confirmPayment(orderNumber, approved)` — exactamente el
   mismo método que ya existe hoy.
5. Se necesitaría persistir el estado de la transacción del proveedor (hoy
   `payment-service` no tiene base de datos); bastaría una tabla simple
   `payment_transactions` (orderNumber, providerTransactionId, status).

**Requisitos previos:** cuenta de comercio de prueba (ambas plataformas
ofrecen sandbox gratis), y HTTPS para los callbacks en un despliegue real
(en `localhost` funciona en modo sandbox/pruebas).
