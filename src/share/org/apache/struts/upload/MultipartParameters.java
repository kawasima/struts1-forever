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

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

/**
 * How {@link MultipartRequestWrapper} combines the parameters of the wrapped
 * request with the ones parsed from the multipart body.
 *
 * <p>The javax and the jakarta <code>MultipartRequestWrapper</code> delegate
 * to the wrapped request in different ways, but must merge parameters in
 * exactly the same way. Both call these methods, which take no Servlet API
 * types so that they compile unchanged against either API. They keep no
 * state: the multipart parameters stay in the wrapper's protected
 * <code>parameters</code> map.</p>
 */
final class MultipartParameters {

    private MultipartParameters() {
    }

    /**
     * Appends <code>value</code> to the values of <code>name</code>.
     */
    static void add(Map multipart, String name, String value) {
        String[] mValue = (String[]) multipart.get(name);
        if (mValue == null) {
            mValue = new String[0];
        }
        String[] newValue = new String[mValue.length + 1];
        System.arraycopy(mValue, 0, newValue, 0, mValue.length);
        newValue[mValue.length] = value;

        multipart.put(name, newValue);
    }

    /**
     * The request parameter if there is one, otherwise the first multipart
     * value.
     */
    static String first(String requestValue, Map multipart, String name) {
        if (requestValue != null) {
            return requestValue;
        }
        String[] mValue = (String[]) multipart.get(name);
        if ((mValue != null) && (mValue.length > 0)) {
            return mValue[0];
        }
        return null;
    }

    /**
     * The request values if there are any, otherwise the multipart values.
     */
    static String[] values(String[] requestValues, Map multipart, String name) {
        if (requestValues != null) {
            return requestValues;
        }
        return (String[]) multipart.get(name);
    }

    /**
     * The request parameter names followed by the multipart parameter names.
     */
    static Enumeration names(Enumeration requestNames, Map multipart) {
        Vector list = new Vector();
        while (requestNames.hasMoreElements()) {
            list.add(requestNames.nextElement());
        }
        Iterator iterator = multipart.keySet().iterator();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return Collections.enumeration(list);
    }

    /**
     * The multipart parameters, overridden by the request parameters.
     *
     * @param requestParameters name to <code>String[]</code> of the wrapped
     *        request
     */
    static Map map(Map multipart, Map requestParameters) {
        Map map = new HashMap(multipart);
        map.putAll(requestParameters);
        return map;
    }
}
