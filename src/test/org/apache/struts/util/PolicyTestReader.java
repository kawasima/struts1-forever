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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * XMLReader that imitates parsers lacking a feature or property, by
 * delegating to a real reader except for the names a test lists.
 */
class PolicyTestReader implements XMLReader {

    private final XMLReader delegate;
    private final Set unrecognized = new HashSet();
    private final Set refused = new HashSet();
    private final Set forcedTrue = new HashSet();

    PolicyTestReader(XMLReader delegate) {
        this.delegate = delegate;
    }

    /** The reader does not know the feature or property. */
    PolicyTestReader unrecognize(String name) {
        unrecognized.add(name);
        return this;
    }

    /** The reader knows the feature but does not let it change. */
    PolicyTestReader refuse(String name) {
        refused.add(name);
        return this;
    }

    /** The feature is on and cannot be turned off. */
    PolicyTestReader forceTrue(String name) {
        forcedTrue.add(name);
        return refuse(name);
    }

    public boolean getFeature(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        check(name);
        if (forcedTrue.contains(name)) {
            return true;
        }
        return delegate.getFeature(name);
    }

    public void setFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        check(name);
        if (refused.contains(name)) {
            throw new SAXNotSupportedException(name);
        }
        delegate.setFeature(name, value);
    }

    public Object getProperty(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        check(name);
        return delegate.getProperty(name);
    }

    public void setProperty(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        check(name);
        delegate.setProperty(name, value);
    }

    private void check(String name) throws SAXNotRecognizedException {
        if (unrecognized.contains(name)) {
            throw new SAXNotRecognizedException(name);
        }
    }

    public void setEntityResolver(EntityResolver resolver) {
        delegate.setEntityResolver(resolver);
    }

    public EntityResolver getEntityResolver() {
        return delegate.getEntityResolver();
    }

    public void setDTDHandler(DTDHandler handler) {
        delegate.setDTDHandler(handler);
    }

    public DTDHandler getDTDHandler() {
        return delegate.getDTDHandler();
    }

    public void setContentHandler(ContentHandler handler) {
        delegate.setContentHandler(handler);
    }

    public ContentHandler getContentHandler() {
        return delegate.getContentHandler();
    }

    public void setErrorHandler(ErrorHandler handler) {
        delegate.setErrorHandler(handler);
    }

    public ErrorHandler getErrorHandler() {
        return delegate.getErrorHandler();
    }

    public void parse(InputSource input) throws IOException, SAXException {
        delegate.parse(input);
    }

    public void parse(String systemId) throws IOException, SAXException {
        delegate.parse(systemId);
    }
}
