#!/usr/bin/env node
/* Build the single-file web app: inlines CSS + seed JSON + all JS modules. */
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

const tpl = read('web/index.template.html');
const out = tpl
  .replace('{{STYLE}}', () => read('web/src/style.css'))
  .replace('{{SEED}}', () => read('data/seed.json'))
  .replace('{{ENGINE}}', () => read('web/src/engine.js'))
  .replace('{{AI}}', () => read('web/src/ai.js'))
  .replace('{{AUDIO}}', () => read('web/src/audio.js'))
  .replace('{{UI}}', () => read('web/src/ui.js'));

// safety: no stray script-closing tags inside inlined payloads
const srcs = {
  'web/src/style.css': read('web/src/style.css'),
  'data/seed.json': read('data/seed.json'),
  'web/src/engine.js': read('web/src/engine.js'),
  'web/src/ai.js': read('web/src/ai.js'),
  'web/src/audio.js': read('web/src/audio.js'),
  'web/src/ui.js': read('web/src/ui.js'),
};
for (const [name, src] of Object.entries(srcs)) {
  if (/<\/script/i.test(src)) throw new Error('`</script>` found in ' + name + ' — would break inlining');
}

fs.writeFileSync(path.join(root, 'web/index.html'), out);
console.log('built web/index.html —', (out.length / 1024).toFixed(1), 'KB');
