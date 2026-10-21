# Producción interna (buffet y platillos que no se venden)

Módulo para los platillos que se preparan pero **no se venden** (buffet, comida de personal).
Su único objetivo es saber cuánto se consumió de ingredientes: cada vez que alguien registra
cuántas porciones se prepararon, se descuenta la receta multiplicada por esas porciones.

## Quién entra

- Admin, gerente: **Menú** → botón **Producción interna** (junto a *Categorías* y *Carta en PDF*).
  No es un módulo aparte del menú lateral: cuelga de Menú porque es un platillo más con receta,
  y el sidebar mantiene **Menú** resaltado mientras se está ahí.
- Chef: desde su panel → tarjeta **Producción interna**.

Nadie más (mesero, cajero, cliente) puede entrar, y los platillos internos no aparecen en ningún menú.

## Cómo se usa

1. **Crear el platillo interno**: nombre, categoría (solo para organizar) y su **receta por una
   porción** (ingrediente + cantidad). No tiene precio ni imagen: no se vende.
2. **Registrar producción**: en el listado, escribir cuántas porciones se prepararon y pulsar
   *Registrar producción*. Se descuenta `receta × porciones` del inventario. Si prepararon 10,
   se descuenta 10 veces la receta; si no alcanza el stock, no se registra nada y se dice qué
   ingrediente falta.
3. **Cancelar**: si la producción no se consumió (o se capturó mal), *Cancelar* devuelve
   exactamente los mismos ingredientes al inventario. Solo se puede cancelar una vez; una vez
   cancelada, se registra de nuevo la producción correcta.

## Búsquedas

La pantalla tiene **dos buscadores independientes**, cada uno con su propio formulario:

- **Platillos internos**: por nombre, descripción o categoría. Ignora mayúsculas y acentos
  ("camaron" encuentra "Camarones a la diabla").
- **Preparaciones recientes**: por platillo o por quien lo registró, más un filtro de estado
  (preparadas / canceladas).

Aplicar uno conserva el otro: cada formulario arrastra el filtro ajeno en un campo oculto, así
que buscar en el historial no borra la búsqueda de platillos ni al revés. Los filtros no cambian
los totales del día, que siempre se calculan sobre todo lo preparado hoy.

## Qué NO hace

- No genera pedido, comanda, ticket ni cuenta: no hay cliente ni mesa.
- No factura ni suma a las ventas. En los PDF de **Productos** y **Ejecutivo** aparece el bloque
  *"Producción Interna (platillos consumo propio o buffet)"* con el resumen del periodo y la tabla
  de platillos (porciones y lotes), más la leyenda de que no forma parte de las ventas: no toca
  totales, ticket promedio, categorías ni porcentajes. Los reportes de **Empleados** y **Clientes**
  no lo incluyen.
- No se puede editar desde **Platillos**: si se intenta abrir el formulario de venta de un
  platillo interno, se redirige a este módulo (darle precio o preparación lo convertiría en un
  platillo vendible sin querer).
- No maneja cancelación parcial: se cancela el lote completo.

## Detalles técnicos

- `item_menu.internal_only = true` marca el platillo. Las consultas de menú (operativo, carta
  impresa, listado del admin, contadores y búsquedas) excluyen esos items; `OrderServiceImpl`
  los rechaza como red de seguridad aunque alguien arme la petición a mano.
- `item_preparations` guarda cada lote: platillo, cantidad, quién y cuándo, estado y
  `stock_returned` (candado para que una cancelación repetida no devuelva stock dos veces).
- El descuento y la devolución usan `IngredientStockService` (transacción propia + bloqueo
  pesimista con reintentos), el mismo camino que usan los pedidos, así que dos personas
  registrando a la vez no se pisan.
- El costo estimado de cada lote sale de la receta (`cantidad × costo unitario del ingrediente`),
  que es el dato que antes no existía.
- `ddl-auto=update` crea la columna y la tabla al desplegar; no hay migración manual.
