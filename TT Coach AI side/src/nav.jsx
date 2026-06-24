function Nav({ lang = 'en', setLang, theme = 'system', setTheme, home = '' }) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const scrollToDownload = () => {
    if (home) { window.location.href = home + '#download'; return; }
    const el = document.getElementById('download');
    if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 70, behavior: 'smooth' });
  };

  const { isMobile, isCompact } = useBreakpoint();

  return (
    <nav style={{
      position: 'fixed', top: 0, left: 0, right: 0, zIndex: 50,
      padding: scrolled ? '14px 0' : '22px 0',
      background: scrolled ? 'color-mix(in oklab, var(--bg) 85%, transparent)' : 'transparent',
      backdropFilter: scrolled ? 'blur(14px) saturate(1.2)' : 'none',
      borderBottom: scrolled ? '1px solid var(--line)' : '1px solid transparent',
      transition: 'all .3s ease',
    }}>
      <Container style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <a href={home || '#'} style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none', color: 'inherit' }}>
          <Logo />
          {!isMobile && <span style={{ fontWeight: 700, fontSize: 17, letterSpacing: '-0.01em' }}>TT Coach AI</span>}
        </a>

        <div style={{ display: 'flex', alignItems: 'center', gap: isMobile ? 8 : 28 }}>
          {!isCompact && (
            <div style={{ display: 'flex', gap: 28, fontSize: 14, color: 'var(--ink-2)', fontWeight: 500 }}>
              <a href={home + '#how'} style={{ color: 'inherit', textDecoration: 'none' }}>{t('nav.how')}</a>
              <a href={home + '#features'} style={{ color: 'inherit', textDecoration: 'none' }}>{t('nav.features')}</a>
              <a href={home + '#proof'} style={{ color: 'inherit', textDecoration: 'none' }}>{t('nav.proof')}</a>
            </div>
          )}
          <ThemeToggle theme={theme} setTheme={setTheme} />
          <LangSwitcher lang={lang} setLang={setLang} />
          {!isCompact && (
            <Button variant="ghost" size="md" style={{ cursor: 'default', opacity: 0.75 }} tabIndex={-1} aria-label="iOS — coming soon">
              <Icon.Apple /> iOS
              <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.07em', padding: '2px 6px', borderRadius: 5, background: 'var(--amber-bg)', color: '#8A5A10', marginLeft: 4 }}>SOON</span>
            </Button>
          )}
          <Button variant="dark" size="md" onClick={scrollToDownload}>
            <Icon.Android /> {isMobile ? t('nav.getApp') : t('hero.android')}
          </Button>
        </div>
      </Container>
    </nav>
  );
}

function ThemeToggle({ theme, setTheme }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const opts = [
    { v: 'system', icon: <Icon.Monitor />, key: 'theme.system' },
    { v: 'light', icon: <Icon.Sun />, key: 'theme.light' },
    { v: 'dark', icon: <Icon.Moon />, key: 'theme.dark' },
  ];
  const current = opts.find((o) => o.v === theme) || opts[0];

  useEffect(() => {
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label="Theme"
        style={{
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          width: 38, height: 38, borderRadius: 10,
          border: '1px solid var(--line)', background: 'var(--bg)',
          color: 'var(--ink-2)', cursor: 'pointer',
        }}>
        {current.icon}
      </button>

      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 8px)', right: 0,
          minWidth: 168, padding: 6,
          background: 'var(--bg)', border: '1px solid var(--line)', borderRadius: 12,
          boxShadow: '0 16px 40px -12px rgba(0,0,0,0.28)',
          display: 'flex', flexDirection: 'column', gap: 2,
        }}>
          {opts.map((o) => {
            const active = o.v === theme;
            return (
              <button key={o.v}
                onClick={() => { setTheme && setTheme(o.v); setOpen(false); }}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
                  padding: '9px 11px', borderRadius: 8, border: 'none',
                  background: active ? 'var(--bg-2)' : 'transparent',
                  color: 'var(--ink)', fontFamily: 'inherit', fontSize: 14,
                  fontWeight: active ? 700 : 500, cursor: 'pointer', textAlign: 'left',
                }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ color: 'var(--ink-3)', display: 'inline-flex' }}>{o.icon}</span>
                  {t(o.key)}
                </span>
                {active && <Icon.Check style={{ color: 'var(--sage-2)' }} />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function LangSwitcher({ lang, setLang }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const current = (window.LANGS || []).find((l) => l.code === lang) || { short: 'EN' };

  useEffect(() => {
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label="Language"
        style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          padding: '9px 12px', borderRadius: 10,
          border: '1px solid var(--line)', background: 'var(--bg)',
          color: 'var(--ink-2)', fontFamily: 'inherit', fontSize: 13, fontWeight: 600,
          cursor: 'pointer', lineHeight: 1,
        }}>
        <Icon.Globe />
        <span style={{ minWidth: 16 }}>{current.short}</span>
        <Icon.Chevron style={{ opacity: 0.6, transform: open ? 'rotate(180deg)' : 'none', transition: 'transform .2s ease' }} />
      </button>

      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 8px)', right: 0,
          minWidth: 168, padding: 6,
          background: 'var(--bg)', border: '1px solid var(--line)', borderRadius: 12,
          boxShadow: '0 16px 40px -12px rgba(0,0,0,0.28)',
          display: 'flex', flexDirection: 'column', gap: 2,
        }}>
          {(window.LANGS || []).map((l) => {
            const active = l.code === lang;
            return (
              <button key={l.code}
                onClick={() => { setLang && setLang(l.code); setOpen(false); }}
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
                  padding: '9px 11px', borderRadius: 8, border: 'none',
                  background: active ? 'var(--bg-2)' : 'transparent',
                  color: 'var(--ink)', fontFamily: 'inherit', fontSize: 14,
                  fontWeight: active ? 700 : 500, cursor: 'pointer', textAlign: 'left',
                }}>
                <span>{l.native}</span>
                <span style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 600 }}>
                  {active ? <Icon.Check style={{ color: 'var(--sage-2)' }} /> : l.short}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Logo() {
  return (
    <div style={{
      width: 32, height: 32, borderRadius: 9,
      background: 'var(--ink)', color: 'var(--bg)',
      display: 'grid', placeItems: 'center',
      position: 'relative', overflow: 'hidden',
    }}>
      <svg width="22" height="22" viewBox="0 0 48 48" fill="none">
        {/* TT monogram + ball */}
        <g stroke="currentColor" strokeWidth="3.4" strokeLinecap="round">
          <path d="M11 15h13" />
          <path d="M17.5 15v20" />
          <path d="M26 23h11" />
          <path d="M31.5 23v12" />
        </g>
        <circle cx="34" cy="14" r="4.2" fill="#F5B547" />
      </svg>
    </div>
  );
}

Object.assign(window, { Nav, Logo, ThemeToggle });
