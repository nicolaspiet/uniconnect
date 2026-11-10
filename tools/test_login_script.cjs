// Execute the production Kotlin JS template in a simulated portal DOM.
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = readFileSync('app/src/main/java/br/edu/uniconnect/LoginScript.kt', 'utf8');
const templates = [...source.matchAll(/"""([\s\S]*?)"""/g)].map(m => m[1]);
const user = "aluno'ç🔒", password = "senha\\'\n🔒";
const script = templates[0].replace('${data(user)}', Buffer.from(user).toString('base64'))
  .replace('${data(password)}', Buffer.from(password).toString('base64'));
function scenario(options = {}) {
  let clicks = 0, handlers = 0, requests = 0;
  const input = () => ({value: '', dispatchEvent() { if (options.prepareError) throw Error(password); }});
  const username = input(), secret = input();
  const button = {disabled: options.disabled, hasAttribute: name => name === 'formaction' && !!options.buttonAction,
    formAction: options.buttonAction,
    click() { clicks++; if (options.clickError) throw Error(password); handlers++; requests++; }};
  const form = {method: options.method || 'post', action: options.action || 'https://login.ufdpar.edu.br:6082/php/uid.php',
    querySelector(q) { return q.includes('submit') ? button : q.includes('passwd') ? secret : username; },
    checkValidity() { return options.valid !== false; },
    requestSubmit() { throw Error('requestSubmit must never run'); }};
  const context = {location: {origin: options.origin || 'https://login.ufdpar.edu.br:6082', href: form.action},
    document: {readyState: options.loading ? 'loading' : 'complete', forms: options.noForm ? [] : [form],
      querySelectorAll: () => options.error ? [{getClientRects: () => [1], textContent: password}] : [],
      querySelector: () => options.noForm ? null : secret},
    getComputedStyle: () => ({visibility:'visible', display:'block'}),
    URL, TextDecoder, Uint8Array, atob, Event:class{}, KeyboardEvent:class{},
    submitClicked() { throw Error('must run only through native page handlers'); },
    respStatus: options.challenge ? 'Challenge' : ''};
  return {result: vm.runInNewContext(options.inspect ? templates[1] : script, context),
    clicks, handlers, requests, username: username.value, password: secret.value};
}
const success = scenario();
assert.equal(success.result, 'submitted');
assert.equal(success.clicks, 1); assert.equal(success.handlers, 1); assert.equal(success.requests, 1);
assert.equal(success.username, user); assert.equal(success.password, password);
for (const [options, expected] of [
  [{loading:true},'not-ready'], [{disabled:true},'not-ready'], [{valid:false},'invalid-fields'],
  [{origin:'https://evil.test'},'wrong-origin'], [{action:'https://evil.test'},'wrong-action'],
  [{buttonAction:'https://evil.test'},'wrong-action'], [{method:'get'},'unsupported'],
  [{noForm:true},'unsupported'], [{prepareError:true},'script-error'], [{clickError:true},'submit-error']
]) {
  const actual = scenario(options);
  assert.equal(actual.result, expected);
  assert.equal(actual.requests, 0);
  if (['wrong-origin','wrong-action','unsupported'].includes(expected)) assert.equal(actual.password, '');
}
for (const [options, expected] of [
  [{error:true},'portal-error'], [{challenge:true},'challenge'], [{},'login-form'],
  [{noForm:true},'other-page'], [{origin:'https://evil.test'},'other-page']
]) assert.equal(scenario({...options, inspect:true}).result, expected);
console.log('Portal scripts: native click, Unicode, guards, failures and response redaction passed (16 scenarios).');
