package com.ceros.servlets;

import com.adobe.granite.ui.components.ds.DataSource;
import com.adobe.granite.ui.components.ds.SimpleDataSource;
import com.adobe.granite.ui.components.ds.ValueMapResource;
import com.ceros.config.CerosFlexModesConfig;
import com.ceros.delivery.modes.CerosDeliveryMode;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.caconfig.ConfigurationBuilder;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Granite datasource backing the {@code cerosMode} select in the cerosflex
 * dialog. Returns only the delivery modes allowed by the global
 * {@link CerosFlexModesConfig} resolved (via Sling Context-Aware Configuration)
 * for the content context being edited, so a site can be limited to a subset of
 * modes. With no stored configuration every mode is offered (the config's
 * all-{@code true} defaults); the configuration only ever <em>restricts</em>.
 */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.resourceTypes=connectors/ceros/datasources/modes",
                "sling.servlet.methods=GET"
        }
)
public class CerosModeOptionsDataSource extends SlingSafeMethodsServlet {

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        CerosFlexModesConfig config = resolveConfig(request);
        ResourceResolver resolver = request.getResourceResolver();

        List<Resource> options = new ArrayList<>();
        for (CerosDeliveryMode mode : CerosDeliveryMode.values()) {
            if (isEnabled(mode, config)) {
                options.add(option(resolver, mode.value(), mode.label()));
            }
        }

        request.setAttribute(DataSource.class.getName(), new SimpleDataSource(options.iterator()));
    }

    /**
     * Resolves the global config for the resource being edited. The dialog
     * datasource request carries the edited component path as its suffix; when
     * that exact resource doesn't exist yet (a freshly-dropped component) we walk
     * up to the nearest existing ancestor so the site context still resolves.
     * Returns {@code null} when no context can be resolved — treated as "all
     * enabled".
     */
    private CerosFlexModesConfig resolveConfig(SlingHttpServletRequest request) {
        Resource context = resolveExisting(request.getResourceResolver(),
                request.getRequestPathInfo().getSuffix());
        if (context == null) {
            return null;
        }
        ConfigurationBuilder builder = context.adaptTo(ConfigurationBuilder.class);
        return builder != null ? builder.as(CerosFlexModesConfig.class) : null;
    }

    private static Resource resolveExisting(ResourceResolver resolver, String path) {
        String current = path;
        while (current != null && current.startsWith("/")) {
            Resource resource = resolver.getResource(current);
            if (resource != null) {
                return resource;
            }
            int slash = current.lastIndexOf('/');
            if (slash <= 0) {
                break;
            }
            current = current.substring(0, slash);
        }
        return null;
    }

    private static boolean isEnabled(CerosDeliveryMode mode, CerosFlexModesConfig config) {
        if (config == null) {
            return true;
        }
        switch (mode) {
            case FETCH:
                return config.fetch();
            case STORE:
                return config.store();
            case IMPORT:
                return config.htmlImport();
            case INLINE:
                return config.inline();
            case EMBED:
                return config.embed();
            default:
                return true;
        }
    }

    private static Resource option(ResourceResolver resolver, String value, String text) {
        Map<String, Object> values = new HashMap<>();
        values.put("value", value);
        values.put("text", text);
        return new ValueMapResource(resolver, "", "nt:unstructured", new ValueMapDecorator(values));
    }
}
