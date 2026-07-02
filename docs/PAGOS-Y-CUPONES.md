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

### 1.2 Flujo completo (integración real con Transbank Webpay Plus)

```
Cliente (SPA)         order-service        payment-service        Transbank (sandbox)      order-service
     |                      |                      |                       |                     |
     | POST /api/orders     |                      |                       |                     |
     |--------------------->|                      |                       |                     |
     |                      | reserva stock        |                       |                     |
     |                      | crea orden APPROVED  |                       |                     |
     | <-- paymentUrl ------|                      |                       |                     |
     |   (.../api/payments/{orden}/checkout)       |                       |                     |
     |                                             |                       |                     |
     | GET paymentUrl (navegador, redirect)        |                       |                     |
     |-------------------------------------------->|                       |                     |
     |                                             | GET /api/orders/{id}  |                     |
     |                                             |------------------------------------------>  |
     |                                             |<---------------- datos de orden -----------  |
     |                                             | transaction.create(buyOrder, sessionId,      |
     |                                             |   monto, returnUrl)   |                     |
     |                                             |---------------------->|                     |
     |                                             |<--- token_ws + url ---|                     |
     | <-- HTML autoenviable (POST token_ws) ------|                       |                     |
     |--------------------------------------------------------------------->|                    |
     |             (usuario paga en el sitio real de Transbank)             |                    |
     | <----------------------- redirect (GET/POST token_ws) --------------|                     |
     |-------------------------------------------->|                       |                     |
     |         /api/payments/{orden}/return         |                       |                     |
     |                                             | transaction.commit(token_ws)                 |
     |                                             |---------------------->|                     |
     |                                             |<-- status + código ---|                     |
     |                                             | PUT .../payment-confirmation?approved=...    |
     |                                             |------------------------------------------->  |
     |                                             |                       |     marca PAID o     |
     |                                             |                       |  libera stock/FAILED |
     |                                             |<---------------------- orden actualizada ---  |
     | <-- redirect 302 a frontend (success/failure/pending) --------------|                     |
```

Puntos clave:

- El link de pago (`paymentUrl`) que devuelve `order-service` al crear la
  orden **no apunta a sí mismo**: apunta a `payment-service` a través del
  gateway (`http://localhost:8080/api/payments/{orderNumber}/checkout`).
- `payment-service` crea una **transacción real** contra el ambiente de
  integración/sandbox de Transbank (`WebpayPlus.Transaction.create(...)`) y
  devuelve un formulario HTML que se **autoenvía por POST** con el
  `token_ws` hacia la URL real de Transbank (`webpay3gint.transbank.cl`) —
  el usuario paga en el sitio real de Transbank, no en un formulario propio.
- Transbank redirige de vuelta a `/api/payments/{orderNumber}/return`
  (puede ser `GET` o `POST` según la versión de su API, por eso el endpoint
  acepta ambos métodos). Ahí `payment-service` hace el
  `transaction.commit(token_ws)` para obtener el resultado real
  (aprobado/rechazado) directamente desde Transbank.
- Como `payment-service` sí necesita hablar con `order-service` (autenticado),
  firma su **propio JWT de servicio** con el mismo secreto compartido
  (`jwt.secret`) cada vez que no hay un token de usuario en el contexto de la
  petición (`HttpClientConfig.buildServiceAuthorization()`). Ese token tiene
  `role=ROLE_ADMIN` y expira a los 60 segundos.

### 1.3 Endpoints

**`payment-service`** (`PaymentController`, base `/api/payments`):

| Método | Ruta | Quién lo llama | Descripción |
|---|---|---|---|
| `GET` | `/{orderNumber}/checkout` | Navegador (redirect) | Crea la transacción en Transbank y devuelve el formulario HTML autoenviable hacia Webpay Plus |
| `GET`/`POST` | `/{orderNumber}/return` | Transbank (redirect de vuelta) | Confirma (`commit`) la transacción real, actualiza la orden y redirige (302) al frontend |

**`order-service`** (`OrderController`, base `/api/orders`):

| Método | Ruta | Quién lo llama | Descripción |
|---|---|---|---|
| `PUT` | `/{orderNumber}/payment-confirmation?approved=true\|false` | Solo `payment-service` (interno, protegido con JWT, requiere `ROLE_ADMIN`) | Marca la orden `PAID` (aprobado) o libera el stock reservado y marca `FAILED` (rechazado) |

Este último endpoint **no existe para que lo llame el navegador ni la SPA** —
desde la corrección de seguridad de rol (ver `README.md`), está restringido a
`ROLE_ADMIN`, rol que solo el JWT de servicio de `payment-service` posee. Un
cliente autenticado con `ROLE_USER` ya no puede llamarlo directamente para
marcar su propia orden como pagada sin pasar por Transbank.

### 1.4 Credenciales y ambiente Transbank

`payment-service` usa el **ambiente de integración (sandbox) público** de
Transbank, documentado en transbankdevelopers.cl (no son credenciales
secretas, son las mismas para cualquier comercio de prueba):

- Código de comercio: `597055555532`
- Ambiente: `TEST` (`IntegrationType.TEST`)
- Tarjeta de prueba VISA que aprueba: `4051885600446623` (cualquier CVV,
  fecha de vencimiento futura)
- Tarjeta Mastercard que simula rechazo: `5186059559590568`
- Autenticador del banco de prueba: RUT `11.111.111-1`, clave `123`

En producción estas credenciales se sobrescriben con variables de entorno
(`TRANSBANK_COMMERCE_CODE`, `TRANSBANK_API_KEY`, `TRANSBANK_ENVIRONMENT=LIVE`)
usando las credenciales reales entregadas por Transbank al comercio.

### 1.5 Configuración (`application.yml`)

```yaml
app:
  gateway:
    base-url: http://localhost:8080
  frontend:
    success-url: http://localhost:5173/?payment=success#/my-orders
    failure-url: http://localhost:5173/?payment=failed#/products
    pending-url: http://localhost:5173/?payment=pending#/my-orders

transbank:
  webpay:
    commerce-code: ${TRANSBANK_COMMERCE_CODE:597055555532}
    api-key: ${TRANSBANK_API_KEY:579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C}
    environment: ${TRANSBANK_ENVIRONMENT:TEST}
```

El frontend (`App.jsx`) lee el query param `?payment=...` al cargar, muestra
un banner (aprobado/rechazado/pendiente), y limpia la URL con
`history.replaceState`.

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
   - usuarios    (8081)     - órdenes      (8085)         (8083)      (8761, Eureka)
   - roles                  - cupones     - checkout →
                                            Transbank
                                           - return/commit
```

`payment-service` depende de `order-service` (vía Eureka/`RestTemplate`
balanceado) para leer datos de la orden y para notificar el resultado del
pago, y de **Transbank** (servicio externo, HTTPS) para crear y confirmar la
transacción real. No tiene su propia base de datos.

---

## 4. Seguridad por rol (RBAC) sobre estos endpoints

Todos los endpoints administrativos usados por la pasarela y los cupones
están protegidos con `@PreAuthorize` a nivel de método (Spring Security),
verificando la autoridad exacta que viene en el JWT:

- `POST/PUT/PATCH/DELETE /api/coupons/**` → `ROLE_ADMIN`.
- `PUT /api/orders/{orderNumber}/payment-confirmation` → `ROLE_ADMIN`
  (solo lo cumple el JWT de servicio que firma `payment-service`).
- `PATCH /api/auth/users/{id}/role` y `.../status` → `ROLE_ADMIN`, con
  bloqueo adicional para que un admin no se cambie el rol ni se suspenda a
  sí mismo.

Antes de esta corrección, el backend solo validaba que el JWT fuera válido
(`authenticated()`), sin revisar el rol — cualquier usuario autenticado
podía, por ejemplo, marcar su propia orden como pagada sin pasar por
Transbank, llamando directamente al endpoint. El detalle completo de esta
corrección está en el informe de arquitectura entregado junto con esta
evaluación.
