# Cómo probar las promociones de la landing

La landing anuncia las promociones **activas y dentro de sus fechas**, aunque el cliente entre
fuera del día u hora en que se aplican. Así puede ver hoy una promoción de los viernes y volver
cuando toque. La tarjeta y el anuncio imprimen el horario para que se sepa cuándo vale.

## Qué cambió

| Antes | Ahora |
|---|---|
| El bloque usaba `findActiveByType()`, que filtra por el momento actual. Una promoción de viernes 18:00 a 21:00 desaparecía el miércoles. | La landing usa `landingPromotions` (`findActiveForLanding()`): solo mira que esté **activa** y entre su fecha de inicio y fin. |
| No se veía el horario en la tarjeta. | Cada tarjeta y cada anuncio muestran días y ventana: `Viernes · 18:00 a 21:00`, `Todos los días · 08:00 a 18:00`. |
| Tres tarjetas fijas, una por tipo de promoción. | Una tarjeta por promoción anunciada, con imagen, etiqueta de oferta, tipo, horario, vigencia, productos y botón al menú. |
| La sección tenía un fondo azul propio y cortes rectos. | La sección vive dentro del contenedor de la landing y usa su mismo degradado claro y sus ondas divisorias. Ambar conserva su crema con ondas azul marino; las demás usan el verde claro con ondas carbón. |
| No había aviso al entrar. | Al entrar aparecen los **anuncios** de las promociones; con varias se recorren como carrusel y cada anuncio lista **todos** los artículos incluidos. |

**Importante:** anunciar no regala el descuento. El precio se sigue calculando con
`Promotion.isValidNow()` al pedir y al cobrar, así que fuera de su horario la promoción se ve
pero no se aplica.

![La sección de promociones con cuatro ofertas](img/promociones-landing.png)

> Las capturas de esta guía son del diseño anterior del fondo. La sección actual usa
> la superficie clara y las ondas del contenedor de cada landing, como se describe arriba.

## El anuncio al entrar

- Se abre **una vez por carga** de la landing, después de que termina la pantalla de bienvenida
  (splash). Si la landing no tiene splash, se abre de inmediato.
- Con **una** promoción es un aviso simple: sin flechas, puntos ni contador.
- Con **dos o más** aparece el carrusel: flechas, puntos, contador `Promoción 1 de N`, `Escape`,
  flechas del teclado y deslizamiento con el dedo en el teléfono.
- Cada anuncio muestra **Artículos incluidos** con todos los productos de la promoción, aunque
  la tarjeta de la sección solo resuma los tres primeros.
- Se cierra con la **X**, con `Escape` o tocando el fondo. El cierre no se recuerda: al recargar
  vuelve a aparecer, que es lo pedido.
- Al cerrar se libera el scroll y el foco vuelve a donde estaba. El fondo del anuncio sigue
  siendo oscuro para que la promoción resalte sobre la página.

![Anuncio con carrusel de cuatro promociones](img/promociones-anuncio.png)

En el teléfono el anuncio se acomoda como hoja inferior y la tarjeta ocupa el ancho disponible:

![Anuncio en el teléfono](img/promociones-anuncio-movil.png)

## Cómo probarlo con datos reales

1. Reinicia la aplicación y recarga sin caché (`Ctrl` + `F5`): el CSS y el JavaScript de la
   landing son archivos estáticos y el navegador los guarda.
2. Crea o edita una promoción con:
   - **Activa** marcada.
   - Fechas que incluyan hoy.
   - Días y horas **fuera del momento de la prueba** (por ejemplo, viernes de 18:00 a 21:00 si
     hoy es miércoles por la mañana).
3. Entra a la landing del establecimiento. Debe verse la sección `promociones` y, tras el splash,
   el anuncio con el horario y la vigencia.
4. Comprueba que en el menú/caja esa promoción **no** aplica el descuento fuera de su horario.
5. Con dos o más promociones activas, comprueba el carrusel. Con una sola, la X y ningún control
   de navegación.

Así se ve una promoción real en la landing del establecimiento (una sola, sin carrusel):

![Una promoción real en la landing](img/promociones-landing-real.png)

## Archivos

| Archivo | Qué hace |
|---|---|
| [landing-promotions.html](../src/main/resources/templates/fragments/landing-promotions.html) | Fragmento compartido: sección y modal de anuncios. |
| [landing-promotions.css](../src/main/resources/static/css/landing-promotions.css) | Superficie y ondas de la sección, tarjetas, modal oscuro y adaptación a móvil. El acento se pasa con `--promo-accent` y cada landing ajusta `--promo-surface-*` y `--promo-wave-color`. |
| [landing-promotions.js](../src/main/resources/static/js/landing-promotions.js) | Abre tras el splash, carrusel, teclado, deslizamiento y cierre. |
| `home/landingAmbarGourmet.html`, `home/landingRestaurant.html`, `home/landingQuintaElParaiso.html` | Incluyen el fragmento, el CSS y el JS. |
| [HomeController.java](../src/main/java/com/aatechsolutions/elgransazon/presentation/controller/HomeController.java) | Publica `landingPromotions`. |
| [PromotionService.java](../src/main/java/com/aatechsolutions/elgransazon/application/service/PromotionService.java) | `findActiveForLanding()` ignora la hora del día, no las fechas. |

## Pruebas automáticas

```bash
./mvnw -Dtest=LandingPromotionsRenderTest,LandingPromotionServiceTest test
```

- [LandingPromotionsRenderTest.java](../src/test/java/com/aatechsolutions/elgransazon/LandingPromotionsRenderTest.java)
  renderiza el fragmento real con Thymeleaf: tarjetas con horario aunque sea otro día, fechas,
  productos, CTA, modal con un punto por promoción y todos los artículos dentro del anuncio, sin
  controles cuando hay una sola, y el contrato que recorre el JavaScript para verificar que cada
  `data-promo-*` que lee existe en el fragmento. También comprueba que las tres landings usan el
  fragmento, que lo colocan dentro del contenedor oscuro y que el CSS ya no tiene el fondo azul
  anterior y sí las ondas divisorias.
- [LandingPromotionServiceTest.java](../src/test/java/com/aatechsolutions/elgransazon/application/service/LandingPromotionServiceTest.java)
  verifica que la landing devuelve una promoción de viernes cuando hoy es miércoles, mientras
  `findActivePromotions()` (el que usa el cobro) la sigue descartando fuera de horario.
