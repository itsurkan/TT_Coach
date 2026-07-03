// STICKY DOWNLOAD — mobile-only persistent CTA that slides in after the hero.
function StickyDownload() {
  const { isMobile } = useBreakpoint();
  const [show, setShow] = useState(false);
  useEffect(() => {
    const onScroll = () => setShow(window.scrollY > 600);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  if (!isMobile) return null;

  const goDownload = () => {
    const el = document.getElementById('download');
    if (el) window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - 70, behavior: 'smooth' });
  };

  return (
    <div style={{
      position: 'fixed', left: 0, right: 0, bottom: 0, zIndex: 60,
      transform: show ? 'translateY(0)' : 'translateY(140%)',
      transition: 'transform .35s cubic-bezier(.2,.7,.2,1)',
      padding: '12px 16px calc(12px + env(safe-area-inset-bottom))',
      background: 'color-mix(in oklab, var(--bg) 92%, transparent)',
      backdropFilter: 'blur(14px) saturate(1.2)',
      borderTop: '1px solid var(--line)',
      boxShadow: '0 -8px 30px -12px rgba(0,0,0,0.18)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Logo />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 700, lineHeight: 1.1 }}>TT Coach AI</div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{t('hero.check3')} {'\u00b7'} 4.8{'\u2605'}</div>
        </div>
        <Button variant="dark" size="md" onClick={goDownload}>
          <Icon.Android /> {t('nav.getApp')}
        </Button>
      </div>
    </div>
  );
}

Object.assign(window, { StickyDownload });
