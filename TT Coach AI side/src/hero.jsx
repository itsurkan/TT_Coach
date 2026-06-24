// HERO — dark "coach canvas" with phone + annotated motion graphics
// Uses CSS keyframes for entry anim (reliable, no React state race)

const heroAnimCSS = `
@keyframes heroFadeUp {
  from { transform: translateY(24px); }
  to { transform: translateY(0); }
}
@keyframes heroFadeUpLg {
  from { transform: translateY(40px); }
  to { transform: translateY(0); }
}
@keyframes heroPhoneIn {
  from { transform: translateY(40px) rotate(-2deg); }
  to { transform: translateY(0) rotate(-2deg); }
}
@keyframes heroCanvasIn {
  from { transform: rotate(6deg) translateY(30px); }
  to { transform: rotate(3deg) translateY(0); }
}
@keyframes heroTipIn {
  from { transform: translateY(20px) rotate(2deg); }
  to { transform: translateY(0) rotate(2deg); }
}
@keyframes heroRibbonIn {
  from { transform: translateY(20px); }
  to { transform: translateY(0); }
}
@media (prefers-reduced-motion: no-preference) {
  .hero-anim { animation: heroFadeUp 800ms cubic-bezier(.2,.7,.2,1) forwards; }
  .hero-anim-lg { animation: heroFadeUpLg 900ms cubic-bezier(.2,.7,.2,1) forwards; }
  .hero-phone { animation: heroPhoneIn 1200ms cubic-bezier(.2,.7,.2,1) 600ms both; }
  .hero-canvas { animation: heroCanvasIn 1200ms cubic-bezier(.2,.7,.2,1) 400ms both; transform-origin: center; }
  .hero-tip { animation: heroTipIn 900ms cubic-bezier(.2,.7,.2,1) 1000ms both; }
  .hero-ribbon { animation: heroRibbonIn 900ms cubic-bezier(.2,.7,.2,1) 1200ms both; }
}
`;

const HERO_ANGLES = {
  inEar:   { pill: 'hero.pill',   lines: ['hero.h1a', 'hero.h1b', 'hero.h1c'],       sub: 'hero.sub' },
  outcome: { pill: 'hero.o.pill', lines: ['hero.o.h1a', 'hero.o.h1b', 'hero.o.h1c'], sub: 'hero.o.sub' },
  proof:   { pill: 'hero.p.pill', lines: ['hero.p.h1a', 'hero.p.h1b', 'hero.p.h1c'], sub: 'hero.p.sub' },
};

function HeroStars() {
  return (
    <div style={{ display: 'flex', gap: 1, color: '#F5B547' }}>
      {[...Array(5)].map((_, i) => (
        <svg key={i} width="14" height="14" viewBox="0 0 16 16" fill="currentColor"><path d="M8 1l2 4.2 4.6.5-3.4 3.1.9 4.6L8 11.6 3.9 13.5l.9-4.6L1.4 5.7 6 5.2z"/></svg>
      ))}
    </div>
  );
}

function RatingProof() {
  const avatars = [['#1E4DA8', 'MD'], ['#2F9D7A', 'PN'], ['#D9892C', 'CL'], ['#6B6B75', '+']];
  return (
    <div className="hero-anim" style={{
      display: 'flex', alignItems: 'center', gap: 14, marginTop: 30,
      animationDelay: '1000ms', flexWrap: 'wrap',
    }}>
      <div style={{ display: 'flex' }}>
        {avatars.map((a, i) => (
          <div key={i} style={{
            width: 34, height: 34, borderRadius: 999, background: a[0], color: '#fff',
            fontSize: 11, fontWeight: 700, display: 'grid', placeItems: 'center',
            border: '2px solid var(--bg)', marginLeft: i ? -10 : 0,
          }}>{a[1]}</div>
        ))}
      </div>
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
          <HeroStars />
          <span style={{ fontWeight: 700, fontSize: 14 }}>4.8</span>
        </div>
        <div style={{ fontSize: 12.5, color: 'var(--ink-3)' }}>{t('proof.loved')} · {t('proof.onPlay')}</div>
      </div>
    </div>
  );
}

function Hero({ variant = 'stacked', angle = 'inEar' }) {
  const { isMobile, isCompact } = useBreakpoint();
  const A = HERO_ANGLES[angle] || HERO_ANGLES.inEar;
  return (
    <section style={{
      position: 'relative',
      paddingTop: isMobile ? 104 : 140,
      paddingBottom: 80,
      background: 'var(--bg)',
      overflow: 'hidden',
    }}>
      <style>{heroAnimCSS}</style>
      <HeroGrid />

      <Container style={{ position: 'relative', zIndex: 2 }}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: isCompact ? '1fr' : '1.1fr 0.9fr',
          gap: isCompact ? 8 : 60,
          alignItems: 'center',
        }}>
          {/* LEFT: Copy */}
          <div>
            <div className="hero-anim" style={{ animationDelay: '0ms' }}>
              <Pill tone="sage" dot>{t(A.pill)}</Pill>
            </div>

            <h1 style={{
              fontSize: 'clamp(44px, 9vw, 92px)',
              lineHeight: 0.98,
              letterSpacing: '-0.035em',
              fontWeight: 800,
              marginTop: 24,
              marginBottom: 28,
              textWrap: 'balance',
            }}>
              <span className="hero-anim-lg" style={{ display: 'block', animationDelay: '120ms' }}>{t(A.lines[0])}</span>
              <span className="hero-anim-lg" style={{ display: 'block', animationDelay: '240ms' }}>{t(A.lines[1])}</span>
              <span className="hero-anim-lg" style={{
                display: 'block',
                animationDelay: '360ms',
                fontStyle: 'italic', fontWeight: 400, color: 'var(--navy)'
              }}>{t(A.lines[2])}</span>
            </h1>

            <p className="hero-anim" style={{
              fontSize: isMobile ? 17 : 20, lineHeight: 1.45, color: 'var(--ink-2)',
              maxWidth: 520, marginBottom: 40,
              animationDelay: '600ms',
            }}>
              {t(A.sub)}
            </p>

            <div className="hero-anim" style={{
              display: 'flex', gap: 14, alignItems: 'center', flexWrap: 'wrap',
              animationDelay: '800ms',
            }}>
              <Button variant="primary" size="lg">
                <Icon.Android /> {t('hero.android')}
              </Button>
              <Button variant="ghost" size="lg" style={{ cursor: 'default', opacity: 0.8 }} tabIndex={-1} aria-label="iOS — coming soon">
                <Icon.Apple /> {t('hero.ios')}
                <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.07em', padding: '2px 7px', borderRadius: 5, background: 'var(--amber-bg)', color: '#8A5A10', marginLeft: 4 }}>SOON</span>
              </Button>
            </div>

            <RatingProof />

            <div className="hero-anim" style={{
              display: 'flex', gap: 28, marginTop: 28,
              fontSize: 13, color: 'var(--ink-3)', fontWeight: 500,
              animationDelay: '1100ms', flexWrap: 'wrap',
            }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Icon.Check style={{ color: 'var(--sage-2)' }}/> {t('hero.check1')}</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Icon.Check style={{ color: 'var(--sage-2)' }}/> {t('hero.check2')}</span>
              <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}><Icon.Check style={{ color: 'var(--sage-2)' }}/> {t('hero.check3')}</span>
            </div>
          </div>

          {/* RIGHT: Annotated phone stack */}
          {isCompact
            ? <HeroVisualMobile />
            : (variant === 'minimal' ? <HeroVisualMinimal /> : <HeroVisual />)}
        </div>

        <div className="hero-anim" style={{
          marginTop: isMobile ? 40 : 80, display: 'flex', gap: 14, alignItems: 'center',
          color: 'var(--ink-3)', fontSize: 12, letterSpacing: '0.08em',
          textTransform: 'uppercase', fontWeight: 500,
          animationDelay: '1400ms',
        }}>
          <span className="mono">↓ 01</span>
          <span>{t('hero.scroll')}</span>
        </div>
      </Container>
    </section>
  );
}

function HeroGrid() {
  return (
    <div aria-hidden style={{
      position: 'absolute', inset: 0,
      background: `
        radial-gradient(ellipse 80% 50% at 70% 20%, color-mix(in oklab, var(--navy) 10%, transparent), transparent 60%),
        radial-gradient(ellipse 60% 40% at 10% 90%, color-mix(in oklab, var(--sage-2) 8%, transparent), transparent 60%)
      `,
      pointerEvents: 'none',
    }}>
      <svg width="100%" height="100%" style={{ opacity: 0.35 }}>
        <defs>
          <pattern id="hgrid" width="60" height="60" patternUnits="userSpaceOnUse">
            <path d="M60 0H0V60" fill="none" stroke="var(--line)" strokeWidth="1" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#hgrid)" />
      </svg>
    </div>
  );
}

function HeroVisualMobile() {
  return (
    <div className="hero-phone" style={{
      width: 'min(340px, 86vw)',
      margin: '8px auto 0',
      filter: 'drop-shadow(0 24px 44px rgba(26,26,31,0.22))',
    }}>
      <PhoneLive />
    </div>
  );
}

function HeroVisual() {
  return (
    <div style={{ position: 'relative', height: 640 }}>
      <div className="hero-canvas" style={{
        position: 'absolute',
        top: 40, right: -20, width: 440, height: 540,
        background: 'var(--canvas)', borderRadius: 32,
        boxShadow: '0 40px 80px -20px rgba(26,26,31,0.25), 0 0 0 1px rgba(255,255,255,0.04) inset',
        overflow: 'hidden',
      }}>
        <SkeletonCanvas />
      </div>

      <div className="hero-phone" style={{
        position: 'absolute',
        left: -10, top: 0,
        width: 340,
        filter: 'drop-shadow(0 30px 50px rgba(26,26,31,0.22))',
      }}>
        <PhoneLive />
      </div>

      <FloatingTip />
      <StrokeRibbon />
    </div>
  );
}

function HeroVisualMinimal() {
  return (
    <div style={{ position: 'relative', height: 640 }}>
      <div className="hero-phone" style={{
        position: 'absolute',
        left: '50%', top: 10,
        width: 360,
        marginLeft: -180,
        filter: 'drop-shadow(0 30px 50px rgba(26,26,31,0.22))',
      }}>
        <PhoneLive />
      </div>
      <FloatingTip />
    </div>
  );
}

function SkeletonCanvas() {
  return (
    <div style={{ position: 'relative', width: '100%', height: '100%', padding: 30 }}>
      <div style={{ position: 'absolute', top: 22, left: 22, color: '#9FE0C4', fontSize: 10, letterSpacing: '0.12em', textTransform: 'uppercase', fontFamily: 'JetBrains Mono, monospace' }}>
        RTMPose 2D · 17 kpts
      </div>
      <div style={{ position: 'absolute', top: 22, right: 22, color: '#F5B547', fontSize: 10, letterSpacing: '0.12em', textTransform: 'uppercase', fontFamily: 'JetBrains Mono, monospace' }}>
        Δ Hand +{handDriftPct()}%
      </div>
      <div style={{ position: 'absolute', bottom: 22, left: 22, color: '#6B6B75', fontSize: 10, letterSpacing: '0.12em', fontFamily: 'JetBrains Mono, monospace' }}>
        {POSE_VIDEO.name} · frame {POSE_VIDEO.frame} · {POSE_VIDEO.tMs} ms
      </div>

      <PoseFigure />
    </div>
  );
}

function FloatingTip() {
  return (
    <div className="hero-tip" style={{
      position: 'absolute',
      right: -60, top: 340,
      width: 260,
      background: 'var(--bg)',
      border: '1px solid var(--line)',
      borderRadius: 16,
      padding: '14px 16px',
      boxShadow: '0 20px 40px -10px rgba(26,26,31,0.15)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
        <div style={{
          width: 28, height: 28, borderRadius: 999,
          background: 'var(--amber-bg)',
          display: 'grid', placeItems: 'center',
          color: '#8A5A10',
        }}>
          <Icon.Mic />
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 11, color: 'var(--amber)', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase' }}>{t('coach.speaking')}</div>
        </div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)' }} className="mono">+ 0.4s</div>
      </div>
      <div style={{ fontSize: 14, lineHeight: 1.4, color: 'var(--ink)', fontWeight: 500 }}>
        {t('coach.tip')}
      </div>
      <PulseLine />
    </div>
  );
}

function PulseLine() {
  return (
    <div style={{ display: 'flex', gap: 3, alignItems: 'end', height: 12, marginTop: 10 }}>
      {[...Array(18)].map((_, i) => (
        <div key={i} style={{
          width: 3, borderRadius: 2,
          background: 'var(--amber)',
          height: `${4 + Math.abs(Math.sin(i * 0.7)) * 10}px`,
          animation: `pulse 1.2s ease-in-out ${i * 60}ms infinite`,
        }}/>
      ))}
      <style>{`@keyframes pulse { 0%,100%{opacity:.35;transform:scaleY(.6)} 50%{opacity:1;transform:scaleY(1)} }`}</style>
    </div>
  );
}

function StrokeRibbon() {
  const strokes = ['good','good','off','good','good','good','good','off','good','focus'];
  const colors = { good: 'var(--sage)', off: '#F5B547', focus: '#1E4DA8' };
  return (
    <div className="hero-ribbon" style={{
      position: 'absolute', left: 10, bottom: 10,
      background: 'var(--bg)',
      border: '1px solid var(--line)',
      borderRadius: 14,
      padding: '12px 14px',
      boxShadow: '0 20px 40px -10px rgba(26,26,31,0.15)',
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, fontSize: 11, color: 'var(--ink-3)', gap: 20 }}>
        <span style={{ fontWeight: 600, color: 'var(--ink)' }}>{t('strokes.last10')}</span>
        <span>8 {t('word.good')} · 2 {t('word.off')}</span>
      </div>
      <div style={{ display: 'flex', gap: 4 }}>
        {strokes.map((s, i) => (
          <div key={i} style={{
            width: 22, height: 26, borderRadius: 4,
            background: colors[s] || colors.good,
            border: s === 'focus' ? '2px solid #1E4DA8' : 'none',
            opacity: s === 'focus' ? 0.3 : 1,
          }} />
        ))}
      </div>
    </div>
  );
}

Object.assign(window, { Hero });
