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

import org.apache.commons.digester.Digester;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * <p>A <code>Digester</code> that follows the XML parsing policy of
 * {@link DigesterSecurity} for every document it parses, whoever calls
 * <code>parse()</code>.</p>
 *
 * <p>Every <code>parse()</code> method of <code>Digester</code> obtains its
 * parser from {@link #getXMLReader()}, which applies the policy after the
 * Digester has been configured.</p>
 *
 * @since Struts 1.2.9-forever-4
 */
public class SecureDigester extends Digester {

    public SecureDigester() {
        super();
    }

    /**
     * Return the XML reader, with the policy of {@link DigesterSecurity}
     * applied.
     *
     * @throws SAXException if the reader cannot be created or cannot be made
     *  to follow the policy
     */
    public XMLReader getXMLReader() throws SAXException {
        XMLReader reader;
        try {
            reader = super.getXMLReader();
        } catch (RuntimeException e) {
            throw new SAXException("Cannot create the XML parser", e);
        }
        if (reader == null) {
            throw new SAXException("Cannot create the XML parser");
        }
        DigesterSecurity.secure(this, reader);
        return reader;
    }

    /**
     * Fail on a reference to an external general entity that the parser
     * skipped. External entities are not expanded, and leaving the reference
     * out would silently drop part of the configuration, for example of a
     * <code>struts-config.xml</code> split into files with
     * <code>&amp;name;</code> references. {@link DigesterSecurity} already
     * rejects the declaration of such an entity on parsers that report
     * declarations; this covers parsers that do not.
     */
    public void skippedEntity(String name) throws SAXException {
        if (!name.startsWith("%")) {
            throw new SAXException("External entity &" + name + "; is not"
                + " expanded. Put its content into the document instead of"
                + " referring to it.");
        }
        super.skippedEntity(name);
    }
}
