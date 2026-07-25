#!/usr/bin/env node

import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const FIELDS = ['towgs84', 'ellipse', 'datumName', 'nadgrids'];
const NULL_MARKER = '\\N';
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDir, '..');
const defaultOutput = path.join(
  projectRoot,
  'src/main/resources/org/datasyslab/proj4sedona/constants/proj4js-datums.tsv'
);

function usage() {
  console.error(
    'Usage: node scripts/sync-proj4js-datums.mjs [--check] [--output FILE] PROJ4JS_CHECKOUT'
  );
}

let checkOnly = false;
let outputPath = defaultOutput;
let sourceRoot;

for (let index = 2; index < process.argv.length; index += 1) {
  const arg = process.argv[index];
  if (arg === '--check') {
    checkOnly = true;
  } else if (arg === '--output') {
    index += 1;
    if (index >= process.argv.length) {
      usage();
      process.exit(2);
    }
    outputPath = path.resolve(process.argv[index]);
  } else if (arg.startsWith('-') || sourceRoot) {
    usage();
    process.exit(2);
  } else {
    sourceRoot = path.resolve(arg);
  }
}

if (!sourceRoot) {
  usage();
  process.exit(2);
}

const datumPath = path.join(sourceRoot, 'lib/constants/Datum.js');
if (!fs.existsSync(datumPath)) {
  throw new Error(`proj4js datum registry not found: ${datumPath}`);
}

const datumStatus = execFileSync(
  'git',
  ['-C', sourceRoot, 'status', '--porcelain', '--', 'lib/constants/Datum.js'],
  {encoding: 'utf8'}
).trim();
if (datumStatus) {
  throw new Error('proj4js Datum.js has uncommitted changes; refusing to generate a mixed snapshot');
}

const upstreamCommit = execFileSync('git', ['-C', sourceRoot, 'rev-parse', 'HEAD'], {
  encoding: 'utf8'
}).trim();
const datumSource = fs.readFileSync(datumPath, 'utf8');
const imported = await import(
  `data:text/javascript;base64,${Buffer.from(datumSource, 'utf8').toString('base64')}`
);
const upstreamDatums = imported.default;

if (!upstreamDatums || typeof upstreamDatums !== 'object') {
  throw new Error('proj4js Datum.js did not export a datum object');
}

const keysByObject = new Map();
const rows = [];

for (const [code, datum] of Object.entries(upstreamDatums)) {
  if (!datum || typeof datum !== 'object') {
    throw new Error(`datum ${code} is not an object`);
  }
  const keys = keysByObject.get(datum) ?? [];
  keys.push(code);
  keysByObject.set(datum, keys);
}

for (const [datum, keys] of keysByObject) {
  let code;
  if (keys.length === 1) {
    [code] = keys;
    if (datum.datumName && datum.datumName !== code) {
      throw new Error(`datum ${code} is missing its ${datum.datumName} alias`);
    }
  } else if (keys.length === 2 && datum.datumName && keys.includes(datum.datumName)) {
    code = keys.find(key => key !== datum.datumName);
  } else {
    throw new Error(`datum object has unexpected lookup keys: ${keys.join(', ')}`);
  }

  const unknownFields = Object.keys(datum).filter(field => !FIELDS.includes(field));
  if (unknownFields.length > 0) {
    throw new Error(`datum ${code} has unsupported fields: ${unknownFields.join(', ')}`);
  }

  const values = FIELDS.map(field => datum[field] ?? '');
  for (const value of [code, ...values]) {
    if (typeof value !== 'string') {
      throw new Error(`datum ${code} contains a non-string value`);
    }
    if (/[\t\r\n]/.test(value)) {
      throw new Error(`datum ${code} contains a tab or newline`);
    }
    if (value === NULL_MARKER) {
      throw new Error(`datum ${code} contains the reserved null marker`);
    }
  }

  const [towgs84, ellipse, datumName, nadgrids] = values;
  if (!code || Boolean(towgs84) === Boolean(nadgrids)) {
    throw new Error(`datum ${code || '<empty>'} must define exactly one of towgs84 or nadgrids`);
  }
  for (const [field, value] of [['ellipse', ellipse], ['datumName', datumName]]) {
    if (datum[field] !== undefined && value.length === 0) {
      throw new Error(`datum ${code} has an empty ${field}`);
    }
  }
  if (towgs84) {
    const parameters = towgs84.split(',');
    if (![3, 7].includes(parameters.length)
        || parameters.some(value => value.length === 0 || !Number.isFinite(Number(value)))) {
      throw new Error(`datum ${code} has invalid towgs84 parameters`);
    }
  }
  rows.push([code, ...values]);
}

rows.sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0);

const exactLookups = new Map();
const normalizedLookups = new Map();
const normalize = key => key.toLowerCase().replace(/[\s_\-\/()]/g, '');
for (const row of rows) {
  const code = row[0];
  const datumName = row[3];
  for (const lookup of datumName && datumName !== code ? [code, datumName] : [code]) {
    for (const [kind, key, registry] of [
      ['case-insensitive', lookup.toLowerCase(), exactLookups],
      ['match.js-normalized', normalize(lookup), normalizedLookups]
    ]) {
      const previous = registry.get(key);
      if (previous && previous !== code) {
        throw new Error(`${kind} lookup collision between ${previous} and ${code}: ${lookup}`);
      }
      registry.set(key, code);
    }
  }
}

const threeParameterRecords = rows.filter(row => row[1] && row[1].split(',').length === 3).length;
const sevenParameterRecords = rows.filter(row => row[1] && row[1].split(',').length === 7).length;
const gridRecords = rows.filter(row => row[4]).length;
const datumNameAliases = rows.filter(row => row[3]).length;
const encode = value => value || NULL_MARKER;
const data = `${rows.map(row => row.map(encode).join('\t')).join('\n')}\n`;
const dataHash = createHash('sha256').update(data, 'utf8').digest('hex');
const generated = [
  '# Generated by scripts/sync-proj4js-datums.mjs; do not edit.',
  '# Source: https://github.com/proj4js/proj4js/blob/'
    + `${upstreamCommit}/lib/constants/Datum.js`,
  `# Upstream commit: ${upstreamCommit}`,
  `# Canonical records: ${rows.length}`,
  `# Datum-name aliases: ${datumNameAliases}`,
  `# Three-parameter records: ${threeParameterRecords}`,
  `# Seven-parameter records: ${sevenParameterRecords}`,
  `# NAD-grid records: ${gridRecords}`,
  `# Null marker: ${NULL_MARKER}`,
  `# Data SHA-256: ${dataHash}`,
  'code\ttowgs84\tellipse\tdatumName\tnadgrids',
  data
].join('\n');

if (checkOnly) {
  const existing = fs.existsSync(outputPath) ? fs.readFileSync(outputPath, 'utf8') : '';
  if (existing !== generated) {
    console.error(`${path.relative(projectRoot, outputPath)} is not synchronized with proj4js`);
    process.exit(1);
  }
  console.log(
    `${path.relative(projectRoot, outputPath)} matches ${rows.length} datums from ${upstreamCommit}`
  );
} else {
  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, generated, 'utf8');
  console.log(
    `Wrote ${rows.length} datums from ${upstreamCommit} to `
      + path.relative(projectRoot, outputPath)
  );
  console.log(`Data SHA-256: ${dataHash}`);
}
