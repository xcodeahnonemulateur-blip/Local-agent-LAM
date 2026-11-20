import SwiftUI
import UIKit

/// Translate Live : maquette iPhone paysage validée (FR en haut, HE en bas).
@available(iOS 16.2, *)
public struct LiveTranslationView: View {
    @ObservedObject private var session = LiveTranslationSession.shared
    @ObservedObject private var watch = SarahWatchBridge.shared
    @Environment(\.dismiss) private var dismiss
    @State private var showSettings = false
    @State private var showKeyboardNotice = false

    public init() {}

    private var items: [LiveTranscriptItem] { session.items.filter { $0.isSpeech } }
    private var french: String {
        items.map(\.translatedText).filter { !$0.isEmpty }.joined(separator: " ")
    }
    private var hebrew: String {
        items.map(\.originalText).filter { !$0.isEmpty }.joined(separator: " ")
    }

    public var body: some View {
        GeometryReader { geometry in
            let landscape = geometry.size.width > geometry.size.height
            VStack(spacing: 0) {
                header.frame(height: landscape ? 42 : 58)
                VStack(spacing: 0) {
                    panel(label: "🇫🇷  FRANÇAIS", content: french,
                          placeholder: "La traduction s'affichera ici",
                          color: Color(red: 0.008, green: 0.02, blue: 0.075),
                          direction: .leftToRight)
                    Rectangle().fill(Color.cyan).frame(height: 1)
                    panel(label: "🇮🇱  HÉBREU", content: hebrew,
                          placeholder: "הטקסט יופיע כאן",
                          color: Color(red: 0.015, green: 0.21, blue: 0.63),
                          direction: .rightToLeft)
                }
                .clipShape(RoundedRectangle(cornerRadius: 16))
                footer.frame(height: landscape ? 54 : 74)
            }
            .padding(.horizontal, landscape ? 9 : 12)
            .background(Color.black.ignoresSafeArea())
        }
        .preferredColorScheme(.dark)
        .onAppear {
            SarahTranslationOrientation.setLive(true)
            session.start()
        }
        .onDisappear {
            session.stop()
            SarahTranslationOrientation.setLive(false)
        }
        .sheet(isPresented: $showSettings) {
            NavigationStack {
                Form {
                    LabeledContent("Microphone", value: session.microphoneSource.rawValue)
                    LabeledContent("Apple Watch", value: watch.isWatchInstalled
                                   ? (watch.isWatchReachable ? "Connectée" : "Non joignable")
                                   : "Non détectée")
                    LabeledContent("Reconnaissance", value: session.recognitionModeLabel)
                    Toggle("Lire le français", isOn: Binding(
                        get: { session.languagePair.isVoiceEnabled },
                        set: { _ in session.toggleVoice() }))
                    Button("Inverser les langues") { session.swapLanguages() }
                    Button("Effacer la transcription") { session.clearTranscript() }
                }
                .navigationTitle("Translate Live")
                .toolbar { ToolbarItem(placement: .confirmationAction) {
                    Button("Fermer") { showSettings = false }
                }}
            }
            .presentationDetents([.medium])
        }
        .alert("Clavier", isPresented: $showKeyboardNotice) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("La saisie clavier arrive dans une prochaine version.")
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Button {
                session.stop()
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(Color.white.opacity(0.12)))
            }
            Image(systemName: "infinity")
                .font(.title2.weight(.bold))
                .foregroundStyle(LinearGradient(colors: [.blue, .purple],
                                                startPoint: .leading, endPoint: .trailing))
            Text("Sarahia").font(.headline).lineLimit(1)
            Spacer(minLength: 4)
            HStack(spacing: 5) {
                Circle().fill(.red).frame(width: 8, height: 8)
                Text("LIVE").font(.system(size: 14, weight: .bold))
            }
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(Capsule().fill(Color.red.opacity(0.13)))
            .overlay(Capsule().stroke(Color.red.opacity(0.65)))
            Spacer(minLength: 4)
            Image(systemName: "waveform").foregroundStyle(.blue)
            Button { showSettings = true } label: {
                Image(systemName: "gearshape.fill")
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(Color.white.opacity(0.12)))
            }
        }
        .foregroundStyle(.white)
    }

    private func panel(label: String, content: String, placeholder: String,
                       color: Color, direction: LayoutDirection) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label).font(.system(size: 12, weight: .bold))
                .foregroundStyle(Color.white.opacity(0.75))
                .padding(.leading, 12).padding(.top, 7)
            ScrollViewReader { reader in
                ScrollView(showsIndicators: false) {
                    VStack {
                        Text(content.isEmpty ? placeholder : content)
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(content.isEmpty ? .white.opacity(0.45) : .white)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 12)
                        Color.clear.frame(height: 1).id("bottom")
                    }
                    .frame(maxWidth: .infinity)
                }
                .environment(\.layoutDirection, direction)
                .onChange(of: content) { _ in
                    withAnimation(.easeOut(duration: 0.12)) {
                        reader.scrollTo("bottom", anchor: .bottom)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(color)
    }

    private var footer: some View {
        HStack {
            Button { showKeyboardNotice = true } label: {
                Image(systemName: "keyboard")
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(.white.opacity(0.12)))
            }
            Spacer()
            Button { session.toggleMute() } label: {
                Image(systemName: session.isMuted ? "mic.slash.fill" : "mic.fill")
                    .font(.system(size: 24))
                    .frame(width: 50, height: 50)
                    .background(Circle().fill(.black))
                    .overlay(Circle().stroke(
                        LinearGradient(colors: [.blue, .purple],
                                       startPoint: .leading, endPoint: .trailing), lineWidth: 3))
                    .shadow(color: .blue.opacity(0.7), radius: 8)
            }
            Spacer()
            Button { session.toggleVoice() } label: {
                Image(systemName: session.languagePair.isVoiceEnabled
                      ? "speaker.wave.2.fill" : "speaker.slash.fill")
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(.white.opacity(0.12)))
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 10)
        .overlay(alignment: .top) {
            if let error = session.lastError {
                Text(error).font(.system(size: 10))
                    .foregroundStyle(.orange)
                    .lineLimit(2).offset(y: -22)
            }
        }
    }
}

enum SarahTranslationOrientation {
    static var isLive = false
    static func setLive(_ value: Bool) {
        isLive = value
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene }).first else { return }
        let mask: UIInterfaceOrientationMask = value ? .landscape : .portrait
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { error in
            print("[Translate Live] Rotation : \(error.localizedDescription)")
        }
        scene.windows.first?.rootViewController?
            .setNeedsUpdateOfSupportedInterfaceOrientations()
    }
}
