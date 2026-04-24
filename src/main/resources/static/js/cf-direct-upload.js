/**
 * Cloudflare Images — Direct Creator Upload helper (browser-side).
 *
 * Responsibilities:
 *   1. Validate the file size and MIME type before doing anything.
 *   2. Ask the backend for a one-time upload URL (token endpoint).
 *   3. POST the file directly to Cloudflare; the bytes never traverse this server.
 *   4. Return the final delivery URL so caller can save it in a hidden form field.
 *
 * The same JS works from the admin UI and the programmer UI; the only thing that
 * changes is the `tokenUrl` (which determines who can mint tokens). The backend
 * enforces role-based access.
 */
(function (global) {
  "use strict";

  // ───── Constants ─────────────────────────────────────────────────────
  const MAX_FILE_SIZE = 3 * 1024 * 1024; // 3 MB — must mirror server-side
  const ALLOWED_MIME = ["image/jpeg", "image/png", "image/gif", "image/webp"];

  // ───── Public error class so callers can switch on `.code` ────────────
  class CFUploadError extends Error {
    constructor(code, message) {
      super(message);
      this.code = code;
    }
  }

  /**
   * Run client-side validation. Throws CFUploadError on failure.
   */
  function validateFile(file) {
    if (!file) throw new CFUploadError("NO_FILE", "No se seleccionó ningún archivo");
    if (file.size > MAX_FILE_SIZE) {
      throw new CFUploadError(
        "TOO_LARGE",
        "La imagen pesa más de 3MB. Reduce su tamaño o usa formato WEBP."
      );
    }
    if (!ALLOWED_MIME.includes(file.type)) {
      throw new CFUploadError("BAD_TYPE", "Formato no permitido. Usa JPG, PNG, GIF o WEBP.");
    }
  }

  /**
   * Request a one-time direct-upload URL from our backend.
   * @returns {Promise<{uploadUrl: string, imageId: string, finalUrl: string}>}
   */
  async function requestToken(tokenUrl, kind, name) {
    const form = new FormData();
    form.append("kind", kind);
    form.append("name", name || "");
    const res = await fetch(tokenUrl, { method: "POST", body: form, credentials: "same-origin" });
    if (!res.ok) {
      throw new CFUploadError("TOKEN_HTTP_" + res.status, "El servidor rechazó la solicitud de carga (" + res.status + ")");
    }
    const data = await res.json();
    if (!data || !data.success) {
      throw new CFUploadError("TOKEN_FAIL", (data && data.message) || "No se pudo obtener el token de carga");
    }
    return data;
  }

  /**
   * POST the file to Cloudflare's one-time upload URL.
   */
  async function uploadToCloudflare(uploadUrl, file) {
    const form = new FormData();
    form.append("file", file);
    const res = await fetch(uploadUrl, { method: "POST", body: form });
    if (!res.ok) {
      throw new CFUploadError("CF_UPLOAD_HTTP_" + res.status, "Cloudflare rechazó la imagen (" + res.status + ")");
    }
    const data = await res.json();
    if (!data || !data.success) {
      throw new CFUploadError(
        "CF_UPLOAD_FAIL",
        (data && data.errors && data.errors[0] && data.errors[0].message) || "Cloudflare rechazó la imagen"
      );
    }
    return data;
  }

  /**
   * Full upload flow. Returns the final delivery URL (to store in DB).
   *
   * @param {Object}   opts
   * @param {File}     opts.file      the file to upload (required)
   * @param {string}   opts.tokenUrl  backend endpoint that mints tokens
   *                                  ("/admin/api/cf-images/upload-token" or
   *                                  "/programmer/api/cf-images/upload-token")
   * @param {string}   opts.kind      folder kind (whitelisted per role on the server)
   * @param {string}   opts.name      base filename (e.g. menu item name)
   * @param {Function} [opts.onProgress] optional progress callback (0..1)
   * @returns {Promise<string>} the public delivery URL to persist in the DB
   */
  async function upload(opts) {
    if (!opts) throw new CFUploadError("BAD_OPTS", "Faltan opciones");
    validateFile(opts.file);

    if (opts.onProgress) opts.onProgress(0.05);
    const token = await requestToken(opts.tokenUrl, opts.kind, opts.name);

    if (opts.onProgress) opts.onProgress(0.25);
    await uploadToCloudflare(token.uploadUrl, opts.file);

    if (opts.onProgress) opts.onProgress(1);
    return token.finalUrl;
  }

  // Expose
  global.CFDirectUpload = {
    upload: upload,
    validateFile: validateFile,
    Error: CFUploadError,
    MAX_FILE_SIZE: MAX_FILE_SIZE,
    /**
     * Wire a form so that on submit, if a new file is staged in the file input,
     * it is direct-uploaded to Cloudflare BEFORE the form is actually submitted.
     * The resulting delivery URL is written to a hidden field; the file input is
     * cleared so the bytes do not also flow through the server.
     *
     * @param {Object} cfg
     * @param {string} cfg.formId        id of the <form> element
     * @param {string} cfg.fileInputId   id of the <input type="file">
     * @param {string} cfg.urlInputId    id of the hidden input that will receive the final URL
     * @param {string} cfg.tokenUrl      backend endpoint that mints tokens
     * @param {string} cfg.kind          folder kind ("menu-items", "promotions", ...)
     * @param {Function} [cfg.getName]   () => string, base filename. Defaults to "image".
     * @param {Function} [cfg.onError]   custom error handler (receives Error). Default: SweetAlert.
     */
    setupForm: function (cfg) {
      const form = document.getElementById(cfg.formId);
      const fileInput = document.getElementById(cfg.fileInputId);
      const urlInput = document.getElementById(cfg.urlInputId);
      if (!form || !fileInput || !urlInput) {
        console.warn("CFDirectUpload.setupForm: missing element(s)", cfg);
        return;
      }

      let uploaded = false;
      form.addEventListener("submit", async function (event) {
        // Skip if no file to upload OR if the file already went through this handler.
        if (uploaded) return;
        if (!fileInput.files || !fileInput.files[0]) return;

        event.preventDefault();
        const submitBtn = form.querySelector("[type=submit]");
        const oldDisabled = submitBtn ? submitBtn.disabled : null;
        if (submitBtn) submitBtn.disabled = true;

        try {
          const finalUrl = await upload({
            file: fileInput.files[0],
            tokenUrl: cfg.tokenUrl,
            kind: cfg.kind,
            name: cfg.getName ? cfg.getName() : "image",
          });
          urlInput.value = finalUrl;
          // Clear the file input so the multipart submission carries no file.
          fileInput.value = "";
          uploaded = true;
          form.submit();
        } catch (err) {
          if (submitBtn) submitBtn.disabled = oldDisabled;
          if (cfg.onError) {
            cfg.onError(err);
          } else if (typeof Swal !== "undefined") {
            Swal.fire({
              icon: "error",
              title: "Error subiendo imagen",
              text: err.message || String(err),
              confirmButtonText: "Entendido",
              confirmButtonColor: "#ef4444",
            });
          } else {
            alert("Error subiendo imagen: " + (err.message || err));
          }
        }
      });
    },
  };
})(window);
