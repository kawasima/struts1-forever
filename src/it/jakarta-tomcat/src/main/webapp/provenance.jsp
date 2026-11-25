<%@ page contentType="text/plain; charset=UTF-8" %><%!
    private static String origin(Class type) {
        return type.getName() + "=" + type.getProtectionDomain().getCodeSource().getLocation();
    }
%><%= origin(org.apache.struts.action.ActionServlet.class) %>
<%= origin(org.apache.commons.fileupload.servlet.ServletFileUpload.class) %>
<%= origin(org.apache.commons.io.IOUtils.class) %>
