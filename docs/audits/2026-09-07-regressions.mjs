import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

// Runs the actual injected Eco JavaScript against a minimal DOM fixture.
// This checks policy coverage, not Android media playback or battery consumption.
const root = process.argv[2];
if (!root) throw new Error('Pass the audited checkout path as the first argument.');
const power = fs.readFileSync(`${root}/app/src/main/java/dev/midroid/app/power/WebViewPowerController.kt`, 'utf8');
const method = power.slice(power.indexOf('private fun applyDocumentPolicy'));
const js = method.split('"""')[1].replaceAll('$modeKey', 'eco');
const nodes = [];
let style;
const makeMedia = () => ({autoplay: true, paused: false, pause() { this.paused = true; }});
const existing = makeMedia();
nodes.push(existing);
const document = {
  getElementById() { return style ?? null; },
  createElement() { return {remove() { style = null; }}; },
  documentElement: {appendChild(node) { style = node; }},
  querySelectorAll(selector) {
    assert.equal(selector, 'video[autoplay], audio[autoplay]');
    return nodes.filter(node => node.autoplay);
  },
};
vm.runInNewContext(js, {document});
assert.equal(existing.autoplay, false);
assert.equal(existing.paused, true);
const dynamicallyAdded = makeMedia();
nodes.push(dynamicallyAdded);
assert.equal(dynamicallyAdded.autoplay, true);
assert.equal(dynamicallyAdded.paused, false);
console.log('CONFIRMED: Eco disables existing autoplay media but leaves later SPA media untouched.');

// Evaluate the exact version-code expression at a run-number rollover.
const release = fs.readFileSync(`${root}/.github/workflows/release.yml`, 'utf8');
assert.ok(release.includes('sequence=$((10#${GITHUB_RUN_NUMBER} % 100))'));
const code = (day, run) => Number(`${day}${String(run % 100).padStart(2, '0')}`);
const before = code('20260907', 99);
const after = code('20260907', 100);
assert.ok(after < before);
console.log(`CONFIRMED: same-day release run 99 -> 100 decreases versionCode: ${before} -> ${after}.`);
