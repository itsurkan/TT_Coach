// PhoneLive — live-rendered phone mock of the in-session screen.
// Replaces the old static assets/phone-live.png so the pose, flag and
// coach copy always stay in sync with PoseFigure / elbowFlareDelta.

function PhoneChip({ label, value, labelColor, align = 'left' }) {
  return (
    <div style={{
      background: 'rgba(40,40,47,0.92)',
      borderRadius: 10,
      padding: '6px 10px',
      display: 'flex', flexDirection: 'column', gap: 1,
      alignItems: align === 'right' ? 'flex-end' : 'flex-start',
    }}>
      <span style={{ fontSize: 9, fontWeight: 600, color: labelColor, letterSpacing: '0.04em' }}>{label}</span>
      <span style={{ fontSize: 15, fontWeight: 800, color: '#F2EEE6', letterSpacing: '-0.01em' }}>{value}</span>
    </div>
  );
}

function PhoneStrokeRow() {
  const strokes = ['good', 'good', 'off', 'good', 'good', 'good', 'good', 'off', 'good', 'focus'];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--ink)' }}>{t('strokes.last10')}</span>
        <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          <span style={{ color: 'var(--sage-2)', fontWeight: 700 }}>8</span> {t('word.good')} · <span style={{ color: '#C77B12', fontWeight: 700 }}>2</span> {t('word.off')}
        </span>
      </div>
      <div style={{ display: 'flex', gap: 4 }}>
        {strokes.map((s, i) => (
          <div key={i} style={{
            flex: 1, height: 22, borderRadius: 5,
            background: s === 'off' ? 'var(--amber)' : s === 'focus' ? 'var(--sage-bg)' : 'var(--sage)',
            border: s === 'focus' ? '2px solid var(--navy)' : 'none',
          }}></div>
        ))}
      </div>
    </div>
  );
}

function PhoneTabBar() {
  const tabs = [
    { key: 'phone.tab.train', active: true },
    { key: 'phone.tab.drills' },
    { key: 'phone.tab.stats' },
    { key: 'phone.tab.profile' },
  ];
  return (
    <div style={{
      display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)',
      borderTop: '1px solid var(--line)',
      padding: '9px 0 6px',
    }}>
      {tabs.map((tb) => (
        <div key={tb.key} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
          <div style={{
            width: 17, height: 17, borderRadius: 5,
            background: tb.active ? 'var(--navy)' : '#9A9AA2',
          }}></div>
          <span style={{
            fontSize: 10, fontWeight: tb.active ? 700 : 500,
            color: tb.active ? 'var(--ink)' : 'var(--ink-3)',
          }}>{t(tb.key)}</span>
        </div>
      ))}
    </div>
  );
}

function PhoneLive() {
  return (
    <div style={{
      borderRadius: 42,
      background: '#FFFFFF',
      border: '1px solid var(--line)',
      padding: 9,
    }}>
      <div style={{
        borderRadius: 34,
        background: 'var(--bg)',
        padding: '12px 14px 6px',
        overflow: 'hidden',
        display: 'flex', flexDirection: 'column', gap: 10,
      }}>
        {/* status bar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 4px' }}>
          <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--ink)' }}>9:41</span>
          <span style={{ display: 'flex', gap: 3 }}>
            {[0, 1, 2].map((i) => <span key={i} style={{ width: 4, height: 4, borderRadius: 99, background: 'var(--ink-3)' }}></span>)}
          </span>
        </div>

        {/* header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 4px' }}>
          <div>
            <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 2 }}>{t('phone.live')}</div>
            <div style={{ fontSize: 23, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--ink)' }}>{t('phone.keepGoing')}</div>
          </div>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 6,
            background: '#F8E3DC', color: '#B5443A',
            borderRadius: 999, padding: '5px 11px',
            fontSize: 12, fontWeight: 700, fontFamily: 'JetBrains Mono, monospace',
          }}>
            <span style={{ width: 7, height: 7, borderRadius: 99, background: '#D14E42' }}></span>
            02:14
          </div>
        </div>

        {/* pose canvas — same live figure as the hero board */}
        <div style={{
          position: 'relative',
          background: 'var(--canvas)',
          borderRadius: 18,
          height: 296,
          overflow: 'hidden',
        }}>
          <div style={{ position: 'absolute', inset: '8px 0 14px' }}>
            <PoseFigure />
          </div>
          <div style={{ position: 'absolute', top: 10, left: 10 }}>
            <PhoneChip label={t('phone.strokes')} value="23 / 50" labelColor="#9FE0C4" />
          </div>
          <div style={{ position: 'absolute', top: 10, right: 10 }}>
            <PhoneChip label={t('phone.match')} value="84%" labelColor="#B7A8F0" align="right" />
          </div>
          <div style={{
            position: 'absolute', bottom: 8, left: 0, right: 0,
            textAlign: 'center', fontSize: 9, color: '#6B6B75',
            fontFamily: 'JetBrains Mono, monospace', letterSpacing: '0.06em',
          }}>
            {t('phone.rep')}
          </div>
        </div>

        {/* coach card — copy mirrors the live correction */}
        <div style={{
          background: 'var(--amber-bg)',
          borderRadius: 14,
          padding: '10px 12px',
          display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <div style={{
            width: 30, height: 30, borderRadius: 999, flexShrink: 0,
            border: '3px solid var(--amber)', borderRightColor: 'rgba(245,181,71,0.3)',
            display: 'grid', placeItems: 'center', color: '#8A5A10', marginTop: 2,
          }}>
            <Icon.Mic />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 2 }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: '#8A5A10' }}>{t('coach.speaking')}</span>
              <span style={{ fontSize: 10, color: '#A1742D' }}>{t('phone.nextIn')}</span>
            </div>
            <div style={{ fontSize: 13, lineHeight: 1.35, fontWeight: 500, color: '#4A3413' }}>
              {t('coach.tip')}
            </div>
          </div>
        </div>

        <PhoneStrokeRow />
        <PhoneTabBar />
      </div>
    </div>
  );
}

Object.assign(window, { PhoneLive });
