# Cómo probar el aviso de "pedido listo para recoger"

Cuando la cocina, la barra o la parrilla marcan un pedido como listo, **a quien lo va a recoger** le
salta una alerta que parpadea, suena y se cierra con **"Entendido"**. No se avisa a todo el mundo,
solo a quien le toca ese pedido.

| Pantalla | A quién le suena |
|---|---|
| Admin y gerente | Los pedidos que **él levantó** |
| Mesero | Los pedidos que **atiende ahora** (si lo traspasó, le suena a quien lo recibió) |
| Cajero | Los pedidos que **él levantó**, nunca los del tablero global |

El sonido es `static/sounds/notification.mp3`, el mismo que ya usa la cocina, y suena **una sola
vez** al aparecer.

---

## Antes de empezar

1. Reinicia la aplicación y recarga sin caché (`Ctrl` + `F5`): `order-ready-alert.js` es un archivo
   estático y el navegador lo guarda.
2. Para probar la regla de propiedad necesitas **dos sesiones a la vez** (dos navegadores, o una
   ventana normal y una de incógnito): una con quien levanta el pedido y otra con otro usuario del
   mismo rol.
3. El pedido debe tener **ítems de preparación** (de cocina, barra o parrilla). Un pedido cuyos
   ítems no se preparan nunca pasa a listo por esa vía.

---

## 1. El camino feliz

1. Con el usuario A (por ejemplo un mesero), levanta un pedido con al menos un ítem de cocina.
2. En otra pantalla, entra como cocinero y **acepta y avanza** los ítems hasta marcarlos **listos**
   (el botón de avance del flujo de un clic, o el cambio de estado de ítems).
3. Vuelve a la pantalla del usuario A.

**Qué debe pasar:** aparece una alerta con el título **"¡Pedido listo!"**, la mesa y el folio del
pedido, el aviso de que ya puedes pasar a recogerlo, y **un solo botón: "Entendido"**. La alerta
late (un halo verde que pulsa) y el título titila; el sonido suena en ese momento. Al pulsar
"Entendido" se cierra y **la pantalla vuelve a responder** (si el aviso se quedara puesto, su capa
invisible se comería todos los clics).

**El atajo sin esperar a la cocina:** si no tienes a mano la pantalla de cocina, hazlo desde la
propia lista de pedidos con el botón de avance de preparación (el flujo de un clic que salta
PENDIENTE → EN PREPARACIÓN → LISTO). El aviso sale igual, porque el cambio de estado es el mismo.

---

## 2. Que NO le suene a quien no le toca

Es la mitad de la prueba, y la más fácil de romper:

1. **Pedido ajeno:** con el usuario B (otro mesero, o un cajero que no lo levantó), comprueba que
   la alerta **no aparece**. En el cajero, el pedido puede estar perfectamente visible en el
   tablero **global**: eso es exactamente lo que no debe sonar.
2. **Traspaso:** con el mesero A, traspasa el pedido al mesero B. Ahora pide a la cocina que marque
   otro pedido de ese mismo pedido listo (o usa el atajo) y comprueba que le suena **a B** y **no a
   A**, que ya solo puede consultarlo.
3. **Una sola vez:** deja que lleguen dos avisos seguidos del mismo pedido (por ejemplo, marca
   listos los ítems de dos en dos). Debe sonar **una sola vez**, no en cada aviso.

---

## 3. Verificarlo desde la consola (opcional)

En la propia pantalla, con DevTools abierto:

```js
// ¿Me tocaría a mí este pedido?
OrderReadyAlert.isMine({ createdBy: "meseroA", operatedBy: "meseroB", status: "READY" })

// ¿Ya se avisó de este pedido en esta pantalla?
OrderReadyAlert.hasAlerted(101)

// Simular el aviso del servidor sin esperar a la cocina
OrderReadyAlert.handle({
  orderId: 101,
  orderNumber: "ORD-0101",
  status: "READY",
  notificationType: "STATUS_CHANGE",
  tableNumber: 7,
  createdBy: "TU_USUARIO",
  operatedBy: null,
});
```

Si el aviso no sale, casi siempre es una de estas tres: el pedido no es tuyo (revisa `createdBy` y
`operatedBy`), el aviso no es un `STATUS_CHANGE` con el pedido en `READY`, o ya se avisó de ese
pedido en esta pantalla.

---

## 4. Pruebas automáticas (sin navegador)

```bash
# Que el aviso lleve dueño y responsable
./mvnw -B test -Dtest=OrderReadyNotificationOwnerTest

# Que las tres listas estén enganchadas y el sonido exista
./mvnw -B test -Dtest=OrderReadyAlertViewTest

# Todo el proyecto
./mvnw -B test
```

La suite completa son **542 pruebas**. Los únicos fallos son **7 errores de
`ConcurrentOrderCreationTest`**, ajenos a esto: su `setup` aborta con "No hay items activos con
stock disponibles todos los dias para empresa 3", o sea, faltan datos de prueba en la empresa 3 de
esta base.

Entre esas pruebas está la del cierre (`thePulseStepsAsideWhenTheAlertCloses`), que vigila el fallo
de la tabla de abajo: se comprobó que **falla** si el latido vuelve a ponerse sobre la alerta sin la
condición de cierre, así que no es una prueba decorativa.

También hay una página de demostración, con un botón por escenario (incluido el traspaso y el
cajero): [order-ready-alert-demo.html](elgransazon/tools/order-ready-alert-demo.html). Carga el
`order-ready-alert.js` real y el **SweetAlert2 real** (el mismo del CDN que usan las pantallas), así
que lo que se ve ahí —incluido el cierre— es lo que pasa en la aplicación. Solo el audio está
simulado, porque depende del navegador.

---

## Si algo no funciona

| Síntoma | Causa más probable |
|---|---|
| Al abrir la pantalla ya hay pedidos listos y no suena nada | Es lo previsto: solo se avisa en el momento en que el pedido **queda** listo, no de lo que ya estaba listo al entrar |
| No suena, pero la alerta sí aparece | El navegador bloqueó el audio hasta la primera interacción de la página: haz un clic y el siguiente ya sonará |
| Le suena a todo el mundo | Falta `currentUsername` en el modelo de esa pantalla, o el rol declarado en `OrderReadyAlert.init` no es el suyo |
| Le suena al mesero que traspasó el pedido | El aviso no está trayendo `operatedBy`: la regla del mesero es la del responsable actual |
| El cajero recibe avisos de pedidos globales | La pantalla está arrancando la alerta con `role: "waiter"` en vez de `"cashier"` |
| No se cierra de ninguna manera | Es lo previsto: se cierra solo con "Entendido" (ni el fondo ni `Escape`) |
| Al dar "Entendido" no se quita, el botón se apaga y no se puede tocar nada | Es el fallo que ya se corrigió: el latido de la alerta le ganaba la cascada a la animación de cierre de SweetAlert, así que la alerta no se iba y su capa bloqueaba la pantalla. Si se ve ahora, es caché: **reinicia y recarga con `Ctrl` + `F5`** (se arregló en `order-ready-alert.js`; con el archivo viejo vuelve a pasar) |
