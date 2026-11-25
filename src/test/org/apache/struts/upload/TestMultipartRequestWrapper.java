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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpSession;

import junit.framework.TestCase;

import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpSession;

/**
 * Tests for {@link MultipartRequestWrapper}.
 *
 * <p>The jakarta JAR ships a different implementation of this class
 * (see <code>src/jakarta</code>). These tests run against both builds and
 * pin the behaviour the two implementations must share. That every
 * <code>HttpServletRequest</code> method is implemented is checked for all
 * classes by <code>TestServletApiCompatibility</code>.</p>
 */
public class TestMultipartRequestWrapper extends TestCase {

    private MockHttpServletRequest base;
    private MultipartRequestWrapper wrapper;

    protected void setUp() throws Exception {
        base = new MockHttpServletRequest(new MockHttpSession());
        wrapper = new MultipartRequestWrapper(base);
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

    /**
     * Filters often wrap the request and override only getParameter,
     * getParameterNames and getParameterValues. The merged map must be built
     * from those methods, not from the wrapped request's getParameterMap.
     */
    public void testGetParameterMap_ReadsRequestThroughNamesAndValues() {
        MockHttpServletRequest filtered = new MockHttpServletRequest(new MockHttpSession()) {
            public String getParameter(String name) {
                return "q".equals(name) ? "filtered" : null;
            }
            public Enumeration getParameterNames() {
                return Collections.enumeration(Collections.singleton("q"));
            }
            public String[] getParameterValues(String name) {
                return "q".equals(name) ? new String[] {"filtered"} : null;
            }
        };
        MultipartRequestWrapper w = new MultipartRequestWrapper(filtered);
        w.setParameter("title", "hello");

        Map map = w.getParameterMap();
        assertEquals("filtered", ((String[]) map.get("q"))[0]);
        assertEquals("hello", ((String[]) map.get("title"))[0]);
    }

    // setRequest(ServletRequest) exists only in the jakarta class, which
    // inherits it from ServletRequestWrapper. Containers call it on forward
    // and include.

    public void testSetRequest_HttpRequest_ReplacesDelegateForAllMethods()
            throws Exception {
        if (!setRequestRequired()) {
            return;
        }
        base.addParameter("a", "1");
        base.setAttribute("k", "1");
        wrapper.setParameter("title", "hello");
        HttpSession otherSession = new MockHttpSession();
        MockHttpServletRequest other = new MockHttpServletRequest(otherSession);
        other.addParameter("a", "2");
        other.setAttribute("k", "2");

        setRequest(wrapper, other);

        assertSame(other, wrapper.getRequest());
        assertEquals("2", wrapper.getParameter("a"));
        assertEquals("2", wrapper.getParameterValues("a")[0]);
        assertEquals("2", ((String[]) wrapper.getParameterMap().get("a"))[0]);
        assertEquals("2", wrapper.getAttribute("k"));
        assertSame(otherSession, wrapper.getSession());
        assertEquals("hello", wrapper.getParameter("title"));
    }

    public void testSetRequest_Null_IsRejectedAndKeepsDelegate()
            throws Exception {
        assertRejectedAndDelegateKept(null);
    }

    public void testSetRequest_NonHttpRequest_IsRejectedAndKeepsDelegate()
            throws Exception {
        ServletRequest plain = (ServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] {ServletRequest.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        return null;
                    }
                });
        assertRejectedAndDelegateKept(plain);
    }

    private void assertRejectedAndDelegateKept(ServletRequest replacement)
            throws Exception {
        if (!setRequestRequired()) {
            return;
        }
        base.addParameter("a", "1");
        base.setAttribute("k", "1");
        try {
            setRequest(wrapper, replacement);
            fail("setRequest accepted " + replacement);
        } catch (IllegalArgumentException expected) {
            // expected
        }
        assertSame(base, wrapper.getRequest());
        assertEquals("1", wrapper.getParameter("a"));
        assertEquals("1", wrapper.getAttribute("k"));
        assertSame(base.getSession(), wrapper.getSession());
    }

    /** True in the jakarta build, where the method must exist. */
    private static boolean setRequestRequired() {
        return ServletRequest.class.getName().startsWith("jakarta.");
    }

    private static void setRequest(MultipartRequestWrapper w, ServletRequest r)
            throws Exception {
        Method m = MultipartRequestWrapper.class.getMethod(
                "setRequest", new Class[] {ServletRequest.class});
        try {
            m.invoke(w, new Object[] {r});
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
        }
    }
}
