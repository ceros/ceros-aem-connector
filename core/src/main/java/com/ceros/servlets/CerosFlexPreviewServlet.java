package com.ceros.servlets;

import com.ceros.delivery.DeliveryResult;
import com.ceros.models.CerosFlexView;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Standalone preview render of a Ceros Flex component. Bound to selector
 * {@code preview} + extension {@code html} on the cerosflex resource type, so
 * the URL is {@code <componentPath>.preview.html}. Emits a minimal HTML page
 * containing only the experience (CSS + body + SSR scripts) — no AEM page
 * chrome — used as the iframe src for the author-mode store preview so the
 * editor's click overlay stays isolated from the SPA router running inside.
 *
 * <p>Implemented as a Java servlet rather than an HTL script because new HTL
 * files in {@code /apps} aren't picked up by Sling's BundledScriptTracker
 * until the host package is rebuilt with the script-metadata generator. A
 * servlet bound by resource type wins over script resolution unconditionally.
 * </p>
 */
@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.resourceTypes=connectors/ceros/components/cerosflex",
                "sling.servlet.selectors=preview",
                "sling.servlet.extensions=html",
                "sling.servlet.methods=GET"
        }
)
public class CerosFlexPreviewServlet extends SlingSafeMethodsServlet {

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws IOException {
        Resource resource = request.getResource();
        CerosFlexView view = request.adaptTo(CerosFlexView.class);
        if (view == null || !view.isConfigured() || !view.isHasContent()) {
            response.setStatus(SlingHttpServletResponse.SC_NO_CONTENT);
            return;
        }

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\"><head>");
        out.println("<meta charset=\"UTF-8\"/>");
        out.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        out.println("<title>Ceros experience preview</title>");
        out.println("<style>html,body{margin:0;padding:0;background:#fff;}</style>");

        for (DeliveryResult.CssLink css : view.getCssLinks()) {
            if (css.getUrl() == null) continue;
            if (css.isSameOrigin()) {
                out.printf("<link rel=\"stylesheet\" href=\"%s\"/>%n", escAttr(css.getUrl()));
            } else {
                out.printf("<link rel=\"stylesheet\" href=\"%s\" integrity=\"%s\" crossorigin=\"anonymous\"/>%n",
                        escAttr(css.getUrl()), escAttr(css.getIntegrity()));
            }
        }
        for (DeliveryResult.ScriptRef s : view.getHeadScripts()) {
            writeScript(out, s);
        }

        out.println("</head><body>");

        if (StringUtils.isNotBlank(view.getHtmlContent())) {
            out.printf("<div class=\"cmp-cerosflex__content\" data-flex-manifest-url=\"%s\">%n",
                    escAttr(view.getManifestUrl()));
            // Manifest body content is trusted Ceros HTML — already emitted
            // unescaped by the main HTL via @context='unsafe'.
            out.println(view.getHtmlContent());
            out.println("</div>");
        }

        // Brand-kit styles — after the html-body so the theme's font rules win
        // over the body's per-element defaults (mirrors the main HTL).
        for (String inlineStyle : view.getInlineStyles()) {
            out.printf("<style>%s</style>%n", inlineStyle);
        }

        for (DeliveryResult.ScriptRef s : view.getBodyScripts()) {
            writeScript(out, s);
        }

        out.println("</body></html>");
    }

    private static void writeScript(PrintWriter out, DeliveryResult.ScriptRef s) {
        if (s.isInline()) {
            String idAttr = s.getScriptId() != null ? " id=\"" + escAttr(s.getScriptId()) + "\"" : "";
            String typeAttr = s.getContentType() != null ? " type=\"" + escAttr(s.getContentType()) + "\"" : "";
            out.printf("<script%s%s>%s</script>%n", typeAttr, idAttr,
                    s.getContent() != null ? s.getContent() : "");
            return;
        }
        if (s.getUrl() == null) {
            return;
        }
        String typeAttr = s.isModule() ? " type=\"module\"" : "";
        out.printf("<script src=\"%s\"%s defer></script>%n", escAttr(s.getUrl()), typeAttr);
    }

    private static String escAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
