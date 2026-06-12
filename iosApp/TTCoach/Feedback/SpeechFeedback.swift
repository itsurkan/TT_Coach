import AVFoundation
import TTCoachShared

/// Voice feedback via `AVSpeechSynthesizer` — iOS counterpart of Android TTS.
/// Messages come exclusively from the shared `FeedbackMessageCatalog` (UA + EN);
/// this class never composes coaching text itself (trust rule lives in shared).
final class SpeechFeedback: NSObject {

    private let synthesizer = AVSpeechSynthesizer()
    private let lang: FeedbackLang

    init(lang: FeedbackLang) {
        self.lang = lang
        super.init()
        // Route as playback so cues are audible even with the silent switch on —
        // spoken coaching is the product's primary channel during a drill.
        try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.duckOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private var voiceLanguageCode: String {
        // Kotlin enum FeedbackLang is exported to Swift with lowercase cases.
        lang == FeedbackLang.ua ? "uk-UA" : "en-US"
    }

    /// Speak one already-formatted catalog message. Corrections preempt any
    /// still-playing utterance: the shared `FeedbackCadencePolicy` guarantees
    /// >= 3 s spacing, so an unfinished utterance is stale by the next one.
    func speak(_ message: String) {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .word)
        }
        let utterance = AVSpeechUtterance(string: message)
        utterance.voice = AVSpeechSynthesisVoice(language: voiceLanguageCode)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.speak(utterance)
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
    }
}
