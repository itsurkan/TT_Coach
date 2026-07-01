/**
 * Offline voice-clip builder. Turns each unique (lang, phrase) of a voice style into a
 * committed audio clip and writes a manifest keyed by the SAME clipKey the app looks up
 * (so playback finds them). Never called at runtime.
 *
 * Two providers:
 *   files (primary) — index audio you already rendered (e.g. ElevenLabs web UI) and copy
 *     each into public/voice/<styleId>/<clipKey>.<ext>. No network / API key.
 *       TTS_PROVIDER=files VOICE_SRC=./my-clips npm run gen:voice -- ./my.voicestyle.json
 *     Mapping: a source file is matched to a phrase when its basename (minus extension),
 *     run through normalizeText, equals the phrase. For arbitrary filenames, add a
 *     <VOICE_SRC>/pairing.json  { "<phrase text>": "<filename>" }  (it wins over auto-map).
 *     Phrases with no file are reported as MISSING and skipped. Files mode always rebuilds
 *     the manifest from what is on disk now, so re-running picks up updated/removed clips.
 *   stub — no real audio; estimates durations so you can dry-run the manifest/key wiring.
 *       TTS_PROVIDER=stub npm run gen:voice -- ./my.voicestyle.json
 *
 * (export a style from the VoiceStyleEditor, or hand-write the JSON.)
 */
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { resolve, dirname, basename, extname } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { clipKey, normalizeText, type ClipEntry, type ClipManifest } from '../src/drill2d/voiceClips'
import { VOICE_METRIC_KEYS, type Lang, type MetricKey, type VoiceStyle } from '../src/drill2d/voiceStyle'
import type { Phase } from '../src/drill2d/drillMetrics'

const __dirname = dirname(fileURLToPath(import.meta.url))
const PUBLIC_DIR = resolve(__dirname, '../public/voice')

interface TtsResult { audio: Uint8Array; ext: 'mp3' | 'wav'; durationMs: number }
/** synthesize returns null when the provider has no audio for this phrase (files mode gap). */
interface TtsProvider { synthesize(text: string, lang: Lang): Promise<TtsResult | null> }

/** ~150 wpm estimate — plausible duration for a dry run or when a real clip can't be probed. */
function estimateMs(text: string): number {
  const words = normalizeText(text).split(' ').filter(Boolean).length
  return Math.round((words / (150 / 60)) * 1000)
}

/** Stub provider: no real audio. Lets you dry-run the manifest/key wiring offline. */
const stubProvider: TtsProvider = {
  async synthesize(text) {
    return { audio: new Uint8Array(0), ext: 'mp3', durationMs: estimateMs(text) }
  },
}

/**
 * Files provider: resolve each phrase to an audio file the user already rendered, under
 * VOICE_SRC. Auto-maps by normalized basename; a pairing.json overrides for odd filenames.
 * Duration is probed with music-metadata, falling back to the word estimate.
 */
function filesProvider(srcDir: string): TtsProvider {
  const dir = resolve(process.cwd(), srcDir)
  const audioFiles = readdirSync(dir).filter(f => /\.(mp3|wav)$/i.test(f))

  // normalizedBasename -> filename (auto-map: file named after the phrase text)
  const byText = new Map<string, string>()
  for (const f of audioFiles) byText.set(normalizeText(basename(f, extname(f))), f)

  // pairing.json overrides (keyed by phrase text; normalized here)
  const pairingPath = resolve(dir, 'pairing.json')
  if (existsSync(pairingPath)) {
    const raw = JSON.parse(readFileSync(pairingPath, 'utf-8')) as Record<string, string>
    for (const [text, file] of Object.entries(raw)) byText.set(normalizeText(text), file)
  }

  return {
    async synthesize(text) {
      const file = byText.get(normalizeText(text))
      if (!file) return null
      const audio = readFileSync(resolve(dir, file))
      const ext = /\.wav$/i.test(file) ? 'wav' : 'mp3'
      let durationMs = estimateMs(text)
      try {
        const mm = await import('music-metadata')
        const meta = await mm.parseBuffer(audio, ext === 'wav' ? 'audio/wav' : 'audio/mpeg')
        if (meta.format.duration) durationMs = Math.round(meta.format.duration * 1000)
      } catch { /* keep the estimate */ }
      return { audio, ext, durationMs }
    },
  }
}

function pickProvider(): TtsProvider {
  const name = process.env.TTS_PROVIDER ?? 'stub'
  switch (name) {
    case 'files': {
      const src = process.env.VOICE_SRC
      if (!src) throw new Error('TTS_PROVIDER=files requires VOICE_SRC=<dir of rendered clips>')
      return filesProvider(src)
    }
    case 'stub': return stubProvider
    default: throw new Error(`unknown TTS_PROVIDER: ${name} (use 'files' for rendered clips, or 'stub' for a dry run)`)
  }
}

/**
 * Every unique (lang, phrase) in the style: cue up/down + per-phase phaseCues up/down +
 * praise, both langs. phaseCues MUST be included — the per-phase coaching phrases
 * (замах/удар/завершення) are voiced during a drill and otherwise get no clip.
 * A style may omit a language (phrases[lang] undefined) — that lang is skipped.
 */
export function uniquePhrases(style: VoiceStyle): Array<{ lang: Lang; text: string }> {
  const seen = new Set<string>()
  const out: Array<{ lang: Lang; text: string }> = []
  const push = (lang: Lang, text: string) => {
    if (!text) return
    const key = clipKey(lang, text)
    if (seen.has(key)) return
    seen.add(key)
    out.push({ lang, text })
  }
  for (const lang of ['en', 'uk'] as Lang[]) {
    const set = style.phrases[lang]
    if (!set) continue
    for (const k of VOICE_METRIC_KEYS) {
      push(lang, set.cues[k].up)
      push(lang, set.cues[k].down)
    }
    if (set.phaseCues) {
      for (const metric of Object.keys(set.phaseCues) as MetricKey[]) {
        const phases = set.phaseCues[metric]
        if (!phases) continue
        for (const phase of Object.keys(phases) as Phase[]) {
          const pc = phases[phase]
          if (!pc) continue
          push(lang, pc.up)
          push(lang, pc.down)
        }
      }
    }
    for (const text of set.praise) push(lang, text)
  }
  return out
}

async function main() {
  const arg = process.argv[2]
  if (!arg) { console.error('usage: npm run gen:voice -- <style.json>'); process.exit(1) }
  let style: VoiceStyle
  try {
    style = JSON.parse(readFileSync(resolve(process.cwd(), arg), 'utf-8')) as VoiceStyle
  } catch (err) {
    console.error(`cannot read style JSON at "${arg}": ${(err as Error).message}`)
    process.exit(1)
  }
  if (typeof style.id !== 'string' || !/^[\w-]+$/.test(style.id)) {
    console.error(`invalid style.id "${style.id}" — must match [A-Za-z0-9_-]+`)
    process.exit(1)
  }
  const mode = process.env.TTS_PROVIDER ?? 'stub'
  const provider = pickProvider()
  const outDir = resolve(PUBLIC_DIR, style.id)
  mkdirSync(outDir, { recursive: true })

  // Files mode rebuilds from what's on disk now (so updates/removals take effect); other
  // providers reuse the existing manifest to skip unchanged phrases (e.g. costly API calls).
  const manifestPath = resolve(outDir, 'manifest.json')
  const freshBuild = mode === 'files'
  let manifest: ClipManifest = { styleId: style.id, clips: {} }
  if (!freshBuild) {
    try { manifest = JSON.parse(readFileSync(manifestPath, 'utf-8')) as ClipManifest } catch { /* fresh */ }
  }

  let written = 0
  const missing: Array<{ lang: Lang; text: string }> = []
  for (const { lang, text } of uniquePhrases(style)) {
    const key = clipKey(lang, text)
    const existing = manifest.clips[key]
    if (existing && normalizeText(existing.text) === normalizeText(text)) {
      console.log(`skip  ${key}  "${text}"`)
      continue
    }
    const res = await provider.synthesize(text, lang)
    if (!res) {
      missing.push({ lang, text })
      console.warn(`MISSING  ${key}  "${text}"`)
      continue
    }
    const file = `${key}.${res.ext}`
    if (res.audio.length > 0) writeFileSync(resolve(outDir, file), res.audio)
    const entry: ClipEntry = { file, durationMs: res.durationMs, text, lang }
    manifest.clips[key] = entry
    written++
    console.log(`write ${key}  (${res.durationMs}ms)  "${text}"`)
  }

  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2))
  console.log(`\nmanifest → ${manifestPath}  (${Object.keys(manifest.clips).length} clips; ${written} written, ${missing.length} missing)`)
  if (missing.length) {
    console.log('\nphrases still needing audio:')
    for (const m of missing) console.log(`  [${m.lang}] "${m.text}"  → ${clipKey(m.lang, m.text)}`)
  }
}

// Only run when invoked directly (npm run gen:voice); stays importable for tests.
const invokedDirectly = !!process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href
if (invokedDirectly) void main()
