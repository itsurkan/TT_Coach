// Sub-page renderer (Privacy / Terms / Support). Reuses Nav, Footer, the
// theme + language machinery from the landing page. The active page id is
// read from window.__PAGE, set by each host HTML file.

const HOME = 'TT Coach AI.html';

// Pick the active-language string from a {en,uk,es,zh} leaf.
function tx(obj) {
  if (obj == null) return '';
  if (typeof obj === 'string') return obj;
  const lang = window.__LANG || 'en';
  return obj[lang] != null ? obj[lang] : obj.en;
}

const PageIcon = {
  Mail: (p) => <svg width="20" height="20" viewBox="0 0 20 20" fill="none" {...p}><rect x="2.5" y="4.5" width="15" height="11" rx="2" stroke="currentColor" strokeWidth="1.5"/><path d="M3 6l7 5 7-5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  Chat: (p) => <svg width="20" height="20" viewBox="0 0 20 20" fill="none" {...p}><path d="M3 5.5A2 2 0 015 3.5h10a2 2 0 012 2v6a2 2 0 01-2 2H8l-4 3v-3H5a2 2 0 01-2-2v-6z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round"/></svg>,
  Plus: (p) => <svg width="16" height="16" viewBox="0 0 16 16" fill="none" {...p}><path d="M8 3.5v9M3.5 8h9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>,
};

// ---- Shared section primitives ----------------------------------------

function Paragraph({ children }) {
  return <p style={{ fontSize: 17, lineHeight: 1.7, color: 'var(--ink-2)', marginBottom: 18, textWrap: 'pretty' }}>{children}</p>;
}

function BulletList({ items }) {
  return (
    <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 12, margin: '4px 0 22px' }}>
      {items.map((it, i) => (
        <li key={i} style={{ display: 'flex', gap: 12, fontSize: 17, lineHeight: 1.6, color: 'var(--ink-2)' }}>
          <span style={{ flexShrink: 0, marginTop: 7, width: 6, height: 6, borderRadius: 999, background: 'var(--navy)' }} />
          <span style={{ textWrap: 'pretty' }}>{it}</span>
        </li>
      ))}
    </ul>
  );
}

// ---- Legal page (Privacy / Terms) --------------------------------------

function LegalPage({ data }) {
  const { isCompact, isMobile } = useBreakpoint();
  const sections = data.sections;

  return (
    <Container narrow style={{ paddingTop: isCompact ? 110 : 150, paddingBottom: isCompact ? 8 : 40 }}>
      {/* Header */}
      <Reveal>
        <div style={{ maxWidth: 720 }}>
          <Pill tone="navy" dot>{tx(data.eyebrow)}</Pill>
          <h1 style={{ fontSize: isMobile ? 'clamp(34px, 10vw, 56px)' : 'clamp(44px, 7vw, 78px)', lineHeight: 1.02, letterSpacing: '-0.035em', fontWeight: 800, margin: '22px 0 18px', overflowWrap: 'break-word', hyphens: 'auto' }}>
            {tx(data.title)}
          </h1>
          <div className="mono" style={{ fontSize: 13, color: 'var(--ink-3)', letterSpacing: '0.04em', marginBottom: 26 }}>
            {tx(data.updated)}
          </div>
          <p style={{ fontSize: 20, lineHeight: 1.6, color: 'var(--ink-2)', textWrap: 'pretty' }}>{tx(data.intro)}</p>
        </div>
      </Reveal>

      {/* Body + TOC */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: isCompact ? '1fr' : '220px 1fr',
        gap: isCompact ? 0 : 56,
        marginTop: isCompact ? 44 : 72,
        alignItems: 'start',
      }}>
        {!isCompact && (
          <nav style={{ position: 'sticky', top: 110, display: 'flex', flexDirection: 'column', gap: 2 }}>
            <div className="mono" style={{ fontSize: 11, letterSpacing: '0.1em', color: 'var(--ink-3)', marginBottom: 12, textTransform: 'uppercase' }}>
              {tx({ en: 'On this page', uk: 'На цій сторінці', es: 'En esta página', zh: '本页目录' })}
            </div>
            {sections.map((s, i) => (
              <a key={i} href={'#s' + i} style={{
                fontSize: 14, lineHeight: 1.4, color: 'var(--ink-3)', textDecoration: 'none',
                padding: '7px 0', borderLeft: '2px solid transparent', paddingLeft: 0,
              }}
                onMouseEnter={e => e.currentTarget.style.color = 'var(--ink)'}
                onMouseLeave={e => e.currentTarget.style.color = 'var(--ink-3)'}>
                {tx(s.h)}
              </a>
            ))}
          </nav>
        )}

        <div style={{ minWidth: 0 }}>
          {sections.map((s, i) => (
            <Reveal key={i} as="section" style={{ marginBottom: 46 }}>
              <div id={'s' + i} style={{ scrollMarginTop: 110 }}>
                <h2 style={{ fontSize: 'clamp(24px, 3vw, 30px)', letterSpacing: '-0.02em', fontWeight: 700, marginBottom: 18 }}>
                  {tx(s.h)}
                </h2>
                {s.body.map((b, j) => (
                  b.ul
                    ? <BulletList key={j} items={tx(b.ul)} />
                    : <Paragraph key={j}>{tx(b.p)}</Paragraph>
                ))}
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </Container>
  );
}

// ---- Support page ------------------------------------------------------

function FAQItem({ q, a, open, onToggle }) {
  return (
    <div style={{ borderBottom: '1px solid var(--line)' }}>
      <button onClick={onToggle} style={{
        width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20,
        padding: '22px 0', background: 'transparent', border: 'none', cursor: 'pointer',
        fontFamily: 'inherit', color: 'var(--ink)', textAlign: 'left',
      }}>
        <span style={{ fontSize: 18, fontWeight: 600, letterSpacing: '-0.01em' }}>{tx(q)}</span>
        <span style={{
          flexShrink: 0, width: 30, height: 30, borderRadius: 8, border: '1px solid var(--line)',
          display: 'grid', placeItems: 'center', color: 'var(--ink-2)',
          transform: open ? 'rotate(45deg)' : 'none', transition: 'transform .25s ease',
        }}>
          <PageIcon.Plus />
        </span>
      </button>
      <div style={{
        maxHeight: open ? 360 : 0, overflow: 'hidden',
        transition: 'max-height .35s cubic-bezier(.2,.7,.2,1), opacity .3s ease',
        opacity: open ? 1 : 0,
      }}>
        <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-2)', paddingBottom: 24, maxWidth: 660, textWrap: 'pretty' }}>
          {tx(a)}
        </p>
      </div>
    </div>
  );
}

function SupportPage({ data }) {
  const { isCompact, isMobile } = useBreakpoint();
  const [open, setOpen] = useState(0);

  return (
    <Container narrow style={{ paddingTop: isCompact ? 110 : 150, paddingBottom: isCompact ? 8 : 40 }}>
      <Reveal>
        <div style={{ maxWidth: 720 }}>
          <Pill tone="sage" dot>{tx(data.eyebrow)}</Pill>
          <h1 style={{ fontSize: isMobile ? 'clamp(34px, 10vw, 56px)' : 'clamp(44px, 7vw, 78px)', lineHeight: 1.02, letterSpacing: '-0.035em', fontWeight: 800, margin: '22px 0 18px', overflowWrap: 'break-word', hyphens: 'auto' }}>
            {tx(data.title)}
          </h1>
          <p style={{ fontSize: 20, lineHeight: 1.6, color: 'var(--ink-2)', textWrap: 'pretty' }}>{tx(data.intro)}</p>
        </div>
      </Reveal>

      {/* Contact cards */}
      <Reveal style={{ marginTop: 48 }}>
        <div style={{ display: 'grid', gridTemplateColumns: isCompact ? '1fr' : '1fr 1fr', gap: 16 }}>
          {data.contacts.map((c, i) => (
            <a key={i} href={c.href} target={/^https?:/.test(c.href) ? '_blank' : undefined} rel={/^https?:/.test(c.href) ? 'noopener noreferrer' : undefined} style={{
              display: 'flex', gap: 16, alignItems: 'flex-start',
              padding: '22px 24px', borderRadius: 18, border: '1px solid var(--line)',
              background: 'var(--bg-2)', textDecoration: 'none', color: 'inherit',
              transition: 'transform .15s ease, border-color .15s ease',
            }}
              onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.borderColor = 'var(--navy)'; }}
              onMouseLeave={e => { e.currentTarget.style.transform = 'none'; e.currentTarget.style.borderColor = 'var(--line)'; }}>
              <span style={{
                flexShrink: 0, width: 44, height: 44, borderRadius: 12, display: 'grid', placeItems: 'center',
                background: 'var(--bg)', border: '1px solid var(--line)', color: 'var(--navy)',
              }}>
                {c.icon === 'mail' ? <PageIcon.Mail /> : <PageIcon.Chat />}
              </span>
              <div>
                <div style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 3 }}>{tx(c.label)}</div>
                <div style={{ fontSize: 17, fontWeight: 600, letterSpacing: '-0.01em', marginBottom: 6 }}>{tx(c.value)}</div>
                <div style={{ fontSize: 14, color: 'var(--ink-3)', lineHeight: 1.5 }}>{tx(c.note)}</div>
              </div>
            </a>
          ))}
        </div>
      </Reveal>

      {/* FAQ */}
      <Reveal style={{ marginTop: 72 }}>
        <h2 style={{ fontSize: 'clamp(26px, 3.4vw, 34px)', letterSpacing: '-0.025em', fontWeight: 700, marginBottom: 8 }}>
          {tx(data.faqHeading)}
        </h2>
        <div style={{ marginTop: 18, borderTop: '1px solid var(--line)' }}>
          {data.faq.map((f, i) => (
            <FAQItem key={i} q={f.q} a={f.a} open={open === i} onToggle={() => setOpen(open === i ? -1 : i)} />
          ))}
        </div>
      </Reveal>
    </Container>
  );
}

// ---- Page app wrapper (theme + language) -------------------------------

function PageApp() {
  const [tweaks, setTweaks] = useState(() => {
    let savedTheme = null, savedPalette = null;
    try { savedTheme = localStorage.getItem('ttc_theme'); savedPalette = localStorage.getItem('ttc_palette'); } catch (e) {}
    return { ...window.TWEAKS, ...(savedTheme ? { theme: savedTheme } : {}), ...(savedPalette ? { palette: savedPalette } : {}) };
  });
  const [lang, setLang] = useState(() => detectLang());

  window.__LANG = lang;
  useEffect(() => {
    window.__LANG = lang;
    document.documentElement.setAttribute('lang', lang);
    try { localStorage.setItem('ttc_lang', lang); } catch (e) {}
  }, [lang]);

  useEffect(() => {
    document.documentElement.setAttribute('data-palette', tweaks.palette);
  }, [tweaks.palette]);

  useEffect(() => {
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      const eff = tweaks.theme === 'system' ? (mql.matches ? 'dark' : 'light') : tweaks.theme;
      document.documentElement.setAttribute('data-theme', eff);
    };
    apply();
    try { localStorage.setItem('ttc_theme', tweaks.theme); } catch (e) {}
    if (tweaks.theme === 'system') {
      mql.addEventListener('change', apply);
      return () => mql.removeEventListener('change', apply);
    }
  }, [tweaks.theme]);

  const setTheme = (v) => setTweaks((t) => ({ ...t, theme: v }));

  const { isMobile, isCompact } = useBreakpoint();
  const pageId = window.__PAGE || 'privacy';
  const data = PAGES[pageId];
  // keep document title in sync with language
  useEffect(() => { document.title = tx(data.title) + ' — TT Coach AI'; }, [lang, pageId]);

  return (
    <>
      <Nav lang={lang} setLang={setLang} theme={tweaks.theme} setTheme={setTheme} home={HOME} />
      <main>
        {pageId === 'support'
          ? <SupportPage data={data} />
          : <LegalPage data={data} />}
        <Container narrow style={{ paddingBottom: isCompact ? 20 : 32 }}>
          <Footer />
        </Container>
      </main>
    </>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<PageApp />);
