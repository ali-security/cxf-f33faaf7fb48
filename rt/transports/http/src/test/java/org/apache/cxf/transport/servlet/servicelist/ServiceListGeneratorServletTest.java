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
package org.apache.cxf.transport.servlet.servicelist;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.servlet.ServletController;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

import org.junit.Assert;
import org.junit.Test;

public class ServiceListGeneratorServletTest extends Assert {
    private static final String HOST = "http://localhost:8080";
    private static final String INJECTED_MATRIX_PARAM = ";a=%22%3E%3Cimg%20src=x%20onerror=alert(1)%3E";

    @Test
    public void testGetServicesPage() throws Exception {
        String s = getServiceListPage("/the/services", "", "/the", "/services", null);
        assertTrue(s.contains("href=\"/the/services/?stylesheet=1\""));
        assertTrue(s.contains("<title>CXF - Service list</title>"));
        assertTrue(s.contains("<a href=\"" + HOST + "/the/bookstore?_wadl\""));
    }

    @Test
    public void testGetServicesPageWithServletPatternMatchOnlyTrailingSlash() throws Exception {
        String s = getServiceListPage("/the/", "", "/the", "/", null);
        assertTrue(s.contains("href=\"/the/?stylesheet=1\""));
        assertTrue(s.contains("<a href=\"" + HOST + "/the/bookstore?_wadl\""));
        assertFalse(s.contains("/the//bookstore"));
    }

    @Test
    public void testGetServicesPageWithServletPatternMatchOnly() throws Exception {
        String s = getServiceListPage("/the/;a=b", "", "/the", "/", null);
        assertTrue(s.contains("href=\"/the/?stylesheet=1\""));
        assertTrue(s.contains("<title>CXF - Service list</title>"));
        assertFalse(s.contains(";a=b"));
        assertTrue(s.contains("<a href=\"" + HOST + "/the/bookstore?_wadl\""));
    }

    @Test
    public void testGetServicesPageWithServletPatternMatchOnly2() throws Exception {
        String s = getServiceListPage("/services;a=b;/list;a=b/;a=b", "", "/services/list", "/", null);
        assertTrue(s.contains("href=\"/services/list/?stylesheet=1\""));
        assertTrue(s.contains("<title>CXF - Service list</title>"));
        assertFalse(s.contains(";a=b"));
        assertTrue(s.contains("<a href=\"" + HOST + "/services/list/bookstore?_wadl\""));
    }

    @Test
    public void testGetServicesPageDoesNotReflectInjectedMatrixParam() throws Exception {
        String s = getServiceListPage("/the/" + INJECTED_MATRIX_PARAM, "", "/the", "/", null);
        assertFalse(s.contains(INJECTED_MATRIX_PARAM));
        assertFalse(s.contains("onerror"));
        assertTrue(s.contains("href=\"/the/?stylesheet=1\""));
        assertTrue(s.contains("<a href=\"" + HOST + "/the/bookstore?_wadl\""));
    }

    @Test
    public void testGetUnformattedServicesPageDoesNotReflectInjectedMatrixParam() throws Exception {
        String s = getServiceListPage("/the/" + INJECTED_MATRIX_PARAM, "", "/the", "/", "false");
        assertFalse(s.contains(INJECTED_MATRIX_PARAM));
        assertFalse(s.contains("onerror"));
        assertTrue(s.contains(HOST + "/the/bookstore?_wadl"));
    }

    @Test
    public void testStyleSheetPathDoesNotReflectRequestUri() throws Exception {
        HttpServletRequest req =
            createRequest("/the/;a=\"><script>alert(1)</script>", "", "/the", "/", null);
        EasyMock.expect(req.getAttribute(Message.BASE_PATH)).andReturn(HOST + "/the").anyTimes();
        StringWriter out = new StringWriter();
        HttpServletResponse res = createResponse(out);
        DestinationRegistry registry = createRegistry();
        EasyMock.replay(req, res, registry);

        new ServiceListGeneratorServlet(registry, null).service(req, res);

        String s = out.toString();
        assertTrue(s.contains("href=\"/the/?stylesheet=1\""));
        assertFalse(s.contains("<script>"));
        assertFalse(s.contains(";a="));
    }

    private String getServiceListPage(String requestUri, String contextPath, String servletPath,
                                      String pathInfo, String formatted) throws Exception {
        HttpServletRequest req = createRequest(requestUri, contextPath, servletPath, pathInfo, formatted);
        // ServletController sets the base address the service list is rendered with
        final Capture<Object> basePath = new Capture<Object>();
        req.setAttribute(EasyMock.eq(Message.BASE_PATH), EasyMock.capture(basePath));
        EasyMock.expectLastCall().anyTimes();
        EasyMock.expect(req.getAttribute(Message.BASE_PATH)).andAnswer(new IAnswer<Object>() {
            public Object answer() throws Throwable {
                return basePath.hasCaptured() ? basePath.getValue() : null;
            }
        }).anyTimes();
        StringWriter out = new StringWriter();
        HttpServletResponse res = createResponse(out);
        DestinationRegistry registry = createRegistry();
        EasyMock.replay(req, res, registry);

        ServletController controller =
            new ServletController(registry, null, new ServiceListGeneratorServlet(registry, null));
        controller.invoke(req, res);

        assertTrue(basePath.hasCaptured());
        assertFalse(basePath.getValue().toString().contains(";"));
        return out.toString();
    }

    private static HttpServletRequest createRequest(String requestUri, String contextPath,
                                                    String servletPath, String pathInfo,
                                                    String formatted) {
        HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(req.getRequestURL()).andReturn(new StringBuffer(HOST + requestUri)).anyTimes();
        EasyMock.expect(req.getRequestURI()).andReturn(requestUri).anyTimes();
        EasyMock.expect(req.getContextPath()).andReturn(contextPath).anyTimes();
        EasyMock.expect(req.getServletPath()).andReturn(servletPath).anyTimes();
        EasyMock.expect(req.getPathInfo()).andReturn(pathInfo).anyTimes();
        EasyMock.expect(req.getParameter("formatted")).andReturn(formatted).anyTimes();
        return req;
    }

    private static HttpServletResponse createResponse(StringWriter out) throws Exception {
        HttpServletResponse res = EasyMock.createNiceMock(HttpServletResponse.class);
        EasyMock.expect(res.getWriter()).andReturn(new PrintWriter(out, true)).anyTimes();
        return res;
    }

    private static DestinationRegistry createRegistry() {
        EndpointInfo ei = new EndpointInfo();
        ei.setAddress("/bookstore");
        AbstractHTTPDestination dest = EasyMock.createMock(AbstractHTTPDestination.class);
        EasyMock.expect(dest.getEndpointInfo()).andReturn(ei).anyTimes();
        EasyMock.replay(dest);

        DestinationRegistry registry = EasyMock.createNiceMock(DestinationRegistry.class);
        EasyMock.expect(registry.getSortedDestinations())
            .andReturn(new AbstractDestination[] {dest}).anyTimes();
        return registry;
    }
}
