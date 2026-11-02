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
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.easymock.EasyMock;

import org.junit.Assert;
import org.junit.Test;

public class FormattedServiceListWriterTest extends Assert {
    private static final String HOST = "http://localhost:8080";
    private static final String NS = "http://example.com/";
    private static final String SCRIPT = "<script>alert(1)</script>";
    private static final String ESCAPED_SCRIPT = "&lt;script&gt;alert(1)&lt;/script&gt;";
    private static final String STYLESHEET = "/the/?stylesheet=1";
    private static final AbstractDestination[] NONE = new AbstractDestination[0];

    @Test
    public void testAddressesAreNotChangedWhenNothingToEscape() throws Exception {
        String s = write(new FormattedServiceListWriter(STYLESHEET, null, false, null), HOST + "/the",
                         new AbstractDestination[] {createSoapDestination("/soap")},
                         new AbstractDestination[] {createRestDestination("/bookstore")});
        assertTrue(s.contains("href=\"" + STYLESHEET + "\""));
        assertTrue(s.contains("cellpadding=\"1\""));
        assertTrue(s.contains("<span class=\"value\">" + HOST + "/the/soap</span>"));
        assertTrue(s.contains("<a href=\"" + HOST + "/the/soap?wsdl\">"));
        assertTrue(s.contains("<a href=\"" + HOST + "/the/bookstore?_wadl\">"
                              + HOST + "/the/bookstore?_wadl</a>"));
    }

    @Test
    public void testStyleSheetPathIsEscaped() throws Exception {
        String styleSheetPath = "/the/\">" + SCRIPT + "/?stylesheet=1";
        String s = write(new FormattedServiceListWriter(styleSheetPath, null, false, null), HOST + "/the",
                         NONE, new AbstractDestination[] {createRestDestination("/bookstore")});
        assertFalse(s.contains(SCRIPT));
        assertFalse(s.contains("<script>"));
        assertTrue(s.contains("href=\"/the/&quot;&gt;" + ESCAPED_SCRIPT + "/?stylesheet=1\""));
        // the escaped stylesheet path must still be recognized as the default CXF one
        assertTrue(s.contains("cellpadding=\"1\""));
    }

    @Test
    public void testSoapEndpointAddressIsEscaped() throws Exception {
        String s = write(new FormattedServiceListWriter(STYLESHEET, null, false, null),
                         HOST + "/" + SCRIPT + "/../the",
                         new AbstractDestination[] {createSoapDestination("/soap")}, NONE);
        assertFalse(s.contains("<script>"));
        assertTrue(s.contains("<span class=\"value\">" + HOST + "/" + ESCAPED_SCRIPT
                              + "/../the/soap</span>"));
        assertTrue(s.contains("<a href=\"" + HOST + "/" + ESCAPED_SCRIPT + "/../the/soap?wsdl\">"));
    }

    @Test
    public void testSoapPublishedEndpointUrlIsEscaped() throws Exception {
        AbstractDestination dest = createSoapDestination("/soap");
        dest.getEndpointInfo().setProperty("publishedEndpointUrl", HOST + "/soap\">" + SCRIPT);
        String s = write(new FormattedServiceListWriter(STYLESHEET, null, false, null), HOST + "/the",
                         new AbstractDestination[] {dest}, NONE);
        assertFalse(s.contains("<script>"));
        assertFalse(s.contains("/soap\">"));
        assertTrue(s.contains("<a href=\"" + HOST + "/soap&quot;&gt;" + ESCAPED_SCRIPT + "?wsdl\">"));
    }

    @Test
    public void testRestEndpointAddressIsEscaped() throws Exception {
        String s = write(new FormattedServiceListWriter(STYLESHEET, null, false, null),
                         HOST + "/\">" + SCRIPT + "/../the",
                         NONE, new AbstractDestination[] {createRestDestination("/bookstore")});
        assertFalse(s.contains("<script>"));
        String escaped = HOST + "/&quot;&gt;" + ESCAPED_SCRIPT + "/../the/bookstore";
        assertTrue(s.contains("<span class=\"value\">" + escaped + "</span>"));
        assertTrue(s.contains("<a href=\"" + escaped + "?_wadl\">" + escaped + "?_wadl</a>"));
    }

    @Test
    public void testAtomLinkIsEscaped() throws Exception {
        Map<String, String> atomMap = new HashMap<String, String>();
        atomMap.put("/bookstore", "/logs/" + SCRIPT);
        String s = write(new FormattedServiceListWriter(STYLESHEET, null, false, atomMap),
                         HOST + "/" + SCRIPT + "/../the",
                         NONE, new AbstractDestination[] {createRestDestination("/bookstore")});
        assertTrue(s.contains("Atom Log Feed"));
        assertFalse(s.contains("<script>"));
    }

    private static String write(FormattedServiceListWriter listWriter, String basePath,
                                AbstractDestination[] soapDestinations,
                                AbstractDestination[] restDestinations) throws Exception {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out, true);
        listWriter.writeServiceList(writer, basePath, soapDestinations, restDestinations);
        writer.flush();
        return out.toString();
    }

    private static AbstractDestination createSoapDestination(String address) {
        ServiceInfo si = new ServiceInfo();
        si.setName(new QName(NS, "BookService"));
        si.setTargetNamespace(NS);
        si.createInterface(new QName(NS, "BookPortType"));
        EndpointInfo ei = new EndpointInfo(si, "http://schemas.xmlsoap.org/soap/http");
        ei.setName(new QName(NS, "BookPort"));
        ei.setAddress(address);
        return createDestination(ei);
    }

    private static AbstractDestination createRestDestination(String address) {
        EndpointInfo ei = new EndpointInfo();
        ei.setAddress(address);
        return createDestination(ei);
    }

    private static AbstractDestination createDestination(EndpointInfo ei) {
        AbstractHTTPDestination dest = EasyMock.createMock(AbstractHTTPDestination.class);
        EasyMock.expect(dest.getEndpointInfo()).andReturn(ei).anyTimes();
        EasyMock.replay(dest);
        return dest;
    }
}
