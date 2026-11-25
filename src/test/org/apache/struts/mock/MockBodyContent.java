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
package org.apache.struts.mock;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.BodyContent;

/**
 * Body content returned by {@link MockPageContext#pushBody()}. It keeps
 * what the body writes, so that a body tag reads it in
 * <code>doAfterBody()</code> as it would in a JSP container.
 */
public class MockBodyContent extends BodyContent {

    private final StringBuffer buffer = new StringBuffer();

    public MockBodyContent(JspWriter enclosingWriter) {
        super(enclosingWriter);
    }

    public String getString() {
        return buffer.toString();
    }

    public Reader getReader() {
        return new StringReader(getString());
    }

    public void writeOut(Writer out) throws IOException {
        out.write(getString());
    }

    public void write(char[] cbuf, int off, int len) {
        buffer.append(cbuf, off, len);
    }

    public void newLine() {
        buffer.append('\n');
    }

    public void print(boolean b) {
        buffer.append(b);
    }

    public void print(char c) {
        buffer.append(c);
    }

    public void print(int i) {
        buffer.append(i);
    }

    public void print(long l) {
        buffer.append(l);
    }

    public void print(float f) {
        buffer.append(f);
    }

    public void print(double d) {
        buffer.append(d);
    }

    public void print(char[] s) {
        buffer.append(s);
    }

    public void print(String s) {
        buffer.append(s);
    }

    public void print(Object o) {
        buffer.append(o);
    }

    public void println() {
        newLine();
    }

    public void println(boolean b) {
        print(b);
        newLine();
    }

    public void println(char c) {
        print(c);
        newLine();
    }

    public void println(int i) {
        print(i);
        newLine();
    }

    public void println(long l) {
        print(l);
        newLine();
    }

    public void println(float f) {
        print(f);
        newLine();
    }

    public void println(double d) {
        print(d);
        newLine();
    }

    public void println(char[] s) {
        print(s);
        newLine();
    }

    public void println(String s) {
        print(s);
        newLine();
    }

    public void println(Object o) {
        print(o);
        newLine();
    }

    public void clear() {
        buffer.setLength(0);
    }

    public void clearBuffer() {
        buffer.setLength(0);
    }

    public void flush() {
    }

    public void close() {
    }

    public int getRemaining() {
        return 0;
    }
}
