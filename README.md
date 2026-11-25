# STRUTS

![build status](https://circleci.com/gh/kawasima/struts1-forever.png?style=shield&circle-token=8f99c0e6c923ca570acda8c3640446fdacad2a47)

This struts1's fork is for maintenance to fix the vulnerabilities.

## Requirements

Original Struts requires Java 1.4 or higher. But struts1-forever requires Java 1.5 or higher.
Because Commons-Beanutils 1.9.2 is used for preventing a dangerous population.

## Jakarta EE (Tomcat 10+)

Each release also ships `struts-<version>-jakarta.jar` for containers that use the `jakarta.servlet` namespace. It is built from the same sources as the javax JAR, so it contains the same fixes. Your own code still has to be migrated from `javax.servlet` to `jakarta.servlet`.

| | |
| --- | --- |
| Target API | Jakarta Servlet 5.0 or later, Jakarta Server Pages 3.0 or later |
| API compatibility checked against | Servlet 5.0 / JSP 3.0, Servlet 6.0 / JSP 3.1, Servlet 6.1 / JSP 4.0 |
| Runtime tested on | Tomcat 10.1 (Servlet 6.0 / JSP 3.1) |
| Class file version | Java 8 |

The API compatibility check verifies that every class loads and that every Servlet/JSP class, method and field it uses exists in that API version. Only Tomcat 10.1 runs the full request cycle in the build.

`org.apache.struts.upload.MultipartRequestWrapper` extends `jakarta.servlet.http.HttpServletRequestWrapper` in the jakarta JAR and has no `protected request` field. Subclasses use `getRequest()` and `setRequest()`.

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

