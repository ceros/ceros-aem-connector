(function ($) {
    'use strict';

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
                $btn.removeAttr('disabled').text('Fetch');
                if (!data || !data.fetchedAt) {
                    var ui = $(window).adaptTo('foundation-ui');
                    if (ui) ui.notify('', 'Fetch failed — unexpected response from server.', 'error');
                    return;
                }
                var tsEl = $dialog.find('[name="./cerosPrefetchedAt"]')[0];
                if (tsEl) tsEl.value = data.fetchedAt;
                var ui = $(window).adaptTo('foundation-ui');
                if (data.saved) {
                    if (ui) ui.notify('', 'Ceros experience fetched and stored.', 'success');
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
                    if (ui) ui.notify('', 'Ceros experience fetched — click Done to save.', 'success');
                }
            })
            .fail(function (xhr) {
                $btn.removeAttr('disabled').text('Fetch');
                var message = 'Fetch failed — check the manifest URL and try again. (status: ' + xhr.status + ')';
                try {
                    var body = JSON.parse(xhr.responseText);
                    if (body && body.message) message = body.message;
                } catch (ignore) {}
                var ui = $(window).adaptTo('foundation-ui');
                if (ui) ui.notify('', message, 'error');
            });
        });
    }

    function getSelectValue($root, name) {
        var el = $root.find('[name="' + name + '"]').first()[0];
        if (!el) return '';
        return (el.value !== undefined && el.value !== null) ? String(el.value) : $(el).val() || '';
    }

    function toggleModeWidgets($root) {
        var mode = getSelectValue($root, './cerosMode');
        var isStore = (mode === 'store');

        $root.find('.cerosflex-fetch-btn').toggle(isStore);

        var $tsWrapper = $root.find('[name="./cerosPrefetchedAt"]').closest('.coral-Form-fieldwrapper, coral-formfield');
        $tsWrapper.toggle(isStore);
    }

    $(document).on('foundation-contentloaded', function (e) {
        injectFetchButton(e.target);
    });

    $(document).on('coral-overlay:open', function (e) {
        injectFetchButton(e.target);
    });

}(Granite.$));
