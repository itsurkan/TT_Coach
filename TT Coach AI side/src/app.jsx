function App() {
  const [tweaks, setTweaks] = useState(() => {
    let saved = null;
    try { saved = localStorage.getItem('ttc_theme'); } catch (e) {}
    return { ...window.TWEAKS, ...(saved ? { theme: saved } : {}) };
  });
  const [lang, setLang] = useState(() => detectLang());

  // expose active language to t() and keep <html lang> in sync
  window.__LANG = lang;
  useEffect(() => {
    window.__LANG = lang;
    document.documentElement.setAttribute('lang', lang);
    try { localStorage.setItem('ttc_lang', lang); } catch (e) {}
  }, [lang]);

  useEffect(() => {
    document.documentElement.setAttribute('data-palette', tweaks.palette);
  }, [tweaks.palette]);

  // Theme: 'system' follows the OS (and updates live); 'light'/'dark' are forced.
  useEffect(() => {
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      const eff = tweaks.theme === 'system' ? (mql.matches ? 'dark' : 'light') : tweaks.theme;
      document.documentElement.setAttribute('data-theme', eff);
    };
    apply();
    try { localStorage.setItem('ttc_theme', tweaks.theme); } catch (e) {}
    if (tweaks.theme === 'system') {
      mql.addEventListener('change', apply);
      return () => mql.removeEventListener('change', apply);
    }
  }, [tweaks.theme]);

  const setTheme = (v) => {
    setTweaks((t) => ({ ...t, theme: v }));
    window.parent.postMessage({ type: '__edit_mode_set_keys', edits: { theme: v } }, '*');
  };

  return (
    <>
      <Nav lang={lang} setLang={setLang} theme={tweaks.theme} setTheme={setTheme} />
      <main>
        <Hero variant={tweaks.heroVariant} angle={tweaks.heroAngle} />
        <SocialBand />
        <HowItWorks />
        <Features />
        <StatsSection />
        <Testimonials />
        <CTA />
      </main>
      <StickyDownload />
      <TweaksPanel tweaks={tweaks} setTweaks={setTweaks} setTheme={setTheme} />
    </>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
