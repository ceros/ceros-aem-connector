package com.ceros.delivery.modes;

/**
 * The Ceros Flex delivery modes, and the single source of truth for the mode
 * string values stored on the component ({@code ./cerosMode}) and offered in
 * the authoring dialog. Each {@link DeliveryHandler} maps to one constant.
 */
public enum CerosDeliveryMode {

    /** Live-fetch the manifest from the Ceros CDN on every render (SSR). */
    FETCH("fetch"),
    /** Pre-fetch the manifest + assets into JCR/DAM and serve offline (SSR). */
    STORE("store"),
    /**
     * Import a Ceros export archive (.tar.gz), unpack it into JCR/DAM and serve
     * offline (SSR). Same stored end state as {@link #STORE}, but the source is
     * an uploaded archive instead of a live CDN fetch.
     */
    IMPORT("import"),
    /** Client-side render in the host DOM via {@code flex-client.js} (Shadow Root). */
    INLINE("inline"),
    /** Client-side render inside an iframe via the Ceros embed script. */
    EMBED("embed");

    private final String value;

    CerosDeliveryMode(String value) {
        this.value = value;
    }

    /** The persisted/dialog string for this mode (e.g. {@code "inline"}). */
    public String value() {
        return value;
    }

    /**
     * Resolves a stored mode string to its constant, falling back to
     * {@link #FETCH} for unknown or blank values so legacy components without an
     * explicit mode keep working.
     */
    public static CerosDeliveryMode fromValue(String value) {
        for (CerosDeliveryMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return FETCH;
    }
}
