/*
 * Copyright 2026 The Apache Software Foundation.
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

package org.apache.struts.upload;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpSession;

/**
 * Tests for {@link MultipartRequestWrapper}.
 *
 * <p>The jakarta JAR ships a different implementation of this class
 * (see <code>src/jakarta</code>). These tests run against both builds and
 * pin the behaviour the two implementations must share.</p>
 */
public class TestMultipartRequestWrapper extends TestCase {

    private MockHttpServletRequest base;
    private MultipartRequestWrapper wrapper;

    protected void setUp() throws Exception {
        base = new MockHttpServletRequest(new MockHttpSession());
        wrapper = new MultipartRequestWrapper(base);
    }

    /**
     * RequestProcessor hands the wrapper to the Action in place of the
     * container's request, so every method of the HttpServletRequest
     * interface on the runtime classpath must have an implementation.
     * A missing one throws AbstractMethodError when an Action calls it.
     */
    public void testImplementsEveryHttpServletRequestMethod() {
        Method[] methods = HttpServletRequest.class.getMethods();
        List missing = new ArrayList();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            try {
                Method impl = MultipartRequestWrapper.class.getMethod(
                        m.getName(), m.getParameterTypes());
                if (Modifier.isAbstract(impl.getModifiers())) {
                    missing.add(m.getName());
                }
            } catch (NoSuchMethodException e) {
                missing.add(m.getName());
            }
        }
        assertTrue("Methods without implementation: " + missing,
                missing.isEmpty());
    }

    public void testGetRequest_ReturnsWrappedRequest() {
        assertSame(base, wrapper.getRequest());
    }

    public void testGetParameter_FallsBackToMultipartParameter() {
        wrapper.setParameter("title", "hello");
        assertEquals("hello", wrapper.getParameter("title"));
    }

    public void testGetParameter_RequestParameterTakesPrecedence() {
        base.addParameter("title", "from-request");
        wrapper.setParameter("title", "from-multipart");
        assertEquals("from-request", wrapper.getParameter("title"));
    }

    public void testGetParameter_Unknown_ReturnsNull() {
        assertNull(wrapper.getParameter("missing"));
    }

    public void testSetParameter_SameNameTwice_KeepsBothValuesInOrder() {
        wrapper.setParameter("tag", "a");
        wrapper.setParameter("tag", "b");
        String[] values = wrapper.getParameterValues("tag");
        assertEquals(2, values.length);
        assertEquals("a", values[0]);
        assertEquals("b", values[1]);
        assertEquals("a", wrapper.getParameter("tag"));
    }

    public void testGetParameterNames_ContainsRequestAndMultipartNames() {
        base.addParameter("q", "1");
        wrapper.setParameter("title", "hello");
        Set names = new HashSet();
        Enumeration e = wrapper.getParameterNames();
        while (e.hasMoreElements()) {
            names.add(e.nextElement());
        }
        assertTrue(names.contains("q"));
        assertTrue(names.contains("title"));
    }

    public void testGetParameterMap_MergesRequestOverMultipart() {
        base.addParameter("q", "from-request");
        wrapper.setParameter("q", "from-multipart");
        wrapper.setParameter("title", "hello");
        Map map = wrapper.getParameterMap();
        assertEquals("from-request", ((String[]) map.get("q"))[0]);
        assertEquals("hello", ((String[]) map.get("title"))[0]);
    }

    public void testAttributes_AreDelegatedToWrappedRequest() {
        wrapper.setAttribute("key", "value");
        assertEquals("value", base.getAttribute("key"));
        wrapper.removeAttribute("key");
        assertNull(base.getAttribute("key"));
    }

    public void testGetSession_IsDelegatedToWrappedRequest() {
        assertSame(base.getSession(), wrapper.getSession());
    }
}
