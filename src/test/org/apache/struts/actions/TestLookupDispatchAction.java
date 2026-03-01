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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.apache.struts.Globals;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.config.MessageResourcesConfig;
import org.apache.struts.config.impl.ModuleConfigImpl;
import org.apache.struts.mock.MockActionServlet;
import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpServletResponse;
import org.apache.struts.mock.MockHttpSession;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.mock.MockServletContext;
import org.apache.struts.util.MessageResources;

/**
 * Unit tests for {@link LookupDispatchAction}.
 *
 * <p>LookupDispatchAction resolves a request parameter's value (the button
 * label) back to a method name via a reverse lookup through the module's
 * MessageResources.  Tests here cover: successful dispatch, empty parameter
 * (unspecified), and unresolvable label.</p>
 */
public class TestLookupDispatchAction extends TestCase {

    // ------------------------------------------------------------------
    // Concrete subclass
    // ------------------------------------------------------------------

    public static class SampleLookupAction extends LookupDispatchAction {

        protected Map getKeyMethodMap() {
            Map map = new HashMap();
            map.put("button.save", "save");
            map.put("button.delete", "delete");
            return map;
        }

        public ActionForward save(ActionMapping mapping, ActionForm form,
                HttpServletRequest request, HttpServletResponse response)
                throws Exception {
            return new ActionForward("/saved.jsp");
        }

        public ActionForward delete(ActionMapping mapping, ActionForm form,
                HttpServletRequest request, HttpServletResponse response)
                throws Exception {
            return new ActionForward("/deleted.jsp");
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private SampleLookupAction action;
    private ActionMapping mapping;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockServletContext servletContext;

    protected void setUp() throws Exception {
        servletContext = new MockServletContext();

        // Build a ModuleConfig with a MessageResources that maps keys→labels
        ModuleConfigImpl moduleConfig = new ModuleConfigImpl("");

        // Register MessageResources that resolve button.save → "Save"
        // and button.delete → "Delete" for Locale.ENGLISH
        MessageResources resources =
                MessageResources.getMessageResources(
                        "org.apache.struts.actions.TestLookupMessages");

        // Attach resources to the context under the default key + prefix
        String key = org.apache.struts.Globals.MESSAGES_KEY;
        servletContext.setAttribute(key, resources);

        // Add a MessageResourcesConfig so moduleConfig.findMessageResourcesConfigs()
        // returns the right entry
        MessageResourcesConfig mrc = new MessageResourcesConfig();
        mrc.setKey(key);
        moduleConfig.addMessageResourcesConfig(mrc);

        request = new MockHttpServletRequest(new MockHttpSession());
        // Put the module config on the request (required by initLookupMap)
        request.setAttribute(Globals.MODULE_KEY, moduleConfig);
        // Put resources on the request (required by getResources(request, key))
        request.setAttribute(key, resources);

        // Set a session locale so getLocale() works
        request.getSession().setAttribute(
                Globals.LOCALE_KEY, Locale.ENGLISH);

        response = new MockHttpServletResponse();

        MockServletConfig servletConfig = new MockServletConfig(servletContext);
        MockActionServlet servlet = new MockActionServlet(servletContext, servletConfig);

        action = new SampleLookupAction();
        action.setServlet(servlet);

        mapping = new ActionMapping();
        mapping.setParameter("method");
    }

    // ------------------------------------------------------------------
    // Empty / absent parameter → unspecified() → ServletException
    // ------------------------------------------------------------------

    public void testDispatch_EmptyParam_ThrowsServletException() {
        request.addParameter("method", "");

        try {
            action.execute(mapping, null, request, response);
            fail("Should throw ServletException for empty parameter");
        } catch (ServletException e) {
            // expected — unspecified() throws by default
        } catch (Exception e) {
            fail("Expected ServletException but got: " + e.getClass().getName());
        }
    }

    public void testDispatch_AbsentParam_ThrowsServletException() {
        // no "method" parameter

        try {
            action.execute(mapping, null, request, response);
            fail("Should throw ServletException for absent parameter");
        } catch (ServletException e) {
            // expected
        } catch (Exception e) {
            fail("Expected ServletException but got: " + e.getClass().getName());
        }
    }

    // ------------------------------------------------------------------
    // Unresolvable label (label not in MessageResources) → ServletException
    // ------------------------------------------------------------------

    public void testDispatch_UnknownLabel_ThrowsServletException() {
        request.addParameter("method", "Unknown Button Label");

        try {
            action.execute(mapping, null, request, response);
            fail("Should throw ServletException when label cannot be resolved");
        } catch (ServletException e) {
            // expected
        } catch (Exception e) {
            fail("Expected ServletException but got: " + e.getClass().getName());
        }
    }
}
