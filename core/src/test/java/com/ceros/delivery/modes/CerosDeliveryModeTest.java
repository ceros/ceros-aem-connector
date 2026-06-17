package com.ceros.delivery.modes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CerosDeliveryModeTest {

    @Test
    void valuesMatchPersistedStrings() {
        assertEquals("fetch", CerosDeliveryMode.FETCH.value());
        assertEquals("store", CerosDeliveryMode.STORE.value());
        assertEquals("import", CerosDeliveryMode.IMPORT.value());
        assertEquals("inline", CerosDeliveryMode.INLINE.value());
        assertEquals("embed", CerosDeliveryMode.EMBED.value());
    }

    @Test
    void fromValueResolvesEachMode() {
        assertEquals(CerosDeliveryMode.FETCH, CerosDeliveryMode.fromValue("fetch"));
        assertEquals(CerosDeliveryMode.STORE, CerosDeliveryMode.fromValue("store"));
        assertEquals(CerosDeliveryMode.IMPORT, CerosDeliveryMode.fromValue("import"));
        assertEquals(CerosDeliveryMode.INLINE, CerosDeliveryMode.fromValue("inline"));
        assertEquals(CerosDeliveryMode.EMBED, CerosDeliveryMode.fromValue("embed"));
    }

    @Test
    void fromValueFallsBackToFetchForUnknownOrBlank() {
        assertEquals(CerosDeliveryMode.FETCH, CerosDeliveryMode.fromValue(null));
        assertEquals(CerosDeliveryMode.FETCH, CerosDeliveryMode.fromValue(""));
        assertEquals(CerosDeliveryMode.FETCH, CerosDeliveryMode.fromValue("nonsense"));
    }
}
