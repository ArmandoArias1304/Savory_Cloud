# Cómo probar el add-on de fidelización

Guía manual para validar el catálogo de recompensas por sellos. El módulo se
vende como add-on del paquete **ECOMMERCE** y no toca el registro/login de
clientes: reutiliza las cuentas que ya existen.

## 1. Activar el add-on (programador)

1. Entra como `PROGRAMMER` a **Empresas → (empresa) → Add-ons**.
2. En la tarjeta **Fidelización** elige meses y precio, y pulsa **Activar add-on**.
   - La empresa debe tener licencia **ECOMMERCE**; si no, el sistema lo rechaza.
3. La tarjeta muestra vigencia, días restantes, precio y ciclo.
   - **Renovar** extiende la fecha sin borrar datos.
   - Desactivarlo conserva todos los sellos e historiales y solo oculta el módulo.

## 2. Configurar reglas y catálogo (admin / gerente)

Con el add-on activo aparecen **Fidelización** y **Canjear** en el sidebar.

1. **Reglas de la tarjeta**: sellos para completar, mínimo de artículos, consumo
   mínimo, sellos por venta, tope diario y textos de la tarjeta.
2. **Nueva recompensa**: elige un producto activo del menú —individual o
   **combo**— (los productos de producción interna no aparecen), sellos
   requeridos, artículos gratis y nombre. Los combos se marcan como **(combo)**
   en el selector y, para poder regalarse, deben tener artículos hijos activos.
   Ejemplos: “Café gratis · 5 sellos”, “Postre gratis · 8”, “Combo pareja · 15”.
3. **Catálogo de recompensas**: edita, activa o desactiva cada recompensa.
4. **Colores de la tarjeta**: elige el color del **frente** (logo y nombre) y del
   **reverso** (los cupones) y pulsa **Guardar colores**. El sistema calcula los
   tonos oscuros del degradado a partir de cada color. Si no eliges ninguno, la
   tarjeta usa la paleta original (naranja al frente, turquesa al reverso).
5. **Ver tarjetas de clientes**: saldo por cliente y **Ajuste manual** (solo
   `ADMIN`), que queda registrado como movimiento `ADJUST` en el ledger.

## 3. Acumular sellos

1. Crea un pedido **con un cliente registrado** (no un pedido de mostrador sin
   cliente) y págalo desde caja.
2. Al pasar a `PAID` se otorgan los sellos configurados, respetando:
   - mínimo de consumo y de artículos;
   - un máximo por venta y un tope diario opcional;
   - un pedido de recompensa ($0.00) nunca genera sellos;
   - un pedido solo acumula una vez (aunque se reintente el cobro).
3. Si el pedido se cancela antes de pagar, los sellos de una recompensa se
   devuelven automáticamente y queda el movimiento `REVERSE` en el historial.

## 4. Canjear recompensas (mesero, cajero, admin y gerente)

1. Entra con el botón **Canjear** del dashboard de mesero o cajero, o con
   **Canjear** del sidebar (admin y gerente). La pantalla es `/loyalty`.
2. Busca al cliente por nombre o teléfono (mínimo 2 caracteres). La tarjeta
   muestra sus sellos, cuántas recompensas puede tomar ya y sus movimientos.
3. Pulsa **Agregar** en una o varias recompensas y ajusta la cantidad. El panel
   del canje suma los sellos y muestra el saldo restante; no permite confirmar si
   no alcanza.
4. Elige el destino:
   - **Cuenta abierta** del cliente (mesa): las líneas de $0.00 se agregan a esa
     cuenta y salen a cocina, barra o parrilla según la preparación del producto.
   - **Pedido nuevo sin mesa ($0.00)**: crea un pedido aparte que caja cierra sin
     cobro cuando esté listo.
5. Al confirmar, el sistema bloquea la cuenta para que dos terminales no gasten
   el mismo saldo, descuenta los sellos del total, descuenta stock y manda la
   comanda. Se guarda **una fila por recompensa** en `loyalty_redemptions`.
   - Si la recompensa es un **combo**, se agregan el combo padre y todos sus
     artículos hijos a la comanda: cada hijo sale a cocina, barra o parrilla
     según su preparación, se descuenta el stock de sus ingredientes y todo el
     grupo queda en $0.00.
6. Si el pedido se cancela antes de pagar, se devuelven los sellos de todas las
   líneas con su movimiento `REVERSE`.
7. Un pedido con recompensas y artículos pagados sí acumula sellos por la parte
   pagada; solo el pedido de $0.00 queda fuera.
8. Para cerrar un pedido de $0.00, caja, admin o gerente lo hacen desde
   **Pedidos de recompensa por cerrar** con **Cerrar sin cobro** (el flujo de
   pago normal exige montos mayores a cero). Una cuenta con saldo se cobra por
   la pantalla de pago normal, no aquí.
   - El botón pide confirmación antes de cerrar: el pedido queda pagado en
     $0.00 y ya no se puede cobrar.
   - El estado de esos pedidos se actualiza en vivo por WebSocket cuando
     cocina, barra o parrilla los avanzan: el botón aparece solo al quedar
     **Listo** o **Entregado**. Si otro cajero lo cierra o se cancela, la fila
     desaparece sola.
   - En las listas de **Pedidos** (caja, admin y mesero) los pedidos de canje de
     $0.00 ya no muestran los botones de cobro: aparece la leyenda
     **Canje · se cierra en Canjear** y un acceso directo a la pantalla.
9. Abrir la pantalla de pago de un pedido de canje de $0.00 (por URL directa)
   también se rechaza con el aviso de que se cierra en **Canjear**; una cuenta
   con artículos pagados sigue cobrándose normal.
10. Un canje de $0.00 **no se factura**: en **Ventas** no aparece el botón verde
    de **Generar link de facturación (QR)** y en las listas de pedidos no
    aparece el botón morado de autofactura. El bloqueo también vive en el
    modelo (`Order.canGenerateInvoiceLink()`), así que ni por URL se puede
    crear un link de factura para esos pedidos.
11. Los pedidos de canje que quedan en $0.00 **no entran a la factura global**
    (tampoco en los totales del panel ni en la vista del programador): no son
    operaciones facturables. Un pedido con recompensas y artículos pagados sí se
    factura por su importe real.

## 5. Tarjeta del cliente

Con el add-on activo, el cliente ve **Mi Tarjeta** en su dashboard
(`/client/loyalty`): sellos actuales, sellos para la siguiente recompensa y el
catálogo. La tarjeta se gira al tocarla: el frente muestra el logo del
restaurante y el nombre del cliente; el reverso muestra los cupones (un sello
por círculo con el logo) y el aviso de cuántos faltan. Los colores de ambas
caras son los que eligió el admin. El catálogo de **Recompensas disponibles**
muestra la imagen del platillo cuando el menú la tiene (si no, un ícono de
regalo), el costo en sellos, una etiqueta **Combo** cuando aplica y una barra de
avance hacia el canje. El **Historial de sellos** queda en una caja
con scroll propio y barra visible, con el aviso “Desliza para ver todo el
historial” cuando hay más de seis movimientos. Si el add-on se desactiva, la
tarjeta y la liga desaparecen sin borrar nada.

## 6. Pruebas automáticas

```bash
cd elgransazon
./mvnw test -Dtest=LoyaltyEarningServiceTest,LoyaltyRedemptionServiceTest,LoyaltyRewardServiceTest,LoyaltyProgramCardColorTest,LoyaltyViewsRenderTest
```

- `LoyaltyEarningServiceTest`: otorgamiento, idempotencia y pedido de $0.
- `LoyaltyRedemptionServiceTest`: canje de una y varias recompensas, canje de un
  combo con sus hijos en comanda, saldo insuficiente, canje sobre cuenta abierta,
  devolución de todas las líneas al cancelar y cierre de pedidos de recompensa
  (Listo → Entregado → Pagado).
- `LoyaltyRewardServiceTest`: los combos aparecen en el selector de recompensas y
  se pueden guardar cuando tienen hijos activos.
- `LoyaltyProgramCardColorTest`: normalización de los colores elegidos por el
  admin y tonos del degradado, con la paleta original como respaldo.
- `LoyaltyViewsRenderTest`: render real de las pantallas de admin, programador,
  caja y cliente (incluye la sección de colores de la tarjeta, el scroll del
  historial, el panel de pedidos de recompensa con su botón y los avisos de
  WebSocket).

## 7. Datos generados

| Tabla | Contenido |
| --- | --- |
| `loyalty_programs` | Reglas y textos por empresa (se crea sola al abrir la pantalla). |
| `loyalty_rewards` | Catálogo de recompensas. |
| `loyalty_accounts` | Saldo por cliente (con bloqueo optimista). |
| `loyalty_stamp_events` | Ledger inmutable: `EARN`, `REDEEM`, `REVERSE`, `ADJUST`. |
| `loyalty_redemptions` | Canjes: una fila por recompensa del canje, pedido generado y estado. |
| `company_features` | Add-on contratado, vigencia y precio. |

El saldo de la cuenta es una caché; el historial del ledger es la fuente de
verdad. Para corregir un error se escribe un ajuste, nunca se edita un movimiento.
