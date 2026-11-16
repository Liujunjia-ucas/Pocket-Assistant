/* Offline Markdown + LaTeX renderer. Model text is data; raw HTML is escaped. */
(function (scope) {
  'use strict';
  const escape = value => String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  function escaped(source, position) {
    let count = 0;
    for (let i = position - 1; i >= 0 && source[i] === '\\'; i--) count++;
    return count % 2 === 1;
  }
  function closing(source, delimiter, from) {
    let at = source.indexOf(delimiter, from);
    while (at !== -1 && escaped(source, at)) at = source.indexOf(delimiter, at + delimiter.length);
    return at;
  }
  function stripDelimiters(value) {
    const text = value.trim();
    for (const [open, close] of [['$$', '$$'], ['\\[', '\\]'], ['\\(', '\\)'], ['$', '$']]) {
      if (text.startsWith(open) && text.endsWith(close) && text.length > open.length + close.length) return text.slice(open.length, -close.length).trim();
    }
    return text;
  }
  function math(tex, display, inlineContainer) {
    const formula = tex.trim();
    try {
      const html = scope.katex.renderToString(formula, {
        displayMode: display, output: 'htmlAndMathml', throwOnError: true,
        trust: false, strict: 'ignore', maxExpand: 1000, maxSize: 20, globalGroup: false,
      });
      return display ? (inlineContainer ? '<span class="math-display">' + html + '</span>' : '<div class="math-block">' + html + '</div>\n') : html;
    } catch (_) {
      return '<span class="math-error"><span class="math-error-label">此公式格式有误，显示原文</span>' + escape(formula) + '</span>';
    }
  }
  function inlineToken(source) {
    let open, close, display = false;
    if (source.startsWith('\\(')) { open = '\\('; close = '\\)'; }
    else if (source.startsWith('\\[')) { open = '\\['; close = '\\]'; display = true; }
    else if (source.startsWith('$$')) { open = '$$'; close = '$$'; display = true; }
    else if (source[0] === '$' && source[1] && !/\s|\$/.test(source[1])) { open = '$'; close = '$'; }
    else return;
    let end = closing(source, close, open.length);
    if (end < 0) return;
    if (open === '$') {
      // Pandoc-style guards keep prices such as "$5 and $10" as text.
      if (/\s/.test(source[end - 1]) || /[0-9]/.test(source[end + 1] || '') || /[\n`]/.test(source.slice(1, end))) return;
    }
    if (end === open.length) return;
    return { type: 'inlineMath', raw: source.slice(0, end + close.length), tex: source.slice(open.length, end), display };
  }
  const renderer = new scope.marked.Renderer();
  renderer.html = token => escape(token.text);
  renderer.code = function (token) {
    if (/^(math|latex|tex)(?:\s|$)/i.test(token.lang || '')) return math(stripDelimiters(token.text), true);
    return '<pre><code>' + escape(token.text) + '</code></pre>\n';
  };
  renderer.image = token => '<span>' + escape(token.text || '[图片]') + '</span>';
  renderer.link = function (token) {
    const label = this.parser.parseInline(token.tokens);
    if (!/^(https?:\/\/|mailto:)/i.test(token.href || '')) return label;
    return '<a href="' + escape(token.href) + '" rel="noopener noreferrer">' + label + '</a>';
  };
  renderer.table = function (token) {
    const cell = (value, heading) => '<' + (heading ? 'th' : 'td') + '>' + this.parser.parseInline(value.tokens) + '</' + (heading ? 'th' : 'td') + '>';
    return '<div class="table-scroll"><table><thead><tr>' + token.header.map(v => cell(v, true)).join('') + '</tr></thead><tbody>' + token.rows.map(row => '<tr>' + row.map(v => cell(v, false)).join('') + '</tr>').join('') + '</tbody></table></div>';
  };
  const parser = new scope.marked.Marked({ gfm: true, breaks: true, renderer, extensions: [
    {
      name: 'blockMath', level: 'block',
      start(source) { const at = source.search(/(?:^|\n) {0,3}(?:\$\$|\\\[|\\begin\{(?:align\*?|aligned|equation\*?|gather\*?)\})/); return at < 0 ? undefined : at; },
      tokenizer(source) {
        const match = source.match(/^ {0,3}(\$\$|\\\[|\\begin\{(align\*?|aligned|equation\*?|gather\*?)\})/);
        if (!match) return;
        const close = match[1] === '$$' ? '$$' : match[1] === '\\[' ? '\\]' : '\\end{' + match[2] + '}';
        const end = closing(source, close, match[0].length);
        if (end < 0) return;
        const raw = source.slice(0, end + close.length);
        const tex = match[2] ? raw.trim() : source.slice(match[0].length, end);
        return { type: 'blockMath', raw, tex };
      },
      renderer(token) { return math(token.tex, true); },
    },
    {
      name: 'inlineMath', level: 'inline',
      start(source) {
        const re = /\\[([]|\$/g; let match;
        while ((match = re.exec(source))) if (!escaped(source, match.index)) return match.index;
      },
      tokenizer: inlineToken,
      renderer(token) {
        return math(token.tex, token.display, true);
      },
    },
  ] });
  function renderMarkdown(text) { return parser.parse(String(text || '')); }
  scope.PocketRenderer = { renderMarkdown };
  if (typeof document === 'undefined') return;
  const root = document.getElementById('reply');
  scope.setReplyTheme = function (dark) { document.documentElement.dataset.theme=dark?'dark':'light'; };
  let revision = 0, lastHeight = -1;
  function reportHeight() {
    const height = Math.max(1, Math.ceil(root.getBoundingClientRect().height));
    if (scope.PocketHeight && typeof scope.PocketHeight.horizontalRegions === 'function') {
      const regions = Array.from(root.querySelectorAll('pre,.math-block,.math-display,.table-scroll'))
        .filter(el => el.scrollWidth > el.clientWidth + 1)
        .slice(0, 200).map(el => { const r = el.getBoundingClientRect(); return [r.left + scope.scrollX, r.top + scope.scrollY, r.width, r.height]; });
      scope.PocketHeight.horizontalRegions(revision, JSON.stringify(regions));
    }
    if (height === lastHeight) return;
    lastHeight = height;
    if (scope.PocketHeight && typeof scope.PocketHeight.resize === 'function') scope.PocketHeight.resize(revision, height);
  }
  scope.renderMessage = function (text, version) {
    revision = version; lastHeight = -1;
    try { root.innerHTML = renderMarkdown(text); }
    catch (_) { root.textContent = String(text || ''); }
    requestAnimationFrame(reportHeight);
    if (document.fonts) document.fonts.ready.then(reportHeight);
  };
  if (typeof ResizeObserver !== 'undefined') new ResizeObserver(reportHeight).observe(root);
  else setInterval(reportHeight, 300);
  scope.addEventListener('resize', reportHeight);
})(typeof globalThis !== 'undefined' ? globalThis : window);
