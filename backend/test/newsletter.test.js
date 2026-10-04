import test from 'node:test';
import assert from 'node:assert/strict';
import { newsletterCipher, newsletterInput, newsletterKey } from '../src/newsletter.js';
test('newsletter requires consent, bounded content, opaque tokens and integer-era request keys',()=>{
  assert.equal(newsletterInput({email:' Demo@EXAMPLE.invalid ',consent:true},'subscribe').email,'demo@example.invalid');
  assert.throws(()=>newsletterInput({email:'demo@example.invalid',consent:false},'subscribe'));
  assert.throws(()=>newsletterInput({token:'legacy-uuid'},'confirm'));
  assert.throws(()=>newsletterInput({subject:'Subject\r\nHeader',body:'Demo synthetic newsletter'},'campaign'));
  assert.throws(()=>newsletterInput({subject:'Subject',body:'Demo synthetic newsletter',role:'OWNER'},'campaign'));
  assert.equal(newsletterKey('a'.repeat(64)).length,32);
  assert.throws(()=>newsletterKey('legacy-uuid'));
});
test('newsletter ciphertext is randomized, authenticated and requires the preserved private key',()=>{
  const cipher=newsletterCipher('a'.repeat(64)), value='synthetic secret newsletter link';
  const first=cipher.seal(value), second=cipher.seal(value);
  assert.ok(!first.equals(second)); assert.ok(!first.includes(Buffer.from(value)));
  assert.equal(cipher.open(first),value);
  const changed=Buffer.from(first); changed[changed.length-1]^=1; assert.throws(()=>cipher.open(changed));
  assert.throws(()=>newsletterCipher('b'.repeat(64)).open(first));
  assert.throws(()=>newsletterCipher('0'.repeat(64))); assert.throws(()=>newsletterCipher('placeholder'));
});
