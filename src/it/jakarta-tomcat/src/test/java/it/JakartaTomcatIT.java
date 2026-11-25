package it;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;

/**
 * Deploys a webapp that uses the jakarta JAR from WEB-INF/lib to Tomcat 10.1
 * and checks the request paths that depend on the Servlet/JSP API.
 */
public class JakartaTomcatIT extends TestCase {

    private static Tomcat tomcat;
    private static Context context;
    private static String baseUrl;

    public static Test suite() {
        return new TestSetup(new TestSuite(JakartaTomcatIT.class)) {
            protected void setUp() throws Exception {
                tomcat = new Tomcat();
                tomcat.setBaseDir(System.getProperty("tomcat.base"));
                tomcat.setPort(0);
                tomcat.getConnector();
                File webapp = new File(System.getProperty("webapp.dir"));
                context = tomcat.addWebapp("/app", webapp.getAbsolutePath());
                tomcat.start();
                baseUrl = "http://localhost:" + tomcat.getConnector().getLocalPort() + "/app";
            }

            protected void tearDown() throws Exception {
                tomcat.stop();
                tomcat.destroy();
            }
        };
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private HttpClient client;

    protected void setUp() {
        client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .cookieHandler(new CookieManager())
                .build();
    }

    public void testWebapp_StartsWithActionServlet() {
        assertEquals(LifecycleState.STARTED, context.getState());
    }

    public void testWebInfLib_HasJakartaJarAndCommonsIoButNoCommonsFileUpload() {
        File lib = new File(System.getProperty("webapp.dir"), "WEB-INF/lib");
        List<String> jars = Arrays.asList(lib.list());
        assertTrue(jars.toString(), jars.contains(System.getProperty("struts.jar")));
        assertTrue(jars.toString(), jars.stream().anyMatch(j -> j.startsWith("commons-io-")));
        assertTrue(jars.toString(), jars.stream().noneMatch(j -> j.startsWith("commons-fileupload")));
    }

    public void testClasses_AreLoadedFromWebInfLib() throws Exception {
        String body = get("/provenance.jsp").body();
        String strutsJar = "/WEB-INF/lib/" + System.getProperty("struts.jar");
        assertContains(body, "org.apache.struts.action.ActionServlet=");
        for (String line : body.trim().split("\\R")) {
            if (line.startsWith("org.apache.commons.io.")) {
                assertTrue(line, line.matches(".*/WEB-INF/lib/commons-io-[^/]+\\.jar"));
            } else {
                assertTrue(line, line.endsWith(strutsJar));
            }
        }
    }

    public void testEcho_RendersStrutsTagsFromJar() throws Exception {
        HttpResponse<String> res = get("/echo.do?name=%3Cb%3Ex");
        assertEquals(200, res.statusCode());
        String body = res.body();
        assertContains(body, "<p id=\"greeting\">Hello from Struts</p>");
        assertContains(body, "<p id=\"name\">&lt;b&gt;x</p>");
        assertContains(body, "<p id=\"present\">form-present</p>");
        assertContains(body, "action=\"/app/echo.do");
        assertContains(body, "value=\"&lt;b&gt;x\"");
    }

    public void testEcho_IncludesThroughIncludeActionAndTiles() throws Exception {
        String body = get("/echo.do?name=a").body();
        assertContains(body, "<p id=\"included\">included-ok</p>");
        assertContains(body, "<p id=\"tile\">tile-ok</p>");
    }

    public void testEcho_KeepsSessionAcrossRequests() throws Exception {
        get("/echo.do?name=a");
        String body = get("/echo.do?name=a").body();
        assertContains(body, "<p id=\"visits\">2</p>");
    }

    public void testUpload_ParsesMultipartThroughWrapper() throws Exception {
        String boundary = "----struts-it";
        ByteArrayOutputStream form = new ByteArrayOutputStream();
        write(form, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\n"
                + "hello\r\n");
        write(form, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + "hello\r\n");
        write(form, "--" + boundary + "--\r\n");

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/upload.do"))
                .timeout(TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(form.toByteArray()))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, res.statusCode());
        assertEquals("title=hello;file=a.txt:5:hello;param=hello;wrapper=true"
                + ";contextPath=/app;dispatcherType=REQUEST", res.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertContains(String body, String expected) {
        assertTrue("Expected <" + expected + "> in:\n" + body, body.contains(expected));
    }
}
