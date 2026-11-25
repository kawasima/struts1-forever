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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
 * bundled commons-fileupload. For each class it checks that</p>
 * <ol>
 * <li>it refers to only one Servlet namespace, so nothing was left behind
 * by the javax-to-jakarta transformation, including in string constants;</li>
 * <li>it loads, and every type in the signatures of its declared fields,
 * methods and constructors resolves;</li>
 * <li>if it is concrete, it implements every abstract public method it
 * inherits;</li>
 * <li>every field and method reference whose owner is a Servlet/JSP API type
 * or one of the checked classes resolves by name and descriptor, searching
 * the owner's superclasses and interfaces like the JVM does.</li>
 * </ol>
 *
 * <p>Not checked: access and static/instance mismatches of resolved members,
 * and members looked up by name through reflection.</p>
 */
public class TestServletApiCompatibility extends TestCase {

    private File root;
    private Map classes;

    /**
     * Prefixes of both Servlet namespaces in internal form. Built at run time
     * so that the bytecode transformation, which rewrites string constants
     * naming javax.servlet, leaves them intact.
     */
    private String[] namespaces;

    /** The namespace this build must not refer to, in internal form. */
    private String foreignNamespace;

    protected void setUp() throws Exception {
        String servlet = "servlet";
        String javax = "javax" + '/' + servlet + '/';
        String jakarta = "jakarta" + '/' + servlet + '/';
        namespaces = new String[] {javax, jakarta};
        String own = Servlet.class.getName().replace('.', '/');
        foreignNamespace = own.startsWith(javax) ? jakarta : javax;

        root = classesRoot();
        classes = new HashMap();
        collect(root, classes);
        assertFalse("No classes found under " + root, classes.isEmpty());
    }

    public void testClasses_ReferToOneServletNamespace() {
        Set problems = new TreeSet();
        String dotted = foreignNamespace.replace('/', '.');
        Iterator it = classes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            ClassFile cf = (ClassFile) entry.getValue();
            for (int i = 1; i < cf.tags.length; i++) {
                if (cf.tags[i] == 1) {
                    String s = (String) cf.pool[i];
                    if (s.indexOf(foreignNamespace) >= 0
                            || s.indexOf(dotted) >= 0) {
                        problems.add(entry.getKey() + ": \"" + s + "\"");
                    }
                }
            }
        }
        assertEmpty("References to " + foreignNamespace, problems);
    }

    public void testClasses_LoadWithResolvableSignatures() {
        Set problems = new TreeSet();
        Iterator it = classes.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            try {
                Class c = loadClass(name);
                c.getDeclaredFields();
                c.getDeclaredMethods();
                c.getDeclaredConstructors();
            } catch (Throwable t) {
                problems.add(name + ": " + t);
            }
        }
        assertEmpty("Classes that cannot be loaded or introspected", problems);
    }

    public void testConcreteClasses_ImplementEveryInheritedAbstractMethod() {
        Set problems = new TreeSet();
        Iterator it = classes.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            Class c;
            Method[] methods;
            try {
                c = loadClass(name);
                methods = c.getMethods();
            } catch (Throwable t) {
                continue; // reported by testClasses_LoadWithResolvableSignatures
            }
            if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
                continue;
            }
            for (int i = 0; i < methods.length; i++) {
                if (Modifier.isAbstract(methods[i].getModifiers())) {
                    problems.add(name + " does not implement "
                            + methods[i].getDeclaringClass().getName() + "."
                            + methods[i].getName()
                            + descriptor(methods[i].getParameterTypes(),
                                    methods[i].getReturnType()));
                }
            }
        }
        assertEmpty("Missing implementations", problems);
    }

    public void testMemberReferences_Resolve() {
        Set problems = new TreeSet();
        int checked = 0;
        Iterator it = classes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            ClassFile cf = (ClassFile) entry.getValue();
            for (int i = 1; i < cf.tags.length; i++) {
                int tag = cf.tags[i];
                if (tag != 9 && tag != 10 && tag != 11) {
                    continue;
                }
                int[] ref = (int[]) cf.pool[i];
                String owner = cf.className(ref[0]);
                if (!isApi(owner) && !classes.containsKey(owner)) {
                    continue;
                }
                checked++;
                int[] nat = (int[]) cf.pool[ref[1]];
                String member = (String) cf.pool[nat[0]];
                String desc = (String) cf.pool[nat[1]];
                String problem;
                try {
                    Class type = loadClass(owner);
                    boolean found = (tag == 9) ? hasField(type, member, desc)
                                               : hasMethod(type, member, desc);
                    problem = found ? null : "not found";
                } catch (Throwable t) {
                    problem = t.toString();
                }
                if (problem != null) {
                    problems.add(owner + "." + member + desc + "  <- "
                            + entry.getKey() + " (" + problem + ")");
                }
            }
        }
        assertTrue("No member references checked", checked > 0);
        assertEmpty("Unresolved member references", problems);
    }

    // ------------------------------------------------------------ helpers

    private boolean isApi(String internalName) {
        for (int i = 0; i < namespaces.length; i++) {
            if (internalName.startsWith(namespaces[i])) {
                return true;
            }
        }
        return false;
    }

    private Class loadClass(String internalName) throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'), false,
                getClass().getClassLoader());
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

    private void collect(File dir, Map result) throws IOException {
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                collect(files[i], result);
            } else if (files[i].getName().endsWith(".class")
                    && !files[i].getName().equals("module-info.class")) {
                ClassFile cf = new ClassFile(files[i]);
                result.put(cf.name, cf);
            }
        }
    }

    private static boolean hasMethod(Class type, String name, String desc) {
        if ("<init>".equals(name)) {
            Constructor[] ctors = type.getDeclaredConstructors();
            for (int i = 0; i < ctors.length; i++) {
                if (desc.equals(descriptor(ctors[i].getParameterTypes(),
                        Void.TYPE))) {
                    return true;
                }
            }
            return false;
        }
        Iterator it = hierarchy(type).iterator();
        while (it.hasNext()) {
            Method[] methods = ((Class) it.next()).getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals(name)
                        && desc.equals(descriptor(
                                methods[i].getParameterTypes(),
                                methods[i].getReturnType()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasField(Class type, String name, String desc) {
        Iterator it = hierarchy(type).iterator();
        while (it.hasNext()) {
            Field[] fields = ((Class) it.next()).getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getName().equals(name)
                        && desc.equals(descriptor(fields[i].getType()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The type, its superclasses and all of their interfaces. For an
     * interface, also Object, whose methods interface method resolution
     * searches as well (JVMS 5.4.3.4).
     */
    private static List hierarchy(Class type) {
        List result = new ArrayList();
        Set seen = new HashSet();
        List queue = new ArrayList();
        queue.add(type);
        if (type.isInterface()) {
            queue.add(Object.class);
        }
        while (!queue.isEmpty()) {
            Class c = (Class) queue.remove(0);
            if (c == null || !seen.add(c)) {
                continue;
            }
            result.add(c);
            queue.add(c.getSuperclass());
            Class[] interfaces = c.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
                queue.add(interfaces[i]);
            }
        }
        return result;
    }

    private static String descriptor(Class[] params, Class returnType) {
        StringBuffer sb = new StringBuffer("(");
        for (int i = 0; i < params.length; i++) {
            sb.append(descriptor(params[i]));
        }
        sb.append(')').append(descriptor(returnType));
        return sb.toString();
    }

    private static String descriptor(Class type) {
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        if (type == Void.TYPE) return "V";
        if (type == Boolean.TYPE) return "Z";
        if (type == Byte.TYPE) return "B";
        if (type == Character.TYPE) return "C";
        if (type == Short.TYPE) return "S";
        if (type == Integer.TYPE) return "I";
        if (type == Long.TYPE) return "J";
        if (type == Float.TYPE) return "F";
        if (type == Double.TYPE) return "D";
        return "L" + type.getName().replace('.', '/') + ";";
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

    /** The constant pool of a class file. */
    private static class ClassFile {
        final String name;
        final int[] tags;
        final Object[] pool;

        ClassFile(File file) throws IOException {
            DataInputStream in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(file)));
            try {
                in.readInt();
                in.readUnsignedShort();
                in.readUnsignedShort();
                int count = in.readUnsignedShort();
                tags = new int[count];
                pool = new Object[count];
                for (int i = 1; i < count; i++) {
                    int tag = in.readUnsignedByte();
                    tags[i] = tag;
                    switch (tag) {
                    case 1:
                        pool[i] = in.readUTF();
                        break;
                    case 3:
                    case 4:
                        in.readInt();
                        break;
                    case 5:
                    case 6:
                        in.readLong();
                        i++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        pool[i] = new int[] {in.readUnsignedShort()};
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        pool[i] = new int[] {in.readUnsignedShort(),
                                in.readUnsignedShort()};
                        break;
                    case 15:
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                        break;
                    default:
                        throw new IOException("Unknown constant pool tag "
                                + tag + " in " + file);
                    }
                }
                in.readUnsignedShort(); // access flags
                name = className(in.readUnsignedShort());
            } finally {
                in.close();
            }
        }

        String className(int classIndex) {
            int[] ref = (int[]) pool[classIndex];
            return (String) pool[ref[0]];
        }
    }
}
