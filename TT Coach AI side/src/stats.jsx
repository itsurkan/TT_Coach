function StatsSection() {
  const { isMobile, isCompact } = useBreakpoint();
  const stats = [
    { n: '17', labelKey: 'stats.s1.label', subKey: 'stats.s1.sub' },
    { n: '120', labelKey: 'stats.s2.label', subKey: 'stats.s2.sub' },
    { n: '0ms', labelKey: 'stats.s3.label', subKey: 'stats.s3.sub' },
    { n: '14d', labelKey: 'stats.s4.label', subKey: 'stats.s4.sub' },
  ];

  return (
    <section id="proof" style={{ padding: isCompact ? '72px 0' : '100px 0', background: 'var(--canvas)', color: '#F2EEE6', position: 'relative', overflow: 'hidden' }}>
      <div aria-hidden style={{
        position: 'absolute', inset: 0,
        background: 'radial-gradient(ellipse 60% 40% at 80% 30%, rgba(245,181,71,0.1), transparent 60%), radial-gradient(ellipse 50% 40% at 10% 80%, rgba(159,224,196,0.1), transparent 60%)',
      }}/>

      <Container style={{ position: 'relative' }}>
        <Reveal>
          <div style={{ maxWidth: 760, marginBottom: isCompact ? 48 : 80 }}>
            <span style={{ display: 'inline-block', color: '#9FE0C4', fontSize: 12, letterSpacing: '0.1em', textTransform: 'uppercase', fontWeight: 600, marginBottom: 16 }} className="mono">
              {t('stats.kicker')}
            </span>
            <h2 style={{ fontSize: 'clamp(40px, 5vw, 64px)', lineHeight: 1.02, letterSpacing: '-0.03em', fontWeight: 800, textWrap: 'balance' }}>
              <RichText k="stats.heading" />
            </h2>
          </div>
        </Reveal>

        <div style={{ display: 'grid', gridTemplateColumns: isMobile ? 'repeat(2, 1fr)' : 'repeat(4, 1fr)', gap: isMobile ? 28 : 32, borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: 40 }}>
          {stats.map((s, i) => (
            <Reveal key={s.labelKey} delay={i * 120}>
              <div>
                <div style={{ fontSize: 'clamp(48px, 5vw, 80px)', fontWeight: 800, letterSpacing: '-0.04em', lineHeight: 1 }}>{s.n}</div>
                <div style={{ fontSize: 16, fontWeight: 600, marginTop: 16, marginBottom: 6 }}>{t(s.labelKey)}</div>
                <div style={{ fontSize: 13, color: '#8A8A92' }}>{t(s.subKey)}</div>
              </div>
            </Reveal>
          ))}
        </div>
      </Container>
    </section>
  );
}

Object.assign(window, { StatsSection });
