#!/usr/bin/env node
/* Conformance vectors for KontrolCore Mobile (KON-492): what the extension itself does with one-time codes and phone commands, written as JSON that the
 * Kotlin tests read (mobile/core/src/test/resources/vectors.json). The app must agree with the extension on every line, so the numbers come from the
 * extension's own code (totp.js, alert-rules.js), never from the Kotlin side.
 *
 *   node mobile/tools/make-vectors.js           writes the file
 *   node mobile/tools/make-vectors.js --check   exits 1 when the committed file is not what the extension gives now (checkMobileVectors runs this)
 *
 * Needs Node 20 or newer (Web Crypto as a global). Exported for the test. */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const OUT = path.join(ROOT, 'mobile', 'core', 'src', 'test', 'resources', 'vectors.json');

require(path.join(ROOT, 'totp.js'));
require(path.join(ROOT, 'alert-rules.js'));
const TOTP = globalThis.KONTROL_TOTP;
const A = globalThis.KONTROL_ALERTS;

const hex = (u8) => Buffer.from(u8).toString('hex');

async function build() {
  // RFC 6238's SHA-1 secret ("12345678901234567890") and others, with the writing an app may meet (lower case, spaces, = padding).
  const secrets = ['GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', 'JBSWY3DPEHPK3PXP', 'jbsw y3dp ehpk 3pxp', 'MFRGGZDFMZTWQ2LK====', 'KRSXG5CTMVRXEZLU'];
  const times = [0, 29, 30, 59, 60, 1111111109, 1111111111, 1234567890, 1731024000, 1731024029, 1731024030, 2000000000];
  const totp = [];
  for (const secret of secrets) for (const time of times) totp.push({ secret, time, counter: TOTP.counterAt(time), code: await TOTP.codeAt(secret, time) });

  const decode = ['JBSWY3DPEHPK3PXP', 'jbsw y3dp', 'MFRGG', 'MFRGGZA=', 'AA==', '', '   ', '1', 'ABC!', 'GEZDGNBVGY3TQOJQ']
    .map((text) => { const bytes = TOTP.decode32(text); return { text, hex: bytes ? hex(bytes) : null }; });
  const encode = ['', '00', 'ff', '666f6f', '666f6f626172', '12345678901234567890'.split('').map((c) => c.charCodeAt(0).toString(16)).join('')]
    .map((h) => ({ hex: h, text: TOTP.encode32(Uint8Array.from(h.match(/../g) || [], (x) => parseInt(x, 16))) }));

  const messages = [
    'RUN Checkout flow #482913', 'run Checkout flow #482913', 'Run  Checkout   flow  #482913', 'STOP Checkout #123456', 'PAUSE Login #000001',
    'START Login #999999', 'RESET Orders #111111', 'ROW Orders 5 #111111', 'ACK Checkout #222222', 'SNOOZE Checkout #333333',
    'SNOOZE 2h Checkout #333333', 'snooze 24h Checkout #333333', 'snooze 99h Checkout #333333', 'snooze 0h Checkout #333333', 'SNOOZE 3 h Checkout #333333',
    'snooze 2h #333333', 'STATUS #444444', 'status', 'STATUS Checkout #444444', 'RUN #555555', 'RUN', 'RUN Checkout', 'RUN Checkout #12345',
    'RUN Checkout #1234567', 'RUN Checkout#123456', 'DANCE Checkout #123456', '', '   ', 'RUN Shop/Checkout flow #482913', 'RUN  #482913',
    '  RUN Checkout #482913  ', 'RUN Checkout flow\n#482913',
  ];
  const parse = [];
  for (const message of messages) for (const hasCode of [true, false]) {
    const r = A.parsePhoneCommand(message, { hasCode });
    parse.push({ message, hasCode, result: r ? { command: r.command, name: r.name, hours: r.hours, code: r.code } : null });
  }

  // What the app sends: each must be read back by the extension's parser as the same command, name and code.
  const specs = [
    ['RUN', 'Checkout flow', 1, '482913'], ['stop', 'Checkout', 1, '000123'], ['PAUSE', 'Shop/Checkout', 1, '123456'], ['start', 'Login', 1, '654321'],
    ['ack', 'Checkout', 1, '222222'], ['snooze', 'Checkout', 1, '333333'], ['snooze', 'Checkout', 2, '333333'], ['snooze', 'Checkout', 24, '333333'],
    ['status', '', 1, '444444'], ['reset', 'Orders', 1, '111111'], ['row', 'Orders 5', 1, '111111'],
  ];
  const format = specs.map(([command, name, hours, code]) => {
    const message = `${command.toUpperCase()}${command.toLowerCase() === 'snooze' && hours !== 1 ? ` ${hours}h` : ''}${name ? ` ${name}` : ''} #${code}`;
    const back = A.parsePhoneCommand(message, { hasCode: true });
    if (!back || back.command !== command.toLowerCase() || back.name !== name || back.code !== code || back.hours !== hours) throw new Error(`the extension does not read back what the app would send: ${message}`);
    return { command, name, hours, code, message };
  });

  return { note: 'Made by mobile/tools/make-vectors.js from totp.js and alert-rules.js. Do not edit by hand.', totp, decode, encode, parse, format };
}

async function main() {
  const text = JSON.stringify(await build(), null, 2) + '\n';
  if (process.argv.includes('--check')) {
    let have = ''; try { have = fs.readFileSync(OUT, 'utf8'); } catch (e) { /* missing */ }
    if (have !== text) { console.error('mobile vectors.json is out of date: run node mobile/tools/make-vectors.js'); process.exit(1); }
    console.log('mobile vectors.json matches the extension');
    return;
  }
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, text);
  console.log(`wrote ${path.relative(ROOT, OUT)}`);
}

if (require.main === module) main().catch((e) => { console.error(e.message); process.exit(1); });
module.exports = { build };
