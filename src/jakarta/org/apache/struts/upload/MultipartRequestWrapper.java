/*
 * Copyright 1999-2026 The Apache Software Foundation.
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

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Jakarta variant of {@code MultipartRequestWrapper}, packaged only in the
 * jakarta JAR in place of the transformed javax class.
 *
 * <p>The javax class implements {@code HttpServletRequest} directly so that
 * it still compiles against Servlet 2.2. After transformation it lacks every
 * method added since then, and it calls methods removed in Servlet 6.0. This
 * class extends {@link HttpServletRequestWrapper} instead, so the container
 * supplies every current and future method, and keeps the public and
 * protected API of the javax class.</p>
 *
 * <p>Behaviour shared with the javax class is pinned by
 * {@code TestMultipartRequestWrapper}, which runs against both JARs.</p>
 */
public class MultipartRequestWrapper extends HttpServletRequestWrapper {

    /** Logging instance */
    private static final Log log = LogFactory.getLog(MultipartRequestWrapper.class);

    /**
     * The parameters for this multipart request
     */
    protected Map parameters;

    /**
     * The underlying HttpServletRequest
     */
    protected HttpServletRequest request;

    public MultipartRequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
        this.parameters = new HashMap();
    }

    /**
     * Sets a parameter for this request.  The parameter is actually
     * separate from the request parameters, but calling on the
     * getParameter() methods of this class will work as if they weren't.
     */
    public void setParameter(String name, String value) {
        String[] mValue = (String[]) parameters.get(name);
        if (mValue == null) {
            mValue = new String[0];
        }
        String[] newValue = new String[mValue.length + 1];
        System.arraycopy(mValue, 0, newValue, 0, mValue.length);
        newValue[mValue.length] = value;

        parameters.put(name, newValue);
    }

    /**
     * Attempts to get a parameter for this request.  It first looks in the
     * underlying HttpServletRequest object for the parameter, and if that
     * doesn't exist it looks for the parameters retrieved from the multipart
     * request
     */
    public String getParameter(String name) {
        String value = request.getParameter(name);
        if (value == null) {
            String[] mValue = (String[]) parameters.get(name);
            if ((mValue != null) && (mValue.length > 0)) {
                value = mValue[0];
            }
        }
        return value;
    }

    /**
     * Returns the names of the parameters for this request.
     * The enumeration consists of the normal request parameter
     * names plus the parameters read from the multipart request
     */
    public Enumeration getParameterNames() {
        Enumeration baseParams = request.getParameterNames();
        Vector list = new Vector();
        while (baseParams.hasMoreElements()) {
            list.add(baseParams.nextElement());
        }
        Collection multipartParams = parameters.keySet();
        Iterator iterator = multipartParams.iterator();
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return Collections.enumeration(list);
    }

    public String[] getParameterValues(String name) {
        String[] value = request.getParameterValues(name);
        if (value == null) {
            value = (String[]) parameters.get(name);
        }
        return value;
    }

    public Map getParameterMap() {
        Map map = new HashMap(parameters);
        Enumeration names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            map.put(name, request.getParameterValues(name));
        }
        return map;
    }

    /**
     * Returns the underlying HttpServletRequest for this wrapper
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    public void setRequest(ServletRequest request) {
        super.setRequest(request);
        this.request = (HttpServletRequest) request;
    }

    /**
     * The javax class logs and swallows a failure here instead of throwing
     * {@link UnsupportedEncodingException}.
     */
    public void setCharacterEncoding(String encoding) {
        try {
            request.setCharacterEncoding(encoding);
        } catch (UnsupportedEncodingException e) {
            log.error("Error invoking method 'setCharacterEncoding' ", e);
        }
    }

    /**
     * {@code ServletRequest.getRealPath} was removed in Servlet 6.0.
     */
    public String getRealPath(String path) {
        return request.getServletContext().getRealPath(path);
    }

    /**
     * {@code HttpServletRequest.isRequestedSessionIdFromUrl} was removed in
     * Servlet 6.0.
     */
    public boolean isRequestedSessionIdFromUrl() {
        return request.isRequestedSessionIdFromURL();
    }
}
