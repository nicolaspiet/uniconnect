package br.edu.uniconnect

import java.util.Base64

/** Fixed script with base64 data, never executable user-supplied JavaScript. */
object LoginScript {
    private fun data(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    fun fillAndSubmit(user: String, password: String): String = """
        (function() {
          let phase = 'prepare';
          try {
            if (location.origin !== 'https://login.ufdpar.edu.br:6082') return 'wrong-origin';
            if (document.readyState !== 'complete') return 'not-ready';
            const forms = Array.from(document.forms).filter(f =>
                f.querySelector('input[name="user"]') && f.querySelector('input[name="passwd"][type="password"]'));
            if (forms.length !== 1) return 'unsupported';
            const f = forms[0];
            const button = f.querySelector('input[type="submit"][name="ok"],button[type="submit"][name="ok"]');
            if (!button || f.method.toLowerCase() !== 'post') return 'unsupported';
            const action = new URL(f.action, location.href);
            const buttonAction = button.hasAttribute('formaction') ? new URL(button.formAction, location.href) : action;
            if (action.origin !== location.origin || buttonAction.origin !== location.origin ||
                action.username || action.password || buttonAction.username || buttonAction.password ||
                (button.hasAttribute('formmethod') && button.formMethod.toLowerCase() !== 'post')) return 'wrong-action';
            const decode = value => new TextDecoder().decode(Uint8Array.from(atob(value), c => c.charCodeAt(0)));
            const user = f.querySelector('input[name="user"]');
            const password = f.querySelector('input[name="passwd"]');
            user.value = decode('${data(user)}');
            password.value = decode('${data(password)}');
            for (const input of [user, password]) {
                input.dispatchEvent(new Event('input', {bubbles:true}));
                input.dispatchEvent(new Event('change', {bubbles:true}));
                input.dispatchEvent(new KeyboardEvent('keyup', {bubbles:true, key:'Tab'}));
            }
            if (button.disabled) return 'not-ready';
            if (typeof f.checkValidity === 'function' && !f.checkValidity()) return 'invalid-fields';
            // Click once: let the portal's click/submit handlers prepare hidden fields.
            // Calling submitClicked separately duplicates side effects and requestSubmit skips click handlers.
            phase = 'click';
            button.click();
            return 'submitted';
          } catch (_) {
            // Report only a fixed code: exception messages may expose credentials or tokens.
            return phase === 'click' ? 'submit-error' : 'script-error';
          }
        })();
    """.trimIndent()

    fun inspectResponse(): String = """
        (function() {
          try {
            if (location.origin !== 'https://login.ufdpar.edu.br:6082') return 'other-page';
            const visible = el => el && el.getClientRects().length > 0 &&
              getComputedStyle(el).visibility !== 'hidden' && getComputedStyle(el).display !== 'none';
            const errors = Array.from(document.querySelectorAll('#dError, [role="alert"]'));
            if (errors.some(el => visible(el) && el.textContent.trim().length > 0)) return 'portal-error';
            if (typeof respStatus === 'string' && respStatus === 'Challenge') return 'challenge';
            if (document.querySelector('input[name="passwd"][type="password"]')) return 'login-form';
            return 'other-page';
          } catch (_) { return 'inspection-error'; }
        })();
    """.trimIndent()
}
