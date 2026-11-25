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
package org.apache.struts.plugins;

import java.util.List;

import javax.servlet.ServletException;

import junit.framework.TestCase;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.xmlrules.DigesterRuleParser;
import org.apache.commons.digester.xmlrules.XmlLoadException;
import org.apache.struts.mock.MemoryServletContext;
import org.apache.struts.mock.MockActionServlet;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.util.ConnectionCounter;

/**
 * Both documents that {@code DigestingPlugIn} parses, the data file and the
 * optional digester rules file, may declare a registered DTD and cannot make
 * the parser read an unregistered external resource (issue #3).
 */
public class TestDigestingPlugIn extends TestCase {

    private static final String DATA_PUBLIC_ID = "-//Struts1 Forever//DTD Test Beans//EN";

    private static final String RULES =
        "<digester-rules><object-create-rule pattern=\"beans\""
        + " classname=\"java.util.ArrayList\"/></digester-rules>";

    private ConnectionCounter counter;
    private int connectionsBefore;
    private MemoryServletContext context;
    private MockActionServlet servlet;

    /** Registers a DTD for the data file, as applications may do. */
    private static class RegisteringPlugIn extends DigestingPlugIn {
        protected Digester newDigesterInstance() {
            Digester digester = new Digester();
            digester.register(DATA_PUBLIC_ID,
                getClass().getResource(
                    "/org/apache/struts/resources/web-app_2_2.dtd").toString());
            digester.addObjectCreate("web-app", "java.util.ArrayList");
            return digester;
        }
    }

    protected void setUp() throws Exception {
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
        context = new MemoryServletContext();
        servlet = new MockActionServlet(context, new MockServletConfig(context));
    }

    public void testDataFile_ApplicationRegisteredDtd_IsParsedWithoutConnection()
            throws Exception {
        context.put("/data.xml", doctype("web-app", DATA_PUBLIC_ID) + "<web-app/>");
        DigestingPlugIn plugIn = new RegisteringPlugIn();

        init(plugIn, null);

        assertTrue(context.getAttribute("result") instanceof List);
        assertNoConnection();
    }

    public void testDataFile_UnregisteredDtd_IsRejectedWithoutConnection()
            throws Exception {
        context.put("/data.xml",
            doctype("web-app", "-//Unknown//DTD Unknown//EN") + "<web-app/>");

        try {
            init(new RegisteringPlugIn(), null);
            fail("a data file with an unregistered DTD must be rejected");
        } catch (ServletException e) {
            // expected
        }
        assertNoConnection();
    }

    public void testRulesFile_DigesterRulesDtd_IsParsedWithoutConnection()
            throws Exception {
        context.put("/rules.xml",
            doctype("digester-rules", DigesterRuleParser.DIGESTER_PUBLIC_ID) + RULES);
        context.put("/data.xml", "<beans/>");

        init(new DigestingPlugIn(), "/rules.xml");

        assertTrue(context.getAttribute("result") instanceof List);
        assertNoConnection();
    }

    public void testRulesFile_UnregisteredDtd_IsRejectedWithoutConnection()
            throws Exception {
        context.put("/rules.xml",
            doctype("digester-rules", "-//Unknown//DTD Unknown//EN") + RULES);
        context.put("/data.xml", "<beans/>");

        // DigesterLoader reports a rules file that cannot be parsed with
        // XmlLoadException, as it did before the rules parser was secured.
        try {
            init(new DigestingPlugIn(), "/rules.xml");
            fail("a rules file with an unregistered DTD must be rejected");
        } catch (XmlLoadException e) {
            // expected
        }
        assertNoConnection();
    }

    private void init(DigestingPlugIn plugIn, String rulesPath) throws Exception {
        plugIn.setKey("result");
        plugIn.setConfigPath("/data.xml");
        if (rulesPath != null) {
            plugIn.setDigesterPath(rulesPath);
        }
        plugIn.init(servlet, null);
    }


    private void assertNoConnection() {
        assertEquals("connections to the system ID",
            connectionsBefore, counter.getCount());
    }

    private String doctype(String root, String publicId) {
        return "<!DOCTYPE " + root + " PUBLIC \"" + publicId + "\" \""
            + counter.url("/remote.dtd") + "\">\n";
    }
}
