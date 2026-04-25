/**
 * QZ Tray message signing setup.
 * Must load AFTER qz-tray.js and BEFORE any qz.websocket.connect() call.
 *
 * When enabled on the server, this removes the "Allow / Remember this decision"
 * dialog by sending a signed certificate + signature with each request.
 *
 * If the server endpoints return 404 (signing disabled / keys not configured),
 * the promises reject silently and QZ Tray falls back to its normal dialog flow.
 */
(function () {
  if (typeof qz === "undefined" || !qz.security) return;

  qz.security.setCertificatePromise(function (resolve, reject) {
    fetch("/qz/certificate", {
      cache: "no-store",
      headers: { "Content-Type": "text/plain" },
    })
      .then(function (data) {
        if (data.ok) {
          data.text().then(resolve);
        } else {
          // No cert configured on server -> let QZ Tray show its default unsigned dialog
          reject();
        }
      })
      .catch(function () {
        reject();
      });
  });

  qz.security.setSignatureAlgorithm("SHA512");

  qz.security.setSignaturePromise(function (toSign) {
    return function (resolve, reject) {
      fetch("/qz/sign?request=" + encodeURIComponent(toSign), {
        cache: "no-store",
        headers: { "Content-Type": "text/plain" },
      })
        .then(function (data) {
          if (data.ok) {
            data.text().then(resolve);
          } else {
            reject();
          }
        })
        .catch(function () {
          reject();
        });
    };
  });
})();
