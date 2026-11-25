package it;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.DynaActionForm;
import org.apache.struts.upload.FormFile;
import org.apache.struts.upload.MultipartRequestWrapper;

/**
 * Reports what Struts parsed from a multipart request. The request passed in
 * is Struts' MultipartRequestWrapper, so the Servlet 3.0+ calls below check
 * that the wrapper implements the current API.
 */
public class UploadAction extends Action {
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        DynaActionForm f = (DynaActionForm) form;
        FormFile file = (FormFile) f.get("file");
        response.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.print("title=" + f.get("title"));
        out.print(";file=" + file.getFileName() + ":" + file.getFileSize() + ":"
                + new String(file.getFileData(), StandardCharsets.UTF_8));
        out.print(";param=" + request.getParameter("title"));
        out.print(";wrapper=" + (request instanceof MultipartRequestWrapper));
        out.print(";contextPath=" + request.getServletContext().getContextPath());
        out.print(";dispatcherType=" + request.getDispatcherType());
        return null;
    }
}
