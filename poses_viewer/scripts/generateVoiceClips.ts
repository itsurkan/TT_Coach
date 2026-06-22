/**
 * Offline voice-clip generator. Renders each unique (lang, phrase) of a voice style
 * to a committed audio clip via a pluggable cloud-TTS provider, and writes a manifest
 * keyed by the SAME clipKey the app uses (so playback finds them). Never called at
 * runtime; credentials come from env vars and are never shipped to the browser.
 *
 * Usage:  TTS_PROVIDER=azure AZURE_TTS_KEY=... AZURE_TTS_REGION=... \
 *           npm run gen:voice -- ./preset-strict.voicestyle.json
 *         (export a style from the editor, or hand-write the JSON.)
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { clipKey, normalizeText, type ClipEntry, type ClipManifest } from '../src/drill2d/voiceClips'
import { VOICE_METRIC_KEYS, type Lang, type VoiceStyle } from '../src/drill2d/voiceStyle'

const __dirname = dirname(fileURLToPath(import.meta.url))
const PUBLIC_DIR = resolve(__dirname, '../public/voice')

interface TtsResult { audio: Uint8Array; ext: 'mp3' | 'wav'; durationMs: number }
interface TtsProvider { synthesize(text: string, lang: Lang): Promise<TtsResult> }

/** Stub provider: no real audio. Lets you dry-run the manifest/key wiring offline. */
const stubProvider: TtsProvider = {
  async synthesize(text) {
    // ~150 wpm estimate so the manifest carries a plausible duration in a dry run.
    const words = normalizeText(text).split(' ').filter(Boolean).length
    return { audio: new Uint8Array(0), ext: 'mp3', durationMs: Math.round((words / (150 / 60)) * 1000) }
  },
}

// Real providers (fill in when credentials are chosen — see spec §8):
//   ElevenLabs: POST https://api.elevenlabs.io/v1/text-to-speech/<voiceId>  (header xi-api-key)
//   Azure Neural: POST https://<region>.tts.speech.microsoft.com/cognitiveservices/v1
//     SSML <voice name="uk-UA-OstapNeural">…</voice>; WAV → durationMs from sample count.
function pickProvider(): TtsProvider {
  const name = process.env.TTS_PROVIDER ?? 'stub'
  switch (name) {
    // case 'azure': return azureProvider()
    // case 'elevenlabs': return elevenLabsProvider()
    case 'stub': return stubProvider
    default: throw new Error(`unknown TTS_PROVIDER: ${name} (set it, or use 'stub' for a dry run)`)
  }
}

/** Every unique (lang, phrase) in the style: all cue up/down + praise, both langs. */
function uniquePhrases(style: VoiceStyle): Array<{ lang: Lang; text: string }> {
  const seen = new Set<string>()
  const out: Array<{ lang: Lang; text: string }> = []
  for (const lang of ['en', 'uk'] as Lang[]) {
    const set = style.phrases[lang]
    const all = [
      ...VOICE_METRIC_KEYS.flatMap(k => [set.cues[k].up, set.cues[k].down]),
      ...set.praise,
    ]
    for (const text of all) {
      const key = clipKey(lang, text)
      if (seen.has(key)) continue
      seen.add(key)
      out.push({ lang, text })
    }
  }
  return out
}

async function main() {
  const arg = process.argv[2]
  if (!arg) { console.error('usage: npm run gen:voice -- <style.json>'); process.exit(1) }
  const style = JSON.parse(readFileSync(resolve(process.cwd(), arg), 'utf-8')) as VoiceStyle
  const provider = pickProvider()
  const outDir = resolve(PUBLIC_DIR, style.id)
  mkdirSync(outDir, { recursive: true })

  // Reuse existing manifest so unchanged phrases (matching key) are skipped.
  const manifestPath = resolve(outDir, 'manifest.json')
  let manifest: ClipManifest = { styleId: style.id, clips: {} }
  try { manifest = JSON.parse(readFileSync(manifestPath, 'utf-8')) as ClipManifest } catch { /* fresh */ }

  for (const { lang, text } of uniquePhrases(style)) {
    const key = clipKey(lang, text)
    const existing = manifest.clips[key]
    if (existing && normalizeText(existing.text) === normalizeText(text)) {
      console.log(`skip  ${key}  "${text}"`)
      continue
    }
    const res = await provider.synthesize(text, lang)
    const file = `${key}.${res.ext}`
    if (res.audio.length > 0) writeFileSync(resolve(outDir, file), res.audio)
    const entry: ClipEntry = { file, durationMs: res.durationMs, text, lang }
    manifest.clips[key] = entry
    console.log(`write ${key}  (${res.durationMs}ms)  "${text}"`)
  }

  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2))
  console.log(`\nmanifest → ${manifestPath}  (${Object.keys(manifest.clips).length} clips)`)
}

void main()
