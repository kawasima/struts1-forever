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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.Rule;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * Tests for the XML parsing policy of {@link DigesterSecurity}, as applied by
 * {@link SecureDigester} and by {@link DigesterSecurity#secure(Digester)}.
 */
public class TestDigesterSecurity extends TestCase {

    static final String TEST_PUBLIC_ID = "-//Struts1 Forever//DTD Test Root//EN";

    /**
     * Declares a default value for root/@source, so a test can see whether
     * the parser read this DTD: the attribute is only there if it did.
     */
    static final String TEST_DTD =
        "<!ELEMENT root (child*)>\n"
        + "<!ATTLIST root source CDATA \"local-dtd\">\n"
        + "<!ELEMENT child EMPTY>\n";

    private static String dtdUrl;
    private static File markerFile;

    private ConnectionCounter counter;
    private int connectionsBefore;

    protected void setUp() throws Exception {
        counter = ConnectionCounter.shared();
        connectionsBefore = counter.getCount();
        if (dtdUrl == null) {
            dtdUrl = writeTempFile(".dtd", TEST_DTD).toURL().toString();
            markerFile = writeTempFile(".txt", "STRUTS-XXE-MARKER");
        }
    }

    private void assertNoConnection() {
        assertEquals("connections to the system ID",
            connectionsBefore, counter.getCount());
    }

    // ------------------------------------------------------------------
    // Registered DTDs are read from their local copy
    // ------------------------------------------------------------------

    public void testRegisteredPublicId_Validating_ReadsLocalDtd() throws Exception {
        Digester digester = secureDigester(true);
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        ErrorCollector errors = collectErrors(digester);

        assertEquals("local-dtd", parseSource(digester,
            doctypePublic(TEST_PUBLIC_ID, counter.url("/root.dtd")) + "<root><child/></root>"));
        assertEquals(0, errors.errors.size());
        assertNoConnection();
    }

    public void testRegisteredPublicId_NonValidating_ReadsLocalDtd() throws Exception {
        Digester digester = secureDigester(false);
        digester.register(TEST_PUBLIC_ID, dtdUrl);

        assertEquals("local-dtd", parseSource(digester,
            doctypePublic(TEST_PUBLIC_ID, counter.url("/root.dtd")) + "<root/>"));
        assertNoConnection();
    }

    public void testSystemIdOnly_RegisteredBySystemId_ReadsLocalDtd() throws Exception {
        String systemId = counter.url("/root.dtd");
        Digester digester = secureDigester(false);
        digester.register(systemId, dtdUrl);

        assertEquals("local-dtd", parseSource(digester,
            "<!DOCTYPE root SYSTEM \"" + systemId + "\">\n<root/>"));
        assertNoConnection();
    }

    public void testRegistrationAfterReaderCreation_IsUsed() throws Exception {
        Digester digester = secureDigester(false);
        digester.getXMLReader();
        digester.register(TEST_PUBLIC_ID, dtdUrl);

        assertEquals("local-dtd", parseSource(digester,
            doctypePublic(TEST_PUBLIC_ID, "root.dtd") + "<root/>"));
    }

    public void testRegisteredPublicId_IsReportedByGetPublicIdDuringParse()
            throws Exception {
        final Digester digester = secureDigester(false);
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        final List seen = new ArrayList();
        digester.addRule("root", new Rule() {
            public void begin(String namespace, String name, Attributes attributes) {
                seen.add(digester.getPublicId());
            }
        });

        digester.parse(stream(doctypePublic(TEST_PUBLIC_ID, "root.dtd") + "<root/>"));

        assertEquals(1, seen.size());
        assertEquals(TEST_PUBLIC_ID, seen.get(0));
    }

    // ------------------------------------------------------------------
    // Configuration made before parsing is kept
    // ------------------------------------------------------------------

    public void testValidating_InvalidDocument_ReportsValidationError() throws Exception {
        Digester digester = secureDigester(true);
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        ErrorCollector errors = collectErrors(digester);

        digester.parse(stream(doctypePublic(TEST_PUBLIC_ID, "root.dtd")
            + "<root><undeclared/></root>"));

        assertTrue("validation error reported", errors.errors.size() > 0);
    }

    public void testNamespaceAware_IsHonoured() throws Exception {
        Digester digester = new SecureDigester();
        digester.setNamespaceAware(true);

        assertTrue(digester.getXMLReader().getFeature(
            "http://xml.org/sax/features/namespaces"));
    }

    // ------------------------------------------------------------------
    // Nothing that the document names is read unless it is registered
    // ------------------------------------------------------------------

    public void testUnregisteredPublicId_Validating_IsRejectedWithoutConnection()
            throws Exception {
        assertRejected(secureDigester(true), doctypePublic(
            "-//Unknown//DTD Unknown//EN", counter.url("/unknown.dtd")) + "<root/>");
        assertNoConnection();
    }

    public void testUnregisteredPublicId_NonValidating_IsRejectedWithoutConnection()
            throws Exception {
        assertRejected(secureDigester(false), doctypePublic(
            "-//Unknown//DTD Unknown//EN", counter.url("/unknown.dtd")) + "<root/>");
        assertNoConnection();
    }

    public void testUnregisteredSystemIdOnly_IsRejectedWithoutConnection()
            throws Exception {
        assertRejected(secureDigester(false),
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testSecondParse_IsStillRestricted() throws Exception {
        Digester digester = secureDigester(false);
        digester.parse(stream("<root/>"));

        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testExternalGeneralEntity_File_IsRejectedAndNotRead() throws Exception {
        Digester digester = secureDigester(false);
        StringBuffer body = new StringBuffer();
        digester.push(body);
        digester.addCallMethod("root", "append", 1, new Class[] {String.class});
        digester.addCallParam("root", 0);

        assertRejected(digester, "<!DOCTYPE root [\n"
            + "  <!ENTITY xxe SYSTEM \"" + markerFile.toURL() + "\">\n"
            + "]>\n<root>&xxe;</root>");
        assertEquals(-1, body.toString().indexOf("STRUTS-XXE-MARKER"));
    }

    public void testExternalGeneralEntity_Http_IsRejectedWithoutConnection()
            throws Exception {
        assertRejected(secureDigester(false), "<!DOCTYPE root [\n"
            + "  <!ENTITY xxe SYSTEM \"" + counter.url("/entity") + "\">\n"
            + "]>\n<root>&xxe;</root>");
        assertNoConnection();
    }

    public void testExternalParameterEntity_IsNotFetched() throws Exception {
        parseIgnoringRejection(secureDigester(true), "<!DOCTYPE root [\n"
            + "  <!ENTITY % ext SYSTEM \"" + counter.url("/param.dtd") + "\">\n"
            + "  %ext;\n"
            + "]>\n<root/>");
        assertNoConnection();
    }

    /** Control: an XInclude-aware parser without the policy fetches the include. */
    public void testXIncludeAwareReader_Unsecured_FetchesInclude() throws Exception {
        parseIgnoringRejection(xincludeAwareReader(), xincludeDoc());
        assertEquals(connectionsBefore + 1, counter.getCount());
    }

    public void testXIncludeAwareReader_Secured_DoesNotFetchInclude() throws Exception {
        parseSecuredIgnoringRejection(new Digester(xincludeAwareReader()), xincludeDoc());
        assertNoConnection();
    }

    /** Control: a schema-validating parser without the policy fetches the schema. */
    public void testSchemaValidatingReader_Unsecured_FetchesSchema() throws Exception {
        parseIgnoringRejection(schemaValidatingReader(), schemaDoc());
        assertEquals(connectionsBefore + 1, counter.getCount());
    }

    public void testSchemaValidatingReader_Secured_DoesNotFetchSchema() throws Exception {
        parseSecuredIgnoringRejection(new Digester(schemaValidatingReader()), schemaDoc());
        assertNoConnection();
    }

    public void testInternalEntityExpansion_IsLimited() throws Exception {
        assertRejected(secureDigester(false), billionLaughs(5, "<root>&e5;</root>"));
    }

    public void testInternalEntityExpansionInAttribute_IsLimited() throws Exception {
        assertRejected(secureDigester(false), billionLaughs(5, "<root a=\"&e5;\"/>"));
    }

    /** Control: with its limit at 0 the parser expands 10^5 entities. */
    public void testUnlimitedReader_Unsecured_ExpandsEntities() throws Exception {
        XMLReader reader = newXmlReader();
        reader.setProperty(JDK_EXPANSION_LIMIT, "0");
        reader.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);

        reader.parse(new InputSource(stream(billionLaughs(5, "<root>&e5;</root>"))));
    }

    /**
     * An explicit limit of 0 (for example from jdk.xml.entityExpansionLimit)
     * leaves expansion unlimited although secure processing is on; the policy
     * replaces it.
     */
    public void testUnlimitedReader_Secured_IsLimited() throws Exception {
        XMLReader reader = newXmlReader();
        reader.setProperty(JDK_EXPANSION_LIMIT, "0");
        reader.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
        Digester digester = new Digester(reader);
        DigesterSecurity.secure(digester);

        assertEquals(String.valueOf(DigesterSecurity.MAX_ENTITY_EXPANSIONS),
            reader.getProperty(JDK_EXPANSION_LIMIT));
        assertRejected(digester, billionLaughs(5, "<root>&e5;</root>"));
    }

    public void testLowerExpansionLimit_IsKept() throws Exception {
        XMLReader reader = newXmlReader();
        reader.setProperty(JDK_EXPANSION_LIMIT, "100");
        DigesterSecurity.secure(new Digester(reader));

        assertEquals("100", reader.getProperty(JDK_EXPANSION_LIMIT));
    }

    public void testInternalEntity_AtLengthLimit_IsAccepted() throws Exception {
        secureDigester(false).parse(stream(entityOfLength(DigesterSecurity.MAX_ENTITY_LENGTH)));
    }

    public void testInternalEntity_OverLengthLimit_IsRejected() throws Exception {
        assertRejected(secureDigester(false),
            entityOfLength(DigesterSecurity.MAX_ENTITY_LENGTH + 1));
    }

    public void testUnreferencedExternalEntityDeclaration_IsRejectedWithoutConnection()
            throws Exception {
        assertRejected(secureDigester(false), "<!DOCTYPE root [\n"
            + "  <!ENTITY unused SYSTEM \"" + counter.url("/entity") + "\">\n"
            + "]>\n<root/>");
        assertNoConnection();
    }

    // ------------------------------------------------------------------
    // Resolvers that the application sets
    // ------------------------------------------------------------------

    public void testInstalledEntityResolver_IsUsedFirst() throws Exception {
        final List requested = new ArrayList();
        Digester digester = secureDigester(false);
        digester.setEntityResolver(new EntityResolver() {
            public InputSource resolveEntity(String publicId, String systemId) {
                requested.add(systemId);
                return new InputSource(dtdUrl);
            }
        });

        assertEquals("local-dtd", parseSource(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/custom.dtd") + "\">\n<root/>"));
        assertEquals(1, requested.size());
        assertNoConnection();
    }

    public void testInstalledEntityResolverReturningNull_RegisteredDtd_ReadsLocalDtd()
            throws Exception {
        Digester digester = secureDigester(false);
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        digester.setEntityResolver(nullResolver());

        assertEquals("local-dtd", parseSource(digester,
            doctypePublic(TEST_PUBLIC_ID, counter.url("/root.dtd")) + "<root/>"));
        assertNoConnection();
    }

    public void testInstalledEntityResolverReturningNull_UnregisteredDtd_IsRejected()
            throws Exception {
        Digester digester = secureDigester(false);
        digester.setEntityResolver(nullResolver());

        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    // ------------------------------------------------------------------
    // Digesters that Struts did not create
    // ------------------------------------------------------------------

    public void testPlainDigester_Secured_ReadsRegisteredDtdAndRejectsOthers()
            throws Exception {
        Digester digester = new Digester();
        digester.register(TEST_PUBLIC_ID, dtdUrl);
        DigesterSecurity.secure(digester);

        assertEquals("local-dtd", parseSource(digester,
            doctypePublic(TEST_PUBLIC_ID, counter.url("/root.dtd")) + "<root/>"));
        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    public void testDigesterOverXmlReader_Secured_IsRestricted() throws Exception {
        Digester digester = new Digester(newXmlReader());
        DigesterSecurity.secure(digester);

        assertRejected(digester,
            "<!DOCTYPE root SYSTEM \"" + counter.url("/unknown.dtd") + "\">\n<root/>");
        assertNoConnection();
    }

    // ------------------------------------------------------------------
    // A parser that cannot follow the policy is not used
    // ------------------------------------------------------------------

    public void testReaderKeepingExternalEntities_IsRefused() throws Exception {
        assertRefused(new PolicyTestReader(newXmlReader())
            .refuse("http://xml.org/sax/features/external-general-entities"));
    }

    public void testReaderWithoutAnyEntityExpansionLimit_IsRefused() throws Exception {
        assertRefused(new PolicyTestReader(newXmlReader())
            .unrecognize("http://javax.xml.XMLConstants/feature/secure-processing")
            .unrecognize(JDK_EXPANSION_LIMIT)
            .unrecognize("http://apache.org/xml/properties/security-manager"));
    }

    public void testReaderWithoutDeclarationEvents_IsRefused() throws Exception {
        assertRefused(new PolicyTestReader(newXmlReader())
            .unrecognize("http://xml.org/sax/properties/declaration-handler"));
    }

    public void testReaderWithXIncludeOn_IsRefused() throws Exception {
        assertRefused(new PolicyTestReader(newXmlReader())
            .forceTrue("http://apache.org/xml/features/xinclude"));
    }

    public void testReaderWithoutXInclude_IsUsed() throws Exception {
        DigesterSecurity.secure(new Digester(new PolicyTestReader(newXmlReader())
            .unrecognize("http://apache.org/xml/features/xinclude")));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static final String JDK_EXPANSION_LIMIT =
        "http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit";


    /** 10^levels expansions of e0 through e1..e{levels}, used by the body. */
    static String billionLaughs(int levels, String body) {
        StringBuffer doc = new StringBuffer("<!DOCTYPE root [\n<!ENTITY e0 \"x\">\n");
        for (int i = 1; i <= levels; i++) {
            doc.append("<!ENTITY e" + i + " \"");
            for (int j = 0; j < 10; j++) {
                doc.append("&e" + (i - 1) + ";");
            }
            doc.append("\">\n");
        }
        return doc.append("]>\n").append(body).toString();
    }

    static String entityOfLength(int length) {
        StringBuffer value = new StringBuffer();
        for (int i = 0; i < length; i++) {
            value.append('x');
        }
        return "<!DOCTYPE root [<!ENTITY long \"" + value + "\">]>\n<root>&long;</root>";
    }

    private XMLReader xincludeAwareReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(true);
        return factory.newSAXParser().getXMLReader();
    }

    private XMLReader schemaValidatingReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        javax.xml.parsers.SAXParser parser = factory.newSAXParser();
        parser.setProperty("http://java.sun.com/xml/jaxp/properties/schemaLanguage",
            "http://www.w3.org/2001/XMLSchema");
        return parser.getXMLReader();
    }

    private String xincludeDoc() {
        return "<root xmlns:xi=\"http://www.w3.org/2001/XInclude\">"
            + "<xi:include href=\"" + counter.url("/included.xml") + "\"/></root>";
    }

    private String schemaDoc() {
        return "<root xmlns=\"urn:test\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"urn:test " + counter.url("/root.xsd") + "\"/>";
    }

    /** Applies the policy, then parses; refusing either step is acceptable. */
    private static void parseSecuredIgnoringRejection(Digester digester, String xml)
            throws IOException {
        try {
            DigesterSecurity.secure(digester);
            digester.parse(stream(xml));
        } catch (SAXException e) {
            // either outcome is fine
        }
    }

    /** Parses with a bare reader, ignoring any error. */
    private static void parseIgnoringRejection(XMLReader reader, String xml)
            throws IOException {
        reader.setErrorHandler(new ErrorCollector() {
            public void fatalError(SAXParseException e) {
            }
        });
        try {
            reader.parse(new InputSource(stream(xml)));
        } catch (SAXException e) {
            // the control only counts connections
        }
    }

    static Digester secureDigester(boolean validating) {
        Digester digester = new SecureDigester();
        digester.setValidating(validating);
        return digester;
    }

    static String doctypePublic(String publicId, String systemId) {
        return "<!DOCTYPE root PUBLIC \"" + publicId + "\" \"" + systemId + "\">\n";
    }

    /** @return the value of root/@source, or null if the attribute is absent */
    static String parseSource(Digester digester, String xml) throws Exception {
        final String[] source = new String[1];
        digester.addRule("root", new Rule() {
            public void begin(String namespace, String name, Attributes attributes) {
                source[0] = attributes.getValue("source");
            }
        });
        digester.parse(stream(xml));
        return source[0];
    }

    static void assertRejected(Digester digester, String xml) throws IOException {
        try {
            digester.parse(stream(xml));
            fail("document must be rejected");
        } catch (SAXException e) {
            // expected
        }
    }

    private static void assertRefused(XMLReader reader) {
        try {
            DigesterSecurity.secure(new Digester(reader));
            fail("a reader that cannot follow the policy must not be used");
        } catch (SAXException e) {
            // expected
        }
    }

    /** Rejecting the document is acceptable; reading the entity is not. */
    private static void parseIgnoringRejection(Digester digester, String xml)
            throws IOException {
        try {
            digester.parse(stream(xml));
        } catch (SAXException e) {
            // either outcome is fine
        }
    }

    static ByteArrayInputStream stream(String xml) throws IOException {
        return new ByteArrayInputStream(
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + xml).getBytes("UTF-8"));
    }

    static XMLReader newXmlReader() throws Exception {
        return SAXParserFactory.newInstance().newSAXParser().getXMLReader();
    }

    private static EntityResolver nullResolver() {
        return new EntityResolver() {
            public InputSource resolveEntity(String publicId, String systemId) {
                return null;
            }
        };
    }

    static ErrorCollector collectErrors(Digester digester) {
        ErrorCollector errors = new ErrorCollector();
        digester.setErrorHandler(errors);
        return errors;
    }

    static File writeTempFile(String suffix, String content) throws IOException {
        File file = File.createTempFile("struts", suffix);
        file.deleteOnExit();
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(content.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        return file;
    }

    /** Records recoverable errors, such as validation errors. */
    static class ErrorCollector implements ErrorHandler {
        final List errors = new ArrayList();

        public void warning(SAXParseException e) {
        }

        public void error(SAXParseException e) {
            errors.add(e);
        }

        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }
}
