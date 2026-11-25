<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://struts.apache.org/tags-tiles" prefix="tiles" %>
<html>
<body>
<p id="greeting"><bean:message key="greeting"/></p>
<p id="name"><bean:write name="echoForm" property="name"/></p>
<p id="visits"><bean:write name="visits" scope="session"/></p>
<logic:present name="echoForm"><p id="present">form-present</p></logic:present>
<html:form action="/echo"><html:text property="name"/></html:form>
<jsp:include page="/included.do"/>
<tiles:insert page="/tile.jsp" flush="false"/>
</body>
</html>
