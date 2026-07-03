/**
 * Visual editor for voice styles. Presets are immutable: editing any field of a
 * builtin style auto-clones it into a new user style first. Live preview is free —
 * the active style feeds the buildSpokenSchedule memo in StrokesPage, so a slider
 * change re-runs only the pure scheduler. "Test voice" plays a sample (clip-or-live).
 *
 * Cadence / Bands / Praise / Skip-stale controls moved to the Налаштування panel
 * (StrokesPage) in Task 5 — this editor now covers reproduction only (Voice + Phrases).
 */
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  type VoiceStyle, type Lang, type MetricKey, VOICE_METRIC_KEYS, voiceProfileOf,
} from '../drill2d/voiceStyle'
import { METRIC_PHASES, type Phase } from '../drill2d/drillMetrics'
import { isPatternMetric } from '../drill2d/decideRepCues'
import {
  type StoredStyles, allStyles, cloneStyle, renameStyle, removeStyle, upsertStyle,
  serializeStyle, importStyle, newStyleId, getActiveStyle, DEFAULT_ACTIVE_ID,
} from '../drill2d/voiceStyleStore'
import { clipKey, lookupClip, type ClipManifest } from '../drill2d/voiceClips'
import { speakNow } from './useSpokenFeedback'
import { Slider } from './controls'

export interface VoiceStyleEditorProps {
  state: StoredStyles
  onChange: (next: StoredStyles) => void
  manifest: ClipManifest | null
}

const LANGS: Lang[] = ['en', 'uk']

/** Short Ukrainian labels for each phase (per-phase pattern-metric rows). */
const PHASE_LABEL: Record<Phase, string> = {
  backswing: 'замах',
  contact: 'удар',
  followthrough: 'завершення',
}

export function VoiceStyleEditor({ state, onChange, manifest }: VoiceStyleEditorProps) {
  const active = getActiveStyle(state)
  const [editLang, setEditLang] = useState<Lang>(active.lang)
  useEffect(() => { setEditLang(active.lang) }, [active.id])
  const styles = allStyles(state.styles)

  // Edit a field; if the active style is builtin, clone it first and switch to the clone.
  function edit(mutate: (s: VoiceStyle) => VoiceStyle) {
    if (active.builtin) {
      const clone = mutate(cloneStyle(active, `${active.name} (copy)`, newStyleId()))
      onChange({ activeStyleId: clone.id, styles: [...state.styles, clone] })
    } else {
      const updated = mutate({ ...active })
      onChange({ activeStyleId: active.id, styles: upsertStyle(state.styles, updated) })
    }
  }

  function setActive(id: string) { onChange({ ...state, activeStyleId: id }) }
  function clone() {
    const c = cloneStyle(active, `${active.name} (copy)`, newStyleId())
    onChange({ activeStyleId: c.id, styles: [...state.styles, c] })
  }
  function rename(name: string) {
    if (active.builtin) return
    onChange({ ...state, styles: renameStyle(state.styles, active.id, name) })
  }
  function remove() {
    if (active.builtin) return
    onChange({ activeStyleId: DEFAULT_ACTIVE_ID, styles: removeStyle(state.styles, active.id) })
  }
  function exportJson() {
    const blob = new Blob([serializeStyle(active)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = `${active.name}.voicestyle.json`; a.click()
    URL.revokeObjectURL(url)
  }
  async function importJson(file: File) {
    try {
      const imported = importStyle(await file.text(), newStyleId())
      onChange({ activeStyleId: imported.id, styles: [...state.styles, imported] })
    } catch { alert('Невалідний файл стилю голосу') }
  }

  return (
    <fieldset className="border border-neutral-700 rounded p-3 max-w-xl space-y-3 text-sm">
      <legend className="px-1">Стиль голосу</legend>

      {/* Header: select / clone / rename / delete / export / import */}
      <div className="flex flex-wrap items-center gap-2">
        <select className="bg-neutral-800 rounded px-2 py-1" value={active.id} onChange={e => setActive(e.target.value)}>
          {styles.map(s => <option key={s.id} value={s.id}>{s.name}{s.builtin ? ' (вбудований)' : ''}</option>)}
        </select>
        <button className="px-2 py-1 bg-neutral-800 rounded" onClick={clone}>Клонувати</button>
        <button className="px-2 py-1 bg-neutral-800 rounded disabled:opacity-40" disabled={active.builtin} onClick={() => {
          const n = prompt('Нова назва', active.name); if (n) rename(n)
        }}>Перейменувати</button>
        <button className="px-2 py-1 bg-neutral-800 rounded disabled:opacity-40" disabled={active.builtin} onClick={remove}>Видалити</button>
        <button className="px-2 py-1 bg-neutral-800 rounded" onClick={exportJson}>Експорт</button>
        <label className="px-2 py-1 bg-neutral-800 rounded cursor-pointer">Імпорт
          <input type="file" accept="application/json" className="hidden" onChange={e => { const f = e.target.files?.[0]; if (f) void importJson(f) }} />
        </label>
        <button className="px-2 py-1 bg-sky-800 rounded" onClick={() => speakNow(
          { text: active.phrases[active.lang].cues.elbow_angle.up, clipKey: clipKey(active.lang, active.phrases[active.lang].cues.elbow_angle.up) },
          voiceProfileOf(active), manifest,
        )}>▶ Тест голосу</button>
      </div>
      {active.builtin && <p className="text-amber-400 text-xs">Вбудований пресет незмінний — редагування створить копію.</p>}

      {/* Voice */}
      <Section title="Голос">
        <div className="flex items-center gap-2">
          <span className="w-40">Мова</span>
          {LANGS.map(l => (
            <button key={l} className={`px-2 py-1 rounded ${active.lang === l ? 'bg-sky-700' : 'bg-neutral-800'}`}
              onClick={() => edit(s => ({ ...s, lang: l }))}>{l.toUpperCase()}</button>
          ))}
        </div>
        <VoicePicker style={active} onPick={uri => edit(s => ({ ...s, voiceURI: uri }))} />
        <Slider label="Темп" min={0.5} max={2} step={0.05} value={active.rate} onChange={v => edit(s => ({ ...s, rate: v }))}
          hint="Швидкість мовлення. 1 — звичайна, більше — швидше." />
        <Slider label="Тон" min={0} max={2} step={0.05} value={active.pitch} onChange={v => edit(s => ({ ...s, pitch: v }))}
          hint="Висота голосу. 1 — звичайна, більше — вищий тон." />
        <Slider label="Гучність" min={0} max={1} step={0.05} value={active.volume} onChange={v => edit(s => ({ ...s, volume: v }))}
          hint="Гучність озвучення: 0 — тихо, 1 — максимально." />
      </Section>

      {/* Phrases */}
      <Section title="Фрази">
        <div className="flex items-center gap-2 mb-1">
          <span className="w-40">Редагувати мову</span>
          {LANGS.map(l => (
            <button key={l} className={`px-2 py-1 rounded ${editLang === l ? 'bg-sky-700' : 'bg-neutral-800'}`} onClick={() => setEditLang(l)}>{l.toUpperCase()}</button>
          ))}
        </div>
        <PhraseTable style={active} lang={editLang} manifest={manifest} onEdit={edit} />
      </Section>
    </fieldset>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="border-t border-neutral-800 pt-2">
      <div className="text-neutral-400 mb-1">{title}</div>
      <div className="space-y-1">{children}</div>
    </div>
  )
}

function VoicePicker({ style, onPick }: { style: VoiceStyle; onPick: (uri: string | null) => void }) {
  const voices = useMemo(() => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return []
    const want = style.lang === 'uk' ? 'uk' : 'en'
    return window.speechSynthesis.getVoices().filter(v => v.lang.toLowerCase().startsWith(want))
  }, [style.lang])
  return (
    <label className="flex items-center gap-2">
      <span className="w-40">Голос TTS</span>
      <select className="bg-neutral-800 rounded px-2 py-1 flex-1" value={style.voiceURI ?? ''} onChange={e => onPick(e.target.value || null)}>
        <option value="">(браузер за замовч.)</option>
        {voices.map(v => <option key={v.voiceURI} value={v.voiceURI}>{v.name} ({v.lang})</option>)}
      </select>
    </label>
  )
}

function PhraseTable({ style, lang, manifest, onEdit }: {
  style: VoiceStyle; lang: Lang; manifest: ClipManifest | null; onEdit: (m: (s: VoiceStyle) => VoiceStyle) => void
}) {
  const set = style.phrases[lang]
  function badge(text: string) {
    const fresh = !!lookupClip(manifest, lang, text)
    return <span className={`ml-1 text-xs ${fresh ? 'text-emerald-400' : 'text-neutral-500'}`}>{fresh ? '● кліп' : '○ live'}</span>
  }
  function setCue(metric: MetricKey, dir: 'up' | 'down', value: string) {
    onEdit(s => {
      const phrases = JSON.parse(JSON.stringify(s.phrases)) as VoiceStyle['phrases']
      phrases[lang].cues[metric][dir] = value
      return { ...s, phrases }
    })
  }
  function setPhaseCue(metric: MetricKey, phase: Phase, dir: 'up' | 'down', value: string) {
    onEdit(s => {
      const phrases = JSON.parse(JSON.stringify(s.phrases)) as VoiceStyle['phrases']
      const pc = (phrases[lang].phaseCues ??= {})
      const m = (pc[metric] ??= {})
      const slot = (m[phase] ??= { up: '', down: '' })
      slot[dir] = value
      return { ...s, phrases }
    })
  }
  function setPraise(i: number, value: string) {
    onEdit(s => {
      const phrases = JSON.parse(JSON.stringify(s.phrases)) as VoiceStyle['phrases']
      phrases[lang].praise[i] = value
      return { ...s, phrases }
    })
  }
  return (
    <table className="w-full text-xs">
      <thead><tr className="text-neutral-500"><th className="text-left">Метрика</th><th className="text-left">вище зони</th><th className="text-left">нижче зони</th></tr></thead>
      <tbody>
        {VOICE_METRIC_KEYS.flatMap(metric => {
          // Pattern metrics (elbow) are graded per phase → render one editable row
          // per phase, writing to phaseCues. Other metrics keep the single row.
          if (isPatternMetric(metric)) {
            const phases = (METRIC_PHASES[metric as keyof typeof METRIC_PHASES] ?? []) as Phase[]
            return phases.map(phase => {
              const slot = set.phaseCues?.[metric]?.[phase] ?? { up: '', down: '' }
              return (
                <tr key={`${metric}-${phase}`}>
                  <td className="pr-2 text-neutral-400">{metric} ({PHASE_LABEL[phase]})</td>
                  {(['up', 'down'] as const).map(dir => (
                    <td key={dir} className="pr-2">
                      <input className="bg-neutral-800 rounded px-1 py-0.5 w-full" value={slot[dir]} onChange={e => setPhaseCue(metric, phase, dir, e.target.value)} />
                      {badge(slot[dir])}
                    </td>
                  ))}
                </tr>
              )
            })
          }
          return [(
            <tr key={metric}>
              <td className="pr-2 text-neutral-400">{metric}</td>
              {(['up', 'down'] as const).map(dir => (
                <td key={dir} className="pr-2">
                  <input className="bg-neutral-800 rounded px-1 py-0.5 w-full" value={set.cues[metric][dir]} onChange={e => setCue(metric, dir, e.target.value)} />
                  {badge(set.cues[metric][dir])}
                </td>
              ))}
            </tr>
          )]
        })}
        <tr><td colSpan={3} className="pt-2 text-neutral-400">Похвала</td></tr>
        {set.praise.map((p, i) => (
          <tr key={i}><td colSpan={3}>
            <input className="bg-neutral-800 rounded px-1 py-0.5 w-full" value={p} onChange={e => setPraise(i, e.target.value)} />
            {badge(p)}
          </td></tr>
        ))}
      </tbody>
    </table>
  )
}
