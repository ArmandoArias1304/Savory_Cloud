package com.aatechsolutions.elgransazon.util;

/**
 * Presentación de los tiempos de preparación capturados en minutos.
 *
 * <p>El mismo número se enseña en dos contextos: el tiempo que el restaurante
 * configuró para un platillo y el tiempo estimado de una comanda completa, que
 * es una aproximación. Por eso hay dos formatos: {@link #format(Integer)} para
 * el dato del platillo ("15 min") y {@link #formatApprox(Integer)} para el
 * estimado de la orden ("≈ 15 min").</p>
 *
 * <p>Devuelve {@code null} cuando no hay tiempo capturado: las pantallas usan
 * ese {@code null} para no dibujar nada, porque el campo es opcional.</p>
 */
public final class PreparationTimeFormatter {

    private PreparationTimeFormatter() {
    }

    /**
     * Formatea minutos como "15 min", "1 h" o "1 h 20 min".
     *
     * @return el texto, o {@code null} si no hay minutos válidos
     */
    public static String format(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return null;
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    /**
     * Igual que {@link #format(Integer)} pero con el signo de aproximado que
     * usan las comandas y el panel del cliente.
     */
    public static String formatApprox(Integer minutes) {
        String formatted = format(minutes);
        return formatted == null ? null : "≈ " + formatted;
    }
}
