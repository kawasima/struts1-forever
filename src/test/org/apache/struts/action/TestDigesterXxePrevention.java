/*
 * Copyright 2026 The Struts1-Forever Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.struts.action;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;

import junit.framework.TestCase;

import org.apache.commons.digester.Digester;
import org.apache.struts.Globals;
import org.apache.struts.config.ModuleConfig;
import org.apache.struts.config.ModuleConfigFactory;
import org.apache.struts.mock.MemoryServletContext;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.util.ConnectionCounter;
import org.apache.struts.util.DigesterSecurity;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XML parsing in {@code ActionServlet} accepts the standard Struts and
 * Servlet DOCTYPE declarations, validates as configured, and never reads an
 * external resource that a document names but Struts did not register
 * (GHSA-h36r-7whm-p5hf, issue #3).
 */
public class TestDigesterXxePrevention extends TestCase {

    private static final String STRUTS_12 =
        "-//Apache Software Foundation//DTD Struts Configuration 1.2//EN";
    private static final String WEB_APP_22 =
        "-//Sun Microsystems, Inc.//DTD Web Application 2.2//EN";
    private static final String UNKNOWN = "-//Unknown//DTD Unknown//EN";

    private ConnectionCounter counter;
    private int connectionsBefore;
    private MemoryServletContext context;
    private MockServletConfig config;
    private TestableActionServlet servlet;

    /**
     * ActionServlet that provides a MockServletConfig without triggering the
     * full init() lifecycle.
     */
    private static class TestableActionServlet extends ActionServlet {
        private final ServletConfig servletConfig;

        TestableActionServlet(ServletConfig config) {
            this.servletConfig = config;
        }

        public ServletConfig getServletConfig() {
            return servletConfig;
        }
    }

    protected void setUp() throws Exception {
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
        context = new MemoryServletContext();
        config = new MockServletConfig(context);
        servlet = new TestableActionServlet(config);
        servlet.initInternal();
    }

    protected void tearDown() throws Exception {
        servlet.destroyInternal();
    }

    private void assertNoConnection() {
        assertEquals("connections to the system ID",
            connectionsBefore, counter.getCount());
    }

    // ------------------------------------------------------------------
    // DTDs that ActionServlet registers
    // ------------------------------------------------------------------

    /**
     * Every DTD in {@code registrations} (Struts 1.0-1.2, Servlet 2.2-2.3)
     * resolves to its local copy whatever system ID the document gives.
     */
    public void testRegisteredDtds_ResolveLocallyWithoutConnection() throws Exception {
        Digester digester = servlet.initConfigDigester();
        DigesterSecurity.secure(digester);

        for (int i = 0; i < servlet.registrations.length; i += 2) {
            InputSource source = digester.getEntityResolver().resolveEntity(
                servlet.registrations[i], counter.url("/remote.dtd"));
            assertNotNull(servlet.registrations[i], source);
            source.getByteStream().close();
        }
        assertNoConnection();
    }

    // ------------------------------------------------------------------
    // struts-config.xml
    // ------------------------------------------------------------------

    public void testModuleConfig_Struts12Doctype_IsParsedWithoutConnection()
            throws Exception {
        List errors = new ArrayList();
        ModuleConfig moduleConfig = parseModuleConfig(
            doctype("struts-config", STRUTS_12)
            + "<struts-config><action-mappings>"
            + "<action path=\"/test\" type=\"org.apache.struts.actions.ForwardAction\""
            + " parameter=\"/test.jsp\"/>"
            + "</action-mappings></struts-config>", errors);

        assertNotNull(moduleConfig.findActionConfig("/test"));
        assertEquals(0, errors.size());
        assertNoConnection();
    }

    /**
     * The {@code validating} init parameter defaults to true, so a document
     * that breaks its DTD is reported to the Digester's error handler.
     */
    public void testModuleConfig_ValidatesByDefault() throws Exception {
        List errors = new ArrayList();
        parseModuleConfig(doctype("struts-config", STRUTS_12)
            + "<struts-config><undeclared/></struts-config>", errors);

        assertTrue("validation error reported", errors.size() > 0);
    }

    /**
     * A subclass may still change the Digester that
     * {@code initConfigDigester()} returned before the config is parsed.
     */
    public void testModuleConfig_ValidatingChangedAfterInitConfigDigester_IsHonoured()
            throws Exception {
        servlet.initConfigDigester().setValidating(false);
        List errors = new ArrayList();
        parseModuleConfig(doctype("struts-config", STRUTS_12)
            + "<struts-config><undeclared/></struts-config>", errors);

        assertEquals(0, errors.size());
    }

    public void testModuleConfig_UnregisteredDtd_IsRejectedWithoutConnection()
            throws Exception {
        try {
            parseModuleConfig(doctype("struts-config", UNKNOWN) + "<struts-config/>",
                new ArrayList());
            fail("struts-config.xml with an unregistered DTD must be rejected");
        } catch (UnavailableException e) {
            // expected
        }
        assertNoConnection();
    }

    // ------------------------------------------------------------------
    // web.xml
    // ------------------------------------------------------------------

    public void testWebXml_Servlet22Doctype_IsParsedWithoutConnection()
            throws Exception {
        context.put("/WEB-INF/web.xml", doctype("web-app", WEB_APP_22)
            + "<web-app><servlet-mapping><servlet-name>action</servlet-name>"
            + "<url-pattern>*.do</url-pattern></servlet-mapping></web-app>");

        servlet.initServlet();

        assertEquals("*.do", context.getAttribute(Globals.SERVLET_KEY));
        assertNoConnection();
    }

    public void testWebXml_UnregisteredDtd_IsRejectedWithoutConnection()
            throws Exception {
        context.put("/WEB-INF/web.xml", doctype("web-app", UNKNOWN) + "<web-app/>");

        try {
            servlet.initServlet();
            fail("web.xml with an unregistered DTD must be rejected");
        } catch (ServletException e) {
            // expected
        }
        assertNoConnection();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String doctype(String root, String publicId) {
        return "<!DOCTYPE " + root + " PUBLIC \"" + publicId + "\" \""
            + counter.url("/remote.dtd") + "\">\n";
    }

    private ModuleConfig parseModuleConfig(String xml, final List errors)
            throws Exception {
        context.put("/WEB-INF/struts-config.xml", xml);

        ModuleConfig moduleConfig =
            ModuleConfigFactory.createFactory().createModuleConfig("");
        Digester digester = servlet.initConfigDigester();
        digester.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) {
            }

            public void error(SAXParseException e) {
                errors.add(e);
            }

            public void fatalError(SAXParseException e) throws SAXException {
                throw e;
            }
        });
        digester.push(moduleConfig);
        servlet.parseModuleConfigFile(digester, "/WEB-INF/struts-config.xml");
        return moduleConfig;
    }
}
