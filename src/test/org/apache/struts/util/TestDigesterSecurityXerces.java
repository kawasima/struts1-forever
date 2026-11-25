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
package org.apache.struts.util;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.apache.commons.digester.Digester;

/**
 * The XML parsing policy holds with Xerces 2.6.2, as shipped in the
 * <code>WEB-INF/lib</code> of older applications. That version has no
 * secure processing feature, so entity expansion is limited through its
 * security manager.
 *
 * <p>The Maven build resolves Xerces as a test dependency, keeps it off the
 * test class path and passes its location in the system property
 * <code>struts.test.xercesImpl</code>. This test loads it with its own class
 * loader, as a webapp class loader would.</p>
 */
public class TestDigesterSecurityXerces extends TestCase {

    private static ClassLoader xercesLoader;

    private ConnectionCounter counter;
    private int connectionsBefore;
    private String dtdUrl;

    /** SecureDigester that gets its parser from Xerces 2.6.2. */
    private static class XercesDigester extends SecureDigester {
        private SAXParserFactory factory;

        public SAXParserFactory getFactory() {
            if (factory == null) {
                try {
                    factory = (SAXParserFactory) Class.forName(
                        "org.apache.xerces.jaxp.SAXParserFactoryImpl", true, xercesLoader)
                        .newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException(e.toString());
                }
                factory.setNamespaceAware(getNamespaceAware());
                factory.setValidating(getValidating());
            }
            return factory;
        }
    }

    protected void setUp() throws Exception {
        if (xercesLoader == null) {
            String jar = System.getProperty("struts.test.xercesImpl");
            assertNotNull("system property struts.test.xercesImpl is set by the"
                + " Maven build; run this test with Maven", jar);
            assertTrue(jar + " exists", new File(jar).isFile());
            xercesLoader = new URLClassLoader(new URL[] {new File(jar).toURL()}, null);
        }
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
        dtdUrl = TestDigesterSecurity.writeTempFile(".dtd", TestDigesterSecurity.TEST_DTD)
            .toURL().toString();
    }

    private static Digester xercesDigester(boolean validating) {
        Digester digester = new XercesDigester();
        digester.setValidating(validating);
        return digester;
    }

    public void testParserIsXerces262() throws Exception {
        assertTrue(xercesDigester(false).getXMLReader().getClass().getName()
            .startsWith("org.apache.xerces."));
    }

    public void testRegisteredPublicId_Validating_ReadsLocalDtd() throws Exception {
        Digester digester = xercesDigester(true);
        digester.register(TestDigesterSecurity.TEST_PUBLIC_ID, dtdUrl);
        TestDigesterSecurity.ErrorCollector errors =
            TestDigesterSecurity.collectErrors(digester);

        assertEquals("local-dtd", TestDigesterSecurity.parseSource(digester,
            TestDigesterSecurity.doctypePublic(TestDigesterSecurity.TEST_PUBLIC_ID,
                counter.url("/root.dtd")) + "<root><child/></root>"));
        assertEquals(0, errors.errors.size());
        assertEquals(connectionsBefore, counter.getCount());
    }

    public void testUnregisteredDtd_IsRejectedWithoutConnection() throws Exception {
        TestDigesterSecurity.assertRejected(xercesDigester(false),
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertEquals(connectionsBefore, counter.getCount());
    }

    public void testExternalGeneralEntity_IsRejectedWithoutConnection() throws Exception {
        TestDigesterSecurity.assertRejected(xercesDigester(false), "<!DOCTYPE root [\n"
            + "  <!ENTITY xxe SYSTEM \"" + counter.url("/entity") + "\">\n"
            + "]>\n<root>&xxe;</root>");
        assertEquals(connectionsBefore, counter.getCount());
    }

    /** Xerces 2.6.2 has no expansion limit unless one is installed. */
    public void testInternalEntityExpansion_IsLimited() throws Exception {
        StringBuffer doc = new StringBuffer("<!DOCTYPE root [\n<!ENTITY e0 \"x\">\n");
        for (int i = 1; i <= 6; i++) {
            doc.append("<!ENTITY e" + i + " \"");
            for (int j = 0; j < 10; j++) {
                doc.append("&e" + (i - 1) + ";");
            }
            doc.append("\">\n");
        }
        doc.append("]>\n<root>&e6;</root>");

        TestDigesterSecurity.assertRejected(xercesDigester(false), doc.toString());
    }
}
