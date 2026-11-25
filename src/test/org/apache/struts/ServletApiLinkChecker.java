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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Static checks that compiled classes link against an API on a class loader.
 * Used by {@link TestServletApiCompatibility}; each check is pinned by
 * {@link TestServletApiLinkChecker}.
 *
 * <p>Every check takes a map of internal class name to {@link ClassFile} and
 * returns the problems found, sorted. API types are those whose internal
 * name starts with one of the given prefixes.</p>
 */
final class ServletApiLinkChecker {

    private ServletApiLinkChecker() {
    }

    /**
     * The javax and jakarta Servlet package prefixes in internal form, built
     * at run time so that the javax-to-jakarta transformation, which rewrites
     * string constants naming javax.servlet, leaves them intact.
     */
    static String[] servletNamespaces() {
        String servlet = "servlet";
        return new String[] {
            "javax" + '/' + servlet + '/',
            "jakarta" + '/' + servlet + '/'
        };
    }

    /** The prefix of the namespace that <code>servletClass</code> is not in. */
    static String foreignNamespace(Class servletClass) {
        String[] namespaces = servletNamespaces();
        String own = servletClass.getName().replace('.', '/');
        return own.startsWith(namespaces[0]) ? namespaces[1] : namespaces[0];
    }

    /** Reads every class file under <code>dir</code>. */
    static Map readClasses(File dir) throws IOException {
        Map result = new HashMap();
        collect(dir, result);
        return result;
    }

    /**
     * Constant pool strings that contain <code>prefix</code> in internal or
     * dotted form, such as references left behind by a namespace
     * transformation.
     */
    static Set references(Map classes, String prefix) {
        Set problems = new TreeSet();
        String dotted = prefix.replace('/', '.');
        Iterator it = classes.values().iterator();
        while (it.hasNext()) {
            ClassFile cf = (ClassFile) it.next();
            for (int i = 1; i < cf.tags.length; i++) {
                if (cf.tags[i] == ClassFile.UTF8) {
                    String s = (String) cf.pool[i];
                    if (s.indexOf(prefix) >= 0 || s.indexOf(dotted) >= 0) {
                        problems.add(cf.name + ": \"" + s + "\"");
                    }
                }
            }
        }
        return problems;
    }

    /**
     * API types named anywhere in the constant pool that do not load: class
     * constants (class literals, casts, instanceof, new, catch) and type
     * names in descriptors and generic signatures.
     */
    static Set unresolvedTypes(Map classes, String[] apiPrefixes,
            ClassLoader loader) {
        Set problems = new TreeSet();
        Iterator it = classes.values().iterator();
        while (it.hasNext()) {
            ClassFile cf = (ClassFile) it.next();
            Set names = new TreeSet();
            for (int i = 1; i < cf.tags.length; i++) {
                if (cf.tags[i] == ClassFile.CLASS) {
                    addTypeName(names, cf.className(i), apiPrefixes);
                } else if (cf.tags[i] == ClassFile.UTF8) {
                    addDescriptorTypes(names, (String) cf.pool[i], apiPrefixes);
                }
            }
            Iterator n = names.iterator();
            while (n.hasNext()) {
                String name = (String) n.next();
                try {
                    load(name, loader);
                } catch (Throwable t) {
                    problems.add(name + "  <- " + cf.name);
                }
            }
        }
        return problems;
    }

    /**
     * Classes that do not load, or whose declared fields, methods and
     * constructors refer to types that do not load.
     */
    static Set unloadableClasses(Map classes, ClassLoader loader) {
        Set problems = new TreeSet();
        Iterator it = classes.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            try {
                Class c = load(name, loader);
                c.getDeclaredFields();
                c.getDeclaredMethods();
                c.getDeclaredConstructors();
            } catch (Throwable t) {
                problems.add(name + ": " + t);
            }
        }
        return problems;
    }

    /**
     * Abstract public methods that concrete classes inherit without
     * implementing. Classes that do not load are skipped; see
     * {@link #unloadableClasses(Map, ClassLoader)}.
     */
    static Set missingImplementations(Map classes, ClassLoader loader) {
        Set problems = new TreeSet();
        Iterator it = classes.keySet().iterator();
        while (it.hasNext()) {
            String name = (String) it.next();
            Class c;
            try {
                c = load(name, loader);
                if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
                    continue;
                }
                problems.addAll(abstractPublicMethods(c));
            } catch (Throwable t) {
                continue;
            }
        }
        return problems;
    }

    /** The abstract public methods of <code>type</code>, as strings. */
    static Set abstractPublicMethods(Class type) {
        Set result = new TreeSet();
        Method[] methods = type.getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (Modifier.isAbstract(methods[i].getModifiers())) {
                result.add(type.getName() + " does not implement "
                        + methods[i].getDeclaringClass().getName() + "."
                        + methods[i].getName()
                        + descriptor(methods[i].getParameterTypes(),
                                methods[i].getReturnType()));
            }
        }
        return result;
    }

    /**
     * Field and method references that do not resolve, where the owner is an
     * API type or one of <code>classes</code>.
     */
    static Set unresolvedMembers(Map classes, String[] apiPrefixes,
            ClassLoader loader) {
        Set problems = new TreeSet();
        Iterator it = classes.values().iterator();
        while (it.hasNext()) {
            ClassFile cf = (ClassFile) it.next();
            for (int i = 1; i < cf.tags.length; i++) {
                int tag = cf.tags[i];
                if (tag != ClassFile.FIELDREF && tag != ClassFile.METHODREF
                        && tag != ClassFile.INTERFACE_METHODREF) {
                    continue;
                }
                int[] ref = (int[]) cf.pool[i];
                String owner = cf.className(ref[0]);
                if (!isApi(owner, apiPrefixes) && !classes.containsKey(owner)) {
                    continue;
                }
                int[] nat = (int[]) cf.pool[ref[1]];
                String member = (String) cf.pool[nat[0]];
                String desc = (String) cf.pool[nat[1]];
                String problem;
                try {
                    Class type = load(owner, loader);
                    boolean found = (tag == ClassFile.FIELDREF)
                            ? hasField(type, member, desc)
                            : hasMethod(type, member, desc);
                    problem = found ? null : "not found";
                } catch (Throwable t) {
                    problem = t.toString();
                }
                if (problem != null) {
                    problems.add(owner + "." + member + desc + "  <- "
                            + cf.name + " (" + problem + ")");
                }
            }
        }
        return problems;
    }

    /**
     * Whether a method with this name and descriptor is found in the type,
     * its superclasses or their interfaces, as JVM method resolution does.
     */
    static boolean hasMethod(Class type, String name, String desc) {
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

    /**
     * Whether a field with this name and descriptor is found in the type,
     * its superclasses or their interfaces.
     */
    static boolean hasField(Class type, String name, String desc) {
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

    // ------------------------------------------------------------ helpers

    private static void collect(File dir, Map result) throws IOException {
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

    private static boolean isApi(String internalName, String[] apiPrefixes) {
        for (int i = 0; i < apiPrefixes.length; i++) {
            if (internalName.startsWith(apiPrefixes[i])) {
                return true;
            }
        }
        return false;
    }

    /** Adds a class constant, which may be an array descriptor. */
    private static void addTypeName(Set names, String classConstant,
            String[] apiPrefixes) {
        if (classConstant.startsWith("[")) {
            addDescriptorTypes(names, classConstant, apiPrefixes);
        } else if (isApi(classConstant, apiPrefixes)) {
            names.add(classConstant);
        }
    }

    /** Adds every <code>Lname;</code> or <code>Lname&lt;</code> in a descriptor or signature. */
    private static void addDescriptorTypes(Set names, String s,
            String[] apiPrefixes) {
        for (int p = 0; p < apiPrefixes.length; p++) {
            String marker = "L" + apiPrefixes[p];
            int from = s.indexOf(marker);
            while (from >= 0) {
                int start = from + 1;
                int end = start;
                while (end < s.length() && s.charAt(end) != ';'
                        && s.charAt(end) != '<') {
                    end++;
                }
                if (end < s.length()) {
                    names.add(s.substring(start, end));
                }
                from = s.indexOf(marker, end);
            }
        }
    }

    private static Class load(String internalName, ClassLoader loader)
            throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'), false, loader);
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

    /** The name and constant pool of a class file. */
    static final class ClassFile {
        static final int UTF8 = 1;
        static final int CLASS = 7;
        static final int FIELDREF = 9;
        static final int METHODREF = 10;
        static final int INTERFACE_METHODREF = 11;

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
