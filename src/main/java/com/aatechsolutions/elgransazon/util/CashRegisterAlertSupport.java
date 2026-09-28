package com.aatechsolutions.elgransazon.util;

import jakarta.servlet.http.HttpSession;

/**
 * Decides whether the "Caja cerrada" warning of the orders list must be shown.
 *
 * <p>The warning is informative — it never blocks a sale, it only warns that cash collections
 * will not land in the drawer until it is opened — so repeating it on every page load and on
 * every refresh is noise. It is shown <b>once per login session</b> instead: the first time the
 * orders list is opened while the drawer is closed.</p>
 *
 * <p>The mark lives in the HTTP session, and the session dies on logout, so the next login gets
 * the reminder again. Operators using a shared terminal under their own user each get their own
 * session, therefore each of them sees the warning once.</p>
 */
public final class CashRegisterAlertSupport {

    /** Session attribute remembering that the warning was already shown to this login. */
    public static final String SESSION_ATTRIBUTE = "cashRegisterClosedAlertShown";

    private CashRegisterAlertSupport() {
    }

    /**
     * True the first time the orders list is opened while the drawer is closed. Marks the
     * session so the following loads and refreshes of the same session stay quiet.
     *
     * @param session           current HTTP session (null when there is none yet)
     * @param cashRegisterOpen  whether the employee already has an open drawer
     */
    public static boolean showOnce(HttpSession session, boolean cashRegisterOpen) {
        if (cashRegisterOpen) {
            return false;
        }
        if (session == null) {
            // Without a session there is nowhere to remember it: better to warn than to hide it.
            return true;
        }
        if (Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE))) {
            return false;
        }
        session.setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
        return true;
    }
}
