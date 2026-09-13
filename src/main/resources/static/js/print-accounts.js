/**
 * Local ("normal") printing of the automatic payment tickets.
 *
 * These are the three ticket kinds that go to a customer:
 *   - whole-order charge          (cobro normal del pedido)
 *   - departing guest             (cobro de la persona que se va)
 *   - split bill / divided account (división de cuentas)
 *
 * They are printed from the PC that charged the order whenever that PC can, and only fall
 * back to the printer agents when it cannot. That is the "primero normal, si no por el
 * agente" order: waiter, cashier, manager and admin do not need the agent page open to
 * hand a ticket over.
 *
 *   1. LOCAL  — this PC prints the ESC/POS ticket with QZ Tray right after the payment
 *               screen loads. It needs a ticket printer chosen for this PC in
 *               /printer-agent (localStorage per company) plus QZ Tray running.
 *   2. AGENT  — when this PC cannot print (no ticket printer configured here, no QZ Tray,
 *               printer not installed on this PC, or the job failed) the ticket is left to
 *               the agents: the server broadcasts the event to every printer agent of the
 *               company a few seconds later and the PC that has the printer prints it.
 *
 * Exactly once: every ticket is claimed before printing and released again when the job
 * fails, so a ticket comes out once even when several PCs share the same printer. The
 * whole-order ticket is never printed when the payment created accounts (that one is just
 * the table total and is not handed to any customer).
 *
 * Kitchen/bar/grill comandas are NOT handled here: those always print through the printer
 * agent.
 *
 * Requires qz-tray.js (and qz-signing.js when signing is enabled) loaded on the page.
 */
(function () {
  "use strict";

  /** Ticket printer chosen for this PC in /printer-agent (localStorage per company). */
  function ticketPrinterFor(companyId) {
    try {
      return localStorage.getItem("ticketPrinter_" + companyId);
    } catch (e) {
      return null;
    }
  }

  /** Resolves true when QZ Tray is reachable and connected. Never throws. */
  function ensureQzConnected() {
    return new Promise(function (resolve) {
      if (typeof qz === "undefined" || !qz.websocket) {
        resolve(false);
        return;
      }
      if (qz.websocket.isActive()) {
        resolve(true);
        return;
      }
      // Probe first: without QZ Tray installed the connect() call leaves a broken socket.
      var probe = new WebSocket("ws://localhost:8182");
      var timer = setTimeout(function () {
        try { probe.close(); } catch (e) {}
        resolve(false);
      }, 700);
      probe.onopen = function () {
        clearTimeout(timer);
        try { probe.close(); } catch (e) {}
        qz.websocket
          .connect()
          .then(function () { resolve(true); })
          .catch(function () { resolve(false); });
      };
      probe.onerror = function () {
        clearTimeout(timer);
        resolve(false);
      };
    });
  }

  /** True when the printer is physically installed on this PC. */
  function printerAvailable(printerName) {
    return qz.printers.find().then(function (list) {
      var names = Array.isArray(list) ? list : (list ? [list] : []);
      var lower = names.map(function (p) { return String(p).toLowerCase(); });
      return lower.indexOf(printerName.toLowerCase()) >= 0;
    });
  }

  /** Exactly-once claim: false when another PC (an agent, or another tab) already took it. */
  function claimTicket(claimUrl) {
    return fetch(claimUrl, { method: "POST" })
      .then(function (resp) {
        if (!resp.ok) return true; // could not ask: better to try printing than to drop it
        return resp.json();
      })
      .then(function (data) { return data.claim === true; })
      .catch(function () { return true; });
  }

  /** Gives the claim back when this PC could not print, so an agent can take the ticket. */
  function releaseTicket(releaseUrl) {
    return fetch(releaseUrl, { method: "POST" }).catch(function () {});
  }

  function printRaw(url, printerName) {
    return fetch(url)
      .then(function (resp) {
        if (!resp.ok) throw new Error("HTTP " + resp.status);
        return resp.arrayBuffer();
      })
      .then(function (buf) {
        var bytes = new Uint8Array(buf);
        var binary = "";
        for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
        return qz.print(qz.configs.create(printerName), [
          { type: "raw", format: "base64", data: btoa(binary) },
        ]);
      });
  }

  /**
   * Claims, prints and — when nothing came out — releases ONE ticket.
   *
   * @returns true when this PC printed it, false when the agents should take it.
   */
  async function printOneTicket(rawUrl, claimUrl, releaseUrl, printerName, label) {
    var claimed = await claimTicket(claimUrl);
    if (!claimed) return false; // an agent (or another tab) already printed it

    try {
      await printRaw(rawUrl, printerName);
      console.log("Ticket impreso localmente (" + label + ") en " + printerName);
      return true;
    } catch (e) {
      // Nothing came out of this PC: hand the ticket over to the agents.
      await releaseTicket(releaseUrl);
      console.warn("No se pudo imprimir " + label + " en esta PC; lo tomará el agente:", e);
      return false;
    }
  }

  /**
   * Prints the ticket(s) of a payment on this PC when it can ("normal" printing).
   *
   * Safe to call on every payment screen: it does nothing — leaving the ticket to the
   * printer agents — when this PC is not a ticket station or when the tickets were already
   * taken by an agent.
   *
   * @param orderId    the paid order
   * @param paymentIds accounts of a split bill / departing guest; empty → whole-order ticket
   * @param companyId  current company (the ticket printer is stored per company)
   * @returns true when this PC printed everything; false when the agents handle it
   */
  window.printTicketLocally = async function (orderId, paymentIds, companyId) {
    if (!orderId) return false;

    var printerName = ticketPrinterFor(companyId);
    if (!printerName) return false; // this PC does not print tickets: the agent handles it

    try {
      var connected = await ensureQzConnected();
      if (!connected) return false;
      var available = await printerAvailable(printerName);
      if (!available) return false;
    } catch (e) {
      return false; // no QZ Tray here: the agent prints the ticket
    }

    var accounts = paymentIds || [];
    if (!accounts.length) {
      return printOneTicket(
        "/api/print/ticket/" + orderId,
        "/api/print/ticket/" + orderId + "/claim",
        "/api/print/ticket/" + orderId + "/release",
        printerName,
        "ticket del pedido " + orderId
      );
    }

    var all = true;
    for (var i = 0; i < accounts.length; i++) {
      var paymentId = accounts[i];
      var printed = await printOneTicket(
        "/api/print/ticket/" + orderId + "/payment/" + paymentId,
        "/api/print/ticket/payment/" + paymentId + "/claim",
        "/api/print/ticket/payment/" + paymentId + "/release",
        printerName,
        "cuenta " + paymentId + " del pedido " + orderId
      );
      all = all && printed;
    }
    return all;
  };

  /** Alias for older screens still calling the previous name. */
  window.printAccountTicketsLocally = window.printTicketLocally;
})();
