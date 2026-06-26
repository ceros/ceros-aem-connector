/**
 * Validates the pasted Ceros experience URL when the author clicks Done in the
 * Ceros Flex dialog, for every URL-based delivery mode — so all modes give the
 * same up-front feedback the Store mode's Fetch button does. On an invalid URL
 * it blocks the save and shows the server's reason in an error toast; on a
 * valid URL it lets the save proceed. The CerosFlexManifestUrlPostProcessor performs
 * the same check server-side as the authoritative gate; this is the UX layer.
 */
(function ($) {
    'use strict';

    var VALIDATE_URL = '/bin/ceros/validate-manifest-url';
    // Set just before we re-dispatch a click we've already validated, so the
    // capture handler lets that one through to the editor's submit handler.
    var passThrough = false;

    function isCerosflexDialog($dialog) {
        return $dialog.length > 0 && $dialog.find('[name="./cerosMode"]').length > 0;
    }

    function fieldValue($root, name) {
        var el = $root.find('[name="' + name + '"]').first()[0];
        if (!el) return '';
        var v = (el.value !== undefined && el.value !== null) ? el.value : $(el).val();
        return $.trim(v || '');
    }

    function notifyError(message) {
        var ui = $(window).adaptTo('foundation-ui');
        if (ui) ui.notify('', message, 'error');
    }

    function describeError(xhr, fallback) {
        try {
            var body = JSON.parse(xhr.responseText);
            if (body && body.message) return body.message;
        } catch (ignore) {}
        return fallback;
    }

    // Capture phase so we run before the editor's own submit handler and can
    // stop it until the URL has been validated server-side.
    document.addEventListener('click', function (event) {
        var target = event.target;
        var submitBtn = target && target.closest ? target.closest('.cq-dialog-submit') : null;
        if (!submitBtn) return;

        // A click we already validated — let it through this once.
        if (passThrough) {
            passThrough = false;
            return;
        }

        var $dialog = $(submitBtn).closest('coral-dialog, form.cq-dialog, .cq-dialog, [role="dialog"]');
        if (!isCerosflexDialog($dialog)) return;

        var mode = fieldValue($dialog, './cerosMode');
        if (mode === 'import') return;            // archive-sourced, no URL

        var url = fieldValue($dialog, './manifestUrl');
        if (!url) return;                          // empty handled by required/pattern

        // Hold the save until the server validates the URL.
        event.preventDefault();
        event.stopImmediatePropagation();

        var $btn = $(submitBtn);
        if ($btn.attr('disabled')) return;         // a validation is already in flight
        $btn.attr('disabled', true);

        $.ajax({
            url: VALIDATE_URL,
            type: 'POST',
            data: { manifestUrl: url, cerosMode: mode },
            dataType: 'json'
        })
        .done(function () {
            $btn.removeAttr('disabled');
            passThrough = true;                    // next click goes straight to submit
            submitBtn.click();
        })
        .fail(function (xhr) {
            $btn.removeAttr('disabled');
            notifyError(describeError(xhr, 'This Ceros experience URL could not be validated.'));
        });
    }, true);

}(Granite.$));
