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

import javax.servlet.ServletException;

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
 * Unit tests for {@link SwitchAction}.
 *
 * <h3>C-1: Path Traversal in SwitchAction</h3>
 *
 * <p>SwitchAction forwards to the {@code page} request parameter. A value
 * that reaches a server-side resource such as {@code /WEB-INF/web.xml} must
 * be rejected with a {@code ServletException}.</p>
 */
public class TestSwitchAction extends TestCase {

    private MockServletContext servletContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SwitchAction action;
    private ActionMapping mapping;

    protected void setUp() throws Exception {
        super.setUp();

        servletContext = new MockServletContext();

        ModuleConfig defaultModule = new ModuleConfigImpl("");
        servletContext.setAttribute(Globals.MODULE_KEY + "", defaultModule);
        servletContext.setAttribute(Globals.MODULE_PREFIXES_KEY, new String[0]);

        MockServletConfig servletConfig = new MockServletConfig();
        MockActionServlet servlet = new MockActionServlet(servletContext, servletConfig);

        action = new SwitchAction();
        action.setServlet(servlet);

        mapping = new ActionMapping();

        MockHttpSession session = new MockHttpSession();
        request = new MockHttpServletRequest(session);
        response = new MockHttpServletResponse();
    }

    // ------------------------------------------------------------------
    // Normal operation
    // ------------------------------------------------------------------

    public void testPage_ValidPath_ForwardsToPage() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/welcome.jsp");

        ActionForward forward = action.execute(mapping, null, request, response);

        assertNotNull(forward);
        assertEquals("/welcome.jsp", forward.getPath());
    }

    public void testPage_Missing_ThrowsServletException() {
        request.addParameter("prefix", "");
        // no page parameter

        try {
            action.execute(mapping, null, request, response);
            fail("Should throw ServletException when page parameter is missing");
        } catch (ServletException e) {
            // expected
        } catch (Exception e) {
            fail("Expected ServletException but got: " + e.getClass().getName());
        }
    }

    public void testPrefix_Missing_ThrowsServletException() {
        request.addParameter("page", "/welcome.jsp");
        // no prefix parameter

        try {
            action.execute(mapping, null, request, response);
            fail("Should throw ServletException when prefix parameter is missing");
        } catch (ServletException e) {
            // expected
        } catch (Exception e) {
            fail("Expected ServletException but got: " + e.getClass().getName());
        }
    }

    public void testPrefix_Unknown_ThrowsException() {
        request.addParameter("prefix", "/nonexistent");
        request.addParameter("page", "/index.jsp");

        try {
            action.execute(mapping, null, request, response);
            fail("Should throw an exception for unknown module prefix");
        } catch (Exception e) {
            // expected — SwitchAction throws ServletException or propagates
            // NullPointerException when the module prefix is unknown
        }
    }

    // ------------------------------------------------------------------
    // Path traversal — required behaviour: reject with ServletException (C-1)
    // ------------------------------------------------------------------

    /**
     * page=/WEB-INF/web.xml must be rejected (C-1).
     * Currently FAILS — SwitchAction performs no page validation.
     */
    public void testPage_WebInf_IsRejected() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/WEB-INF/web.xml");

        try {
            action.execute(mapping, null, request, response);
            fail("page=/WEB-INF/web.xml must be rejected with ServletException (C-1)");
        } catch (ServletException e) {
            // expected once the vulnerability is fixed
        }
    }

    /**
     * page=/../WEB-INF/web.xml must be rejected (C-1).
     * Currently FAILS — SwitchAction performs no page validation.
     */
    public void testPage_DotDotTraversal_IsRejected() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/../WEB-INF/web.xml");

        try {
            action.execute(mapping, null, request, response);
            fail("page with ../ traversal must be rejected with ServletException (C-1)");
        } catch (ServletException e) {
            // expected once the vulnerability is fixed
        }
    }

    /**
     * page=/META-INF/context.xml must be rejected (C-1).
     * Currently FAILS — SwitchAction performs no page validation.
     */
    public void testPage_MetaInf_IsRejected() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/META-INF/context.xml");

        try {
            action.execute(mapping, null, request, response);
            fail("page=/META-INF/context.xml must be rejected with ServletException (C-1)");
        } catch (ServletException e) {
            // expected once the vulnerability is fixed
        }
    }

    /**
     * page=../WEB-INF/web.xml (relative, no leading slash) must be rejected (C-1).
     * Currently FAILS — SwitchAction performs no page validation.
     */
    public void testPage_RelativeDotDotTraversal_IsRejected() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "../WEB-INF/web.xml");

        try {
            action.execute(mapping, null, request, response);
            fail("Relative path traversal must be rejected with ServletException (C-1)");
        } catch (ServletException e) {
            // expected once the vulnerability is fixed
        }
    }

    /**
     * page=/%2E%2E/WEB-INF/web.xml (URL-encoded traversal) must be rejected (C-1).
     * Currently FAILS — SwitchAction performs no page validation.
     *
     * <p>Servlet containers decode percent-encoded sequences before passing
     * them to getParameter(), so the raw value seen by SwitchAction is
     * {@code /../WEB-INF/web.xml}.</p>
     */
    public void testPage_UrlEncodedDotDotTraversal_IsRejected() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/%2E%2E/WEB-INF/web.xml");

        try {
            action.execute(mapping, null, request, response);
            fail("URL-encoded path traversal must be rejected with ServletException (C-1)");
        } catch (ServletException e) {
            // expected once the vulnerability is fixed
        }
    }

    /**
     * page=//WEB-INF/web.xml (double-slash prefix) must be rejected (C-1).
     * Currently FAILS — SwitchAction performs no page validation.
     */
    public void testPage_DoubleSlashWebInf_IsRejected() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "//WEB-INF/web.xml");

        try {
            action.execute(mapping, null, request, response);
            fail("Double-slash WEB-INF path must be rejected with ServletException (C-1)");
        } catch (ServletException e) {
            // expected once the vulnerability is fixed
        }
    }

    /**
     * page=/web-inf/web.xml (lower-case) must be rejected (C-1).
     * Currently FAILS — SwitchAction performs no page validation.
     *
     * <p>Case-insensitive file systems (e.g. Windows, macOS) resolve
     * {@code /web-inf/} to the same directory as {@code /WEB-INF/}.</p>
     */
    public void testPage_LowerCaseWebInf_IsRejected() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/web-inf/web.xml");

        try {
            action.execute(mapping, null, request, response);
            fail("Lower-case WEB-INF path must be rejected with ServletException (C-1)");
        } catch (ServletException e) {
            // expected once the vulnerability is fixed
        }
    }

    // ------------------------------------------------------------------
    // Normal operation — additional boundary cases
    // ------------------------------------------------------------------

    public void testPage_WithQueryString_ForwardsWithQueryString() throws Exception {
        request.addParameter("prefix", "");
        request.addParameter("page", "/search.jsp?q=test");

        ActionForward forward = action.execute(mapping, null, request, response);

        assertNotNull(forward);
        assertEquals("/search.jsp?q=test", forward.getPath());
    }

    public void testPage_Empty_DoesNotThrowUnexpectedException() {
        request.addParameter("prefix", "");
        request.addParameter("page", "");

        try {
            action.execute(mapping, null, request, response);
        } catch (ServletException e) {
            // Acceptable: action rejects empty page.
        } catch (Exception e) {
            fail("Unexpected exception type: " + e.getClass().getName());
        }
    }
}
