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
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import junit.framework.TestCase;

import org.apache.struts.action.ActionServlet;

/**
 * Every reference from the production classes to the Servlet and JSP API
 * must resolve against the API on the classpath.
 *
 * <p>In the javax build this holds by construction, because the classes are
 * compiled against that API. The jakarta build is produced by bytecode
 * transformation and runs against Jakarta Servlet 6.0 / JSP 3.1, which removed
 * long-deprecated methods. A call to a removed method passes the
 * transformation unnoticed and throws <code>NoSuchMethodError</code> only
 * when that code path runs. This test reads the constant pool of every class
 * in the directory that holds the production classes (including bundled
 * libraries) and resolves each Servlet/JSP class, method and field
 * reference.</p>
 */
public class TestServletApiLinkage extends TestCase {

    public void testServletApiReferences_AllResolve() throws Exception {
        File root = classesRoot();
        Set unresolved = new TreeSet();
        int checked = scan(root, unresolved);
        assertTrue("No Servlet API references found under " + root,
                checked > 0);
        assertTrue("Unresolved Servlet API references:\n" + join(unresolved),
                unresolved.isEmpty());
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

    private int scan(File dir, Set unresolved) throws IOException {
        int checked = 0;
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                checked += scan(files[i], unresolved);
            } else if (files[i].getName().endsWith(".class")
                    && !files[i].getName().equals("module-info.class")) {
                checked += check(files[i], unresolved);
            }
        }
        return checked;
    }

    private int check(File file, Set unresolved) throws IOException {
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)));
        int[] tags;
        Object[] pool;
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
                    throw new IOException("Unknown constant pool tag " + tag
                            + " in " + file);
                }
            }
        } finally {
            in.close();
        }

        String source = file.getPath();
        int checked = 0;
        for (int i = 1; i < tags.length; i++) {
            if (tags[i] == 7) {
                String name = className(pool, i);
                if (isServletApi(name)) {
                    checked++;
                    if (loadClass(name) == null) {
                        unresolved.add(name + " (class)  <- " + source);
                    }
                }
            } else if (tags[i] == 9 || tags[i] == 10 || tags[i] == 11) {
                int[] ref = (int[]) pool[i];
                String owner = className(pool, ref[0]);
                if (!isServletApi(owner)) {
                    continue;
                }
                checked++;
                int[] nat = (int[]) pool[ref[1]];
                String name = (String) pool[nat[0]];
                String desc = (String) pool[nat[1]];
                Class type = loadClass(owner);
                boolean found = type != null
                        && (tags[i] == 9 ? hasField(type, name, desc)
                                         : hasMethod(type, name, desc));
                if (!found) {
                    unresolved.add(owner + "." + name + desc + "  <- "
                            + source);
                }
            }
        }
        return checked;
    }

    private static String className(Object[] pool, int classIndex) {
        int[] ref = (int[]) pool[classIndex];
        return (String) pool[ref[0]];
    }

    private static boolean isServletApi(String internalName) {
        String name = internalName;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L")) {
            name = name.substring(1);
        }
        return name.startsWith("javax/servlet/")
                || name.startsWith("jakarta/servlet/");
    }

    private Class loadClass(String internalName) {
        String name = internalName.replace('/', '.');
        try {
            return Class.forName(name, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        } catch (LinkageError e) {
            return null;
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

    /** The type, its superclasses and all of their interfaces. */
    private static List hierarchy(Class type) {
        List result = new ArrayList();
        Set seen = new HashSet();
        List queue = new ArrayList();
        queue.add(type);
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

    private static String join(Set lines) {
        StringBuffer sb = new StringBuffer();
        Iterator it = lines.iterator();
        while (it.hasNext()) {
            sb.append("  ").append(it.next()).append('\n');
        }
        return sb.toString();
    }
}
