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
package org.apache.struts.taglib.html;

import java.text.AttributedCharacterIterator;
import java.text.MessageFormat;
import java.util.Locale;

import org.apache.struts.util.MessageResources;
import org.apache.struts.util.PropertyMessageResources;
import org.apache.struts.util.ResponseUtils;

/**
 * Renders a resource message as HTML for {@link ErrorsTag}: the template
 * from the message resources is written as it is, and the text each
 * replacement argument produces is HTML-escaped.
 *
 * <p>The template is treated as trusted HTML. The escaping covers HTML text
 * only; an argument placed in a URL, an event handler, a script or a style
 * is not made safe by it.</p>
 *
 * <p>Only {@link PropertyMessageResources} itself is handled this way,
 * because its formatting is known: {@link MessageResources#getMessage(Locale,
 * String, Object[])} with a {@link MessageFormat}. Any other
 * implementation, including a subclass of {@code PropertyMessageResources},
 * may format messages differently, so its formatted message is escaped as a
 * whole, as in 1.2.9-forever-2.</p>
 *
 * <p>The cached {@code MessageFormat} and the protected members of
 * {@code MessageResources} cannot be reached from this package without
 * adding public API, so this class builds its own {@code MessageFormat} the
 * way {@code MessageResources.getMessage(Locale, String, Object[])} does.
 * The output differs from that method only in these cases:</p>
 * <ul>
 * <li>The pattern is parsed on every call. {@code MessageResources} parses
 *     it once per locale and key and keeps it, so a change to the JVM
 *     default locale or time zone, or to {@code setEscape()}, after the
 *     first call is seen here and not there.</li>
 * <li>A <code>null</code> locale is replaced with the current
 *     {@code Locale.getDefault()}, not the default locale saved when the
 *     resources were created. {@code ErrorsTag} passes the request locale,
 *     which a container does not leave <code>null</code>.</li>
 * <li>A template that starts and ends with "???" is taken for a missing
 *     key by {@code isPresent()} and escaped whole.</li>
 * </ul>
 * <p>If this class is shared with another tag (for example to escape the
 * arguments of {@code <bean:message>}), move it next to
 * {@code MessageResources} so it can use the cached format instead of
 * copying it.</p>
 */
final class HtmlMessageFormatter {

    private HtmlMessageFormatter() {
    }

    /**
     * Returns the message for <code>key</code> as HTML, or <code>null</code>
     * if the resources return <code>null</code> for it.
     *
     * @param resources the message resources to read the template from
     * @param locale the requested locale, or <code>null</code>
     * @param key the message key
     * @param args the replacement arguments, or <code>null</code> to write
     *  the template without formatting it, as
     *  {@link org.apache.struts.taglib.TagUtils#message} does
     */
    static String format(MessageResources resources, Locale locale,
            String key, Object[] args) {

        // isPresent() is checked before args: the "???key???" text that
        // PropertyMessageResources returns for a missing key holds the key,
        // not a template, even when there are no arguments.
        if (resources.getClass() != PropertyMessageResources.class
                || !resources.isPresent(locale, key)) {
            String message = (args == null)
                    ? resources.getMessage(locale, key)
                    : resources.getMessage(locale, key, args);
            return ResponseUtils.filter(message);
        }

        String template = resources.getMessage(locale, key);
        if (args == null) {
            return template;
        }

        // Built as MessageResources.getMessage(Locale, String, Object[])
        // builds it, so number and date subformats get the same locale.
        // That method replaces a null locale with its protected
        // defaultLocale, which is Locale.getDefault() when it was created.
        MessageFormat format =
                new MessageFormat(escapeQuotes(resources, template));
        format.setLocale((locale == null) ? Locale.getDefault() : locale);

        return escapeArguments(format.formatToCharacterIterator(args));
    }

    /**
     * Writes the text of the iterator, HTML-escaping each run that carries
     * a {@link MessageFormat.Field#ARGUMENT} attribute.
     */
    private static String escapeArguments(AttributedCharacterIterator it) {
        StringBuffer result = new StringBuffer();
        StringBuffer run = new StringBuffer();

        // The end is found by index, not by CharacterIterator.DONE, because
        // DONE is U+FFFF and may occur in the message itself.
        it.first();
        while (it.getIndex() < it.getEndIndex()) {
            int limit = it.getRunLimit(MessageFormat.Field.ARGUMENT);
            boolean argument =
                    it.getAttribute(MessageFormat.Field.ARGUMENT) != null;

            run.setLength(0);
            while (it.getIndex() < limit) {
                run.append(it.current());
                it.next();
            }

            if (argument) {
                result.append(ResponseUtils.filter(run.toString()));
            } else {
                result.append(run);
            }
        }
        return result.toString();
    }

    /**
     * Doubles single quotes as the protected
     * <code>MessageResources.escape(String)</code> does.
     */
    private static String escapeQuotes(MessageResources resources,
            String template) {
        if (!resources.isEscape() || template.indexOf('\'') < 0) {
            return template;
        }
        StringBuffer sb = new StringBuffer(template.length() + 8);
        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            if (ch == '\'') {
                sb.append('\'');
            }
            sb.append(ch);
        }
        return sb.toString();
    }
}
