package com.aatechsolutions.elgransazon.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "Caja cerrada" warning must be shown to each operator once per login session: the first
 * time they open the orders list with no opened drawer, and never again on reloads or refreshes
 * of that same session. A new login (new session) gets the reminder again.
 */
class CashRegisterAlertSupportTest {

    @Test
    void warningIsShownOnlyTheFirstTimeOfTheSession() {
        MockHttpSession session = new MockHttpSession();

        assertThat(CashRegisterAlertSupport.showOnce(session, false))
                .as("first load of the session warns")
                .isTrue();
        assertThat(CashRegisterAlertSupport.showOnce(session, false))
                .as("reloading the page must stay quiet")
                .isFalse();
        assertThat(CashRegisterAlertSupport.showOnce(session, false))
                .as("navigating around the list must stay quiet")
                .isFalse();
    }

    @Test
    void aNewLoginSeesTheWarningAgain() {
        MockHttpSession firstLogin = new MockHttpSession();
        assertThat(CashRegisterAlertSupport.showOnce(firstLogin, false)).isTrue();

        // Logout invalidates the session; the next login starts a brand new one.
        MockHttpSession nextLogin = new MockHttpSession();

        assertThat(CashRegisterAlertSupport.showOnce(nextLogin, false))
                .as("a new login must be warned again")
                .isTrue();
    }

    @Test
    void noWarningWhenTheDrawerIsAlreadyOpen() {
        MockHttpSession session = new MockHttpSession();

        assertThat(CashRegisterAlertSupport.showOnce(session, true)).isFalse();
        assertThat(session.getAttribute(CashRegisterAlertSupport.SESSION_ATTRIBUTE))
                .as("nothing to remember when there is nothing to warn about")
                .isNull();
    }

    @Test
    void warningStillShowsWhenThereIsNoSessionToRememberIt() {
        assertThat(CashRegisterAlertSupport.showOnce(null, false)).isTrue();
    }
}
