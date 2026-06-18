(function ($) {
    'use strict';

    var CONFIG_URL = '/bin/ceros/flex-api/config.json';
    var TREE_URL   = '/bin/ceros/flex-api/folder-tree.json';

    function initBrowse(root) {
        var $root = $(root || document);
        var $field = $root.find('[name="./manifestUrl"]').first();
        if (!$field.length) return;

        // Don't inject twice
        if ($root.find('.cerosflex-browse-btn').length) return;

        $.getJSON(CONFIG_URL).done(function (data) {
            if (!data || !data.enabled) return;
            // Re-check after async call to prevent duplicates
            if ($root.find('.cerosflex-browse-btn').length) return;
            injectBrowseButton($root, $field);
        });
    }

    function injectBrowseButton($root, $field) {
        var $input = $field.first();

        // Wrap the URL input so the browse icon can sit at its right edge, like
        // a trailing affix inside the field.
        var $affix = $input.parent('.cerosflex-url-affix');
        if (!$affix.length) {
            $affix = $('<div class="cerosflex-url-affix"></div>');
            $input.before($affix);
            $affix.append($input);
        }

        // Compact quiet icon button (folder/browse) with a tooltip, sitting
        // inside the right edge of the Ceros Experience URL field.
        var $btn = $(
            '<button is="coral-button" type="button" variant="quiet" ' +
            'icon="folderSearch" iconsize="S" class="cerosflex-browse-btn" ' +
            'title="Browse Ceros Experiences" aria-label="Browse Ceros Experiences"></button>'
        );

        $affix.append($btn);

        // Browse picks a CDN experience URL — irrelevant in HTML-import mode.
        // Hide on inject (this runs async, after the mode toggler) so it never
        // flashes in for an already-imported component reopened in import mode.
        var modeEl = $root.find('[name="./cerosMode"]').first()[0];
        var mode = modeEl ? (modeEl.value !== undefined ? String(modeEl.value) : $(modeEl).val()) : '';
        if (mode === 'import') {
            $btn.hide();
        }

        $btn.on('click', function () {
            $btn.prop('disabled', true);
            $.getJSON(TREE_URL)
                .done(function (data) {
                    $btn.prop('disabled', false);
                    showOverlay($root, $field, data);
                })
                .fail(function (xhr) {
                    $btn.prop('disabled', false);
                    var ui = $(window).adaptTo('foundation-ui');
                    if (ui) ui.notify('', 'Could not load experiences (HTTP ' + xhr.status + ')', 'error');
                });
        });
    }

    function showOverlay($root, $field, data) {
        // Remove any existing overlay
        $('.cerosflex-browse-overlay').remove();

        var $overlay = $('<div>', { 'class': 'cerosflex-browse-overlay' });
        var $panel   = $('<div>', { 'class': 'cerosflex-browse-panel' });

        // Header
        var $header = $('<div>', { 'class': 'cerosflex-browse-header' });
        var title = data.accountName ? 'Experiences \u2014 ' + data.accountName : 'Experiences';
        $header.append($('<span>', { 'class': 'cerosflex-browse-title', text: title }));

        var $close = $('<button>', { 'class': 'cerosflex-browse-close', type: 'button', text: '\u00d7' });
        $close.on('click', function () { $overlay.remove(); });
        $header.append($close);
        $panel.append($header);

        // Search
        var $search = $('<input>', {
            'class': 'cerosflex-browse-search',
            type: 'text',
            placeholder: 'Filter by name\u2026'
        });
        $panel.append($search);

        // Tree container
        var $tree = $('<div>', { 'class': 'cerosflex-browse-tree' });
        buildTree($tree, data.folders || [], $field, $overlay);
        $panel.append($tree);

        $overlay.append($panel);

        // Close on backdrop click
        $overlay.on('click', function (e) {
            if (e.target === $overlay[0]) $overlay.remove();
        });

        // Filter
        $search.on('input', function () {
            var q = $.trim($(this).val()).toLowerCase();
            filterTree($tree, q);
        });

        $('body').append($overlay);
        $search.focus();
    }

    function buildTree($container, folders, $field, $overlay) {
        if (!folders || !folders.length) {
            $container.append($('<div>', {
                'class': 'cerosflex-browse-empty',
                text: 'No folders found.'
            }));
            return;
        }

        for (var i = 0; i < folders.length; i++) {
            buildFolderNode($container, folders[i], $field, $overlay);
        }
    }

    function buildFolderNode($parent, folder, $field, $overlay) {
        var $folder = $('<div>', { 'class': 'cerosflex-browse-folder' });

        var hasChildren = folder.children && folder.children.length > 0;
        var hasExps     = folder.experiences && folder.experiences.length > 0;

        var $label = $('<div>', { 'class': 'cerosflex-browse-folder-label' });
        var $toggle = $('<span>', {
            'class': 'cerosflex-browse-toggle',
            text: '\u25b6'
        });
        $label.append($toggle);
        $label.append($('<span>', {
            'class': 'cerosflex-browse-folder-name',
            text: folder.name || '(unnamed)'
        }));

        if (hasExps) {
            $label.append($('<span>', {
                'class': 'cerosflex-browse-folder-count',
                text: '(' + folder.experiences.length + ')'
            }));
        }

        $folder.append($label);

        var $content = $('<div>', { 'class': 'cerosflex-browse-folder-content' });
        $content.hide();

        // Experiences in this folder
        if (hasExps) {
            for (var i = 0; i < folder.experiences.length; i++) {
                buildExperienceRow($content, folder.experiences[i], $field, $overlay);
            }
        }

        // Child folders
        if (hasChildren) {
            for (var j = 0; j < folder.children.length; j++) {
                buildFolderNode($content, folder.children[j], $field, $overlay);
            }
        }

        $folder.append($content);
        $parent.append($folder);

        // Toggle expand/collapse
        $label.on('click', function () {
            var expanded = $content.is(':visible');
            $content.toggle(!expanded);
            $toggle.text(expanded ? '\u25b6' : '\u25bc');
        });
    }

    function buildExperienceRow($parent, exp, $field, $overlay) {
        var $row = $('<div>', {
            'class': 'cerosflex-browse-exp',
            'data-name': (exp.name || '').toLowerCase()
        });

        if (exp.thumbnailUrl) {
            $row.append($('<img>', {
                'class': 'cerosflex-browse-exp-thumb',
                src: exp.thumbnailUrl,
                alt: ''
            }));
        } else {
            $row.append($('<div>', { 'class': 'cerosflex-browse-exp-thumb cerosflex-browse-exp-thumb--empty' }));
        }

        var $info = $('<div>', { 'class': 'cerosflex-browse-exp-info' });
        $info.append($('<div>', { 'class': 'cerosflex-browse-exp-name', text: exp.name || '(unnamed)' }));

        if (exp.lastPublishedDate) {
            var dateStr = exp.lastPublishedDate;
            try { dateStr = new Date(exp.lastPublishedDate).toLocaleDateString(); } catch (ignore) {}
            $info.append($('<div>', {
                'class': 'cerosflex-browse-exp-date',
                text: 'Published: ' + dateStr
            }));
        }

        $row.append($info);

        $row.on('click', function () {
            // Set the manifest URL field
            var el = $field[0];
            if (el && el.value !== undefined) {
                el.value = exp.manifestUrl;
            } else {
                $field.val(exp.manifestUrl);
            }
            // Trigger change so Coral picks it up
            $field.trigger('change');

            $overlay.remove();

            var ui = $(window).adaptTo('foundation-ui');
            if (ui) ui.notify('', 'Selected: ' + exp.name, 'success');
        });

        $parent.append($row);
    }

    function filterTree($tree, query) {
        if (!query) {
            // Show everything, collapse folders
            $tree.find('.cerosflex-browse-exp').show();
            $tree.find('.cerosflex-browse-folder').show();
            return;
        }

        // Hide non-matching experiences
        $tree.find('.cerosflex-browse-exp').each(function () {
            var name = $(this).attr('data-name') || '';
            $(this).toggle(name.indexOf(query) !== -1);
        });

        // Show folders that have visible experiences (recursively)
        $tree.find('.cerosflex-browse-folder').each(function () {
            var $f = $(this);
            var hasVisible = $f.find('.cerosflex-browse-exp:visible').length > 0;
            $f.toggle(hasVisible);
            if (hasVisible) {
                $f.find('> .cerosflex-browse-folder-content').show();
                $f.find('> .cerosflex-browse-folder-label .cerosflex-browse-toggle').text('\u25bc');
            }
        });
    }

    $(document).on('foundation-contentloaded', function (e) { initBrowse(e.target); });
    $(document).on('coral-overlay:open', function (e) { initBrowse(e.target); });

}(Granite.$));
