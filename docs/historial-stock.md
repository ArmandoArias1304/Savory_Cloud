# Historial de stock: corregir y anular entradas

El historial de un ingrediente (`/admin/ingredients/{id}/stock-history`) registra cada compra:
cantidad, costo unitario, costo total, stock antes/después, quién y cuándo. Hasta ahora era solo
de consulta; un error de captura no tenía arreglo y dejaba el inventario mal para siempre.

Ahora cada entrada tiene dos acciones, solo para **admin y gerente**:

## Editar

Corrige la **cantidad** y/o el **costo unitario**. El stock del ingrediente se ajusta por la
diferencia:

```
stock_nuevo = stock_actual + (cantidad_nueva − cantidad_original)
```

Ejemplo: compra de 10, consumiste 4, stock actual 16. Corriges la compra a 8 → stock 14.
El costo solo recalcula el total de esa entrada; no mueve stock ni el costo de las recetas.

## Anular

Resta la **cantidad completa** que la entrada había sumado y saca la fila del historial y de los
gastos. Conserva quién la anuló, cuándo y el motivo.

Ejemplo: primera compra 10, segunda compra 10 (stock 20), se consumen 4 (stock 16). Anulas la
segunda compra → stock 6, que es lo mismo que "solo existió la primera compra y se consumieron 4".

## Reglas

- **El stock nunca queda negativo.** Si la corrección o la anulación lo dejaría por debajo de 0,
  la operación se rechaza con el cálculo explícito y el stock no se toca. En ese caso la entrada
  ya tenía consumo encima: corrige la cantidad o registra la compra que falta.
- Una entrada anulada no se puede editar ni volver a anular.
- Para unidades (`UN`) la cantidad debe ser entera.
- La cantidad debe ser mayor a 0 y el costo mayor o igual a 0.
- `previous_stock` / `new_stock` son fotos del momento. Al corregir una entrada se recalcula su
  propia foto, pero las filas posteriores conservan lo que se registró entonces: el sistema no
  reescribe la cadena histórica.

## Detalle técnico

- `ingredient_stock_history` gana `edited_at`, `edited_by`, `voided_at`, `voided_by` y
  `void_reason`. Una fila con `voided_at` no nula está anulada.
- Todas las consultas de gastos (por ingrediente, por categoría, de la empresa y por rango de
  fechas) excluyen las anuladas, igual que el listado del historial.
- `isOutOfStock()` considera agotado cualquier stock `<= 0`, como red de seguridad.
- Las operaciones no usan bloqueo pesimista, igual que el alta de stock existente; el riesgo es
  el mismo que ya tenía el módulo.
