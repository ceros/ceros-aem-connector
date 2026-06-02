!function() {
    var noop = function() {};
    var createRef = function(value) {
        return { current: value, onChange: function() { return noop; } };
    };
    var publicDataService = {
        getPublicData: function() {
            return {
                pageContext: {
                    accountSlug: "",
                    experienceSlug: "",
                    experienceInternalSlug: "",
                    experienceId: "",
                    experienceVersionResourceId: "",
                    experiencePath: "",
                    pageSlug: "",
                    pageNumber: 0
                },
                cookieCompliance: { requireUserConsentForAnalytics: false },
                integrations: {
                    cerosAnalytics: { enabled: false },
                    googleAnalytics: { enabled: false },
                    googleTagManager: { enabled: false },
                    universalAnalytics: { enabled: false }
                },
                sentry: { enabled: false },
                pages: [],
                displayMetadata: {}
            };
        },
        resetCache: noop,
        refreshData: noop,
        isCached: function() { return false; }
    };
    var cookieConsentRef = createRef({
        source: "initial",
        consent: ["necessary", "functional", "performance"]
    });
    var embeddedStateRef = createRef({ isEmbedded: false, hostVersion: null });
    var frameMessengerRef = createRef(null);
    window.__cerosShared = {
        publicDataService: publicDataService,
        cookieConsentRef: cookieConsentRef,
        embeddedStateRef: embeddedStateRef,
        frameMessengerRef: frameMessengerRef
    };
    window.flexPlayer = {
        analytics: {
            isDebugEnabled: function() { return false; },
            toggleDebug: noop,
            enableDebug: noop,
            disableDebug: noop,
            getCurrentDebugState: function() { return false; },
            clearDebugState: noop
        },
        cookieConsent: {
            set: noop,
            get: function() { return cookieConsentRef.current; },
            onChange: function() { return noop; }
        },
        hover: {
            toggleDebug: noop,
            isDebugEnabled: function() { return false; },
            init: noop
        }
    };
}();
