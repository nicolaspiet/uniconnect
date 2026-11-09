package br.edu.uniconnect

import java.util.Base64

/** Fixed script with base64 data, never executable user-supplied JavaScript. */
object LoginScript {
    private fun data(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    fun fillAndSubmit(user: String, password: String): String = """
        (function() {
            if (location.origin !== 'https://login.ufdpar.edu.br:6082') return 'wrong-origin';
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
            if (button.disabled) return 'needs-interaction';
            // Palo Alto pages register a submit handler that creates preauthid/escapeUser.
            // Run that handler before requestSubmit; a plain button.click() can leave the page
            // on the form without sending those hidden values on some WebView versions.
            if (typeof submitClicked === 'function') submitClicked();
            if (typeof f.requestSubmit === 'function') f.requestSubmit(button); else button.click();
            return 'submitted';
        })();
    """.trimIndent()
}
