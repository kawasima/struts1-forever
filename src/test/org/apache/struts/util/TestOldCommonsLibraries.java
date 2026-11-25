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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import junit.framework.TestCase;

/**
 * Applications may still have the Commons Digester 1.6 and Commons Validator
 * 1.1.4 that Struts 1.2.9 shipped in <code>WEB-INF/lib</code>. This runs the
 * XML parsing and <code>&lt;html:javascript&gt;</code> tests with those
 * versions in place of the declared ones, in a class loader of their own,
 * and checks that the validator scripts in Struts match those of Commons
 * Validator 1.1.4.
 *
 * <p>The Maven build copies the old JARs to the directory in the system
 * property <code>struts.test.libs</code>.</p>
 */
public class TestOldCommonsLibraries extends TestCase {

    private static final String[] TEST_CLASSES = {
        "org.apache.struts.util.TestDigesterSecurity",
        "org.apache.struts.action.TestDigesterXxePrevention",
        "org.apache.struts.plugins.TestDigestingPlugIn",
        "org.apache.struts.tiles.xmlDefinition.TestXmlParser",
        "org.apache.struts.tiles.TestTilesPlugin",
        "org.apache.struts.validator.TestValidatorResourcesLoader",
        "org.apache.struts.taglib.html.TestJavascriptValidatorTagDefaultRules",
    };

    private static final String JAVASCRIPT_PATH = "org/apache/commons/validator/javascript/";

    private static final String DIGESTER_JAR = "commons-digester-1.6.jar";
    private static final String VALIDATOR_JAR = "commons-validator-1.1.4.jar";

    public void testXmlParsingTests_PassWithDigester16AndValidator114() throws Exception {
        ClassLoader loader = oldLibraryLoader();
        assertLoadedFrom(loader, "org.apache.commons.digester.Digester", DIGESTER_JAR);
        assertLoadedFrom(loader, "org.apache.commons.validator.ValidatorResources",
            VALIDATOR_JAR);

        Class suiteClass = loader.loadClass("junit.framework.TestSuite");
        Class resultClass = loader.loadClass("junit.framework.TestResult");
        StringBuffer failures = new StringBuffer();
        int run = 0;
        // Struts and Commons Digester load classes through the context class
        // loader, which must see the same classes as the tests.
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            for (int i = 0; i < TEST_CLASSES.length; i++) {
                Object suite = suiteClass.getConstructor(new Class[] {Class.class})
                    .newInstance(new Object[] {loader.loadClass(TEST_CLASSES[i])});
                Object result = resultClass.newInstance();
                suiteClass.getMethod("run", new Class[] {resultClass})
                    .invoke(suite, new Object[] {result});
                run += ((Integer) call(result, "runCount")).intValue();
                append(failures, (Enumeration) call(result, "failures"));
                append(failures, (Enumeration) call(result, "errors"));
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }

        assertEquals("", failures.toString());
        assertTrue("tests run: " + run, run > 50);
    }

    public void testJavascript_SameAsInValidator114() throws Exception {
        ClassLoader loader = oldLibraryLoader();
        URL validatorJar = oldJar(System.getProperty("struts.test.libs"), VALIDATOR_JAR);
        JarFile jar = new JarFile(new File(validatorJar.getFile()));
        int compared = 0;
        try {
            Enumeration entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = ((JarEntry) entries.nextElement()).getName();
                if (!name.startsWith(JAVASCRIPT_PATH) || !name.endsWith(".js")) {
                    continue;
                }
                Enumeration copies = loader.getResources(name);
                URL inValidator = null;
                URL inStruts = null;
                while (copies.hasMoreElements()) {
                    URL copy = (URL) copies.nextElement();
                    if (copy.toString().indexOf(VALIDATOR_JAR) >= 0) {
                        inValidator = copy;
                    } else {
                        inStruts = copy;
                    }
                }
                assertNotNull(name, inValidator);
                assertNotNull(name, inStruts);
                assertEquals(name, read(inValidator), read(inStruts));
                compared++;
            }
        } finally {
            jar.close();
        }
        assertEquals(14, compared);
    }

    /**
     * The test class path with the Commons Digester and Commons Validator
     * JARs replaced, under the platform class loader so that no class is
     * shared with this test.
     */
    private static ClassLoader oldLibraryLoader() throws Exception {
        String libs = System.getProperty("struts.test.libs");
        assertNotNull("system property struts.test.libs is set by the Maven build;"
            + " run this test with Maven", libs);

        String classPath = System.getProperty("surefire.test.class.path");
        if (classPath == null) {
            classPath = System.getProperty("java.class.path");
        }
        List urls = new ArrayList();
        StringTokenizer entries = new StringTokenizer(classPath, File.pathSeparator);
        while (entries.hasMoreTokens()) {
            File entry = new File(entries.nextToken());
            String name = entry.getName();
            if (!name.startsWith("commons-digester-")
                && !name.startsWith("commons-validator-")) {
                urls.add(entry.toURL());
            }
        }
        urls.add(oldJar(libs, DIGESTER_JAR));
        urls.add(oldJar(libs, VALIDATOR_JAR));

        return new URLClassLoader((URL[]) urls.toArray(new URL[urls.size()]),
            ClassLoader.getSystemClassLoader().getParent());
    }

    private static URL oldJar(String dir, String name) throws Exception {
        File jar = new File(dir, name);
        assertTrue(jar + " exists", jar.isFile());
        return jar.toURL();
    }

    private static void assertLoadedFrom(ClassLoader loader, String className, String jar)
            throws Exception {
        String location = loader.loadClass(className)
            .getProtectionDomain().getCodeSource().getLocation().toString();
        assertTrue(className + " from " + location, location.endsWith(jar));
    }

    /**
     * The lines of the resource, without line terminators.
     */
    private static String read(URL url) throws Exception {
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(url.openStream(), "ISO-8859-1"));
        StringBuffer text = new StringBuffer();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    private static Object call(Object target, String method) throws Exception {
        return target.getClass().getMethod(method, new Class[0])
            .invoke(target, new Object[0]);
    }

    private static void append(StringBuffer text, Enumeration problems) {
        while (problems.hasMoreElements()) {
            Object problem = problems.nextElement();
            text.append(problem).append(": ")
                .append(call0(problem, "exceptionMessage")).append('\n');
        }
    }

    private static Object call0(Object target, String method) {
        try {
            return call(target, method);
        } catch (Exception e) {
            return "";
        }
    }
}
