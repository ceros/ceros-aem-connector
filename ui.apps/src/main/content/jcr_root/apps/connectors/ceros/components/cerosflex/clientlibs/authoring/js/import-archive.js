/*
 * HTML-import mode: injects a file picker into the cerosflex dialog and, as soon
 * as the author chooses a Ceros .tar.gz export, uploads it to
 * /bin/ceros/import-archive and polls the shared
 * /bin/ceros/fetch-manifest-status endpoint until the background unpack + store
 * job finishes. No separate "Import" button — selecting the file starts it.
 */
(function ($) {
    'use strict';

    var POLL_INTERVAL_MS = 2000;
    var MAX_POLL_MS = 10 * 60 * 1000; // 10 minutes

    var PHASE_LABELS = {
        'reading-archive':  'Reading archive…',
        'uploading-assets': 'Uploading assets…',
        'persisting':       'Saving…'
    };

    function injectImportControls(root) {
        var $root = $(root || document);

        var $section = $root.find('.cerosflex-import-section');
        if (!$section.length) return;
        if ($section.find('.cerosflex-import-controls').length) return;

        var $wrap = $('<div>', { 'class': 'cerosflex-import-controls' });
        var $file = $('<input>', {
            'type':   'file',
            'class':  'cerosflex-import-file',
            'accept': '.tar.gz,.tgz,application/gzip',
            'title':  'Upload a Ceros HTML export file (.tar.gz)'
        });
        var $status = $('<span>', { 'class': 'cerosflex-progress' });
        $status.append($('<span>', { 'class': 'cerosflex-spinner', 'aria-hidden': 'true' }));
        $status.append($('<span>', { 'class': 'cerosflex-progress-text' }));
        $wrap.append($file).append($status);
        $section.append($wrap);

        // Muted "last imported" timestamp below the picker.
        var $ts = $('<div>', { 'class': 'cerosflex-timestamp cerosflex-import-timestamp' });
        $section.append($ts);
        updateTimestamp($root, $ts, 'Last imported');

        $file.on('change', function () {
            var fileEl = $file[0];
            var file   = fileEl && fileEl.files && fileEl.files[0];
            if (!file) return;

            var $dialog       = $file.closest('coral-dialog, .coral-Dialog, [role="dialog"]');
            var componentPath = $dialog.find('form').attr('action') || '';
            if (!componentPath || componentPath.indexOf('*') !== -1) {
                notify('error', 'Save the dialog once before importing.');
                resetFile($file, $status);
                return;
            }

            startImport($dialog, $file, $status, file, componentPath);
        });
    }

    function startImport($dialog, $file, $status, file, componentPath) {
        $file.attr('disabled', true);
        setStatus($status, 'Uploading…', true);

        var tsEl = $dialog.find('[name="./cerosPrefetchedAt"]')[0];
        if (tsEl) tsEl.value = '';

        var data = new FormData();
        data.append('archive', file, file.name);
        data.append('componentPath', componentPath);

        $.ajax({
            url:         '/bin/ceros/import-archive.json',
            type:        'POST',
            data:        data,
            dataType:    'json',
            processData: false,
            contentType: false
        })
        .done(function (resp) {
            if (!resp || !resp.jobId) {
                resetFile($file, $status);
                notify('error', 'Import failed — unexpected response from server.');
                return;
            }
            pollImportStatus($dialog, $file, $status, resp.jobId, componentPath, Date.now());
        })
        .fail(function (xhr) {
            resetFile($file, $status);
            notify('error', describeError(xhr, 'Import failed — check the archive and try again.'));
        });
    }

    function pollImportStatus($dialog, $file, $status, jobId, componentPath, startedAt) {
        if (Date.now() - startedAt > MAX_POLL_MS) {
            resetFile($file, $status);
            notify('error', 'Import is taking too long — check the job status in /var/ceros/fetch-jobs.');
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
                scheduleNextPoll($dialog, $file, $status, jobId, componentPath, startedAt);
                return;
            }

            setStatus($status, phaseLabel(data), data.status !== 'success' && data.status !== 'failed');

            if (data.status === 'success') {
                onImportSuccess($dialog, $file, $status, componentPath, data);
            } else if (data.status === 'failed') {
                resetFile($file, $status);
                notify('error', data.error || 'Import failed.');
            } else {
                scheduleNextPoll($dialog, $file, $status, jobId, componentPath, startedAt);
            }
        })
        .fail(function (xhr) {
            if (xhr.status === 404) {
                resetFile($file, $status);
                notify('error', 'Import job not found — it may have been cleaned up.');
                return;
            }
            scheduleNextPoll($dialog, $file, $status, jobId, componentPath, startedAt);
        });
    }

    function scheduleNextPoll($dialog, $file, $status, jobId, componentPath, startedAt) {
        setTimeout(function () {
            pollImportStatus($dialog, $file, $status, jobId, componentPath, startedAt);
        }, POLL_INTERVAL_MS);
    }

    function phaseLabel(data) {
        if (data.phase === 'uploading-assets' && data.pagesTotal) {
            return 'Uploading assets (' + (data.pagesProcessed || 0) + '/' + data.pagesTotal + ')…';
        }
        return PHASE_LABELS[data.phase] || 'Importing…';
    }

    function onImportSuccess($dialog, $file, $status, componentPath, data) {
        $file.removeAttr('disabled');
        var tsEl = $dialog.find('[name="./cerosPrefetchedAt"]')[0];
        if (tsEl && data.fetchedAt) tsEl.value = data.fetchedAt;
        updateTimestamp($dialog, $dialog.find('.cerosflex-import-timestamp'), 'Last imported');

        if (data.saved) {
            setStatus($status, 'Imported ✓', false);
            notify('success', 'Ceros experience imported and stored.');
            // The import job wrote manifestUrl (the DAM manifest path) on the
            // component, mirroring store mode. Mirror it into the dialog's hidden
            // manifestUrl field so clicking Done re-submits it instead of clearing
            // it — keeping the component's stored shape identical to store mode.
            $.getJSON(componentPath + '.json').done(function (node) {
                if (node && node.manifestUrl) {
                    var urlEl = $dialog.find('[name="./manifestUrl"]')[0];
                    if (urlEl) {
                        urlEl.value = node.manifestUrl;
                    }
                }
            });
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
            setStatus($status, 'Imported — click Done to save.', false);
            notify('success', 'Ceros experience imported — click Done to save.');
        }
    }

    function setStatus($status, text, loading) {
        if (!$status || !$status.length) return;
        $status.toggleClass('is-loading', !!loading);
        $status.find('.cerosflex-progress-text').text(text || '');
    }

    function resetFile($file, $status) {
        $file.removeAttr('disabled');
        if (($file[0]) && $file[0].value !== undefined) $file[0].value = '';
        setStatus($status, '', false);
    }

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

    $(document).on('foundation-contentloaded', function (e) {
        injectImportControls(e.target);
    });

    $(document).on('coral-overlay:open', function (e) {
        injectImportControls(e.target);
    });

}(Granite.$));
