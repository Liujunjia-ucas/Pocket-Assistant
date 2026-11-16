import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
const assets = 'app/src/main/assets/renderer/';
const sandbox = vm.createContext({ console });
for (const name of ['katex.min.js', 'marked.umd.js', 'renderer.js']) vm.runInContext(readFileSync(assets + name, 'utf8'), sandbox, { filename: name, timeout: 10000 });
const render = source => sandbox.PocketRenderer.renderMarkdown(source);
const countMath = html => (html.match(/class="katex"/g) || []).length;

test('renders inline and display formulas in all four common delimiter styles', () => {
  const source = String.raw`行内：\(x^2 + \frac{1}{2}\)，以及 $a_1+b_2$。

\[
\int_0^1 x^2\,dx = \frac{1}{3}
\]

$$ \sum_{n=1}^{\infty}\frac{1}{n^2}=\frac{\pi^2}{6} $$`;
  const html = render(source);
  assert.equal(countMath(html), 4);
  assert.ok(html.includes('katex-mathml'));
  assert.ok(!html.includes('math-error'));
  assert.ok(!html.includes('\\['));
});

test('renders matrices, aligned derivations, math fences and math inside Markdown tables', () => {
  const source = String.raw`## 推导

$$\begin{aligned}f(x)&=x^2+1\\f'(x)&=2x\end{aligned}$$

\[A=\begin{pmatrix}1&2\\3&4\end{pmatrix}\]

| 项目 | 值 |
| --- | --- |
| 根 | $\sqrt{2}$ |

` + '```latex\n\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}\n```';
  const html = render(source);
  assert.equal(countMath(html), 4);
  assert.ok(html.includes('<table>'));
  assert.ok(html.includes('<h2>推导</h2>'));
  assert.ok(!html.includes('math-error'));
});

test('preserves code examples, escaped dollars and currency', () => {
  const source = '价格 $5 and $10；`$x^2$` 是代码。\n\n```python\nprint("$a+b$")\n```\n\n\\$5';
  const html = render(source);
  assert.equal(countMath(html), 0);
  assert.ok(html.includes('print(&quot;$a+b$&quot;)'));
  assert.ok(html.includes('<code>$x^2$</code>'));
  assert.ok(html.includes('$5 and $10'));
});

test('does not interpret model-provided HTML, remote images or dangerous links as active content', () => {
  const source = '<script>globalThis.stolen=true</script>\n<img src="https://evil.invalid/x" onerror="steal()">\n\n[bad](javascript:alert(1))\n![remote](https://evil.invalid/image)\n\n$\\href{javascript:alert(1)}{click}$';
  const html = render(source);
  assert.ok(!/<script|<img|<iframe|href="javascript:/i.test(html));
  assert.ok(html.includes('&lt;script&gt;'));
  assert.equal(sandbox.stolen, undefined);
});

test('handles invalid LaTeX and every prefix of a streamed answer without breaking the reply', () => {
  const broken = render(String.raw`答案为 \[\frac{1}{\] 后续说明仍可阅读。`);
  assert.ok(broken.includes('math-error'));
  assert.ok(broken.includes('后续说明仍可阅读'));
  const source = String.raw`计算得 \(\frac{a}{b}\)，所以 $$\begin{aligned}a&=1\\b&=2\end{aligned}$$。`;
  for (let i = 1; i <= source.length; i++) assert.equal(typeof render(source.slice(0, i)), 'string');
  assert.equal(countMath(render(source)), 2);
});

test('reports only actual horizontal overflow rectangles so ordinary formula replies can page', () => {
  const reports = [];
  const elements = [
    { scrollWidth: 180, clientWidth: 200, getBoundingClientRect: () => ({ left: 0, top: 10, width: 200, height: 25 }) },
    { scrollWidth: 600, clientWidth: 200, getBoundingClientRect: () => ({ left: 2, top: 50, width: 200, height: 40 }) },
  ];
  const root = { innerHTML: '', getBoundingClientRect: () => ({height: 200}), querySelectorAll: () => elements };
  const browser = vm.createContext({ console, document: { getElementById: () => root, documentElement: {dataset:{}} },
    scrollX: 0, scrollY: 0, requestAnimationFrame: callback => callback(), setInterval: () => {}, addEventListener: () => {},
    PocketHeight: { resize: () => {}, horizontalRegions: (version, json) => reports.push([version, JSON.parse(json)]) } });
  for (const name of ['katex.min.js', 'marked.umd.js', 'renderer.js']) vm.runInContext(readFileSync(assets + name, 'utf8'), browser);
  browser.renderMessage('$x+1$', 7);
  assert.deepEqual(reports.at(-1), [7, [[2,50,200,40]]]);
  elements.pop(); browser.renderMessage('$x+1$', 8);
  assert.deepEqual(reports.at(-1), [8, []]);
});
