package com.aatechsolutions.elgransazon;

import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate;
import com.aatechsolutions.elgransazon.domain.entity.SystemUpdate.ChangeEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The changelog lines are written as "Etiqueta: detalle". Templates render the
 * label in bold, so the split has to be predictable and never lose text.
 */
class SystemUpdateChangeEntryTest {

    private SystemUpdate updateWith(String changes) {
        return SystemUpdate.builder().changes(changes).build();
    }

    @Test
    @DisplayName("la etiqueta va antes de los dos puntos y conserva los dos puntos")
    void labelIsTheTextBeforeTheFirstColon() {
        List<ChangeEntry> entries = updateWith(
                "Transferencia de pedidos entre meseros: Ahora los meseros pueden compartir una comanda")
                .getChangeEntries();

        assertEquals(1, entries.size());
        assertEquals("Transferencia de pedidos entre meseros:", entries.get(0).getLabel());
        assertEquals("Ahora los meseros pueden compartir una comanda", entries.get(0).getDetail());
    }

    @Test
    @DisplayName("una línea sin dos puntos se muestra entera y sin etiqueta")
    void aLineWithoutAColonHasNoLabel() {
        List<ChangeEntry> entries = updateWith("Corrección del total de la cuenta").getChangeEntries();

        assertEquals(1, entries.size());
        assertNull(entries.get(0).getLabel());
        assertEquals("Corrección del total de la cuenta", entries.get(0).getDetail());
    }

    @Test
    @DisplayName("solo se parte por el primer los dos puntos; el resto conserva los suyos")
    void onlyTheFirstColonSplits() {
        List<ChangeEntry> entries = updateWith(
                "Horario de cocina: abierto hasta las 23:00 todos los días").getChangeEntries();

        assertEquals("Horario de cocina:", entries.get(0).getLabel());
        assertEquals("abierto hasta las 23:00 todos los días", entries.get(0).getDetail());
    }

    @Test
    @DisplayName("las líneas vacías se ignoran y cada línea no vacía genera una entrada")
    void blankLinesAreIgnored() {
        List<ChangeEntry> entries = updateWith(
                "Primera mejora: detalle uno\n\n   \nSegunda mejora: detalle dos\n").getChangeEntries();

        assertEquals(2, entries.size());
        assertEquals("Primera mejora:", entries.get(0).getLabel());
        assertEquals("Segunda mejora:", entries.get(1).getLabel());
    }

    @Test
    @DisplayName("una etiqueta demasiado larga no es etiqueta: la línea se queda tal cual")
    void aVeryLongSentenceStaysPlainText() {
        String line = "Esta línea es demasiado larga para considerarse una etiqueta porque describe "
                + "varias cosas a la vez antes de continuar con el resto del texto normalmente";
        List<ChangeEntry> entries = updateWith(line).getChangeEntries();

        assertEquals(1, entries.size());
        assertNull(entries.get(0).getLabel());
        assertEquals(line, entries.get(0).getDetail());
    }

    @Test
    @DisplayName("una línea vacía o nula no genera entradas")
    void emptyChangesProduceNoEntries() {
        assertTrue(updateWith(null).getChangeEntries().isEmpty());
        assertTrue(updateWith("   \n  ").getChangeEntries().isEmpty());
    }
}
