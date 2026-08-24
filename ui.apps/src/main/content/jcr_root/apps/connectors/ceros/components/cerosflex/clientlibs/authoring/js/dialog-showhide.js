/*
 * Show/hide dialog fields based on the value of a coral-select trigger.
 *
 * Pattern (mirrors AEM's built-in cq-dialog-dropdown-showhide):
 *   - Trigger select  : class="cq-dialog-dropdown-showhide"
 *                       data-cq-dialog-dropdown-showhide-target="<css selector>"
 *   - Target elements : class="<target class> hidden"
 *                       data-showhidetargetvalue="<option value>[,<option value>…]"
 *
 * A target listing more than one trigger value must be reached through
 * data-cerosflex-showhide-target instead, which only this script reads. AEM's
 * built-in handler compares data-showhidetargetvalue for exact equality and
 * hides non-matches with an inline display style, so a multi-value target on
 * its selector would be hidden for every value including its own.
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

    function toggleTargets(selector, value) {
        if (!selector) {
            return;
        }
        $(selector).each(function () {
            var $target = $(this);
            // A target may list several trigger values, comma-separated, for a
            // field shared by more than one mode (e.g. the server-side modes).
            // A single value contains no comma and still matches exactly.
            var expected = String($target.data("showhidetargetvalue")).split(",");
            var match = expected.some(function (candidate) {
                return candidate.trim() === String(value);
            });
            $target.toggleClass("hidden", !match);
            if (match) {
                // AEM's built-in handler hides its targets with an inline
                // display style, which toggling a class cannot undo. Clear it
                // so showing still works if this element is also one of its
                // targets, or if anything else has set display directly.
                $target.css("display", "");
            }
        });
    }

    function applyShowHide(trigger) {
        var $trigger = $(trigger);
        var value = $trigger.val();
        toggleTargets($trigger.data("cqDialogDropdownShowhideTarget"), value);
        // A second, separate target selector for fields the built-in handler
        // must not manage. It compares showhidetargetvalue for exact equality,
        // so any target naming more than one trigger value has to stay off the
        // built-in's selector or it gets hidden on every pass.
        toggleTargets($trigger.data("cerosflexShowhideTarget"), value);
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
