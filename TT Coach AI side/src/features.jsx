function Features() {
  const { isMobile, isCompact } = useBreakpoint();
  const features = [
    { titleKey: 'feat.f1.title', bodyKey: 'feat.f1.body', chipKey: 'feat.f1.chip', chipTone: 'amber', visual: <TechViz /> },
    { titleKey: 'feat.f2.title', bodyKey: 'feat.f2.body', chipKey: 'feat.f2.chip', chipTone: 'navy', visual: <DrillViz /> },
    { titleKey: 'feat.f3.title', bodyKey: 'feat.f3.body', chipKey: 'feat.f3.chip', chipTone: 'sage', visual: <ReviewViz /> },
  ];

  return (
    <section id="features" style={{ padding: isCompact ? '88px 0 64px' : '140px 0 100px', background: 'var(--bg-2)', position: 'relative' }}>
      <Container>
        <Reveal>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'end', marginBottom: 56, flexWrap: 'wrap', gap: 24 }}>
            <div style={{ maxWidth: 560 }}>
              <Pill tone="ink">{t('feat.pill')}</Pill>
              <h2 style={{ fontSize: 'clamp(40px, 5vw, 60px)', lineHeight: 1.02, letterSpacing: '-0.03em', fontWeight: 800, marginTop: 18, textWrap: 'balance' }}>
                {t('feat.heading')}
              </h2>
            </div>
            <p style={{ maxWidth: 360, fontSize: 15, color: 'var(--ink-2)', lineHeight: 1.5 }}>
              {t('feat.intro')}
            </p>
          </div>
        </Reveal>

        <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : (isCompact ? 'repeat(2, 1fr)' : 'repeat(3, 1fr)'), gap: 20 }}>
          {features.map((f, i) => (
            <Reveal key={f.titleKey} delay={i * 120}>
              <FeatureCard f={f} />
            </Reveal>
          ))}
        </div>
      </Container>
    </section>
  );
}

function FeatureCard({ f }) {
  return (
    <div style={{
      background: 'var(--bg)',
      border: '1px solid var(--line)',
      borderRadius: 20,
      padding: 24,
      display: 'flex', flexDirection: 'column', gap: 18,
      minHeight: 480,
    }}>
      <div style={{ aspectRatio: '4/3', width: '100%' }}>
        {f.visual}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <Pill tone={f.chipTone}>{t(f.chipKey)}</Pill>
      </div>
      <h3 style={{ fontSize: 22, fontWeight: 700, letterSpacing: '-0.01em' }}>{t(f.titleKey)}</h3>
      <p style={{ fontSize: 14, lineHeight: 1.55, color: 'var(--ink-2)' }}>{t(f.bodyKey)}</p>
    </div>
  );
}

function TechViz() {
  return (
    <div style={{ width: '100%', height: '100%', background: 'var(--canvas)', borderRadius: 14, position: 'relative', overflow: 'hidden', padding: 6 }}>
      <PoseFigure compact showFlag={false} />
      <div style={{ position: 'absolute', top: 12, right: 12, color: '#F5B547', fontSize: 9, letterSpacing: '0.1em', fontFamily: 'JetBrains Mono, monospace', textTransform: 'uppercase' }}>
        Δ Hand +{handDriftPct()}%
      </div>
      <div style={{ position: 'absolute', bottom: 10, left: 12, color: '#6B6B75', fontSize: 9, fontFamily: 'JetBrains Mono, monospace' }}>
        RTMPose 2D · 17 kpts · 120fps
      </div>
    </div>
  );
}

function DrillViz() {
  return (
    <div style={{ width: '100%', height: '100%', background: 'var(--bg-2)', borderRadius: 14, padding: 16, display: 'flex', flexDirection: 'column', gap: 8, border: '1px solid var(--line)' }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', fontFamily: 'JetBrains Mono' }}>{t('feat.drill.new')}</div>
      <div style={{ fontSize: 14, fontWeight: 600 }}>{t('feat.drill.name')}</div>
      {[
        { label: t('feat.drill.stroke'), val: t('feat.drill.strokeVal') },
        { label: t('feat.drill.reference'), val: t('feat.drill.refVal') },
        { label: t('feat.drill.strictness'), val: '●●●○○' },
      ].map(r => (
        <div key={r.label} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 10px', background: 'var(--bg)', borderRadius: 8, fontSize: 12 }}>
          <span style={{ color: 'var(--ink-3)' }}>{r.label}</span>
          <span style={{ fontWeight: 500 }}>{r.val}</span>
        </div>
      ))}
      <div style={{ padding: '8px 12px', background: 'var(--navy)', color: '#fff', borderRadius: 8, fontSize: 12, fontWeight: 600, textAlign: 'center', marginTop: 4 }}>
        {t('feat.drill.start')}
      </div>
    </div>
  );
}

function ReviewViz() {
  return (
    <div style={{ width: '100%', height: '100%', background: 'var(--bg-2)', borderRadius: 14, padding: 16, border: '1px solid var(--line)', display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--ink-3)' }}>
        <span>{t('feat.rev.session')}</span>
        <span className="mono">42:18</span>
      </div>
      {/* Timeline of strokes */}
      <div style={{ display: 'flex', gap: 1, height: 40, alignItems: 'end' }}>
        {Array.from({ length: 40 }).map((_, i) => {
          const h = 20 + Math.abs(Math.sin(i * 0.8)) * 20;
          const bad = i === 12 || i === 27 || i === 33;
          return <div key={i} style={{ flex: 1, height: `${h}px`, background: bad ? '#F5B547' : 'var(--sage)', borderRadius: 1 }}/>;
        })}
      </div>
      <div style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, color: 'var(--ink-2)' }}>
        <Icon.Play style={{ color: 'var(--navy)' }}/>
        <span>{t('feat.rev.stroke27')}</span>
      </div>
      <div style={{ background: 'var(--sage-bg)', padding: '8px 10px', borderRadius: 8, fontSize: 12, color: 'var(--sage-ink)' }}>
        <strong>{t('feat.rev.working')}</strong> {t('feat.rev.workingVal')}
      </div>
      <div style={{ background: 'var(--amber-bg)', padding: '8px 10px', borderRadius: 8, fontSize: 12, color: '#8A5A10' }}>
        <strong>{t('feat.rev.next')}</strong> {t('feat.rev.nextVal')}
      </div>
    </div>
  );
}

Object.assign(window, { Features });
