function CTA() {
  const { isMobile, isCompact } = useBreakpoint();
  return (
    <section id="download" style={{ padding: isCompact ? '72px 0 32px' : '120px 0 56px', background: 'var(--bg)' }}>
      <Container>
        <Reveal>
          <div style={{
            background: 'var(--ink)',
            color: 'var(--bg)',
            borderRadius: isMobile ? 24 : 32,
            padding: isMobile ? '44px 26px' : '80px 60px',
            position: 'relative',
            overflow: 'hidden',
            display: 'grid',
            gridTemplateColumns: isCompact ? '1fr' : '1.2fr 1fr',
            gap: 40,
            alignItems: 'center',
          }}>
            <div style={{ position: 'relative', zIndex: 2 }}>
              <h2 style={{ fontSize: 'clamp(40px, 5vw, 64px)', lineHeight: 1, letterSpacing: '-0.03em', fontWeight: 800, marginBottom: 20, textWrap: 'balance' }}>
                {t('cta.heading')}
              </h2>
              <p style={{ fontSize: 18, color: '#C9C7BF', lineHeight: 1.5, maxWidth: 440, marginBottom: 32 }}>
                {t('cta.body')}
              </p>
              <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                <Button variant="ghost" size="lg" style={{ background: '#F5B547', color: '#1A1A1F', borderColor: 'transparent' }}>
                  <Icon.Android /> {t('hero.android')}
                </Button>
                <Button variant="ghost" size="lg" style={{ background: 'transparent', color: '#F2EEE6', borderColor: 'rgba(255,255,255,0.2)', cursor: 'default', opacity: 0.75 }} tabIndex={-1} aria-label="iOS — coming soon">
                  <Icon.Apple /> {t('hero.ios')}
                  <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.07em', padding: '2px 7px', borderRadius: 5, background: 'rgba(255,255,255,0.15)', color: '#F2EEE6', marginLeft: 4 }}>SOON</span>
                </Button>
              </div>
            </div>

            {/* decorative QR-ish block */}
            <div style={{ position: 'relative', height: 260, display: isMobile ? 'none' : 'flex', justifyContent: 'center', alignItems: 'center' }}>
              <div style={{
                width: 200, height: 200, borderRadius: 24,
                background: 'var(--bg)', padding: 18,
                display: 'grid', gridTemplateColumns: 'repeat(12, 1fr)', gridTemplateRows: 'repeat(12, 1fr)', gap: 2,
              }}>
                {Array.from({ length: 144 }).map((_, i) => {
                  const r = (Math.sin(i * 2.3) + Math.cos(i * 1.1)) > 0.2;
                  return <div key={i} style={{ background: r ? 'var(--ink)' : 'transparent', borderRadius: 1 }}/>;
                })}
              </div>
              <div style={{ position: 'absolute', bottom: 20, fontSize: 11, color: '#8A8A92', fontFamily: 'JetBrains Mono' }}>
                {t('cta.scan')}
              </div>
            </div>

            {/* corner motif */}
            <div aria-hidden style={{ position: 'absolute', top: -60, right: -60, width: 240, height: 240, borderRadius: '50%', background: 'rgba(245,181,71,0.08)' }}/>
          </div>
        </Reveal>

        <Footer />
      </Container>
    </section>
  );
}

function Footer() {
  const { isCompact } = useBreakpoint();
  return (
    <div style={{
      marginTop: isCompact ? 40 : 80, paddingTop: isCompact ? 24 : 32, borderTop: '1px solid var(--line)',
      display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--ink-3)',
      flexWrap: 'wrap', gap: 16,
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span>© 2026 TT Coach AI.</span>
      </div>
      <div style={{ display: 'flex', gap: 24 }}>
        <a href="Privacy.html" style={{ color: 'inherit', textDecoration: 'none' }}>{t('cta.privacy')}</a>
        <a href="Terms.html" style={{ color: 'inherit', textDecoration: 'none' }}>{t('cta.terms')}</a>
        <a href="Support.html" style={{ color: 'inherit', textDecoration: 'none' }}>{t('cta.support')}</a>
      </div>
    </div>
  );
}

Object.assign(window, { CTA, Footer });
