package it;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.DynaActionForm;
import org.apache.struts.upload.FormFile;
import org.apache.struts.upload.MultipartRequestWrapper;

/**
 * Reports what Struts parsed from a multipart request. The request passed in
 * is Struts' MultipartRequestWrapper, so the Servlet 3.0, 3.1 and 4.0 calls
 * below check that the wrapper delegates them to the container's request.
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
        out.print(";wrapperJar=" + new File(request.getClass().getProtectionDomain()
                .getCodeSource().getLocation().getPath()).getName());
        out.print(";contextPath=" + request.getServletContext().getContextPath());
        out.print(";dispatcherType=" + request.getDispatcherType());
        out.print(";contentLength=" + (request.getContentLengthLong() == request.getContentLength()));
        out.print(";mapping=" + request.getHttpServletMapping().getMatchValue());
        return null;
    }
}
