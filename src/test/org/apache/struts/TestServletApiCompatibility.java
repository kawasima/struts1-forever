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
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.Servlet;

import junit.framework.TestCase;

import org.apache.struts.action.ActionServlet;

/**
 * Checks that the production classes link against the Servlet/JSP API on
 * the test classpath.
 *
 * <p>Struts is compiled against Servlet 2.2, but runs on newer APIs: the
 * javax JAR on Servlet 2.3 to 4.0, and the jakarta JAR (bytecode-transformed)
 * on Jakarta Servlet 5.0 and later. Newer APIs add interface methods and
 * remove deprecated ones. Neither shows up when compiling, and both fail only
 * when the affected code path runs (<code>AbstractMethodError</code>,
 * <code>NoSuchMethodError</code>, <code>NoClassDefFoundError</code>). The
 * build runs this test once per supported API version.</p>
 *
 * <p>The classes checked are all classes in the directory that holds
 * {@link ActionServlet}: Struts itself and, in the jakarta build, the
 * bundled commons-fileupload. See {@link ServletApiLinkChecker} for each
 * check. Not checked: access and static/instance mismatches of resolved
 * members, and members looked up by name through reflection.</p>
 */
public class TestServletApiCompatibility extends TestCase {

    private Map classes;
    private String[] namespaces;
    private String foreignNamespace;
    private ClassLoader loader;

    protected void setUp() throws Exception {
        namespaces = ServletApiLinkChecker.servletNamespaces();
        foreignNamespace = ServletApiLinkChecker.foreignNamespace(Servlet.class);
        loader = getClass().getClassLoader();

        File root = classesRoot();
        classes = ServletApiLinkChecker.readClasses(root);
        assertFalse("No classes found under " + root, classes.isEmpty());
    }

    public void testClasses_ReferToOneServletNamespace() {
        assertEmpty("References to " + foreignNamespace,
                ServletApiLinkChecker.references(classes, foreignNamespace));
    }

    public void testApiTypeReferences_Resolve() {
        assertEmpty("Unresolved Servlet/JSP types",
                ServletApiLinkChecker.unresolvedTypes(classes, namespaces, loader));
    }

    public void testClasses_LoadWithResolvableSignatures() {
        assertEmpty("Classes that cannot be loaded or introspected",
                ServletApiLinkChecker.unloadableClasses(classes, loader));
    }

    public void testConcreteClasses_ImplementEveryInheritedAbstractMethod() {
        assertEmpty("Missing implementations",
                ServletApiLinkChecker.missingImplementations(classes, loader));
    }

    public void testMemberReferences_Resolve() {
        assertEmpty("Unresolved member references",
                ServletApiLinkChecker.unresolvedMembers(classes, namespaces, loader));
    }

    private File classesRoot() throws Exception {
        URL url = ActionServlet.class.getResource("ActionServlet.class");
        assertEquals("Production classes must be in a directory",
                "file", url.getProtocol());
        File file = new File(new URI(url.toString()));
        // org/apache/struts/action/ActionServlet.class
        for (int i = 0; i < 5; i++) {
            file = file.getParentFile();
        }
        return file;
    }

    private static void assertEmpty(String message, Set problems) {
        if (problems.isEmpty()) {
            return;
        }
        StringBuffer sb = new StringBuffer(message).append(":\n");
        Iterator it = problems.iterator();
        while (it.hasNext()) {
            sb.append("  ").append(it.next()).append('\n');
        }
        fail(sb.toString());
    }
}
