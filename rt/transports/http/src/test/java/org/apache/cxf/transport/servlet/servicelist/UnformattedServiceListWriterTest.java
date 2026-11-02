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

import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.easymock.EasyMock;

import org.junit.Assert;
import org.junit.Test;

public class UnformattedServiceListWriterTest extends Assert {
    private static final String HOST = "http://localhost:8080";
    private static final String SCRIPT = "<script>alert(1)</script>";
    private static final String ESCAPED_SCRIPT = "&lt;script&gt;alert(1)&lt;/script&gt;";
    private static final AbstractDestination[] NONE = new AbstractDestination[0];

    @Test
    public void testAddressesAreNotChangedWhenNothingToEscape() throws Exception {
        String s = write(new UnformattedServiceListWriter(true), HOST + "/the",
                         new AbstractDestination[] {createDestination(HOST + "/the/soap")},
                         new AbstractDestination[] {createDestination(HOST + "/the/bookstore")});
        assertTrue(s.contains(HOST + "/the/soap?wsdl\n"));
        assertTrue(s.contains(HOST + "/the/bookstore?_wadl\n"));
    }

    @Test
    public void testSoapEndpointAddressIsEscaped() throws Exception {
        String s = write(new UnformattedServiceListWriter(true), HOST + "/\">" + SCRIPT + "/../the",
                         new AbstractDestination[] {createDestination("/soap")}, NONE);
        assertFalse(s.contains("<script>"));
        assertTrue(s.contains(HOST + "/&quot;&gt;" + ESCAPED_SCRIPT + "/../the/soap?wsdl\n"));
    }

    @Test
    public void testSoapPublishedEndpointUrlIsEscaped() throws Exception {
        AbstractDestination dest = createDestination("/soap");
        dest.getEndpointInfo().setProperty("publishedEndpointUrl", HOST + "/" + SCRIPT);
        String s = write(new UnformattedServiceListWriter(false), HOST + "/the",
                         new AbstractDestination[] {dest}, NONE);
        assertFalse(s.contains("<script>"));
        assertTrue(s.contains(HOST + "/" + ESCAPED_SCRIPT + "\n"));
    }

    @Test
    public void testRestEndpointAddressIsEscaped() throws Exception {
        String s = write(new UnformattedServiceListWriter(false), HOST + "/" + SCRIPT + "/../the",
                         NONE, new AbstractDestination[] {createDestination("/bookstore")});
        assertFalse(s.contains("<script>"));
        assertTrue(s.contains(HOST + "/" + ESCAPED_SCRIPT + "/../the/bookstore?_wadl\n"));
    }

    private static String write(UnformattedServiceListWriter listWriter, String basePath,
                                AbstractDestination[] soapDestinations,
                                AbstractDestination[] restDestinations) throws Exception {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out, true);
        listWriter.writeServiceList(writer, basePath, soapDestinations, restDestinations);
        writer.flush();
        return out.toString();
    }

    private static AbstractDestination createDestination(String address) {
        EndpointInfo ei = new EndpointInfo();
        ei.setAddress(address);
        AbstractHTTPDestination dest = EasyMock.createMock(AbstractHTTPDestination.class);
        EasyMock.expect(dest.getEndpointInfo()).andReturn(ei).anyTimes();
        EasyMock.replay(dest);
        return dest;
    }
}
