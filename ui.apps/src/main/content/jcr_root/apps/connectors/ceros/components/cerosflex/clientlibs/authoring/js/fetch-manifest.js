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

        var $container = $field.closest('coral-textfield, .coral-Form-field-wrapper, .coral-Form-fieldwrapper');
        if (!$container.length) $container = $field;
        if ($container.next('.cerosflex-fetch-btn').length) return;

        var $btn = $('<button>', {
            'class': 'cerosflex-fetch-btn',
            'type':  'button',
            'text':  'Fetch'
        });

        $container.after($btn);

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
                var ui = $(window).adaptTo('foundation-ui');
                if (ui) ui.notify('', 'Save the dialog once before fetching.', 'error');
                return;
            }

            startFetch($dialog, $btn, url, componentPath);
        });
    }

    function startFetch($dialog, $btn, url, componentPath) {
        $btn.attr('disabled', true).text('Fetching…');

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
                resetButton($btn);
                notify('error', 'Fetch failed — unexpected response from server.');
                return;
            }
            pollFetchStatus($dialog, $btn, data.jobId, componentPath, Date.now());
        })
        .fail(function (xhr) {
            resetButton($btn);
            notify('error', describeError(xhr, 'Fetch failed — check the manifest URL and try again.'));
        });
    }

    function pollFetchStatus($dialog, $btn, jobId, componentPath, startedAt) {
        if (Date.now() - startedAt > MAX_POLL_MS) {
            resetButton($btn);
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
                scheduleNextPoll($dialog, $btn, jobId, componentPath, startedAt);
                return;
            }

            updateButtonLabel($btn, data);

            if (data.status === 'success') {
                onFetchSuccess($dialog, $btn, componentPath, data);
            } else if (data.status === 'failed') {
                resetButton($btn);
                notify('error', data.error || 'Fetch failed.');
            } else {
                scheduleNextPoll($dialog, $btn, jobId, componentPath, startedAt);
            }
        })
        .fail(function (xhr) {
            // Treat transient 5xx / network blips as recoverable; only stop on 404 (unknown job).
            if (xhr.status === 404) {
                resetButton($btn);
                notify('error', 'Fetch job not found — it may have been cleaned up.');
                return;
            }
            scheduleNextPoll($dialog, $btn, jobId, componentPath, startedAt);
        });
    }

    function scheduleNextPoll($dialog, $btn, jobId, componentPath, startedAt) {
        setTimeout(function () {
            pollFetchStatus($dialog, $btn, jobId, componentPath, startedAt);
        }, POLL_INTERVAL_MS);
    }

    function updateButtonLabel($btn, data) {
        var label = PHASE_LABELS[data.phase] || 'Fetching…';
        if (data.phase === 'uploading-assets' && data.pagesTotal) {
            label = 'Uploading assets (' + (data.pagesProcessed || 0) + '/' + data.pagesTotal + ')…';
        }
        $btn.text(label);
    }

    function onFetchSuccess($dialog, $btn, componentPath, data) {
        resetButton($btn);
        var tsEl = $dialog.find('[name="./cerosPrefetchedAt"]')[0];
        if (tsEl && data.fetchedAt) tsEl.value = data.fetchedAt;

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

    function resetButton($btn) {
        $btn.removeAttr('disabled').text('Fetch');
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

        // Fetch button is store-mode only (import uses its own Import button).
        $root.find('.cerosflex-fetch-btn').toggle(isStore);

        // Browse Experiences is for picking a CDN experience URL — not for import.
        $root.find('.cerosflex-browse-btn').toggle(!isImport);

        // Last Fetched / Manifest URL belong to the CDN fetch flow — hide for import.
        var $tsWrapper = $root.find('[name="./cerosPrefetchedAt"]').closest('.coral-Form-fieldwrapper, coral-formfield');
        $tsWrapper.toggle(!isImport);

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
