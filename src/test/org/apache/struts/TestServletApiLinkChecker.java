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

package org.apache.struts;

import java.io.File;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.servlet.http.HttpServletRequest;

import junit.framework.TestCase;

import org.apache.struts.linkfixture.ClassLiteralFixture;

/**
 * Pins what each check of {@link ServletApiLinkChecker} detects, using the
 * classes in <code>org.apache.struts.linkfixture</code> and a class loader
 * that hides one API type.
 */
public class TestServletApiLinkChecker extends TestCase {

    private static final String SESSION_CONTEXT =
            "javax.servlet.http.HttpSessionContext";
    private static final String SESSION = "javax.servlet.http.HttpSession";
    private static final String FIXTURES = "org/apache/struts/linkfixture/";

    private Map fixtures;
    private String[] namespaces;

    protected void setUp() throws Exception {
        fixtures = ServletApiLinkChecker.readClasses(
                new File(testClassesRoot(), FIXTURES));
        namespaces = ServletApiLinkChecker.servletNamespaces();
    }

    public void testUnresolvedTypes_ClassLiteralInMethodBody_IsReported()
            throws Exception {
        Set problems = ServletApiLinkChecker.unresolvedTypes(
                fixtures, namespaces, hiding(SESSION_CONTEXT));
        assertReported(problems, "HttpSessionContext  <- " + FIXTURES
                + "ClassLiteralFixture");
    }

    public void testUnresolvedTypes_TypeInDeclaredSignature_IsReported()
            throws Exception {
        Set problems = ServletApiLinkChecker.unresolvedTypes(
                fixtures, namespaces, hiding(SESSION_CONTEXT));
        assertReported(problems, "HttpSessionContext  <- " + FIXTURES
                + "SignatureFixture");
    }

    /**
     * Loading and introspecting a class does not resolve types used only in
     * method bodies, so unresolvedTypes is needed in addition.
     */
    public void testUnloadableClasses_ReportsSignatureButNotMethodBody()
            throws Exception {
        Set problems = ServletApiLinkChecker.unloadableClasses(
                fixtures, hiding(SESSION_CONTEXT));
        assertReported(problems, FIXTURES + "SignatureFixture: ");
        assertNotReported(problems, FIXTURES + "ClassLiteralFixture");
    }

    public void testUnresolvedMembers_UnloadableOwner_IsReported()
            throws Exception {
        Set problems = ServletApiLinkChecker.unresolvedMembers(
                fixtures, namespaces, hiding(SESSION));
        assertReported(problems, "HttpSession.getSessionContext()");
    }

    public void testReferences_FindsDottedForm() {
        assertReported(
                ServletApiLinkChecker.references(fixtures, "org/example/foreign/"),
                FIXTURES + "NamespaceFixture: \"org.example.foreign.Thing\"");
        assertTrue(ServletApiLinkChecker.references(
                fixtures, "org/example/other/").isEmpty());
    }

    public void testHasMethod_InheritedFromSuperclass_IsFound() {
        assertTrue(ServletApiLinkChecker.hasMethod(Stack.class, "size", "()I"));
    }

    public void testHasMethod_ObjectMethodThroughInterface_IsFound() {
        assertTrue(ServletApiLinkChecker.hasMethod(
                List.class, "toString", "()Ljava/lang/String;"));
    }

    public void testHasMethod_Constructor_IsMatchedByDescriptor() {
        assertTrue(ServletApiLinkChecker.hasMethod(
                ArrayList.class, "<init>", "(I)V"));
        assertFalse(ServletApiLinkChecker.hasMethod(
                ArrayList.class, "<init>", "(J)V"));
    }

    public void testHasMethod_WrongNameOrDescriptor_IsNotFound() {
        assertFalse(ServletApiLinkChecker.hasMethod(List.class, "nope", "()V"));
        assertFalse(ServletApiLinkChecker.hasMethod(Stack.class, "size", "()J"));
    }

    public void testHasField_ConstantFromInterface_IsFound() {
        assertTrue(ServletApiLinkChecker.hasField(
                ObjectOutputStream.class, "STREAM_MAGIC", "S"));
        assertFalse(ServletApiLinkChecker.hasField(
                ObjectOutputStream.class, "STREAM_MAGIC", "I"));
    }

    public void testAbstractPublicMethods_ListsUnimplementedMethods() {
        Set methods = ServletApiLinkChecker.abstractPublicMethods(
                AbstractList.class);
        assertReported(methods, "java.util.AbstractList.get(I)");
        assertReported(methods, "size()I");
    }

    // ------------------------------------------------------------ helpers

    /**
     * Loads the fixtures and the Servlet API, but not <code>hidden</code>.
     */
    private ClassLoader hiding(final String hidden) throws Exception {
        URL[] urls = new URL[] {
            ClassLiteralFixture.class.getProtectionDomain().getCodeSource().getLocation(),
            HttpServletRequest.class.getProtectionDomain().getCodeSource().getLocation()
        };
        return new URLClassLoader(urls, null) {
            protected Class loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (name.equals(hidden)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    private static File testClassesRoot() throws Exception {
        return new File(new URI(ClassLiteralFixture.class.getProtectionDomain()
                .getCodeSource().getLocation().toString()));
    }

    private static void assertReported(Set problems, String fragment) {
        Iterator it = problems.iterator();
        while (it.hasNext()) {
            if (((String) it.next()).indexOf(fragment) >= 0) {
                return;
            }
        }
        fail("Expected a problem containing <" + fragment + "> in " + problems);
    }

    private static void assertNotReported(Set problems, String fragment) {
        Iterator it = problems.iterator();
        while (it.hasNext()) {
            String problem = (String) it.next();
            assertTrue(problem, problem.indexOf(fragment) < 0);
        }
    }
}
