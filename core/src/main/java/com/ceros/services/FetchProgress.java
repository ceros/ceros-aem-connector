package com.ceros.services;

/**
 * Callback used by {@link CerosManifestService#performFetchAndStore} to report
 * progress out of the long-running fetch pipeline. Implementations may write
 * to JCR for poll-based status APIs or do nothing for fire-and-forget callers.
 */
public interface FetchProgress {

    /** Phase IDs surfaced through {@link #onPhase(String)}. */
    String PHASE_FETCHING_MANIFEST = "fetching-manifest";
    String PHASE_UPLOADING_ASSETS = "uploading-assets";
    String PHASE_PERSISTING = "persisting";

    String STATUS_PENDING = "pending";
    String STATUS_RUNNING = "running";
    String STATUS_SUCCESS = "success";
    String STATUS_FAILED = "failed";

    void onPhase(String phase);

    void onPageProgress(int processed, int total);

    void onComplete(String fetchedAt, boolean saved, int pages);

    void onError(String message);

    FetchProgress NOOP = new FetchProgress() {
        @Override public void onPhase(String phase) {}
        @Override public void onPageProgress(int processed, int total) {}
        @Override public void onComplete(String fetchedAt, boolean saved, int pages) {}
        @Override public void onError(String message) {}
    };
}
