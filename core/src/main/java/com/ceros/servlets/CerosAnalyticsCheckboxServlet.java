package com.ceros.servlets;

import com.ceros.models.CerosFlexModel;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.osgi.service.component.annotations.Component;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the dialog's "Ceros analytics tracking" checkbox as a plain Granite
 * checkbox whose {@code checked} default is what the component renders today.
 * Granite only uses that default when the component has no stored
 * {@code cerosAnalytics}, which is the case for every component authored
 * before the switch existed. Without it, an old iframe embed, which reports,
 * would open unchecked and saving the dialog would turn its reporting off.
 *
 * <p>The component being edited is the dialog request's suffix. With no
 * component there yet, the default is on, matching the {@code cq:template}
 * new components get.</p>
 */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.resourceTypes=" + CerosAnalyticsCheckboxServlet.RESOURCE_TYPE,
                "sling.servlet.extensions=html",
                "sling.servlet.methods=GET"
        }
)
public class CerosAnalyticsCheckboxServlet extends SlingSafeMethodsServlet {

    static final String RESOURCE_TYPE = "connectors/ceros/components/cerosflex/analyticscheckbox";
    static final String CHECKBOX_RESOURCE_TYPE = "granite/ui/components/coral/foundation/form/checkbox";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        Map<String, Object> config = new HashMap<>(request.getResource().getValueMap());
        config.put("checked", isCheckedByDefault(request.getRequestPathInfo().getSuffixResource()));
        Resource checkbox = new CheckboxResource(request.getResource(), new ValueMapDecorator(config));

        RequestDispatcher dispatcher = request.getRequestDispatcher(checkbox);
        if (dispatcher != null) {
            dispatcher.include(request, response);
        }
    }

    static boolean isCheckedByDefault(Resource component) {
        if (component == null) {
            return true;
        }
        ValueMap props = component.getValueMap();
        return CerosFlexModel.resolveCerosAnalytics(
                props.get("cerosAnalytics", String.class),
                props.get("cerosMode", String.class));
    }

    /** The field's own resource, rendered as a Granite checkbox with the computed config. */
    static final class CheckboxResource extends ResourceWrapper {
        private final ValueMap config;

        CheckboxResource(Resource field, ValueMap config) {
            super(field);
            this.config = config;
        }

        @Override
        public String getResourceType() {
            return CHECKBOX_RESOURCE_TYPE;
        }

        @Override
        public String getResourceSuperType() {
            return null;
        }

        @Override
        public ValueMap getValueMap() {
            return config;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T adaptTo(Class<T> type) {
            if (type == ValueMap.class || type == Map.class) {
                return (T) config;
            }
            return super.adaptTo(type);
        }
    }
}
