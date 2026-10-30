/*
 * Shows a component authored before the "Ceros analytics tracking" checkbox
 * existed the way it renders. Such a component has no ./cerosAnalytics
 * property, and CerosFlexModel#isCerosAnalytics() keeps its earlier behaviour:
 * the iframe embed reports, Always Fetch and Inline don't. The checkbox has no
 * default of its own (new components get true from cq:template), so without
 * this it would show unchecked for an old iframe embed, and saving the dialog
 * would turn that embed's reporting off.
 */
(function ($, $document) {
    "use strict";

    var CHECKBOX = 'coral-checkbox[name="./cerosAnalytics"]';

    function applyStoredDefault(form) {
        var $form = $(form);
        var checkbox = $form.find(CHECKBOX)[0];
        var componentPath = $form.attr("action") || "";
        if (!checkbox || $form.data("cerosflexAnalyticsDefaulted")
                || !componentPath || componentPath.indexOf("*") !== -1) {
            return;
        }
        $form.data("cerosflexAnalyticsDefaulted", true);

        $.getJSON(componentPath + ".json").done(function (props) {
            if (props && props.cerosAnalytics === undefined) {
                checkbox.checked = props.cerosMode === "embed";
            }
        });
    }

    $document.on("foundation-contentloaded dialog-ready", function (event) {
        $(event.target).find(CHECKBOX).closest("form").each(function () {
            applyStoredDefault(this);
        });
    });
})(Granite.$, Granite.$(document));
