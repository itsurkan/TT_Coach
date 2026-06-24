// SOCIAL PROOF — thin credibility band + testimonials.
// Numbers and quotes are honest PLACEHOLDERS; swap for real data before launch.

function SocialBand() {
  const { isMobile } = useBreakpoint();
  const items = [
    { n: '4.8\u2605', l: t('social.c1.l') },
    { n: '2,400+', l: t('social.c2.l') },
    { n: '1.9M', l: t('social.c3.l') },
    { n: '0ms', l: t('social.c4.l') },
  ];
  return (
    <section style={{ background: 'var(--bg)', borderTop: '1px solid var(--line)', borderBottom: '1px solid var(--line)' }}>
      <Container style={{
        display: 'grid',
        gridTemplateColumns: isMobile ? 'repeat(2, 1fr)' : 'repeat(4, 1fr)',
        gap: isMobile ? 24 : 32,
        padding: isMobile ? '26px 20px' : '30px 32px',
      }}>
        {items.map((it, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'baseline', gap: 10, justifyContent: isMobile ? 'flex-start' : 'center' }}>
            <span style={{ fontSize: isMobile ? 24 : 28, fontWeight: 800, letterSpacing: '-0.03em', lineHeight: 1 }}>{it.n}</span>
            <span style={{ fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.2 }}>{it.l}</span>
          </div>
        ))}
      </Container>
    </section>
  );
}

function QuoteStars() {
  return (
    <div style={{ display: 'flex', gap: 2, color: '#F5B547' }}>
      {[...Array(5)].map((_, i) => (
        <svg key={i} width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M8 1l2 4.2 4.6.5-3.4 3.1.9 4.6L8 11.6 3.9 13.5l.9-4.6L1.4 5.7 6 5.2z"/></svg>
      ))}
    </div>
  );
}

function QuoteCard({ c }) {
  const av = { navy: '#1E4DA8', sage: '#2F9D7A', amber: '#D9892C' }[c.tone];
  const initials = t(c.name).split(' ').map(s => s[0]).join('').slice(0, 2);
  return (
    <div style={{
      background: 'var(--bg-2)', border: '1px solid var(--line)', borderRadius: 20,
      padding: 28, display: 'flex', flexDirection: 'column', gap: 20, minHeight: 300,
    }}>
      <QuoteStars />
      <p style={{ fontSize: 17, lineHeight: 1.5, color: 'var(--ink)', fontWeight: 500, flex: 1, textWrap: 'pretty' }}>
        {t(c.q)}
      </p>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{
          width: 40, height: 40, borderRadius: 999, background: av, color: '#fff',
          fontWeight: 700, fontSize: 14, display: 'grid', placeItems: 'center', flexShrink: 0,
        }}>{initials}</div>
        <div>
          <div style={{ fontWeight: 600, fontSize: 14 }}>{t(c.name)}</div>
          <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{t(c.role)}</div>
        </div>
      </div>
    </div>
  );
}

function Testimonials() {
  const { isMobile, isCompact } = useBreakpoint();
  const quotes = [
    { q: 'voices.q1', name: 'voices.q1.name', role: 'voices.q1.role', tone: 'navy' },
    { q: 'voices.q2', name: 'voices.q2.name', role: 'voices.q2.role', tone: 'sage' },
    { q: 'voices.q3', name: 'voices.q3.name', role: 'voices.q3.role', tone: 'amber' },
  ];
  return (
    <section id="voices" style={{ padding: isCompact ? '88px 0' : '140px 0', background: 'var(--bg)' }}>
      <Container>
        <Reveal>
          <div style={{ maxWidth: 680, marginBottom: isCompact ? 48 : 72 }}>
            <span className="mono" style={{ color: 'var(--sage-2)', fontSize: 12, letterSpacing: '0.1em', textTransform: 'uppercase', fontWeight: 600 }}>
              {t('voices.kicker')}
            </span>
            <h2 style={{ fontSize: 'clamp(34px, 4.5vw, 56px)', lineHeight: 1.04, letterSpacing: '-0.03em', fontWeight: 800, marginTop: 16, textWrap: 'balance' }}>
              {t('voices.heading')}
            </h2>
          </div>
        </Reveal>
        <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : (isCompact ? 'repeat(2, 1fr)' : 'repeat(3, 1fr)'), gap: 20 }}>
          {quotes.map((c, i) => (
            <Reveal key={c.q} delay={i * 120}>
              <QuoteCard c={c} />
            </Reveal>
          ))}
        </div>
      </Container>
    </section>
  );
}

Object.assign(window, { SocialBand, Testimonials });
