# Cómo probar los tres efectos

Guía para verificar, uno por uno, los tres efectos añadidos:

1. Entrada escalonada de filas de lista
2. Pulso de pedido nuevo en cocina (KDS)
3. Vuelo del platillo al carrito

---

## Antes de empezar (una sola vez)

**1. Reinicia la aplicación.** La instancia que tengas corriendo sirve el código anterior.

**2. Recarga sin caché** en el navegador: `Ctrl` + `F5` (`Cmd` + `Shift` + `R` en Mac). Es importante: `theme.css` y `fly-to-cart.js` son archivos estáticos y el navegador los guarda. Si no ves nada, casi siempre es esto.

**3. Prueba primero la página de demostración** (sin datos, sin login, en 10 segundos):
abre [animaciones-demo.html](elgransazon/tools/animaciones-demo.html) en el panel de vista previa y pulsa los dos botones. Si ahí se ven los tres efectos, tu CSS y tu JavaScript están llegando bien al navegador y cualquier problema posterior es de datos o de permisos, no de código.

---

## 1. Entrada escalonada de filas

**Qué hace:** al abrir una lista, las filas entran una tras otra (35 ms de retraso cada una) en lugar de aparecer todas de golpe.

**Dónde probarlo** (cualquiera de estas; todas comparten la clase `stagger-rows`):

| Pantalla | Ruta |
|---|---|
| Ingredientes | `/admin/ingredients` |
| Menú / platillos | `/admin/menu-items` |
| Pedidos | `/admin/orders` |
| Clientes | `/admin/customers` (tabla y, en móvil, las tarjetas) |
| Empleados | `/admin/employees` |
| Promociones · Proveedores · Categorías · Categorías de ingredientes | `/admin/promotions`, `/suppliers`, `/categories`, `/ingredient-categories` |
| Reservaciones · Ventas | `/admin/reservations`, `/admin/sales` |
| Objetos de inventario | `/admin/inventory/objects` |

**Cómo probarlo:**

1. Abre una de esas listas **con al menos 6 registros** (con 1 o 2 filas el efecto es casi imperceptible).
2. Observa la primera carga: las filas deben aparecer en cascada, de arriba abajo.
3. Recarga con `F5` para volver a verlo.

**Qué debe verse:** la fila 1 entra ya; la 2 un instante después; la 6 unos 175 ms después. También en modo oscuro.

**Dato:** la fila 20 y siguientes entran juntas (el retraso se topa ahí a propósito, para que una lista de 200 filas no tarde 7 segundos en mostrarse).

**Cómo comprobarlo con precisión** (DevTools → pestaña Console, en la propia página):

```js
// Leer el retraso real de la tercera fila: debe decir "0.07s"
getComputedStyle(document.querySelector('tbody.stagger-rows > tr:nth-child(3)')).animationDelay
```

---

## 2. Pulso de pedido nuevo en cocina (KDS)

**Qué hace:** cuando entra un pedido por WebSocket, su tarjeta late tres veces con un anillo verde y la pantalla se desplaza hasta ella. Si el pedido todavía no está en la pantalla (llegó después de cargar), aparece arriba un aviso verde "N pedidos nuevos" con un botón **Actualizar**.

**Dónde probarlo:** `/admin/kitchen` (el tablero de cocina).

**Cómo probarlo sin esperar un pedido real** — esta es la parte útil, porque el disparador es un WebSocket:

1. Abre `/admin/kitchen` con **al menos dos pedidos activos**.
2. Abre DevTools → Console.
3. Pega esto (toma el número de un pedido que **sí** esté en pantalla):

```js
handleKitchenNotification({ notificationType: "NEW_ORDER", orderNumber: "PEGA-AQUÍ-EL-NÚMERO" })
```

4. La tarjeta de ese pedido debe latir con un anillo verde y desplazarse al centro.

**El otro caso (pedido que no está en la lista):**

```js
handleKitchenNotification({ notificationType: "NEW_ORDER", orderNumber: "NO-EXISTE" })
```

5. Debe aparecer el aviso verde pulsante arriba al centro. Llámalo dos veces más: el texto debe cambiar a "2 pedidos nuevos" y "3 pedidos nuevos". El botón **Actualizar** recarga la página a propósito, sin perder tu scroll de forma silenciosa.

**Cómo probarlo de verdad, de punta a punta:** abre la cocina en una ventana y crea un pedido desde otra (o desde el móvil). Debe llegar el aviso y latir la tarjeta, con la campana sonando (ese sonido ya existía).

---

## 3. Vuelo del platillo al carrito

**Qué hace:** al agregar un platillo, una copia de su foto vuela en arco hasta el carrito; el contador rebota y la tarjeta de origen da un pulso verde para confirmar **cuál** se agregó.

**Dónde probarlo** (los cinco menús con carrito):

| Rol | Ruta |
|---|---|
| Admin | `/admin/orders/select-table` → elige mesa → menú (`/admin/orders/menu`) |
| Mesero | `/waiter/orders` → abre un pedido → menú (`/waiter/orders/menu`) |
| Cajero | `/cashier/orders` → abre un pedido → menú (`/cashier/orders/menu`) |
| Cliente (menú digital) | `/client/menu` |
| Cliente (agregar a un pedido) | desde `/client/orders`, "agregar artículos" |

**Cómo probarlo:**

1. Entra al menú y toca un platillo **que tenga foto** (sin foto el vuelo no aplica: solo rebota el contador y pulsa la tarjeta).
2. Completa los complementos y confirma **Agregar**.
3. Mira el contador del carrito: debe salir volando una miniatura circular que se encoge al llegar, y el contador debe rebotar.

**Los dos casos límite que conviene verificar:**

- **Sin foto:** el contador igual rebota y la tarjeta pulsa. Nunca debe quedarse sin respuesta visual.
- **Cantidad desde el carrito** (`+` sobre un artículo ya agregado): no vuela nada, y es correcto: el origen ya no es una tarjeta del menú.

**Cómo probarlo a mano desde la consola** (si no quieres buscar un platillo):

```js
flyToCart(document.querySelector(".menu-item-card img")?.closest(".menu-item-card"))
```

Debe devolver `true` y verse el vuelo.

---

## 4. Que el sistema respete "menos movimiento"

Los tres efectos se apagan para quien lo pide en su sistema operativo, y los contadores siguen funcionando.

**En Chrome/Edge:** DevTools (`F12`) → `Ctrl` + `Shift` + `P` → escribe `reduced` → elige **Emulate CSS media feature prefers-reduced-motion** → selecciona `reduce` → recarga.

**Qué debe pasar:** ninguna fila entra en cascada, ninguna tarjeta late, nada vuela. La información sigue ahí (los contadores se actualizan), simplemente sin movimiento.

---

## 5. Pruebas automáticas (sin navegador)

```bash
# Comportamiento del vuelo al carrito (17 comprobaciones)
node tools/fly-to-cart.check.js

# El tablero de cocina tal como lo recibe el navegador
./mvnw -B test -Dtest=KitchenBoardNewOrderPulseRenderTest

# Todo el proyecto
./mvnw -B test
```

La suite completa son **516 pruebas**. Los únicos fallos son **7 errores previos** de `ConcurrentOrderCreationTest` (un problema de datos de la empresa 3 en este entorno, ajeno a estos cambios).

---

## Si algo no funciona

| Síntoma | Causa más probable |
|---|---|
| No se ve ninguna animación en ninguna pantalla | La aplicación no se reinició, o `theme.css` está en caché: `Ctrl` + `F5` |
| Todo funciona en la demo pero no en la aplicación | La lista tiene menos de ~4 filas, o el platillo no tiene foto |
| No vuela nada al agregar | Ese platillo no tiene imagen cargada |
| El aviso verde de cocina no aparece | Se llamó con un número de pedido que **sí** está en pantalla: en ese caso late la tarjeta, no el aviso |
| El pulso no se ve en un pedido PENDIENTE | Es a propósito: el latido es un anillo verde por fuera, para no pisar el brillo ámbar que ya tienen esos pedidos |
