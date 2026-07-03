// Tweaks panel — wired to postMessage protocol
function TweaksPanel({ tweaks, setTweaks, setTheme }) {
  const [active, setActive] = useState(false);

  useEffect(() => {
    const onMsg = (e) => {
      if (!e.data || typeof e.data !== 'object') return;
      if (e.data.type === '__activate_edit_mode') setActive(true);
      if (e.data.type === '__deactivate_edit_mode') setActive(false);
    };
    window.addEventListener('message', onMsg);
    // announce AFTER listener installed
    window.parent.postMessage({ type: '__edit_mode_available' }, '*');
    return () => window.removeEventListener('message', onMsg);
  }, []);

  const update = (patch) => {
    setTweaks(t => ({ ...t, ...patch }));
    window.parent.postMessage({ type: '__edit_mode_set_keys', edits: patch }, '*');
  };

  if (!active) return null;

  return (
    <div style={{
      position: 'fixed', bottom: 20, right: 20, zIndex: 100,
      width: 260,
      background: 'var(--bg)',
      border: '1px solid var(--line)',
      borderRadius: 16,
      padding: 18,
      boxShadow: '0 20px 50px -10px rgba(0,0,0,0.2)',
      fontFamily: 'Inter, sans-serif',
    }}>
      <div style={{ fontSize: 11, letterSpacing: '0.1em', textTransform: 'uppercase', color: 'var(--ink-3)', fontWeight: 600, marginBottom: 12 }}>
        Tweaks
      </div>

      <Row label="Palette">
        <SegGroup
          value={tweaks.palette}
          onChange={v => update({ palette: v })}
          options={[['navy','Navy'],['amber','Amber'],['sage','Sage']]}
        />
      </Row>

      <Row label="Theme">
        <SegGroup
          value={tweaks.theme}
          onChange={v => (setTheme ? setTheme(v) : update({ theme: v }))}
          options={[['system','System'],['light','Light'],['dark','Dark']]}
        />
      </Row>

      <Row label="Hero">
        <SegGroup
          value={tweaks.heroVariant}
          onChange={v => update({ heroVariant: v })}
          options={[['stacked','Canvas'],['minimal','Minimal']]}
        />
      </Row>

      <Row label="Message">
        <SegGroup
          value={tweaks.heroAngle}
          onChange={v => update({ heroAngle: v })}
          options={[['inEar','In-ear'],['outcome','Outcome'],['proof','Proof']]}
        />
      </Row>
    </div>
  );
}

function Row({ label, children }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <div style={{ fontSize: 12, color: 'var(--ink-2)', marginBottom: 6, fontWeight: 500 }}>{label}</div>
      {children}
    </div>
  );
}

function SegGroup({ value, onChange, options }) {
  return (
    <div style={{ display: 'flex', gap: 4, background: 'var(--bg-2)', padding: 3, borderRadius: 8 }}>
      {options.map(([v, l]) => (
        <button key={v} onClick={() => onChange(v)} style={{
          flex: 1, padding: '6px 4px', borderRadius: 6,
          border: 'none',
          background: value === v ? 'var(--bg)' : 'transparent',
          boxShadow: value === v ? '0 1px 2px rgba(0,0,0,0.08)' : 'none',
          color: 'var(--ink)', fontSize: 12, fontWeight: value === v ? 600 : 500,
          cursor: 'pointer', fontFamily: 'inherit',
        }}>{l}</button>
      ))}
    </div>
  );
}

Object.assign(window, { TweaksPanel });
