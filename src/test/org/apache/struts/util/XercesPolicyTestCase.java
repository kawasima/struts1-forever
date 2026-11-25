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
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.apache.commons.digester.Digester;
import org.xml.sax.XMLReader;

/**
 * Checks of the XML parsing policy with an Apache Xerces version, as shipped
 * in the <code>WEB-INF/lib</code> of applications. Subclasses name the
 * version.
 *
 * <p>The Maven build copies the Xerces JARs to the directory in the system
 * property <code>struts.test.parsers</code>. They are loaded with their own
 * class loader, as a webapp class loader would; on the test class path they
 * would replace the JDK parser for every test.</p>
 */
public abstract class XercesPolicyTestCase extends TestCase {

    private static final String SECURITY_MANAGER =
        "http://apache.org/xml/properties/security-manager";

    private static final Map loaders = new HashMap();

    private ClassLoader xercesLoader;
    private ConnectionCounter counter;
    private int connectionsBefore;
    private String dtdUrl;

    /** @return the Xerces version, such as "2.6.2" */
    protected abstract String version();

    /** SecureDigester that gets its parser from the Xerces under test. */
    private class XercesDigester extends SecureDigester {
        private SAXParserFactory factory;

        public SAXParserFactory getFactory() {
            if (factory == null) {
                factory = newFactory();
                factory.setNamespaceAware(getNamespaceAware());
                factory.setValidating(getValidating());
            }
            return factory;
        }
    }

    protected void setUp() throws Exception {
        synchronized (loaders) {
            xercesLoader = (ClassLoader) loaders.get(version());
            if (xercesLoader == null) {
                String dir = System.getProperty("struts.test.parsers");
                assertNotNull("system property struts.test.parsers is set by the"
                    + " Maven build; run this test with Maven", dir);
                File jar = new File(dir, "xercesImpl-" + version() + ".jar");
                assertTrue(jar + " exists", jar.isFile());
                xercesLoader = new URLClassLoader(new URL[] {jar.toURL()}, null);
                loaders.put(version(), xercesLoader);
            }
        }
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
        dtdUrl = TestDigesterSecurity.writeTempFile(".dtd", TestDigesterSecurity.TEST_DTD)
            .toURL().toString();
    }

    private SAXParserFactory newFactory() {
        try {
            return (SAXParserFactory) Class.forName(
                "org.apache.xerces.jaxp.SAXParserFactoryImpl", true, xercesLoader)
                .newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e.toString());
        }
    }

    private XMLReader newReader() throws Exception {
        return newFactory().newSAXParser().getXMLReader();
    }

    private Digester xercesDigester(boolean validating) {
        Digester digester = new XercesDigester();
        digester.setValidating(validating);
        return digester;
    }

    public void testParserIsXercesUnderTest() throws Exception {
        assertTrue(xercesDigester(false).getXMLReader().getClass().getName()
            .startsWith("org.apache.xerces."));
        assertTrue(xercesLoader.getResource("org/apache/xerces/util/SecurityManager.class")
            .toString().indexOf("xercesImpl-" + version()) >= 0);
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

    public void testInternalEntityExpansion_IsLimited() throws Exception {
        TestDigesterSecurity.assertRejected(xercesDigester(false),
            TestDigesterSecurity.billionLaughs(5, "<root>&e5;</root>"));
    }

    public void testInternalEntityExpansionInAttribute_IsLimited() throws Exception {
        TestDigesterSecurity.assertRejected(xercesDigester(false),
            TestDigesterSecurity.billionLaughs(5, "<root a=\"&e5;\"/>"));
    }

    /** Xerces limits only the count, so the length of each entity is limited. */
    public void testInternalEntity_OverLengthLimit_IsRejected() throws Exception {
        TestDigesterSecurity.assertRejected(xercesDigester(false),
            TestDigesterSecurity.entityOfLength(DigesterSecurity.MAX_ENTITY_LENGTH + 1));
    }

    /** A parser set to continue after fatal errors still stops at the limit. */
    public void testContinueAfterFatalError_ExpansionLimitStillStopsParsing()
            throws Exception {
        Digester digester = xercesDigester(false);
        digester.getXMLReader().setFeature(
            "http://apache.org/xml/features/continue-after-fatal-error", true);

        TestDigesterSecurity.assertRejected(digester,
            TestDigesterSecurity.billionLaughs(5, "<root>&e5;</root>"));
    }

    /**
     * Without a security manager Xerces does not limit expansions; the one
     * Struts installs allows exactly MAX_ENTITY_EXPANSIONS (Xerces allows
     * limit + 1).
     */
    public void testNoSecurityManager_AllowsExactlyMaxExpansions() throws Exception {
        XMLReader reader = newReader();
        assertNull(reader.getProperty(SECURITY_MANAGER));
        Digester digester = new Digester(reader);
        DigesterSecurity.secure(digester);

        digester.parse(TestDigesterSecurity.stream(
            TestDigesterSecurity.references(DigesterSecurity.MAX_ENTITY_EXPANSIONS)));
        TestDigesterSecurity.assertRejected(digester,
            TestDigesterSecurity.references(DigesterSecurity.MAX_ENTITY_EXPANSIONS + 1));
    }

    /** A manager the application set with a stricter limit is kept as it is. */
    public void testStricterSecurityManager_IsKept() throws Exception {
        XMLReader reader = newReader();
        Object strict = newManager(100, 50);
        reader.setProperty(SECURITY_MANAGER, strict);

        DigesterSecurity.secure(new Digester(reader));

        assertSame(strict, reader.getProperty(SECURITY_MANAGER));
        assertEquals(new Integer(100), call(strict, "getEntityExpansionLimit"));
    }

    /** For Xerces a limit of 0 allows one expansion; it is not "unlimited". */
    public void testZeroLimitSecurityManager_IsKept() throws Exception {
        XMLReader reader = newReader();
        Object zero = newManager(0, 50);
        reader.setProperty(SECURITY_MANAGER, zero);
        Digester digester = new Digester(reader);
        DigesterSecurity.secure(digester);

        assertSame(zero, reader.getProperty(SECURITY_MANAGER));
        TestDigesterSecurity.assertRejected(digester, TestDigesterSecurity.references(2));
    }

    /**
     * A manager that allows more expansions is replaced, not changed, since
     * other parsers may share it, and its other limits are kept.
     */
    public void testLooseSecurityManager_IsReplacedKeepingOtherLimits()
            throws Exception {
        XMLReader reader = newReader();
        Object loose = newManager(100000, 50);
        reader.setProperty(SECURITY_MANAGER, loose);

        DigesterSecurity.secure(new Digester(reader));

        Object installed = reader.getProperty(SECURITY_MANAGER);
        assertNotSame(loose, installed);
        assertEquals(new Integer(DigesterSecurity.MAX_ENTITY_EXPANSIONS - 1),
            call(installed, "getEntityExpansionLimit"));
        assertEquals(new Integer(50), call(installed, "getMaxOccurNodeLimit"));
        assertEquals(new Integer(100000), call(loose, "getEntityExpansionLimit"));
    }

    /** A limit that cannot be read may be stricter, so it is not replaced. */
    public void testUnreadableSecurityManager_IsRefused() throws Exception {
        TestDigesterSecurity.assertRefused(
            new PolicyTestReader(newReader()).fix(SECURITY_MANAGER, new Object()));
    }

    private Object newManager(int expansionLimit, int maxOccurLimit) throws Exception {
        Class managerClass =
            Class.forName("org.apache.xerces.util.SecurityManager", true, xercesLoader);
        Object manager = managerClass.newInstance();
        managerClass.getMethod("setEntityExpansionLimit", new Class[] {int.class})
            .invoke(manager, new Object[] {new Integer(expansionLimit)});
        managerClass.getMethod("setMaxOccurNodeLimit", new Class[] {int.class})
            .invoke(manager, new Object[] {new Integer(maxOccurLimit)});
        return manager;
    }

    private static Object call(Object target, String getter) throws Exception {
        return target.getClass().getMethod(getter, new Class[0])
            .invoke(target, new Object[0]);
    }
}
