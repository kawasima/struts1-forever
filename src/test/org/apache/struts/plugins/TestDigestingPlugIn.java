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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import javax.servlet.ServletException;

import junit.framework.TestCase;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.xmlrules.DigesterLoader;
import org.apache.commons.digester.xmlrules.DigesterRuleParser;
import org.apache.commons.digester.xmlrules.DigesterRulesSource;
import org.apache.commons.digester.xmlrules.XmlLoadException;
import org.apache.struts.mock.MemoryServletContext;
import org.apache.struts.mock.MockActionServlet;
import org.apache.struts.mock.MockServletConfig;
import org.apache.struts.util.ConnectionCounter;
import org.apache.struts.util.SecureDigesterLoader;

/**
 * Every document that {@code DigestingPlugIn} parses (the data file, the
 * digester rules file and the rules files it includes) may declare a
 * registered DTD and cannot make the parser read an unregistered external
 * resource (issue #3).
 */
public class TestDigestingPlugIn extends TestCase {

    private static final String DATA_PUBLIC_ID = "-//Struts1 Forever//DTD Test Beans//EN";

    private static final String RULES =
        "<digester-rules><object-create-rule pattern=\"beans\""
        + " classname=\"java.util.ArrayList\"/></digester-rules>";

    /** Rules files for the include tests, on the context class loader. */
    private static File includeDir;

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

    /** Adds rules from code, for &lt;include class="..."/&gt;. */
    public static class ItemRules implements DigesterRulesSource {
        public void getRules(Digester digester) {
            digester.addObjectCreate("item", "java.util.HashMap");
            digester.addSetNext("item", "add");
        }
    }

    protected void setUp() throws Exception {
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
        context = new MemoryServletContext();
        servlet = new MockActionServlet(context, new MockServletConfig(context));
        if (includeDir == null) {
            includeDir = writeIncludeFiles();
        }
    }

    /**
     * Rules files whose names say what they hold: ok-* chain two levels of
     * includes inside a pattern, bad1-* has an unregistered DTD in the first
     * included file, bad2-* in the second, loop includes itself.
     */
    private File writeIncludeFiles() throws IOException {
        File dir = File.createTempFile("struts-include", "");
        dir.delete();
        dir.mkdir();
        dir.deleteOnExit();
        String registered = doctype("digester-rules", DigesterRuleParser.DIGESTER_PUBLIC_ID);
        String unknown = doctype("digester-rules", "-//Unknown//DTD Unknown//EN");

        write(dir, "ok-root.xml", registered + "<digester-rules>"
            + "<object-create-rule pattern=\"beans\" classname=\"java.util.ArrayList\"/>"
            + "<pattern value=\"beans\"><include path=\"ok-1.xml\"/></pattern>"
            + "</digester-rules>");
        write(dir, "ok-1.xml", registered
            + "<digester-rules><include path=\"ok-2.xml\"/></digester-rules>");
        write(dir, "ok-2.xml", registered + "<digester-rules><pattern value=\"item\">"
            + "<object-create-rule classname=\"java.util.HashMap\"/>"
            + "<set-next-rule methodname=\"add\"/></pattern></digester-rules>");

        write(dir, "class-root.xml", registered + "<digester-rules>"
            + "<object-create-rule pattern=\"beans\" classname=\"java.util.ArrayList\"/>"
            + "<pattern value=\"beans\"><include class=\"" + ItemRules.class.getName()
            + "\"/></pattern></digester-rules>");

        write(dir, "bad1-root.xml", registered
            + "<digester-rules><include path=\"bad1-1.xml\"/></digester-rules>");
        write(dir, "bad1-1.xml", unknown + "<digester-rules/>");

        write(dir, "bad2-root.xml", registered
            + "<digester-rules><include path=\"bad2-1.xml\"/></digester-rules>");
        write(dir, "bad2-1.xml", registered
            + "<digester-rules><include path=\"bad2-2.xml\"/></digester-rules>");
        write(dir, "bad2-2.xml", unknown + "<digester-rules/>");

        write(dir, "loop.xml", registered
            + "<digester-rules><include path=\"loop.xml\"/></digester-rules>");
        return dir;
    }

    private static void write(File dir, String name, String xml) throws IOException {
        File file = new File(dir, name);
        file.deleteOnExit();
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xml)
                .getBytes("UTF-8"));
        } finally {
            out.close();
        }
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

    // ------------------------------------------------------------------
    // Rules files included from the rules file
    // ------------------------------------------------------------------

    public void testIncludedRules_TwoLevelsInPattern_IsParsedWithoutConnection()
            throws Exception {
        context.put("/data.xml", "<beans><item/><item/></beans>");

        initWithIncludes("ok-root.xml");

        assertEquals("[{}, {}]", context.getAttribute("result").toString());
        assertNoConnection();
    }

    /** The replaced include rule gives the rules that Commons Digester gives. */
    public void testIncludedRules_BehaveLikeDigesterLoader() throws Exception {
        URL rules = new File(includeDir, "ok-root.xml").toURL();
        ClassLoader previous = useIncludeDir();
        try {
            Object expected = DigesterLoader.createDigester(rules)
                .parse(new java.io.StringReader("<beans><item/><item/></beans>"));
            Object actual = SecureDigesterLoader.createDigester(rules)
                .parse(new java.io.StringReader("<beans><item/><item/></beans>"));
            assertEquals(expected, actual);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public void testIncludedClassRules_AreAddedInPattern() throws Exception {
        context.put("/data.xml", "<beans><item/></beans>");

        initWithIncludes("class-root.xml");

        assertEquals("[{}]", context.getAttribute("result").toString());
    }

    public void testIncludedRules_UnregisteredDtd_IsRejectedWithoutConnection()
            throws Exception {
        assertIncludeRejected("bad1-root.xml");
        assertNoConnection();
    }

    public void testRulesIncludedTwoLevelsDown_UnregisteredDtd_IsRejectedWithoutConnection()
            throws Exception {
        assertIncludeRejected("bad2-root.xml");
        assertNoConnection();
    }

    public void testCircularInclude_IsRejected() throws Exception {
        assertIncludeRejected("loop.xml");
    }

    private void initWithIncludes(String rootRules) throws Exception {
        DigestingPlugIn plugIn = new DigestingPlugIn();
        plugIn.setDigesterSource("file");
        ClassLoader previous = useIncludeDir();
        try {
            init(plugIn, new File(includeDir, rootRules).getAbsolutePath());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private void assertIncludeRejected(String rootRules) throws Exception {
        context.put("/data.xml", "<beans/>");
        try {
            initWithIncludes(rootRules);
            fail("rules must be rejected");
        } catch (XmlLoadException e) {
            // expected, as DigesterLoader reports it
        }
    }

    /** Included rules files are looked up on the context class loader. */
    private ClassLoader useIncludeDir() throws IOException {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(
            new URLClassLoader(new URL[] {includeDir.toURL()}, previous));
        return previous;
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
