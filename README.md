# STRUTS

![build status](https://circleci.com/gh/kawasima/struts1-forever.png?style=shield&circle-token=8f99c0e6c923ca570acda8c3640446fdacad2a47)

This struts1's fork is for maintenance to fix the vulnerabilities.

## Requirements

The classes of the default JAR are compiled for Java 1.4 (releases are built with JDK 8), and those of the servlet3 and jakarta JARs for Java 8. The dependencies that the POM declares need Java 8: Commons BeanUtils 1.11.0, Commons FileUpload 1.6.0 and Commons IO 2.19.0. Whatever versions you use, Struts needs Commons BeanUtils 1.9.2 or later, because it uses `SuppressPropertiesBeanIntrospector` to prevent the population of dangerous properties (CVE-2014-0114).

## Servlet 3.0 to 4.0 (Tomcat 7 to 9)

For the request of a multipart form, Struts passes its own `org.apache.struts.upload.MultipartRequestWrapper` to the Action. In the default JAR this class implements `HttpServletRequest` itself and is compiled against Servlet 2.2, so on a Servlet 3.0 or later container the methods added since then do not reach the container's request:

- Abstract methods, such as `getServletContext()` or `getDispatcherType()`, throw `AbstractMethodError`.
- The default methods of Servlet 4.0 (`getHttpServletMapping()`, `newPushBuilder()`, `getTrailerFields()`, `isTrailerFieldsReady()`) return the interface's default instead of the container's value.

The default JAR keeps this class as it is for applications that subclass it. Each release also ships `struts-<version>-servlet3.jar`, which contains the same classes except that `MultipartRequestWrapper` extends `javax.servlet.http.HttpServletRequestWrapper`, so the container's request answers every method of its Servlet API.

| | |
| --- | --- |
| Target API | Servlet 3.0 to 4.0 |
| API compatibility checked against | Servlet 3.0 / JSP 2.2, Servlet 3.1 / JSP 2.3, Servlet 4.0 / JSP 2.3 |
| Runtime tested on | Tomcat 9.0 (Servlet 4.0) |
| Class file version | Java 8 |

The API compatibility check verifies that every class loads and that every Servlet/JSP class, method and field it uses exists in that API version. For this JAR, Tomcat 9.0 runs a multipart request through the Action in the build.

`MultipartRequestWrapper` in this JAR differs from the one in the default JAR:

- It has no `protected request` field. Subclasses use `getRequest()` and `setRequest()`, which accepts only an `HttpServletRequest`.
- `getRealPath(path)` returns `getServletContext().getRealPath(path)`, and `isRequestedSessionIdFromUrl()` returns `isRequestedSessionIdFromURL()`, instead of calling the method of the same name on the wrapped request. Servlet 6.0 removed both methods, and the jakarta JAR uses the same class.
- Exceptions thrown by the wrapped request reach the caller. The default JAR logs an exception from `getRequestURL()`, `isRequestedSessionIdFromCookie()`, `getLocalAddr()`, `getLocalName()`, `getLocalPort()` or `getRemotePort()` and returns `null`, `false` or `0`.

The servlet3 JAR replaces the default JAR. Keep only one Struts JAR in `WEB-INF/lib`, and exclude `struts:struts` without classifier if another dependency brings it in. With two Struts JARs, the class loader decides which `MultipartRequestWrapper` is used; `ActionServlet` logs a warning at startup when it finds its own class in more than one location.

```xml
<dependency>
  <groupId>struts</groupId>
  <artifactId>struts</artifactId>
  <version>${struts.version}</version>
  <classifier>servlet3</classifier>
</dependency>
```

## Jakarta EE (Tomcat 10+)

Each release also ships `struts-<version>-jakarta.jar` for containers that use the `jakarta.servlet` namespace. It is built from the same sources as the javax JAR, so it contains the same fixes. Your own code still has to be migrated from `javax.servlet` to `jakarta.servlet`. Like the servlet3 JAR, it replaces the default JAR: keep only one Struts JAR in `WEB-INF/lib`.

| | |
| --- | --- |
| Target API | Jakarta Servlet 5.0 or later, Jakarta Server Pages 3.0 or later |
| API compatibility checked against | Servlet 5.0 / JSP 3.0, Servlet 6.0 / JSP 3.1, Servlet 6.1 / JSP 4.0 |
| Runtime tested on | Tomcat 10.1 (Servlet 6.0 / JSP 3.1) |
| Class file version | Java 8 |

For this JAR, Tomcat 10.1 runs the full request cycle in the build.

`MultipartRequestWrapper` is the class of the servlet3 JAR, converted to `jakarta.servlet`; it extends `jakarta.servlet.http.HttpServletRequestWrapper` and differs from the default JAR as described above.

The jakarta JAR bundles Commons FileUpload 1.6.0, converted to `jakarta.servlet`, under its original package names. When you switch to it:

- Remove `commons-fileupload-1.x.jar` from `WEB-INF/lib`. Two copies of the same classes on the classpath cause unpredictable class loading.
- Keep `commons-io` (2.19.0 or later). FileUpload needs it.
- With Maven, exclude `commons-fileupload` from the dependency:

```xml
<dependency>
  <groupId>struts</groupId>
  <artifactId>struts</artifactId>
  <version>${struts.version}</version>
  <classifier>jakarta</classifier>
  <exclusions>
    <exclusion>
      <groupId>commons-fileupload</groupId>
      <artifactId>commons-fileupload</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

## Fixed vulnerabilities

- CVE-2014-0114
- CVE-2016-1181
- CVE-2016-1182

## Introduction (Original)

This subproject contains the source code for the "Struts" application support
package, consisting of the following major components:

- Controller servlet with action mapping technology, implementing the Model-
  View-Controller (MVC) design pattern for web applications commonly called
  "Model 2", with a servlet as the "front component".

- Comprehensive custom tag library for building internationalized JSP pages
  that have HTML forms which interact with JavaBeans that are managed
  automatically by the controller servlet.

For help with installing and using Struts, see the Struts User Guide.

