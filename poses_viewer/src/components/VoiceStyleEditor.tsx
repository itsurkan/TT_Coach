/**
 * Visual editor for voice styles. Presets are immutable: editing any field of a
 * builtin style auto-clones it into a new user style first. Live preview is free —
 * the active style feeds the buildSpokenSchedule memo in StrokesPage, so a slider
 * change re-runs only the pure scheduler. "Test voice" plays a sample (clip-or-live).
 */
import { useMemo, useState, type ReactNode } from 'react'
import {
  type VoiceStyle, type Lang, type MetricKey, VOICE_METRIC_KEYS, voiceProfileOf,
} from '../drill2d/voiceStyle'
import {
  type StoredStyles, allStyles, cloneStyle, renameStyle, removeStyle, upsertStyle,
  serializeStyle, importStyle, newStyleId, getActiveStyle,
} from '../drill2d/voiceStyleStore'
import { clipKey, lookupClip, type ClipManifest } from '../drill2d/voiceClips'
import { speakNow } from './useSpokenFeedback'

export interface VoiceStyleEditorProps {
  state: StoredStyles
  onChange: (next: StoredStyles) => void
  manifest: ClipManifest | null
}

const LANGS: Lang[] = ['en', 'uk']

export function VoiceStyleEditor({ state, onChange, manifest }: VoiceStyleEditorProps) {
  const active = getActiveStyle(state)
  const [editLang, setEditLang] = useState<Lang>(active.lang)
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
    onChange({ activeStyleId: 'preset-strict', styles: removeStyle(state.styles, active.id) })
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

      {/* Cadence */}
      <Section title="Каденс (мс)">
        <Slider label="Пауза між підказками" min={0} max={10000} step={250} value={active.correctiveMinGapMs} onChange={v => edit(s => ({ ...s, correctiveMinGapMs: v }))} />
        <Slider label="Тиша перед похвалою" min={0} max={15000} step={250} value={active.praiseMinSilenceMs} onChange={v => edit(s => ({ ...s, praiseMinSilenceMs: v }))} />
        <Slider label="Пауза після удару" min={0} max={1500} step={50} value={active.postStrokeGapMs} onChange={v => edit(s => ({ ...s, postStrokeGapMs: v }))} />
      </Section>

      {/* Bands */}
      <Section title="Зони">
        <Slider label="Ширина зони ×" min={0.5} max={2} step={0.05} value={active.bandWidthMult} onChange={v => edit(s => ({ ...s, bandWidthMult: v }))} />
        <Slider label="Поріг значущості (°)" min={0} max={20} step={1} value={active.minMeaningfulDeltaDeg} onChange={v => edit(s => ({ ...s, minMeaningfulDeltaDeg: v }))} />
        <Slider label="Інтервал нагадування" min={0} max={20000} step={500} value={active.reminderIntervalMs} onChange={v => edit(s => ({ ...s, reminderIntervalMs: v }))} />
        <Toggle label="Чергувати підказки" value={active.varyCues} onChange={v => edit(s => ({ ...s, varyCues: v }))} />
      </Section>

      {/* Praise */}
      <Section title="Похвала">
        <Toggle label="Увімкнено" value={active.praiseEnabled} onChange={v => edit(s => ({ ...s, praiseEnabled: v }))} />
        <Toggle label="За виправлення" value={active.praiseOnCorrection} onChange={v => edit(s => ({ ...s, praiseOnCorrection: v }))} />
        <Toggle label="За серію" value={active.praiseOnStreak} onChange={v => edit(s => ({ ...s, praiseOnStreak: v }))} />
        <Slider label="Довжина серії" min={1} max={10} step={1} value={active.praiseStreakLen} onChange={v => edit(s => ({ ...s, praiseStreakLen: v }))} />
      </Section>

      {/* Skip-stale */}
      <Section title="Пропуск застарілих">
        <Toggle label="Увімкнено" value={active.skipStaleEnabled} onChange={v => edit(s => ({ ...s, skipStaleEnabled: v }))} />
        <Slider label="Запас перед ударом (мс)" min={0} max={1000} step={50} value={active.skipStaleMarginMs} onChange={v => edit(s => ({ ...s, skipStaleMarginMs: v }))} />
      </Section>

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
        <Slider label="Темп" min={0.5} max={2} step={0.05} value={active.rate} onChange={v => edit(s => ({ ...s, rate: v }))} />
        <Slider label="Тон" min={0} max={2} step={0.05} value={active.pitch} onChange={v => edit(s => ({ ...s, pitch: v }))} />
        <Slider label="Гучність" min={0} max={1} step={0.05} value={active.volume} onChange={v => edit(s => ({ ...s, volume: v }))} />
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

function Slider(props: { label: string; min: number; max: number; step: number; value: number; onChange: (v: number) => void }) {
  return (
    <label className="flex items-center gap-2">
      <span className="w-48">{props.label}</span>
      <input type="range" min={props.min} max={props.max} step={props.step} value={props.value}
        onChange={e => props.onChange(Number(e.target.value))} className="flex-1" />
      <span className="w-16 text-right tabular-nums">{props.value}</span>
    </label>
  )
}

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center gap-2">
      <input type="checkbox" checked={value} onChange={e => onChange(e.target.checked)} />
      <span>{label}</span>
    </label>
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
        {VOICE_METRIC_KEYS.map(metric => (
          <tr key={metric}>
            <td className="pr-2 text-neutral-400">{metric}</td>
            {(['up', 'down'] as const).map(dir => (
              <td key={dir} className="pr-2">
                <input className="bg-neutral-800 rounded px-1 py-0.5 w-full" value={set.cues[metric][dir]} onChange={e => setCue(metric, dir, e.target.value)} />
                {badge(set.cues[metric][dir])}
              </td>
            ))}
          </tr>
        ))}
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
