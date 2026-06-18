(function ($) {
    'use strict';

    var POLL_INTERVAL_MS = 2000;
    var MAX_POLL_MS = 10 * 60 * 1000; // 10 minutes

    var PHASE_LABELS = {
        'fetching-manifest': 'Fetching manifest…',
        'uploading-assets':  'Uploading assets…',
        'persisting':        'Saving…'
    };

    function injectFetchButton(root) {
        var $root = $(root || document);

        var $field = $root.find('[name="./manifestUrl"]').first();
        if (!$field.length) return;

        // Hide the dialog's Help (?) icon for now — scoped to this dialog (we
        // only get here when our cerosMode/manifestUrl fields are present) so
        // other components keep theirs. Restore when we have a real help link.
        $field.closest('coral-dialog, .coral-Dialog, [role="dialog"]')
            .find('.cq-dialog-help').hide();

        var $container = $field.closest('coral-textfield, .coral-Form-field-wrapper, .coral-Form-fieldwrapper');
        if (!$container.length) $container = $field;
        if ($container.next('.cerosflex-fetch-controls').length) return;

        var $btn = $('<button>', {
            'class': 'cerosflex-fetch-btn',
            'type': 'button',
            'text': 'Fetch',
            'title': 'Download and save this experience so it loads fast and reliably'
        });
        var $progress = buildProgress();
        // Fetch button, the muted "last fetched" timestamp, and the progress
        // indicator all share one row.
        var $ts = $('<span>', { 'class': 'cerosflex-timestamp' });
        var $controls = $('<div>', { 'class': 'cerosflex-fetch-controls' })
            .append($btn).append($ts).append($progress);
        $container.after($controls);

        updateTimestamp($root, $ts, 'Last fetched');
        toggleModeWidgets($root);

        $root.find('[name="./cerosMode"]').on('change coral-select:change', function () {
            toggleModeWidgets($root);
        });

        $btn.on('click', function () {
            var $dialog       = $btn.closest('coral-dialog, .coral-Dialog, [role="dialog"]');
            var componentPath = $dialog.find('form').attr('action') || '';
            var $urlInput     = $dialog.find('[name="./manifestUrl"]').first();
            var urlEl         = $urlInput[0];
            var url           = $.trim((urlEl && urlEl.value !== undefined ? urlEl.value : $urlInput.val()) || '');

            if (!url) {
                $urlInput.attr('invalid', true);
                return;
            }
            if (!componentPath || componentPath.indexOf('*') !== -1) {
                notify('error', 'Save the dialog once before fetching.');
                return;
            }

            startFetch($dialog, $btn, $progress, url, componentPath);
        });
    }

    function startFetch($dialog, $btn, $progress, url, componentPath) {
        $btn.attr('disabled', true);
        setProgress($progress, 'Fetching…', true);

        var tsEl = $dialog.find('[name="./cerosPrefetchedAt"]')[0];
        if (tsEl) tsEl.value = '';

        $.ajax({
            url:      '/bin/ceros/fetch-manifest.json',
            type:     'POST',
            data:     { manifestUrl: url, componentPath: componentPath },
            dataType: 'json'
        })
        .done(function (data) {
            if (!data || !data.jobId) {
                resetFetch($btn, $progress);
                notify('error', 'Fetch failed — unexpected response from server.');
                return;
            }
            pollFetchStatus($dialog, $btn, $progress, data.jobId, componentPath, Date.now());
        })
        .fail(function (xhr) {
            resetFetch($btn, $progress);
            notify('error', describeError(xhr, 'Fetch failed — check the manifest URL and try again.'));
        });
    }

    function pollFetchStatus($dialog, $btn, $progress, jobId, componentPath, startedAt) {
        if (Date.now() - startedAt > MAX_POLL_MS) {
            resetFetch($btn, $progress);
            notify('error', 'Fetch is taking too long — check the job status in /var/ceros/fetch-jobs.');
            return;
        }

        $.ajax({
            url:      '/bin/ceros/fetch-manifest-status.json',
            type:     'GET',
            data:     { jobId: jobId },
            dataType: 'json'
        })
        .done(function (data) {
            if (!data || !data.status) {
                scheduleNextPoll($dialog, $btn, $progress, jobId, componentPath, startedAt);
                return;
            }

            setProgress($progress, phaseLabel(data), data.status !== 'success' && data.status !== 'failed');

            if (data.status === 'success') {
                onFetchSuccess($dialog, $btn, $progress, componentPath, data);
            } else if (data.status === 'failed') {
                resetFetch($btn, $progress);
                notify('error', data.error || 'Fetch failed.');
            } else {
                scheduleNextPoll($dialog, $btn, $progress, jobId, componentPath, startedAt);
            }
        })
        .fail(function (xhr) {
            // Treat transient 5xx / network blips as recoverable; only stop on 404 (unknown job).
            if (xhr.status === 404) {
                resetFetch($btn, $progress);
                notify('error', 'Fetch job not found — it may have been cleaned up.');
                return;
            }
            scheduleNextPoll($dialog, $btn, $progress, jobId, componentPath, startedAt);
        });
    }

    function scheduleNextPoll($dialog, $btn, $progress, jobId, componentPath, startedAt) {
        setTimeout(function () {
            pollFetchStatus($dialog, $btn, $progress, jobId, componentPath, startedAt);
        }, POLL_INTERVAL_MS);
    }

    function phaseLabel(data) {
        if (data.phase === 'uploading-assets' && data.pagesTotal) {
            return 'Uploading assets (' + (data.pagesProcessed || 0) + '/' + data.pagesTotal + ')…';
        }
        return PHASE_LABELS[data.phase] || 'Fetching…';
    }

    function onFetchSuccess($dialog, $btn, $progress, componentPath, data) {
        resetFetch($btn, $progress);
        var tsEl = $dialog.find('[name="./cerosPrefetchedAt"]')[0];
        if (tsEl && data.fetchedAt) tsEl.value = data.fetchedAt;
        updateTimestamp($dialog, $dialog.find('.cerosflex-fetch-controls .cerosflex-timestamp'), 'Last fetched');

        if (data.saved) {
            notify('success', 'Ceros experience fetched and stored.');
            try {
                var editables = Granite.author.store.getEditables();
                if (editables) {
                    editables.forEach(function (e) {
                        if (componentPath && e.path === componentPath) {
                            Granite.author.edit.EditableActions.REFRESH.execute(e);
                        }
                    });
                }
            } catch (ignore) {}
        } else {
            notify('success', 'Ceros experience fetched — click Done to save.');
        }
    }

    // ---- shared progress (spinner + status text), matching import-archive.js ----

    function buildProgress() {
        var $p = $('<span>', { 'class': 'cerosflex-progress' });
        $p.append($('<span>', { 'class': 'cerosflex-spinner', 'aria-hidden': 'true' }));
        $p.append($('<span>', { 'class': 'cerosflex-progress-text' }));
        return $p;
    }

    function setProgress($progress, text, loading) {
        if (!$progress || !$progress.length) return;
        $progress.toggleClass('is-loading', !!loading);
        $progress.find('.cerosflex-progress-text').text(text || '');
    }

    function resetFetch($btn, $progress) {
        $btn.removeAttr('disabled').text('Fetch');
        setProgress($progress, '', false);
    }

    // ---- muted "last fetched/imported" timestamp ----

    function formatTimestamp(iso) {
        if (!iso) return '';
        var d = new Date(iso);
        if (isNaN(d.getTime())) return iso;
        return d.toLocaleString();
    }

    function updateTimestamp($root, $ts, prefix) {
        if (!$ts || !$ts.length) return;
        var el = $root.find('[name="./cerosPrefetchedAt"]')[0];
        var val = el ? $.trim(el.value || $(el).val() || '') : '';
        var formatted = formatTimestamp(val);
        $ts.text(formatted ? (prefix + ': ' + formatted) : '');
    }

    function notify(type, message) {
        var ui = $(window).adaptTo('foundation-ui');
        if (ui) ui.notify('', message, type);
    }

    function describeError(xhr, fallback) {
        try {
            var body = JSON.parse(xhr.responseText);
            if (body && body.message) return body.message;
        } catch (ignore) {}
        return fallback + ' (status: ' + xhr.status + ')';
    }

    function getSelectValue($root, name) {
        var el = $root.find('[name="' + name + '"]').first()[0];
        if (!el) return '';
        return (el.value !== undefined && el.value !== null) ? String(el.value) : $(el).val() || '';
    }

    function toggleModeWidgets($root) {
        var mode = getSelectValue($root, './cerosMode');
        var isStore = (mode === 'store');
        var isImport = (mode === 'import');

        // The Fetch control row (Fetch button + progress) is store-mode only;
        // hide it entirely otherwise so it leaves no empty gap. The Browse icon
        // now lives as an affix inside the URL field, so it's not in this row.
        $root.find('.cerosflex-fetch-controls').toggle(isStore);

        // Browse Experiences is for picking a CDN experience URL — not for import.
        // (It sits inside the URL field, which is itself hidden in import mode.)
        $root.find('.cerosflex-browse-btn').toggle(!isImport);

        // The raw Last Fetched/Imported field is always hidden; its value is
        // surfaced as a muted timestamp next to Fetch (store) and the file
        // picker (import).
        $root.find('[name="./cerosPrefetchedAt"]')
            .closest('.coral-Form-fieldwrapper, coral-formfield').hide();

        // Manifest URL is irrelevant for import (the source is an uploaded archive).
        var $urlWrapper = $root.find('[name="./manifestUrl"]').closest('.coral-Form-fieldwrapper, coral-formfield');
        $urlWrapper.toggle(!isImport);
    }

    $(document).on('foundation-contentloaded', function (e) {
        injectFetchButton(e.target);
    });

    $(document).on('coral-overlay:open', function (e) {
        injectFetchButton(e.target);
    });

}(Granite.$));
