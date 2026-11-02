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

package org.apache.cxf.transport.https;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLStreamHandler;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.cxf.configuration.jsse.TLSClientParameters;

/**
 * Tests for hostname verification when the Java system property "java.protocol.handler.pkgs" is set to
 * "com.sun.net.ssl.internal.www.protocol". This means that com.sun.net.ssl.HostnameVerifier
 * (boolean verify(String urlHostname, String certHostname)) is used instead of the javax version, and
 * the HttpsURLConnectionFactory has to bridge the configured javax.net.ssl.HostnameVerifier to it.
 */
public class HostnameVerificationDeprecatedTest extends org.junit.Assert {

    private static final String KEYSTORE_DIR = "src/test/java/org/apache/cxf/transport/https/resources/";

    private static final String DEPRECATED_HANDLER =
        "com.sun.net.ssl.internal.www.protocol.https.Handler";

    //
    // The javax -> deprecated bridge installed by HttpsURLConnectionFactory.decorateWithTLS
    //

    // The server certificate is "CN=Bethal", but we are talking to "localhost". The default
    // hostname verifier contributed by CXF must object to this.
    @org.junit.Test
    public void testLocalhostNotMatching() throws Exception {
        X509Certificate bethalCert = loadCertificate("Bethal.jks", "password", "bethal");
        String[] cns = CertificateHostnameVerifier.Certificates.getCNs(bethalCert);
        assertNotNull(cns);
        assertEquals("Bethal", cns[0]);

        DeprecatedHostnameVerifier verifier = decorate(new TLSClientParameters());
        assertFalse(verifier.verify("localhost", cns[0]));
    }

    @org.junit.Test
    public void testDefaultVerifierMismatchingHostnames() throws Exception {
        DeprecatedHostnameVerifier verifier = decorate(new TLSClientParameters());

        assertFalse(verifier.verify("www.example.com", "www.attacker.com"));
        assertFalse(verifier.verify("www.example.com", "example.com"));
        assertFalse(verifier.verify("www.example.org", "*.example.com"));
        assertFalse(verifier.verify("www.example.com", ""));
        assertFalse(verifier.verify("www.example.com", null));
    }

    @org.junit.Test
    public void testDefaultVerifierMatchingHostnames() throws Exception {
        DeprecatedHostnameVerifier verifier = decorate(new TLSClientParameters());

        assertTrue(verifier.verify("localhost", "localhost"));
        assertTrue(verifier.verify("www.example.com", "www.example.com"));
        assertTrue(verifier.verify("WWW.Example.COM", "www.example.com"));
        assertTrue(verifier.verify("www.example.com", "*.example.com"));
    }

    @org.junit.Test
    public void testDisableCNCheck() throws Exception {
        TLSClientParameters tlsClientParameters = new TLSClientParameters();
        tlsClientParameters.setDisableCNCheck(true);
        DeprecatedHostnameVerifier verifier = decorate(tlsClientParameters);

        assertTrue(verifier.verify("localhost", "Bethal"));
        assertTrue(verifier.verify("www.example.com", "www.attacker.com"));
        assertFalse(verifier.verify("www.example.com", ""));
        assertFalse(verifier.verify("www.example.com", null));
    }

    // A custom javax.net.ssl.HostnameVerifier that has no way to verify a (host, certHostname) pair
    // must not result in every hostname being accepted.
    @org.junit.Test
    public void testCustomVerifierWithoutDeprecatedMethodFailsClosed() throws Exception {
        TLSClientParameters tlsClientParameters = new TLSClientParameters();
        tlsClientParameters.setHostnameVerifier(new RejectingHostnameVerifier());
        DeprecatedHostnameVerifier verifier = decorate(tlsClientParameters);

        assertFalse(verifier.verify("www.example.com", "www.attacker.com"));
        assertFalse(verifier.verify("www.example.com", "www.example.com"));
    }

    @org.junit.Test
    public void testCustomVerifierWithDeprecatedMethodIsUsed() throws Exception {
        TLSClientParameters tlsClientParameters = new TLSClientParameters();
        tlsClientParameters.setHostnameVerifier(new ExactMatchHostnameVerifier());
        DeprecatedHostnameVerifier verifier = decorate(tlsClientParameters);

        assertTrue(verifier.verify("www.example.com", "www.example.com"));
        assertFalse(verifier.verify("www.example.com", "www.attacker.com"));
    }

    //
    // The verify(String, String) contract on the CXF verifiers themselves
    //

    @org.junit.Test
    public void testAbstractVerifierHostnameCheck() throws Exception {
        CertificateHostnameVerifier.AbstractVerifier defaultVerifier =
            (CertificateHostnameVerifier.AbstractVerifier)CertificateHostnameVerifier.DEFAULT;
        assertTrue(defaultVerifier.verify("www.example.com", "www.example.com"));
        assertTrue(defaultVerifier.verify("a.b.example.com", "*.example.com"));
        assertFalse(defaultVerifier.verify("localhost", "Bethal"));
        assertFalse(defaultVerifier.verify("www.example.org", "*.example.com"));
        assertFalse(defaultVerifier.verify("www.example.com", ""));
        assertFalse(defaultVerifier.verify("www.example.com", (String)null));

        CertificateHostnameVerifier.AbstractVerifier strictVerifier =
            (CertificateHostnameVerifier.AbstractVerifier)CertificateHostnameVerifier.STRICT;
        assertTrue(strictVerifier.verify("a.example.com", "*.example.com"));
        assertFalse(strictVerifier.verify("a.b.example.com", "*.example.com"));

        CertificateHostnameVerifier.AbstractVerifier localhostVerifier =
            (CertificateHostnameVerifier.AbstractVerifier)CertificateHostnameVerifier.DEFAULT_AND_LOCALHOST;
        assertTrue(localhostVerifier.verify("localhost", "Bethal"));
        assertFalse(localhostVerifier.verify("www.example.com", "Bethal"));

        CertificateHostnameVerifier.AbstractVerifier allowAllVerifier =
            (CertificateHostnameVerifier.AbstractVerifier)CertificateHostnameVerifier.ALLOW_ALL;
        assertTrue(allowAllVerifier.verify("localhost", "Bethal"));
        assertFalse(allowAllVerifier.verify("localhost", ""));
        assertFalse(allowAllVerifier.verify("localhost", (String)null));
    }

    //
    // End-to-end against the JDK's deprecated com.sun.net.ssl HTTPS protocol handler
    //

    // Here we expect an exception, as the default hostname verifier contributed by CXF will object to the
    // fact that the server certificate does not have "CN=localhost".
    @org.junit.Test
    public void testDeprecatedHandlerLocalhostNotMatching() throws Exception {
        SSLServerSocket serverSocket = startServer();
        try {
            HttpURLConnection connection =
                connectDeprecated(new TLSClientParameters(), serverSocket.getLocalPort());
            assertFalse(connection instanceof javax.net.ssl.HttpsURLConnection);
            try {
                connection.getResponseCode();
                fail("Failure expected on the hostname verification");
            } catch (IOException ex) {
                // expected
                assertNotNull(ex.getMessage());
                assertTrue(ex.getMessage(), ex.getMessage().toLowerCase().contains("hostname"));
            } finally {
                connection.disconnect();
            }
        } finally {
            serverSocket.close();
        }
    }

    // With the CN check disabled the very same connection must succeed, which shows that the failure
    // above is caused by the hostname verification and nothing else.
    @org.junit.Test
    public void testDeprecatedHandlerDisableCNCheck() throws Exception {
        SSLServerSocket serverSocket = startServer();
        try {
            TLSClientParameters tlsClientParameters = new TLSClientParameters();
            tlsClientParameters.setDisableCNCheck(true);
            HttpURLConnection connection =
                connectDeprecated(tlsClientParameters, serverSocket.getLocalPort());
            assertFalse(connection instanceof javax.net.ssl.HttpsURLConnection);
            try {
                assertEquals(200, connection.getResponseCode());
            } finally {
                connection.disconnect();
            }
        } finally {
            serverSocket.close();
        }
    }

    //
    // Private utilities
    //

    private static DeprecatedHostnameVerifier decorate(TLSClientParameters tlsClientParameters)
        throws Exception {
        DeprecatedHttpsURLConnection connection =
            new DeprecatedHttpsURLConnection(new URL("http://localhost/"));
        new HttpsURLConnectionFactory().decorateWithTLS(tlsClientParameters, connection);
        assertNotNull("SSLSocketFactory was not set", connection.getSSLSocketFactory());
        DeprecatedHostnameVerifier verifier = connection.getHostnameVerifier();
        assertNotNull("HostnameVerifier was not set", verifier);
        return verifier;
    }

    private static HttpURLConnection connectDeprecated(TLSClientParameters tlsClientParameters,
                                                       int port) throws Exception {
        tlsClientParameters.setTrustManagers(new TrustManager[] {new TrustAllTrustManager()});
        URLStreamHandler handler =
            (URLStreamHandler)Class.forName(DEPRECATED_HANDLER).newInstance();
        URL url = new URL(null, "https://localhost:" + port + "/", handler);
        HttpURLConnection connection =
            new HttpsURLConnectionFactory().createConnection(tlsClientParameters, null, url);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);

        // Make sure the bridge was actually installed on the deprecated connection
        Method getHostnameVerifier = connection.getClass().getMethod("getHostnameVerifier");
        assertTrue(java.lang.reflect.Proxy.isProxyClass(
            getHostnameVerifier.invoke(connection).getClass()));
        return connection;
    }

    private static SSLServerSocket startServer() throws Exception {
        KeyStore keyStore = loadKeyStore("Bethal.jks", "password");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "password".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);

        final SSLServerSocket serverSocket = (SSLServerSocket)context.getServerSocketFactory()
            .createServerSocket(0, 50, InetAddress.getByName("localhost"));
        serverSocket.setNeedClientAuth(false);
        Thread thread = new Thread() {
            public void run() {
                try {
                    while (true) {
                        Socket socket = serverSocket.accept();
                        try {
                            socket.setSoTimeout(30000);
                            serve(socket);
                        } catch (IOException ex) {
                            // the client may abort the handshake
                        } finally {
                            socket.close();
                        }
                    }
                } catch (IOException ex) {
                    // server socket closed
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
        return serverSocket;
    }

    private static void serve(Socket socket) throws IOException {
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
        String line = reader.readLine();
        while (line != null && line.length() > 0) {
            line = reader.readLine();
        }
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\n"
            + "Content-Type: text/plain\r\n"
            + "Content-Length: 2\r\n"
            + "Connection: close\r\n"
            + "\r\n"
            + "OK").getBytes("ISO-8859-1"));
        out.flush();
    }

    private static KeyStore loadKeyStore(String keystoreFilename, String keystorePassword)
        throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        InputStream in = new FileInputStream(KEYSTORE_DIR + keystoreFilename);
        try {
            store.load(in, keystorePassword.toCharArray());
        } finally {
            in.close();
        }
        return store;
    }

    private static X509Certificate loadCertificate(String keystoreFilename, String keystorePassword,
                                                   String alias) throws Exception {
        X509Certificate cert = (X509Certificate)loadKeyStore(keystoreFilename, keystorePassword)
            .getCertificate(alias);
        assertNotNull("keystore " + keystoreFilename + " has no certificate " + alias, cert);
        return cert;
    }

    /**
     * Stand-in for com.sun.net.ssl.HostnameVerifier
     */
    public interface DeprecatedHostnameVerifier {
        boolean verify(String urlHostname, String certHostname);
    }

    /**
     * Stand-in for a com.sun.net.ssl.HttpsURLConnection, which is not a javax.net.ssl.HttpsURLConnection
     */
    public static class DeprecatedHttpsURLConnection extends HttpURLConnection {
        private DeprecatedHostnameVerifier hostnameVerifier;
        private SSLSocketFactory sslSocketFactory;

        public DeprecatedHttpsURLConnection(URL url) {
            super(url);
        }

        public DeprecatedHostnameVerifier getHostnameVerifier() {
            return hostnameVerifier;
        }

        public void setHostnameVerifier(DeprecatedHostnameVerifier verifier) {
            this.hostnameVerifier = verifier;
        }

        public SSLSocketFactory getSSLSocketFactory() {
            return sslSocketFactory;
        }

        public void setSSLSocketFactory(SSLSocketFactory factory) {
            this.sslSocketFactory = factory;
        }

        public void connect() throws IOException {
            // not used
        }

        public void disconnect() {
            // not used
        }

        public boolean usingProxy() {
            return false;
        }
    }

    public static class RejectingHostnameVerifier implements HostnameVerifier {
        public boolean verify(String host, SSLSession session) {
            return false;
        }
    }

    public static class ExactMatchHostnameVerifier implements HostnameVerifier {
        public boolean verify(String host, SSLSession session) {
            return false;
        }

        public boolean verify(String host, String certHostname) {
            return host != null && host.equals(certHostname);
        }
    }

    public static class TrustAllTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // trust everything - this test is only about hostname verification
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // trust everything - this test is only about hostname verification
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
