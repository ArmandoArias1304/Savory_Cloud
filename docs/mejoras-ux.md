# El Gran Sazón / SavoryCloud — Análisis de experiencia de usuario

**Fecha:** 15 de octubre de 2026
**Alcance:** 140 plantillas Thymeleaf, 51 controladores, 11 roles (ADMIN, MANAGER, CASHIER, CHEF, WAITER, BARISTA, PARRILLERO, DELIVERY, CLIENT, EMPLOYEE, PROGRAMMER).

---

## 1. Radiografía medida del sistema

Antes de proponer nada, esto es lo que **encontré en el código** (no impresiones):

| Hallazgo | Medición | Consecuencia UX |
|---|---|---|
| Tailwind cargado desde CDN | **111 de 140** plantillas usan `cdn.tailwindcss.com` | El navegador compila los estilos **en runtime**: parpadeo de estilos (FOUC), ~1 s de retraso en la primera pintura y dependencia total de la red de un tercero |
| Layout no extraído | **131** plantillas tienen su propio `<head>` completo; **42** su propio `<header>` | Cambiar un encabezado es editar decenas de archivos; la navegación se siente distinta entre módulos |
| Mensajes flash duplicados | **40** plantillas repiten el bloque `successMessage` y **51** el de `errorMessage` | Texto, color y posición de los avisos varían por página |
| Animaciones copiadas | **53 keyframes distintos**, pero `fadeIn` aparece **24 veces**, `scaleIn` **23**, `fadeInUp` **22**, `fadeInDown` **15** | Las listas entran con ritmos distintos; no hay un lenguaje de movimiento |
| Accesibilidad de movimiento | `prefers-reduced-motion` solo en **2 de 140** archivos | Usuarios con sensibilidad vestibular no pueden desactivar el movimiento |
| Accesibilidad general | Solo **11 de 140** plantillas usan `aria-label`; **no existe skip-link** | Navegación por teclado y lectores de pantalla quedan a medias |
| Librería de animación | `theme.css` tiene 250 líneas y **un solo keyframe** (`skeleton-pulse`) + utilidades `img-fade` / `img-skeleton` | Hay base para esqueletos, pero solo se usa en imágenes, no en filas de tabla |
| Diálogos | **90** plantillas usan SweetAlert2, pero **4** siguen usando `confirm()` nativo del navegador | Experiencia inconsistente (el `confirm()` rompe el modo oscuro y no es táctil) |
| Gráficos | Chart.js en solo **6** vistas (dashboard, reportes de cajero/chef/mesero/delivery, backup) | Ventas, inventario y reportes de admin son tablas puras: difícil detectar tendencias |
| Tiempo real | WebSocket/STOMP **ya funciona** (cocina, pedidos, dashboard) | Base lista para notificaciones, KDS y contadores en vivo — hoy desaprovechada |
| Búsqueda | Solo **4** plantillas tienen campo de búsqueda y **no existe búsqueda global** | Un gerente con 25 módulos no puede llegar a un ingrediente sin recordar la ruta |
| Búsquedas táctiles | **70** archivos usan `h-11`/`h-12` (≥44 px) | Buena base para meseros, pero los flujos de hover puro (`opacity-0 group-hover`) fallan en tablet |
| Módulos existentes | dashboard, menú, promociones, complementos, cocina, crear pedido, caja, pedidos, inventario, proveedores, clientes, empleados, turnos, mesas, reservas, reseñas, ventas, reportes, facturación, impresoras, configuración, programador | 22 módulos: la superficie ya es grande; falta **coherencia**, no cantidad |
| Ya implementado (no repetir) | Propinas (mesero/delivery), ranking de meseros y cocineros, salvapantallas animado, 3 landings públicas, menú digital del cliente con carrito | Buenas piezas de UX que conviene **reutilizar como patrón** |

### Capacidades que NO existen (verificado con búsqueda en todo el repo)

`mermas` / `waste` · `lista de espera` / `waitlist` · programa de lealtad o puntos · `búsqueda global` / command palette · tours guiados / onboarding · exportar CSV/Excel en reportes · PWA / modo offline · bitácora de auditoría visible.

Estas ausencias definen las secciones propuestas abajo: **no inventé funcionalidad, propuse sobre datos que el sistema ya tiene** (recetas, inventario, ventas, turnos, proveedores, proveedores de tiempo real).

---

## 2. Diez secciones nuevas

Ordenadas por **relación impacto/esfuerzo**.

### 2.1 Búsqueda global con paleta de comandos (⌘K / Ctrl+K)
**Qué:** un solo campo que busca ingredientes, platillos, clientes, pedidos, mesas y empleados, y navega directo. Incluye comandos ("crear pedido", "abrir caja", "reporte de hoy").
**Por qué:** hay 22 módulos y cero búsqueda global. Es el mayor ahorro de tiempo diario del sistema.
**Dato clave:** los repositorios y servicios ya existen; se necesitan endpoints ligeros + un overlay. **Esfuerzo: medio.**

### 2.2 Centro de notificaciones persistente
**Qué:** bandeja con campana, contador de no leídas, agrupación por tipo (stock bajo, pedido listo, reserva nueva, pago fallido, licencia por vencer) y marcado de leído.
**Por qué:** el WebSocket ya empuja eventos, pero si el usuario no está mirando la pantalla correcta, el evento **se pierde**. Hoy no hay memoria de lo notificado.
**Dato clave:** la infraestructura de tiempo real está probada; falta persistir y agrupar. **Esfuerzo: medio.**

### 2.3 Control de mermas y desperdicio
**Qué:** registrar qué se tiró, cuánto costaba, por qué (vencido, quemado, error de preparación, devolución) y quién lo reportó; con costo acumulado diario.
**Por qué:** el inventario descuenta por receta, pero **el desperdicio no se registra en ningún lado**, así que el food cost nunca cuadra. Es dinero invisible.
**Esfuerzo: bajo-medio** (una entidad + pantalla + reporte). **Impacto alto en rentabilidad.**

### 2.4 Lista de espera y flujo de anfitrión
**Qué:** pantalla de hostess con grupos en espera, tiempo transcurrido, tamaño de mesa, estimación y asignación a mesa con aviso por SMS/pantalla.
**Por qué:** existen mesas y reservas, pero el flujo real de un restaurante lleno (los que llegan sin reserva) **no tiene dónde vivir**.
**Esfuerzo: medio.**

### 2.5 Ingeniería de menú (matriz de rentabilidad)
**Qué:** clasificar automáticamente cada platillo en **estrella / caballo de batalla / enigma / perro** según popularidad × margen, con matriz visual y sugerencias (subir precio, promocionar, retirar).
**Por qué:** las recetas (`ItemIngredient`) y las ventas ya existen; falta cruzarlas. Convierte datos que ya tienes en decisiones de carta.
**Esfuerzo: medio. Impacto estratégico muy alto.**

### 2.6 Costeo y rentabilidad por platillo en vivo
**Qué:** costo real de un platillo calculado desde su receta y el último precio de compra de cada ingrediente, con margen bruto por porción, alerta cuando el margen cae por debajo de un umbral y precio sugerido.
**Por qué:** cuando sube el precio del tomate, hoy **nadie se entera hasta el corte mensual**. Esto cierra ese hueco.
**Esfuerzo: medio.**

### 2.7 Órdenes de compra y recepción de mercancía
**Qué:** generar orden a partir de los faltantes de inventario, enviar al proveedor, registrar la recepción con cantidades y precios reales y actualizar stock y costo de una vez.
**Por qué:** hay proveedores e inventario, pero el ciclo de compra está partido: se anota fuera del sistema y se teclea a mano.
**Esfuerzo: alto.** Pero elimina doble captura y da trazabilidad de precios.

### 2.8 Conteo físico de inventario (inventario cíclico)
**Qué:** hojas de conteo por zona, captura por báscula/teclado, cálculo automático de diferencias contra el stock teórico, aprobación y ajuste con motivo.
**Por qué:** el stock teórico sin conteos nunca es confiable; sin confiabilidad, el sistema de inventario pierde credibilidad total.
**Esfuerzo: medio-alto.** Es el cimiento de compras y mermas.

### 2.9 Metas y objetivos de venta
**Qué:** definir objetivo diario/semanal/mensual por sucursal, turno, mesero y categoría, con barra de progreso en vivo en el dashboard y proyección al cierre ("vas a terminar en 92 % de la meta").
**Por qué:** existe el ranking de meseros (competición), pero **no hay meta**: sin objetivo, el marcador no motiva.
**Esfuerzo: bajo-medio. Impacto motivacional inmediato.**

### 2.10 Reloj checador (asistencia del personal)
**Qué:** entrada/salida por PIN o QR con foto opcional, tolerancias de retardo, horas trabajadas por empleado y cruce con turnos programados y propinas.
**Por qué:** hay módulo de turnos y asignación de empleados, pero no registro de asistencia real; propinas y costos de nómina se calculan sobre datos frágiles.
**Esfuerzo: medio.**

### Menciones extra (fuera del top 10, alto valor)
Centro de tareas de apertura/cierre por rol · Bitácora de auditoría visible ("quién cambió el precio de este platillo") · Comparador de periodos ("este viernes vs. el anterior") · Plano visual del salón con arrastrar y soltar · Exportación a CSV/Excel en todos los reportes · Modo PWA con caché para que el mesero no pierda la comanda si se cae el WiFi.

---

## 3. Diez animaciones (con propósito, no adorno)

Regla de oro: cada animación debe **comunicar un cambio de estado**, durar 150–350 ms, y respetar `prefers-reduced-motion`.

### 3.1 Entrada escalonada de filas de tabla (stagger)
**Qué:** cada fila aparece con 30–40 ms de retraso respecto a la anterior, en vez de que toda la tabla parpadee de golpe.
**Por qué ahora:** `fadeInUp` ya existe pero se aplica a todas las filas simultáneamente, lo que en listas de 50 filas se siente como un salto. El stagger **convierte la carga en una lectura ordenada**.
**Implementación:** una clase `.stagger` con `animation-delay: calc(var(--i) * 35ms)`.

### 3.2 Pulso de pedido nuevo en cocina (KDS)
**Qué:** cuando entra un pedido por WebSocket, la tarjeta hace un doble pulso de borde verde y suena una campana corta.
**Por qué:** la cocina no mira la pantalla continuamente. Hoy el pedido nuevo **solo aparece**; sin movimiento periférico, se pierde entre los demás.
**Implementación:** evento STOMP ya existente + `animation` de borde + `Audio` breve.

### 3.3 Semáforo animado de tiempo en cocina
**Qué:** cada comanda tiene una barra que avanza según el tiempo transcurrido: verde → ámbar a los 8 min → rojo palpitante a los 15 min (SLA configurable).
**Por qué:** traduce el reloj en urgencia **sin texto**. Es la métrica que más afecta la experiencia del comensal.

### 3.4 Transición de vista lista ↔ tablero con View Transitions API
**Qué:** al cambiar entre lista y tarjetas, los elementos compartidos se transforman suavemente en lugar de re-renderizar la página.
**Por qué ya:** existe el fragmento `orders-view-toggle.html` con dos vistas. El cambio hoy es abrupto y pierde el contexto visual.
**Implementación:** `document.startViewTransition()` con respaldo silencioso para navegadores sin soporte.

### 3.5 Cambio de tema circular (expansión desde el botón)
**Qué:** el modo oscuro/claro se expande en un círculo desde el botón de tema en lugar de cambiar todo de golpe.
**Por qué:** hay botón de tema en todas las páginas. Es el cambio visual más grande del sistema y hoy ocurre instantáneamente, lo que desorienta.
**Implementación:** View Transitions + `clip-path` circular. Nota: requiere desactivar la transición global de colores durante el cambio.

### 3.6 Esqueleto de filas (shimmer) reutilizando `.img-skeleton`
**Qué:** mientras carga una lista, se muestran filas grises con brillo deslizante en el lugar exacto que ocupará el contenido.
**Por qué ya:** `theme.css` ya tiene `skeleton-pulse` e `.img-skeleton`, pero solo para imágenes. Extenderlo a filas elimina el salto de layout (CLS) y hace la espera perceptiblemente más corta.

### 3.7 Vuelo del artículo al carrito
**Qué:** al agregar un platillo, una miniatura vuela en arco hasta el icono del carrito, que rebota y muestra el contador incrementándose.
**Por qué:** ya existe `cartBounce` en una plantilla y el carrito en el menú digital. Convertirlo en un patrón único da retroalimentación inmediata en la acción más repetida del sistema (agregar al pedido).

### 3.8 Aparición de gráficos al entrar en el viewport
**Qué:** las barras y líneas de Chart.js crecen hasta su valor solo cuando el usuario las ve, con animación de 600 ms.
**Por qué:** hay 6 vistas con gráficos. Animar al entrar en pantalla evita animaciones que el usuario nunca ve (porque ocurren fuera de vista) y dirige la atención.

### 3.9 Estado de carga dentro del botón (sin salto de ancho)
**Qué:** al enviar un formulario, el botón conserva exactamente su ancho, el texto se desvanece y un spinner aparece centrado; el botón se bloquea para evitar doble envío.
**Por qué:** evita el problema clásico de "¿se envió?" que produce **pedidos duplicados** — un riesgo real y costoso en este sistema.

### 3.10 Toasts apilables con progreso
**Qué:** las confirmaciones entran desde arriba a la derecha, se apilan hasta tres y se desvanecen con una línea de tiempo.
**Por qué:** hoy 90 plantillas usan SweetAlert2 en modo modal, que **interrumpe** el trabajo: obliga a cerrar un diálogo para seguir. Los toasts no bloquean y permiten encadenar tareas.

### Menciones extra
Transición tipo acordeón para filtros · Contador de KPI que "rueda" al cambiar de valor · Check dibujado con trazo SVG al guardar con éxito · Deslizamiento entre pasos de un asistente · Efecto de "respiración" de la lista al filtrar en vivo · Barra de progreso de carga superior estilo YouTube para navegación entre secciones.

---

## 4. Diez mejoras de interfaz y experiencia

### 4.1 Compilar Tailwind en local (eliminar el CDN)
**Qué:** reemplazar `cdn.tailwindcss.com` por un build local con solo las clases usadas.
**Por qué:** afecta a **111 de 140 plantillas**. Es la mejora de rendimiento y estabilidad visual con mayor retorno: elimina el FOUC, quita la dependencia de red del CDN y permite clases personalizadas reutilizables.
**Impacto:** primera pintura mucho más rápida y apariencia consistente desde el primer frame.

### 4.2 Extraer un layout maestro
**Qué:** un `layout.html` con `head`, topbar, sidebar, mensajes flash, contenedor y pie como fragmentos reutilizables. Migrar por módulos.
**Por qué:** **131 heads duplicados** y **42 headers** distintos. Hoy cada módulo ensaya su propia versión: el usuario percibe el sistema como una colección de aplicaciones, no como una sola.
**Ganancia secundaria:** agregar un elemento a todas las pantallas pasa de 130 ediciones a una.

### 4.3 Unificar mensajes flash
**Qué:** un fragmento único que renderiza éxito, error, aviso e información con el mismo estilo, animación de entrada y autocierre opcional.
**Por qué:** se repite en 40 y 51 plantillas. Una sola corrección arregla todo el sistema.

### 4.4 Unificar diálogos: adiós al `confirm()` nativo
**Qué:** un helper JS único (`confirmar()`, `exito()`, `error()`, `preguntar()`) sobre SweetAlert2, con los colores del tema en modo oscuro ya definidos en `theme.css`.
**Por qué:** quedan **4 usos de `confirm()` nativo** que se ven fuera de estilo, no respetan el modo oscuro y son incómodos en tablet táctil.

### 4.5 Accesibilidad de base (objetivo WCAG AA)
**Qué:** skip-link ("Saltar al contenido"), `aria-label` en botones con solo icono, foco visible consistente, contraste revisado en modo oscuro, tablas navegables por teclado y cierre de modales con Escape.
**Por qué:** solo **11 de 140** plantillas usan `aria-label` y no hay skip-link. Además de la obligación ética y legal, mejora el uso con teclado en caja y cocina, donde se teclea más de lo que se hace clic.

### 4.6 Respetar `prefers-reduced-motion` en todo el sistema
**Qué:** mover las 53 animaciones a `theme.css` con un bloque global que las desactive cuando el usuario lo pida.
**Por qué:** hoy solo **2 de 140** archivos lo consideran. Es una línea de CSS que resuelve molestias reales (mareo, ansiedad) para quien las sufre.

### 4.7 Barra superior común (búsqueda, notificaciones, contexto)
**Qué:** una topbar fija con búsqueda global, campana de notificaciones, nombre del restaurante/sucursal, cambio de tema y menú de perfil activo.
**Por qué:** hoy el usuario no tiene orientación permanente: no ve dónde está, ni si tiene avisos, ni cómo volver rápido. La topbar resuelve tres problemas de una vez y **alimenta la sección 2.1 y 2.2**.

### 4.8 Estados vacíos que enseñan y accionan
**Qué:** cuando una lista no tiene datos, mostrar icono, una frase que explique *qué es* esa pantalla y un botón con la acción esperada ("Añadir el primer ingrediente").
**Por qué:** hoy los estados vacíos son texto plano ("No hay ingredientes") en ~25 plantillas. La primera impresión de un usuario nuevo es la pantalla más importante del sistema.

### 4.9 Mobile-first real para mesero y repartidor
**Qué:** objetivos táctiles ≥44 px en todo el flujo, acciones principales al alcance del pulgar (barra inferior en móvil) y eliminar cualquier función que dependa solo de `hover`.
**Por qué:** ambos roles trabajan **de pie y con una sola mano**. El sistema ya usa `h-11`/`h-12` en 70 archivos, pero quedan flujos con acciones al pasar el cursor, inaccesibles en tablet.

### 4.10 Tablas de alta densidad: densidad configurable y columnas fijas
**Qué:** conmutador compacto/cómodo, encabezado fijo al hacer scroll, primeras columnas congeladas, anchos estables y filas seleccionables con acción masiva.
**Por qué:** inventario, ventas y pedidos manejan miles de filas. La lectura y el barrido visual son hoy agotadores, y ahí es donde pasan más horas los gerentes.

### Menciones extra
Exportar a CSV/Excel en cada reporte · Renderizado de gráficos en ventas e inventario (hoy solo tablas, y Chart.js ya está disponible) · Validación en vivo en formularios con el mensaje junto al campo · Guardado automático de borradores en formularios largos (hay plantillas de más de 4 500 líneas) · Paginación y filtros siempre en la URL para compartir y volver · Modo "pantalla de cocina" con tema oscuro por defecto, tipografía grande y cero distracciones.

---

## 5. Ruta sugerida

**Semana 1 — cimientos (bajo riesgo, alto impacto)**
Unificar mensajes flash y diálogos, respetar `prefers-reduced-motion`, estados vacíos, botón con estado de carga. *Son cambios aislados, sin migración de arquitectura.*

**Semana 2 y 3 — coherencia visual**
Layout maestro + topbar con búsqueda global y notificaciones + Tailwind compilado en local. *Aquí se paga la deuda de mantenimiento y se vuelve un solo sistema.*

**Semana 4 y siguientes — producto**
Búsqueda global, metas, mermas, costeo por platillo, lista de espera. *Cada una es valor nuevo visible para el cliente.*

## 6. Cómo verificar que funcionó

- **Técnico:** LCP y CLS antes/después de compilar Tailwind; número de plantillas que dependen del CDN (objetivo: 0).
- **Tarea:** tiempo para "crear un pedido completo" y "encontrar un ingrediente" con usuario real, antes y después. La búsqueda global debería recortar el segundo en más de la mitad.
- **Operación:** tickets por pedido duplicado (deben tender a cero con el botón bloqueado); mermas reportadas por semana; porcentaje de mesas ocupadas vs. tiempo de espera.
- **Satisfacción:** encuesta corta a meseros y cocina después de cada bloque (1–5).

---

*Documento generado a partir de la inspección directa del repositorio. Todas las cifras de la sección 1 se obtuvieron midiendo el código, no son estimaciones.*
