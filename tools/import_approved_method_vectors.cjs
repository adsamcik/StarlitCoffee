#!/usr/bin/env node
// Native projection of the accepted prototype family, with the newer Pulsar override.
// node tools/import_approved_method_vectors.cjs [--check]
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const crypto = require('node:crypto');

const root = path.resolve(__dirname, '..');
const review = JSON.parse(fs.readFileSync(path.join(root, 'docs/assets/native-method-family/manifest.json'), 'utf8'));
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const normalizedText = bytes => Buffer.from(bytes.toString('utf8').replace(/\r\n/g, '\n'));
const source = normalizedText(fs.readFileSync(path.join(root, review.prototype_source)));
if (hash(source) !== review.prototype_sha256) throw new Error('Prototype method artwork changed; review it before importing');
const context = vm.createContext(Object.create(null));
vm.runInContext(source.toString('utf8') + '\nthis.icons = MethodIcons;', context, { timeout: 1000 });

const mappings = {
  chemex: 'chemex', v60: 'v60_02', espresso: 'espresso', cold_brew: 'cold',
  kalita_wave: 'wave_185', wedge: 'wedge', aeropress: 'aeropress', french_press: 'french-press',
  clever: 'clever', hario_switch: 'hario-switch', melitta: 'melitta', automatic_drip: 'automatic-drip',
  moka_pot: 'moka-pot', turkish: 'turkish', phin: 'phin', siphon: 'siphon', percolator: 'percolator',
};
const vectorExports = new Map();
for (const [key, id] of Object.entries(mappings)) {
  const icon = context.icons[id];
  if (!icon || icon.viewBox !== '0 0 24 24' || !Array.isArray(icon.paths) || !icon.paths.length) {
    throw new Error(`Unexpected reviewed viewport or paths: ${id}`);
  }
  const lines = [
    '<!-- Accepted prototype recognition icon; tools/import_approved_method_vectors.cjs -->',
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
    '    android:width="24dp" android:height="24dp"',
    '    android:viewportWidth="24" android:viewportHeight="24">',
  ];
  for (const contour of icon.paths) {
    if (typeof contour !== 'string' || !/^[MmLlHhVvCcSsQqTtAaZz0-9.,+eE\s-]+$/.test(contour)) {
      throw new Error(`Unsupported path syntax: ${id}`);
    }
    lines.push('    <path android:fillColor="#FF000000" android:fillType="evenOdd"',
      `        android:pathData="${contour}" />`);
  }
  lines.push('</vector>');
  vectorExports.set(`equipment_${key}.xml`, Buffer.from(lines.join('\n') + '\n'));
}

const pulsar = normalizedText(fs.readFileSync(path.join(root, review.pulsar_source)));
if (hash(pulsar) !== review.pulsar_sha256) throw new Error('Approved Pulsar export changed');
vectorExports.set('equipment_pulsar.xml', pulsar);
const check = process.argv.includes('--check');
for (const [filename, contents] of vectorExports) {
  if (hash(contents) !== review.native_sha256[filename]) throw new Error(`Native projection changed: ${filename}`);
  const destination = path.join(root, 'app/src/main/res/drawable', filename);
  if (check) {
    if (!fs.existsSync(destination) || !normalizedText(fs.readFileSync(destination)).equals(contents)) {
      throw new Error(`Native resource differs from reviewed contours: ${filename}`);
    }
  } else fs.writeFileSync(destination, contents);
}
console.log(`${check ? 'Verified' : 'Imported'} ${vectorExports.size} native method vectors, preserving approved winding and Pulsar bytes`);
