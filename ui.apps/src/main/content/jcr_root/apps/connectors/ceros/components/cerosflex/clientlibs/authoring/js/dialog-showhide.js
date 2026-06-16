/*
 * Show/hide dialog fields based on the value of a coral-select trigger.
 *
 * Pattern (mirrors AEM's built-in cq-dialog-dropdown-showhide):
 *   - Trigger select  : class="cq-dialog-dropdown-showhide"
 *                       data-cq-dialog-dropdown-showhide-target="<css selector>"
 *   - Target elements : class="<target class> hidden"
 *                       data-showhidetargetvalue="<option value>"
 *
 * AEM's built-in handler fires inconsistently on initial load and on
 * coral-select:change in some SDK builds (the iframe Type / Height fields
 * stay hidden even after the author switches the dropdown). This script
 * runs the same logic explicitly inside the cerosflex dialog so visibility
 * is deterministic.
 */
(function ($, $document) {
    "use strict";

    var TRIGGER = ".cq-dialog-dropdown-showhide";

    function applyShowHide(trigger) {
        var $trigger = $(trigger);
        var selector = $trigger.data("cqDialogDropdownShowhideTarget");
        if (!selector) {
            return;
        }
        var value = $trigger.val();
        $(selector).each(function () {
            var $target = $(this);
            var match = String($target.data("showhidetargetvalue")) === String(value);
            $target.toggleClass("hidden", !match);
        });
    }

    function initAll(scope) {
        $(scope || $document).find(TRIGGER).each(function () {
            applyShowHide(this);
        });
    }

    // Initial state when the dialog renders.
    $document.on("foundation-contentloaded dialog-ready", function (event) {
        initAll(event.target);
    });

    // React to coral-select changes inside any cerosflex dialog.
    $document.on("change", TRIGGER, function () {
        applyShowHide(this);
    });

    // Coral 3 dispatches its own custom event in addition to "change".
    $document.on("coral-select:change", TRIGGER, function () {
        applyShowHide(this);
    });

    // Run once on initial DOM ready in case the events above missed.
    $(function () { initAll(); });
})(Granite.$, Granite.$(document));
