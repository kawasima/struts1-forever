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

## External resources in XML files

Struts parses `web.xml`, `struts-config.xml`, Tiles definition files, the validation files of `ValidatorPlugIn`, and the files read by `DigestingPlugIn` (the data file, the digester rules file and every rules file it includes). A `DOCTYPE` declaration is allowed in all of them, and the following holds whether or not the parser validates:

- An external DTD is read only from a local copy registered with `Digester.register()`, or from what an `EntityResolver` set on the `Digester` returns. Struts never falls back to opening a URI written in the document. A registration or a resolver is the application's explicit permission, and any I/O it does is the application's responsibility.
- External entities are not expanded. A document that declares an external general entity, for example to split `struts-config.xml` into files with `&name;` references, fails to parse instead of being loaded with those parts left out.
- Entity expansion is limited, and XInclude is not processed.

If the XML parser cannot be made to work this way, Struts does not parse with it and fails at start-up. The JDK parser and Xerces 2.x, including versions without secure processing such as 2.6.2, can.

The local copies registered by default are the Struts Configuration 1.0, 1.1 and 1.2 DTDs, the Servlet 2.2 and 2.3 Web Application DTDs, the Tiles Configuration 1.1 DTD, the Commons Validator DTDs and the Commons Digester rules DTD. A document whose `DOCTYPE` refers to any other DTD fails to parse. Up to 1.2.9-forever-2 the parser downloaded such a DTD from its system identifier. If your files use a DTD of your own, register a local copy, for example in `DigestingPlugIn.newDigesterInstance()`, or remove the `DOCTYPE`.

A `Digester` that an application creates itself, such as the one returned by an overridden `DigestingPlugIn.newDigesterInstance()`, gets the same restrictions when Struts parses with it. Create it as `org.apache.struts.util.SecureDigester` to have them whenever it parses.

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

