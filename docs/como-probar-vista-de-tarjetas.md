# Cómo probar la vista de tarjetas de pedidos

La lista de pedidos ahora se puede ver **como tabla o como tarjetas** en:

| Pantalla | Ruta | Llave de la preferencia |
|---|---|---|
| Admin y gerente | `/admin/orders` (y `/manager/orders`, misma plantilla) | `adminOrdersView` |
| Mesero | `/waiter/orders` | `waiterOrdersView` |

El **cajero no cambió**: conserva su vista de tarjetas de siempre, con su propio comportamiento (abre el detalle en un panel lateral). Esta pieza nueva es independiente para no arriesgar lo que ya funcionaba.

---

## Antes de empezar

1. **Reinicia la aplicación** y recarga sin caché (`Ctrl` + `F5`): `orders-view.js` es un archivo estático y el navegador lo guarda.
2. Ten a mano un día con **al menos 3 pedidos** de tipos distintos (uno de salón con mesa, uno a domicilio y uno para recoger). Los que no tienen mesa son los que mejor prueban la tarjeta.

---

## 1. El interruptor

1. Entra a `/admin/orders`.
2. En la cabecera de "Lista de Pedidos", a la derecha, hay un interruptor con dos botones de icono: uno de filas (vista de tabla) y uno de cuadrícula (vista de tarjetas). Al pasar el ratón se lee "Vista de tabla" y "Vista de tarjetas".
3. Pulsa el de la cuadrícula.

**Qué debe pasar:** la tabla desaparece, aparecen las tarjetas y el botón de tarjetas queda marcado. Con la lista vacía o sin pedidos que coincidan con los filtros, se lee "No hay pedidos que coincidan con los filtros".

**Prueba clave de que se recuerda:** cambia a tarjetas, recarga la página (`F5`) y vuelve a entrar. Debe seguir en tarjetas. Entra con el usuario mesero: **su** elección es independiente, aunque el navegador sea el mismo.

---

## 2. El contenido de cada tarjeta

Cada tarjeta sale de su fila, así que debe mostrar exactamente lo que muestra la tabla de ese rol:

| En la tarjeta | De dónde sale |
|---|---|
| Caja grande arriba a la izquierda | "Mesa 7" si el pedido tiene mesa; si no, el **tipo** con su icono y color (domicilio en naranja, para recoger en morado) |
| Número del pedido en grande | El mismo de la tabla |
| Fecha y hora + etiqueta de estado | La fecha de la tabla y el **mismo** `status-badge`, con sus colores por estado |
| "Tipo · Cliente" | El tipo y el cliente (si no hay cliente: "Público general") |
| Etiqueta de pago y total | El método de pago con sus colores y el total |
| Línea inferior | "Creado por" y "Cobrado por" en admin/gerente; "Cobrado por" y "Propina" en el mesero; "cuentas por persona" si el pedido se dividió, y "Ver detalle" |
| Pie | **Los mismos botones de la fila** |

**El caso que hay que mirar con lupa:** un pedido **sin mesa** (domicilio o para llevar). La caja NO debe decir "N/A": debe cambiar al icono del reparto y rotularse "Domicilio" o "Recoger".

---

## 3. El modal de detalle (igual que en cajero)

Al pulsar una tarjeta **ya no te manda a la vista del pedido**: se abre un modal con el detalle, igual que en la lista del cajero.

1. Pulsa el cuerpo de una tarjeta (el número del pedido, por ejemplo).
2. Debe aparecer el modal con un giro de carga y, enseguida, el detalle: mesa y número en grande, estado, total, cliente, ítems consumidos con sus notas y complementos, y las cuentas por persona si la cuenta se dividió.
3. Cierra tocando el fondo, la **X** o con `Escape`.
4. En el pie del modal, **"Ver pedido completo"** lleva a la vista de siempre (`/admin/orders/view/...`, `/waiter/orders/view/...`).

**Lo que hay que mirar aquí:** los botones de dentro del modal son **los mismos de la fila de tu rol**, no los del cajero. En el mesero deben seguir apareciendo la transferencia y el "Solo consulta" cuando correspondan, y en ningún caso un botón de cobro que lleve a la caja (el mesero no puede entrar a `/cashier/...`).

**Si el panel no carga,** el modal lo dice y muestra el motivo (`HTTP 404`, `HTTP 500`…) sin abrir la consola, y deja el enlace "Ver pedido completo" a mano.

---

## 4. Que los botones sigan funcionando

Este es el riesgo real de la vista de tarjetas: los botones del pie son **copias** de los de la tabla, y las copias nacen sin sus listeners.

1. En vista de tarjetas, pulsa un botón de una tarjeta, por ejemplo **Cancelar**.
2. Debe aparecer la confirmación y el pedido cancelarse, **igual que desde la tabla**.
3. Prueba también "Avanzar" y el cambio de estado: todos deben responder.
4. Toca el **cuerpo** de la tarjeta (el número, por ejemplo), no un botón: debe abrir el pedido (`/admin/orders/view/...` o `/waiter/orders/view/...`). Al tocar un botón, en cambio, no debe abrirse el pedido.

---

## 5. Verificarlo desde la consola (opcional)

En la propia página, con DevTools abierto:

```js
// El modal de detalle
OrdersView.isDetailPanelOpen()              // ¿hay un detalle abierto?
OrdersView.openDetailPanel(123)             // abrirlo sin tocar la tarjeta
OrdersView.refreshDetailPanel(123)          // volver a pedirlo al servidor
OrdersView.closeDetailPanel()

// La vista actual y dónde se recuerda
OrdersView.currentView()                    // "table" o "grid"

// Cuántas cuentas por persona detectó en un pedido
OrdersView.splitAccountCount(document.querySelector("tr[data-order-id]"))

// Forzar una vista sin tocar el botón
OrdersView.switchView("grid")
OrdersView.switchView("table")
```

Y para ver de dónde saca los datos una tarjeta, mira los atributos de una fila:

```js
const fila = document.querySelector("tr[data-order-id]");
[...fila.attributes].filter(a => a.name.startsWith("data-order-")).forEach(a => console.log(a.name, "=", a.value));
```

Si una tarjeta sale vacía, casi siempre es que falta un `data-order-*` en la fila: eso es exactamente lo que vigila la prueba automática de abajo.

---

## 6. Pruebas automáticas (sin navegador)

```bash
# El contrato entre la plantilla y el JavaScript de las tarjetas (y el modal)
./mvnw -B test -Dtest=AdminWaiterOrdersCardsViewTest

# El endpoint del panel de detalle, rol por rol
./mvnw -B test -Dtest=AdminWaiterOrderDetailPanelEndpointTest

# El panel renderizado de verdad (con y sin bloque de acciones)
./mvnw -B test -Dtest=OrderDetailPanelRenderTest

# Todo el proyecto
./mvnw -B test
```

La suite completa son **531 pruebas**. Los únicos fallos son **7 errores de `ConcurrentOrderCreationTest`**, y no vienen de este cambio: su `setup` aborta con "No hay items activos con stock disponibles todos los dias para empresa 3", es decir, faltan datos de prueba en la empresa 3 de esta base. Se arreglan cargando items activos con stock para esa empresa, o se ignoran mientras esa empresa no tenga datos.

También hay una página de demostración que carga el `orders-view.js` real sobre una tabla de ejemplo, para ver las tarjetas sin datos reales: [orders-view-demo.html](elgransazon/tools/orders-view-demo.html).

---

## Si algo no funciona

| Síntoma | Causa más probable |
|---|---|
| No aparece el interruptor | La aplicación no se reinició, o la caché: `Ctrl` + `F5` |
| Las tarjetas salen vacías o incompletas | Falta un `data-order-*` en la fila: córrelo con `AdminWaiterOrdersCardsViewTest`, que avisa de cuál |
| Los botones de la tarjeta no hacen nada | `bindActions` no está pasado a `OrdersView.init` en esa página |
| El clic en la tarjeta manda a la vista del pedido en vez de abrir el modal | Falta `detailPanel: { role: ... }` en `OrdersView.init` |
| El modal se abre pero no carga nada | Falta el endpoint: mira el mensaje del propio modal (`HTTP 404` = no existe esa ruta en el proceso que corre) |
| El modal sale sin botones | Los `data-order-id` de los botones de la fila faltan: el enganche los lee de ahí |
| El modal muestra botones de cobro en el mesero | Se devolvió `panelFromModel` (con acciones del cajero) en vez de `panelInfoFromModel` |
| El cambio de vista no se recuerda | El navegador está en modo privado: la preferencia vive solo en memoria |
| En el cajero no hay cambios | Es lo previsto: se dejó intacto a propósito |
