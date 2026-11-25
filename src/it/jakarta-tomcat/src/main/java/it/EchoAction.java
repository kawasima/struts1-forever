package it;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/** Counts visits in the session and forwards to echo.jsp. */
public class EchoAction extends Action {
    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        Integer visits = (Integer) session.getAttribute("visits");
        session.setAttribute("visits", visits == null ? 1 : visits + 1);
        return mapping.findForward("success");
    }
}
