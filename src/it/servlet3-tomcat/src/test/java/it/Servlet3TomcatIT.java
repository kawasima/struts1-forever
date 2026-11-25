package it;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;

/**
 * Deploys a webapp that uses the servlet3 JAR from WEB-INF/lib to Tomcat 9.0
 * and checks that multipart requests reach the Action with the Servlet 4.0
 * API intact.
 */
public class Servlet3TomcatIT extends TestCase {

    private static Tomcat tomcat;
    private static Context context;
    private static String baseUrl;

    public static Test suite() {
        return new TestSetup(new TestSuite(Servlet3TomcatIT.class)) {
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

    public void testWebapp_StartsWithActionServlet() {
        assertEquals(LifecycleState.STARTED, context.getState());
    }

    public void testWebInfLib_HasOnlyServlet3StrutsJarAndCommonsFileUpload() {
        File lib = new File(System.getProperty("webapp.dir"), "WEB-INF/lib");
        List<String> jars = Arrays.asList(lib.list());
        assertEquals(jars.toString(), List.of(System.getProperty("struts.jar")),
                jars.stream().filter(j -> j.startsWith("struts")).collect(Collectors.toList()));
        assertTrue(jars.toString(), jars.stream().anyMatch(j -> j.startsWith("commons-fileupload-")));
    }

    public void testUpload_DelegatesServletApiThroughWrapper() throws Exception {
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
        HttpResponse<String> res = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
                .send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(res.body(), 200, res.statusCode());
        assertEquals("title=hello;file=a.txt:5:hello;param=hello;wrapper=true"
                + ";wrapperJar=" + System.getProperty("struts.jar")
                + ";contextPath=/app;dispatcherType=REQUEST"
                + ";contentLength=" + form.size()
                + ";mapping=upload", res.body());
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
