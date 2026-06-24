function HowItWorks() {
  const { isCompact } = useBreakpoint();
  const steps = [
    { n: '01', tagKey: 'how.s1.tag', titleKey: 'how.s1.title', bodyKey: 'how.s1.body', visual: <SetupVisual /> },
    { n: '02', tagKey: 'how.s2.tag', titleKey: 'how.s2.title', bodyKey: 'how.s2.body', visual: <PlayVisual /> },
    { n: '03', tagKey: 'how.s3.tag', titleKey: 'how.s3.title', bodyKey: 'how.s3.body', visual: <ListenVisual /> },
    { n: '04', tagKey: 'how.s4.tag', titleKey: 'how.s4.title', bodyKey: 'how.s4.body', visual: <ReviewVisual /> },
  ];

  return (
    <section id="how" style={{ padding: isCompact ? '88px 0 56px' : '140px 0 80px', background: 'var(--bg)', position: 'relative' }}>
      <Container>
        <Reveal>
          <div style={{ maxWidth: 720, marginBottom: isCompact ? 48 : 80 }}>
            <Pill tone="ink">{t('nav.how')}</Pill>
            <h2 style={{
              fontSize: 'clamp(40px, 5vw, 64px)', lineHeight: 1, letterSpacing: '-0.03em',
              fontWeight: 800, marginTop: 18, textWrap: 'balance',
            }}>
              <RichText k="how.heading" />
            </h2>
          </div>
        </Reveal>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {steps.map((s, i) => <StepRow key={i} step={s} index={i} />)}
        </div>
      </Container>
    </section>
  );
}

function StepRow({ step, index }) {
  const [ref, inView] = useInView({ threshold: 0.2 });
  const { isCompact } = useBreakpoint();
  const flipped = index % 2 === 1;
  return (
    <div ref={ref} style={{
      display: 'grid',
      gridTemplateColumns: isCompact ? '1fr' : (flipped ? '1fr 1.1fr' : '1.1fr 1fr'),
      gap: isCompact ? 24 : 48,
      alignItems: 'center',
      padding: isCompact ? '28px 0' : '40px 0',
      borderTop: '1px solid var(--line)',
      opacity: inView ? 1 : 0,
      transform: inView ? 'translateY(0)' : 'translateY(30px)',
      transition: 'all 800ms cubic-bezier(.2,.7,.2,1)',
    }}>
      <div style={{ order: isCompact ? 1 : (flipped ? 2 : 1) }}>
        <div className="mono" style={{ color: 'var(--ink-3)', fontSize: 12, marginBottom: 12 }}>
          {t('how.stepLabel')} {step.n} · {t(step.tagKey).toUpperCase()}
        </div>
        <h3 style={{
          fontSize: 'clamp(26px, 5.5vw, 40px)', lineHeight: 1.05, letterSpacing: '-0.02em',
          fontWeight: 700, marginBottom: 18, maxWidth: 520, textWrap: 'balance',
        }}>
          {t(step.titleKey)}
        </h3>
        <p style={{ fontSize: 17, lineHeight: 1.55, color: 'var(--ink-2)', maxWidth: 480 }}>
          {t(step.bodyKey)}
        </p>
      </div>
      <div style={{ order: isCompact ? 2 : (flipped ? 1 : 2) }}>
        {step.visual}
      </div>
    </div>
  );
}

// Step visuals
function SetupVisual() {
  return (
    <div style={{
      aspectRatio: '5/4',
      background: 'var(--bg-2)',
      borderRadius: 20,
      position: 'relative',
      overflow: 'hidden',
      border: '1px solid var(--line)',
    }}>
      {/* table */}
      <svg viewBox="0 0 500 400" style={{ width: '100%', height: '100%', position: 'absolute', inset: 0 }}>
        {/* perspective table */}
        <path d="M80 280 L420 280 L380 200 L120 200 Z" fill="var(--navy)" opacity="0.85"/>
        <path d="M250 200 L250 280" stroke="rgba(255,255,255,0.4)" strokeWidth="1.5" strokeDasharray="4 4"/>
        <path d="M120 200 L380 200" stroke="rgba(255,255,255,0.6)" strokeWidth="2"/>
        {/* net */}
        <rect x="150" y="178" width="200" height="24" fill="rgba(255,255,255,0.15)" stroke="rgba(255,255,255,0.5)"/>

        {/* phone on a stand */}
        <g transform="translate(410 220)">
          <rect x="0" y="0" width="24" height="42" rx="4" fill="#1A1A1F"/>
          <rect x="2" y="4" width="20" height="34" rx="2" fill="#9FE0C4" opacity="0.8"/>
          <path d="M12 42 L12 60 M4 60 L20 60" stroke="#1A1A1F" strokeWidth="2"/>
        </g>
        {/* camera fov rays */}
        <g opacity="0.35">
          <path d="M420 240 L200 100" stroke="var(--navy)" strokeDasharray="3 4"/>
          <path d="M420 240 L120 340" stroke="var(--navy)" strokeDasharray="3 4"/>
        </g>

        {/* ball */}
        <circle cx="220" cy="180" r="6" fill="#F5B547"/>
        <circle cx="220" cy="180" r="10" fill="none" stroke="#F5B547" opacity="0.4"/>
      </svg>

      <div style={{
        position: 'absolute', top: 20, left: 20,
        background: 'var(--bg)', border: '1px solid var(--line)',
        padding: '8px 12px', borderRadius: 999, fontSize: 12,
        display: 'flex', alignItems: 'center', gap: 8, fontWeight: 500,
      }}>
        <span style={{ width: 6, height: 6, borderRadius: 999, background: 'var(--sage-2)', animation: 'blink 2s infinite' }}/>
        {t('how.setup.locked')} · 2.8s
      </div>
      <style>{`@keyframes blink { 0%,100%{opacity:1} 50%{opacity:.3} }`}</style>
    </div>
  );
}

function PlayVisual() {
  return (
    <div style={{
      aspectRatio: '5/4',
      background: 'var(--canvas)',
      borderRadius: 20,
      position: 'relative',
      overflow: 'hidden',
      padding: 24,
    }}>
      <div className="mono" style={{ color: '#9FE0C4', fontSize: 10, letterSpacing: '0.12em', marginBottom: 8 }}>{t('how.play.refVsYou')}</div>
      <svg viewBox="0 0 500 320" style={{ width: '100%', height: 'calc(100% - 30px)' }}>
        {/* reference arc (sage, dashed) */}
        <path d="M80 260 Q 200 40 420 220" stroke="#9FE0C4" strokeWidth="2.5" strokeDasharray="6 6" fill="none" opacity="0.6"/>
        {/* your arc (amber) */}
        <path d="M80 260 Q 180 80 420 200" stroke="#F5B547" strokeWidth="3" fill="none"/>

        {/* sample points */}
        {[0.15, 0.3, 0.5, 0.7, 0.85].map((t, i) => {
          // approximate along "your" quadratic
          const x = 80 + (420 - 80) * t;
          const y = 260 + (200 - 260) * t - 180 * t * (1 - t) * 4;
          const ref_y = 260 + (220 - 260) * t - 220 * t * (1 - t) * 4;
          return (
            <g key={i}>
              <line x1={x} y1={y} x2={x} y2={ref_y} stroke="#F5B547" strokeWidth="1" opacity="0.5"/>
              <circle cx={x} cy={y} r="4" fill="#F5B547"/>
              <circle cx={x} cy={ref_y} r="4" fill="#9FE0C4" opacity="0.7"/>
            </g>
          );
        })}

        {/* axis labels */}
        <text x="80" y="295" fontSize="10" fill="#6B6B75" fontFamily="JetBrains Mono">t=0</text>
        <text x="400" y="295" fontSize="10" fill="#6B6B75" fontFamily="JetBrains Mono">t=1.2s</text>
      </svg>

      <div style={{
        position: 'absolute', bottom: 24, right: 24,
        display: 'flex', gap: 14, fontSize: 11,
      }}>
        <span style={{ color: '#9FE0C4', display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ width: 10, height: 2, background: '#9FE0C4' }}/>{t('how.play.reference')}
        </span>
        <span style={{ color: '#F5B547', display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ width: 10, height: 2, background: '#F5B547' }}/>{t('how.play.you')}
        </span>
      </div>
    </div>
  );
}

function ListenVisual() {
  const [val, setVal] = useState(8);
  return (
    <div style={{
      aspectRatio: '5/4',
      background: 'var(--bg-2)',
      borderRadius: 20,
      border: '1px solid var(--line)',
      padding: 28,
      display: 'flex', flexDirection: 'column', justifyContent: 'center',
    }}>
      <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 8 }}>{t('how.listen.title')}</div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 14 }}>
        <span style={{ fontSize: 18, fontWeight: 600 }}>{t('how.listen.tipEvery')}</span>
        <span style={{ fontSize: 28, fontWeight: 700, color: 'var(--navy)' }}>{val} <span style={{ fontSize: 16, color: 'var(--ink-3)', fontWeight: 500 }}>{t('how.listen.sec')}</span></span>
      </div>
      <input type="range" min="5" max="15" value={val} onChange={e => setVal(+e.target.value)}
        style={{ width: '100%', accentColor: 'var(--navy)' }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'var(--ink-3)', marginTop: 8 }}>
        <span>5{t('how.listen.sec')} · {t('how.listen.chatty')}</span><span>10{t('how.listen.sec')}</span><span>15{t('how.listen.sec')} · {t('how.listen.quiet')}</span>
      </div>

      <div style={{ marginTop: 24, display: 'flex', gap: 8 }}>
        {['how.listen.calm', 'how.listen.energetic', 'how.listen.textOnly'].map((vk, i) => (
          <div key={vk} style={{
            padding: '10px 16px', borderRadius: 10,
            border: `1.5px solid ${i === 0 ? 'var(--navy)' : 'var(--line)'}`,
            background: 'var(--bg)',
            fontSize: 13, fontWeight: 500,
            color: i === 0 ? 'var(--navy)' : 'var(--ink-2)',
          }}>{t(vk)}</div>
        ))}
      </div>
    </div>
  );
}

function ReviewVisual() {
  return (
    <div style={{
      aspectRatio: '5/4',
      background: 'var(--bg-2)',
      borderRadius: 20,
      border: '1px solid var(--line)',
      padding: 28,
      display: 'flex', flexDirection: 'column', gap: 16,
    }}>
      <div style={{ background: 'var(--sage-bg)', borderRadius: 12, padding: '14px 16px', display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <div>
          <div style={{ fontSize: 12, color: 'var(--sage-ink)', fontWeight: 600 }}>{t('how.review.matchRef')}</div>
          <div style={{ fontSize: 34, fontWeight: 800, color: 'var(--sage-ink)', lineHeight: 1 }}>84<span style={{ fontSize: 22 }}>%</span></div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 11, color: 'var(--sage-ink)', opacity: 0.7 }}>{t('how.review.vsLast')}</div>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--sage-ink)' }}>▲ +6%</div>
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {[
          { label: t('how.review.elbow'), val: '+12°', pct: 62, color: '#F5B547' },
          { label: t('how.review.hip'), val: '+3°', pct: 92, color: 'var(--sage-2)' },
          { label: t('how.review.knee'), val: '-8°', pct: 72, color: '#F5B547' },
        ].map(r => (
          <div key={r.label}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 4 }}>
              <span style={{ fontWeight: 500 }}>{r.label}</span>
              <span style={{ color: 'var(--ink-3)' }}>{r.val}</span>
            </div>
            <div style={{ height: 6, background: 'var(--line)', borderRadius: 4, overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${r.pct}%`, background: r.color, borderRadius: 4 }}/>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

Object.assign(window, { HowItWorks });
