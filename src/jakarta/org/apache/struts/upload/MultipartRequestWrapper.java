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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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
 * it still compiles against Servlet 2.2, which freezes the methods it can
 * delegate at that version. This class extends
 * {@link HttpServletRequestWrapper} instead, so the container supplies every
 * current and future method.</p>
 *
 * <p>Differences from the javax class:</p>
 * <ul>
 * <li>There is no {@code protected HttpServletRequest request} field. The
 * wrapped request is held only by {@code ServletRequestWrapper}, whose
 * methods use it directly, and containers replace it through
 * {@link #setRequest(ServletRequest)} during forward and include. A second
 * copy in a field could silently point to a different request. Subclasses
 * use {@link #getRequest()} and {@link #setRequest(ServletRequest)}.</li>
 * <li>{@link #setRequest(ServletRequest)} accepts only an
 * {@code HttpServletRequest}.</li>
 * <li>{@link #getRealPath(String)} and {@link #isRequestedSessionIdFromUrl()}
 * cannot delegate to the method of the same name, which Servlet 6.0
 * removed. Every other method reads the wrapped request through the same
 * methods as the javax class.</li>
 * </ul>
 *
 * <p>Parameter merging is shared with the javax class through
 * {@link MultipartParameters}, and {@code TestMultipartRequestWrapper} runs
 * against both JARs.</p>
 */
public class MultipartRequestWrapper extends HttpServletRequestWrapper {

    /** Logging instance */
    private static final Log log = LogFactory.getLog(MultipartRequestWrapper.class);

    /**
     * The parameters for this multipart request
     */
    protected Map parameters;

    public MultipartRequestWrapper(HttpServletRequest request) {
        super(request);
        this.parameters = new HashMap();
    }

    /**
     * Sets a parameter for this request.  The parameter is actually
     * separate from the request parameters, but calling on the
     * getParameter() methods of this class will work as if they weren't.
     */
    public void setParameter(String name, String value) {
        MultipartParameters.add(parameters, name, value);
    }

    /**
     * Attempts to get a parameter for this request.  It first looks in the
     * underlying HttpServletRequest object for the parameter, and if that
     * doesn't exist it looks for the parameters retrieved from the multipart
     * request
     */
    public String getParameter(String name) {
        return MultipartParameters.first(
                getRequest().getParameter(name), parameters, name);
    }

    /**
     * Returns the names of the parameters for this request.
     * The enumeration consists of the normal request parameter
     * names plus the parameters read from the multipart request
     */
    public Enumeration getParameterNames() {
        return MultipartParameters.names(
                getRequest().getParameterNames(), parameters);
    }

    public String[] getParameterValues(String name) {
        return MultipartParameters.values(
                getRequest().getParameterValues(name), parameters, name);
    }

    /**
     * Reads the wrapped request through getParameterNames() and
     * getParameterValues(), like the javax class, so that request wrappers
     * that override only those methods are honoured.
     */
    public Map getParameterMap() {
        HttpServletRequest request = getRequest();
        Map requestParameters = new HashMap();
        Enumeration names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            requestParameters.put(name, request.getParameterValues(name));
        }
        return MultipartParameters.map(parameters, requestParameters);
    }

    /**
     * Returns the underlying HttpServletRequest for this wrapper
     */
    public HttpServletRequest getRequest() {
        return (HttpServletRequest) super.getRequest();
    }

    /**
     * Replaces the wrapped request. Rejects anything but an
     * {@code HttpServletRequest} before changing any state.
     */
    public void setRequest(ServletRequest request) {
        if (!(request instanceof HttpServletRequest)) {
            throw new IllegalArgumentException(
                    "MultipartRequestWrapper requires an HttpServletRequest: "
                    + request);
        }
        super.setRequest(request);
    }

    /**
     * The javax class logs and swallows a failure here instead of throwing
     * {@link UnsupportedEncodingException}.
     */
    public void setCharacterEncoding(String encoding) {
        try {
            getRequest().setCharacterEncoding(encoding);
        } catch (UnsupportedEncodingException e) {
            log.error("Error invoking method 'setCharacterEncoding' ", e);
        }
    }

    /**
     * {@code ServletRequest.getRealPath} was removed in Servlet 6.0.
     */
    public String getRealPath(String path) {
        return getServletContext().getRealPath(path);
    }

    /**
     * {@code HttpServletRequest.isRequestedSessionIdFromUrl} was removed in
     * Servlet 6.0.
     */
    public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
    }
}
