/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.servlet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.Bus;
import org.easymock.EasyMock;

import org.junit.Assert;
import org.junit.Test;

public class AbstractHTTPServletTest extends Assert {

    private ByteArrayOutputStream output;

    @Test
    public void testStaticResourcesWithRedirectQueryCheck() throws Exception {
        // GET /services/?.html must not be served as the static resource "/" (directory listing)
        TestServlet servlet = createServlet(staticResourcesWithQueryCheck());
        servlet.service(createRequest("/", ".html"), createResponse());

        assertTrue(servlet.requestedResources.isEmpty());
        assertEquals(0, output.size());
        assertTrue(servlet.invoked);
    }

    @Test
    public void testStaticResourcesNonMatchingFileWithRedirectQueryCheck() throws Exception {
        // GET /services/WEB-INF/web.xml?.html must not expose an arbitrary resource
        TestServlet servlet = createServlet(staticResourcesWithQueryCheck());
        servlet.service(createRequest("/WEB-INF/web.xml", "a=.html"), createResponse());

        assertTrue(servlet.requestedResources.isEmpty());
        assertFalse(output.toString("UTF-8").contains("web.xml"));
        assertTrue(servlet.invoked);
    }

    @Test
    public void testStaticResourcesMatchWithRedirectQueryCheck() throws Exception {
        TestServlet servlet = createServlet(staticResourcesWithQueryCheck());
        servlet.service(createRequest("/index.html", "a=b"), createResponse());

        assertEquals(1, servlet.requestedResources.size());
        assertEquals("/index.html", servlet.requestedResources.get(0));
        assertEquals("CONTENT OF /index.html", output.toString("UTF-8"));
        assertFalse(servlet.invoked);
    }

    @Test
    public void testStaticResourcesWithoutRedirectQueryCheck() throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("static-resources-list", ".*\\.html");
        TestServlet servlet = createServlet(params);
        servlet.service(createRequest("/", ".html"), createResponse());

        assertTrue(servlet.requestedResources.isEmpty());
        assertEquals(0, output.size());
        assertTrue(servlet.invoked);
    }

    @Test
    public void testRedirectListWithRedirectQueryCheck() throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("redirects-list", ".*\\.jsp");
        params.put("redirect-servlet-path", "/jsp");
        params.put("redirect-query-check", "true");
        TestServlet servlet = createServlet(params);
        servlet.service(createRequest("/page", "view.jsp"), createResponse());

        // the query string is still taken into account for the redirects-list
        assertEquals("/page", servlet.redirectedPath);
        assertFalse(servlet.invoked);
    }

    @Test
    public void testRedirectListWithoutRedirectQueryCheck() throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("redirects-list", ".*\\.jsp");
        params.put("redirect-servlet-path", "/jsp");
        TestServlet servlet = createServlet(params);
        servlet.service(createRequest("/page", "view.jsp"), createResponse());

        assertNull(servlet.redirectedPath);
        assertTrue(servlet.invoked);
    }

    private static Map<String, String> staticResourcesWithQueryCheck() {
        Map<String, String> params = new HashMap<String, String>();
        params.put("static-resources-list", ".*\\.html");
        params.put("redirect-query-check", "true");
        return params;
    }

    private static TestServlet createServlet(Map<String, String> initParams) throws ServletException {
        ServletConfig config = EasyMock.createNiceMock(ServletConfig.class);
        for (Map.Entry<String, String> entry : initParams.entrySet()) {
            config.getInitParameter(entry.getKey());
            EasyMock.expectLastCall().andReturn(entry.getValue()).anyTimes();
        }
        EasyMock.replay(config);

        TestServlet servlet = new TestServlet();
        servlet.init(config);
        return servlet;
    }

    private static HttpServletRequest createRequest(String pathInfo, String queryString) {
        HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        request.getMethod();
        EasyMock.expectLastCall().andReturn("GET").anyTimes();
        request.getPathInfo();
        EasyMock.expectLastCall().andReturn(pathInfo).anyTimes();
        request.getQueryString();
        EasyMock.expectLastCall().andReturn(queryString).anyTimes();
        EasyMock.replay(request);
        return request;
    }

    private HttpServletResponse createResponse() throws IOException {
        output = new ByteArrayOutputStream();
        final ByteArrayOutputStream out = output;
        ServletOutputStream os = new ServletOutputStream() {
            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }
        };
        HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
        response.getOutputStream();
        EasyMock.expectLastCall().andReturn(os).anyTimes();
        EasyMock.replay(response);
        return response;
    }

    private static class TestServlet extends AbstractHTTPServlet {
        private static final long serialVersionUID = 1L;

        private final List<String> requestedResources = new ArrayList<String>();
        private boolean invoked;
        private String redirectedPath;

        @Override
        protected Bus getBus() {
            return null;
        }

        @Override
        protected InputStream getResourceAsStream(String path) {
            // simulates a container which can resolve any path, including directories
            // and protected files such as WEB-INF/web.xml
            requestedResources.add(path);
            try {
                return new ByteArrayInputStream(("CONTENT OF " + path).getBytes("UTF-8"));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        protected void invoke(HttpServletRequest request, HttpServletResponse response)
            throws ServletException {
            invoked = true;
        }

        @Override
        protected void redirect(HttpServletRequest request, HttpServletResponse response, String pathInfo)
            throws ServletException {
            redirectedPath = pathInfo;
        }

        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }
}
