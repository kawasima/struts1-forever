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

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import junit.framework.TestCase;

import org.apache.struts.mock.MockHttpServletRequest;
import org.apache.struts.mock.MockHttpSession;

/**
 * Tests for {@link MultipartRequestWrapper}.
 *
 * <p>The servlet3 and jakarta JARs ship a different implementation of this
 * class (see <code>src/servlet3</code>). These tests run against every build
 * and pin the behaviour the two implementations must share. The build is
 * named by the system property <code>struts.build</code>. That every
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

    // setRequest(ServletRequest) exists only in the Servlet 3.0 class, which
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

    /** True in the servlet3 and jakarta builds, where the method must exist. */
    private static boolean setRequestRequired() {
        return !"javax".equals(build());
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

    // ------------------------------------------------------- Class per build

    /**
     * The default JAR keeps the class of Struts 1.2.9 for its subclasses;
     * the servlet3 and jakarta JARs extend the container's wrapper.
     */
    public void testClass_MatchesBuild() throws Exception {
        Class type = MultipartRequestWrapper.class;
        if ("javax".equals(build())) {
            assertSame(Object.class, type.getSuperclass());
            assertTrue(HttpServletRequest.class.isAssignableFrom(type));
            int modifiers = type.getDeclaredField("request").getModifiers();
            assertTrue(Modifier.isProtected(modifiers));
        } else {
            Class wrapperType = Class.forName(HttpServletRequest.class.getName() + "Wrapper");
            assertTrue(type.getSuperclass().getName(), wrapperType.isAssignableFrom(type));
        }
    }

    // ------------------------------------------------------------ Delegation

    /** Methods whose results the wrapper computes itself. */
    private static final Set OWN_METHODS = new HashSet(Arrays.asList(new String[] {
        "getParameter", "getParameterNames", "getParameterValues", "getParameterMap"
    }));

    /**
     * Methods that the javax build calls through reflection, logging a
     * failure instead of throwing it.
     */
    private static final Set REFLECTED_METHODS = new HashSet(Arrays.asList(new String[] {
        "setCharacterEncoding", "isRequestedSessionIdFromCookie", "getRequestURL",
        "getLocalAddr", "getLocalName", "getLocalPort", "getRemotePort"
    }));

    /** Methods that the servlet3 and jakarta builds answer differently. */
    private static final Set SERVLET3_METHODS = new HashSet(Arrays.asList(new String[] {
        "getRealPath", "isRequestedSessionIdFromUrl"
    }));

    /**
     * Every other method of the Servlet API on the class path passes its
     * arguments, result and exception through unchanged.
     */
    public void testServletApiMethods_AreDelegatedToWrappedRequest() throws Exception {
        Method[] methods = HttpServletRequest.class.getMethods();
        int checked = 0;
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (OWN_METHODS.contains(method.getName())
                    || ("javax".equals(build())
                        && REFLECTED_METHODS.contains(method.getName()))
                    || (!"javax".equals(build())
                        && SERVLET3_METHODS.contains(method.getName()))) {
                continue;
            }
            assertDelegated(method);
            checked++;
        }
        assertTrue("methods checked: " + checked, checked > 40);
    }

    public void testReflectedMethods_Javax_ReturnResultOfWrappedRequest() {
        if (!"javax".equals(build())) {
            return;
        }
        ReflectedRequest request = new ReflectedRequest();
        MultipartRequestWrapper w = new MultipartRequestWrapper(request);

        w.setCharacterEncoding("UTF-8");
        assertEquals("UTF-8", request.encoding);
        assertTrue(w.isRequestedSessionIdFromCookie());
        assertSame(request.url, w.getRequestURL());
        assertEquals("local-addr", w.getLocalAddr());
        assertEquals("local-name", w.getLocalName());
        assertEquals(8081, w.getLocalPort());
        assertEquals(50000, w.getRemotePort());
    }

    public void testReflectedMethods_Javax_FailureReturnsDefault() {
        if (!"javax".equals(build())) {
            return;
        }
        ReflectedRequest request = new ReflectedRequest();
        request.failure = new IllegalStateException();
        MultipartRequestWrapper w = new MultipartRequestWrapper(request);

        w.setCharacterEncoding("UTF-8");
        assertNull(request.encoding);
        assertFalse(w.isRequestedSessionIdFromCookie());
        assertNull(w.getRequestURL());
        assertNull(w.getLocalAddr());
        assertNull(w.getLocalName());
        assertEquals(0, w.getLocalPort());
        assertEquals(0, w.getRemotePort());
    }

    /** Has the methods that the javax build calls through reflection. */
    public static class ReflectedRequest extends MockHttpServletRequest {
        final StringBuffer url = new StringBuffer("http://localhost/");
        String encoding;
        RuntimeException failure;

        public ReflectedRequest() {
            super(new MockHttpSession());
        }

        private void check() {
            if (failure != null) {
                throw failure;
            }
        }

        public void setCharacterEncoding(String encoding) {
            check();
            this.encoding = encoding;
        }

        public boolean isRequestedSessionIdFromCookie() {
            check();
            return true;
        }

        public StringBuffer getRequestURL() {
            check();
            return url;
        }

        public String getLocalAddr() {
            check();
            return "local-addr";
        }

        public String getLocalName() {
            check();
            return "local-name";
        }

        public int getLocalPort() {
            check();
            return 8081;
        }

        public int getRemotePort() {
            check();
            return 50000;
        }
    }

    public void testGetRealPath_Servlet3_ReadsServletContext() {
        if ("javax".equals(build())) {
            return;
        }
        Recorder context = new Recorder("real-path");
        Recorder request = new Recorder(proxy(ServletContext.class, context));
        MultipartRequestWrapper w = new MultipartRequestWrapper(
                (HttpServletRequest) proxy(HttpServletRequest.class, request));

        assertEquals("real-path", w.getRealPath("/a"));
        assertEquals("getServletContext", request.method.getName());
        assertEquals("getRealPath", context.method.getName());
        assertEquals("/a", context.args[0]);
    }

    public void testIsRequestedSessionIdFromUrl_Servlet3_CallsFromURL() {
        if ("javax".equals(build())) {
            return;
        }
        Recorder request = new Recorder(Boolean.TRUE);
        MultipartRequestWrapper w = new MultipartRequestWrapper(
                (HttpServletRequest) proxy(HttpServletRequest.class, request));

        assertTrue(w.isRequestedSessionIdFromUrl());
        assertEquals("isRequestedSessionIdFromURL", request.method.getName());
    }

    private static void assertDelegated(Method method) throws Exception {
        String name = method.toString();
        Object[] args = arguments(method);
        Object result = value(method.getReturnType());
        Recorder recorder = new Recorder(result);
        MultipartRequestWrapper w = new MultipartRequestWrapper(
                (HttpServletRequest) proxy(HttpServletRequest.class, recorder));

        Object returned = method.invoke(w, args);
        assertEquals(name, 1, recorder.calls);
        assertEquals(name, method.getName(), recorder.method.getName());
        assertTrue(name, Arrays.equals(method.getParameterTypes(),
                recorder.method.getParameterTypes()));
        for (int i = 0; i < args.length; i++) {
            assertSame(name, args[i], recorder.args[i]);
        }
        if (method.getReturnType().isPrimitive()) {
            assertEquals(name, result, returned);
        } else {
            assertSame(name, result, returned);
        }

        assertThrown(w, method, args, new IllegalStateException(name), recorder);
        Class[] declared = method.getExceptionTypes();
        for (int i = 0; i < declared.length; i++) {
            Exception failure = (Exception) declared[i]
                .getConstructor(new Class[] {String.class})
                .newInstance(new Object[] {name});
            if ("setCharacterEncoding".equals(method.getName())) {
                // Logged instead of thrown, as in the javax build
                recorder.failure = failure;
                method.invoke(w, args);
            } else {
                assertThrown(w, method, args, failure, recorder);
            }
        }
    }

    private static void assertThrown(MultipartRequestWrapper w, Method method,
            Object[] args, Exception failure, Recorder recorder) throws Exception {
        recorder.failure = failure;
        try {
            method.invoke(w, args);
            fail(method + " did not throw " + failure);
        } catch (InvocationTargetException e) {
            assertSame(method.toString(), failure, e.getTargetException());
        }
    }

    /** Records the last call and answers it with a fixed result. */
    private static class Recorder implements InvocationHandler {
        private final Object result;
        Exception failure;
        int calls;
        Method method;
        Object[] args;

        Recorder(Object result) {
            this.result = result;
        }

        public Object invoke(Object proxy, Method m, Object[] a) throws Exception {
            calls++;
            method = m;
            args = (a == null) ? new Object[0] : a;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static Object proxy(Class type, InvocationHandler handler) {
        return Proxy.newProxyInstance(TestMultipartRequestWrapper.class.getClassLoader(),
                new Class[] {type}, handler);
    }

    private static Object[] arguments(Method method) {
        Class[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class type = types[i];
            if (type == String.class) {
                args[i] = "arg" + i;
            } else if (type == boolean.class) {
                args[i] = Boolean.TRUE;
            } else if (type == int.class) {
                args[i] = new Integer(3);
            } else if (type == Class.class) {
                args[i] = String.class;
            } else if (type == Object.class) {
                args[i] = new Object();
            } else if (type.isInterface()) {
                args[i] = proxy(type, new Recorder(null));
            } else {
                fail("No argument of " + type + " for " + method);
            }
        }
        return args;
    }

    /** A value of <code>type</code> that no default implementation returns. */
    private static Object value(Class type) throws Exception {
        if (type == void.class) {
            return null;
        } else if (type == boolean.class) {
            return Boolean.TRUE;
        } else if (type == int.class) {
            return new Integer(7);
        } else if (type == long.class) {
            return new Long(Integer.MAX_VALUE + 1L);
        } else if (type == String.class) {
            return new String("value");
        } else if (type == Object.class) {
            return new Object();
        } else if (type == StringBuffer.class) {
            return new StringBuffer("value");
        } else if (type == Locale.class) {
            return new Locale("xx");
        } else if (type == BufferedReader.class) {
            return new BufferedReader(new StringReader(""));
        } else if (type == ServletInputStream.class) {
            return new ServletInputStream() {
                public int read() {
                    return -1;
                }
            };
        } else if (type.isArray()) {
            return Array.newInstance(type.getComponentType(), 1);
        } else if (type.isInterface()) {
            return proxy(type, new Recorder(null));
        }
        // An enum such as DispatcherType
        Method values = type.getMethod("values", new Class[0]);
        Object[] constants = (Object[]) values.invoke(null, new Object[0]);
        return constants[constants.length - 1];
    }

    /** The JAR under test, set by the Maven build. */
    private static String build() {
        String build = System.getProperty("struts.build");
        assertTrue("system property struts.build is javax, servlet3 or jakarta,"
            + " set by the Maven build; run this test with Maven: " + build,
            "javax".equals(build) || "servlet3".equals(build) || "jakarta".equals(build));
        return build;
    }
}
