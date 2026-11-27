import SwiftUI
import AVFoundation
import WatchConnectivity

// Sarahia Watch 1.0.0 - SwiftUI companion, watchOS 10+.
// Le micro est sur la montre : aucun audio n'est capté par l'iPhone
// tant qu'une session de traduction Watch est sélectionnée.
@main
struct SarahiaWatchApp: App {
    @StateObject private var link = SarahiaWatchLink()
    var body: some Scene {
        WindowGroup {
            WatchHomeView()
                .environmentObject(link)
                .preferredColorScheme(.dark)
        }
    }
}

private enum WatchPalette {
    static let violet = Color(red: 0.57, green: 0.25, blue: 0.98)
    static let blue = Color(red: 0.09, green: 0.47, blue: 1.0)
    static let panel = Color(red: 0.12, green: 0.10, blue: 0.23)
}

struct WatchHomeView: View {
    @EnvironmentObject private var link: SarahiaWatchLink

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 9) {
                    HStack(spacing: 5) {
                        Image(systemName: "infinity")
                            .font(.title2.weight(.bold))
                            .foregroundStyle(WatchPalette.violet)
                        VStack(alignment: .leading, spacing: 0) {
                            Text("Sarahia").font(.headline)
                            Text("Local Agent LLM").font(.system(size: 10))
                                .foregroundStyle(.secondary)
                        }
                    }.padding(.vertical, 7)

                    NavigationLink {
                        WatchListeningView(mode: .voice)
                    } label: {
                        WatchMenuRow(icon: "mic", title: "Parler avec Sarahia", prominent: true)
                    }.buttonStyle(.plain)

                    NavigationLink {
                        WatchListeningView(mode: .translate)
                    } label: {
                        WatchMenuRow(icon: "character.book.closed", title: "Translate Live")
                    }.buttonStyle(.plain)

                    NavigationLink {
                        WatchPianoView()
                    } label: {
                        WatchMenuRow(icon: "pianokeys", title: "Piano Live")
                    }.buttonStyle(.plain)

                    NavigationLink {
                        WatchAgentsView()
                    } label: {
                        WatchMenuRow(icon: "ellipsis", title: "Autres agents")
                    }.buttonStyle(.plain)

                    if !link.reachable {
                        Label("Ouvre Sarahia sur l’iPhone", systemImage: "iphone.slash")
                            .font(.system(size: 10))
                            .foregroundStyle(.orange)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(.horizontal, 4)
            }
            .navigationBarHidden(true)
            .background(.black)
        }
        .tint(.white)
    }
}

private struct WatchMenuRow: View {
    let icon: String
    let title: String
    var prominent = false
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .medium))
                .frame(width: 27)
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .lineLimit(2)
                .minimumScaleFactor(0.82)
            Spacer(minLength: 0)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 12)
        .frame(height: 59)
        .background(
            RoundedRectangle(cornerRadius: 15)
                .fill(prominent
                      ? LinearGradient(colors: [WatchPalette.blue.opacity(0.8), WatchPalette.violet.opacity(0.65)],
                                       startPoint: .leading, endPoint: .trailing)
                      : LinearGradient(colors: [WatchPalette.panel, WatchPalette.panel],
                                       startPoint: .leading, endPoint: .trailing))
        )
        .accessibilityElement(children: .combine)
    }
}

private enum ListeningMode: String {
    case voice
    case translate
}

private struct WatchListeningView: View {
    @EnvironmentObject private var link: SarahiaWatchLink
    let mode: ListeningMode
    @State private var started = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text(mode == .translate ? "Translate Live" : "Conversation vocale")
                    .font(.system(size: 14, weight: .bold))
                ZStack {
                    Circle().strokeBorder(
                        AngularGradient(colors: [WatchPalette.blue, WatchPalette.violet, WatchPalette.blue],
                                        center: .center), lineWidth: 4)
                    Circle().fill(WatchPalette.violet.opacity(0.12)).padding(5)
                    Image(systemName: "mic.fill")
                        .font(.system(size: 39))
                        .foregroundStyle(.white)
                }
                .frame(width: 100, height: 100)
                .accessibilityLabel("Microphone de l’Apple Watch")

                Text(link.isRecording ? "J’écoute…" : "Micro arrêté")
                    .font(.headline)
                Image(systemName: "waveform")
                    .foregroundStyle(WatchPalette.blue)
                    .font(.title2)
                if link.isRecording {
                    ProgressView(value: link.inputLevel)
                        .tint(WatchPalette.violet)
                        .accessibilityLabel("Niveau du microphone")
                }
                Text(link.message)
                    .font(.system(size: 11))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(link.reachable ? Color.secondary : Color.orange)
                if !link.recognizedText.isEmpty {
                    Text(link.recognizedText)
                        .font(.system(size: 11))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .foregroundStyle(.secondary)
                }
                if mode == .translate, !link.frenchText.isEmpty {
                    Text(link.frenchText)
                        .font(.system(size: 13, weight: .medium))
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                }

                Button {
                    if link.isRecording { link.stop() }
                    else { link.start(mode: mode.rawValue) }
                } label: {
                    Label(link.isRecording ? "Arrêter" : "Démarrer",
                          systemImage: link.isRecording ? "stop.fill" : "mic.fill")
                        .frame(maxWidth: .infinity)
                }
                .tint(link.isRecording ? .red : WatchPalette.violet)
            }
            .padding(.horizontal, 8)
        }
        .onAppear {
            guard !started else { return }
            started = true
            link.start(mode: mode.rawValue)
        }
        .onDisappear { link.stop() }
    }
}

private struct WatchPianoView: View {
    @State private var index = 0
    private let notes: [(String, String, Int)] = [
        ("Do (C)", "Do : à gauche des 2 touches noires", 0),
        ("Ré (D)", "Ré : entre les 2 touches noires", 1),
        ("Mi (E)", "Mi : à droite des 2 touches noires", 2)
    ]
    var body: some View {
        ScrollView {
            VStack(spacing: 9) {
                Text("∞ Sarahia  ·  Piano Live")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(WatchPalette.violet)
                Text("Note actuelle").font(.caption2).foregroundStyle(.secondary)
                Text(notes[index].0)
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(6)
                    .background(WatchPalette.panel, in: RoundedRectangle(cornerRadius: 13))
                HStack(spacing: 3) {
                    ForEach(0..<3) { n in
                        Text(["Do", "Ré", "Mi"][n])
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(n == index ? .black : .black.opacity(0.7))
                            .frame(maxWidth: .infinity)
                            .frame(height: 69)
                            .background(n == index ? Color.red : Color.white,
                                        in: RoundedRectangle(cornerRadius: 5))
                    }
                }
                Text(notes[index].1)
                    .font(.system(size: 11))
                    .multilineTextAlignment(.center)
                Text("Aperçu : MIDI non connecté")
                    .font(.system(size: 10))
                    .foregroundStyle(.orange)
                Button("Note suivante") { index = (index + 1) % notes.count }
                    .tint(WatchPalette.violet)
            }
            .padding(.horizontal, 6)
        }
    }
}

private struct WatchAgentsView: View {
    var body: some View {
        List {
            Text("Sarah · conversation")
            Text("Yohan · traduction")
            Text("Tom · vision")
            Text("Raphaël · code")
            Text("Connexion avec l’iPhone requise pour les agents.")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .navigationTitle("Autres agents")
    }
}

/// MVP: WatchConnectivity transporte des blocs PCM mono 16 kHz dans les DEUX
/// applications ouvertes. Ce transport n'est PAS garanti lorsque la montre
/// ou l'iPhone suspend l'application. Ne pas prétendre à une écoute perpétuelle.
final class SarahiaWatchLink: NSObject, ObservableObject, WCSessionDelegate {
    @Published private(set) var reachable = false
    @Published private(set) var isRecording = false
    @Published private(set) var inputLevel = 0.0
    @Published var message = "Prêt à écouter"
    @Published var recognizedText = ""
    @Published var frenchText = ""

    private let audioEngine = AVAudioEngine()
    private var audioConverter: AVAudioConverter?
    private let sendQueue = DispatchQueue(label: "sarahia.watch.audio", qos: .userInitiated)
    private var mode = "translate"
    private var pendingStartMode: String?
    private var isStarting = false
    private var lastLevelUpdate = ProcessInfo.processInfo.systemUptime

    override init() {
        super.init()
        guard WCSession.isSupported() else { return }
        let s = WCSession.default
        s.delegate = self
        s.activate()
        reachable = s.isReachable
    }

    func start(mode: String) {
        guard !isRecording, !isStarting else { return }
        self.mode = mode
        recognizedText = ""
        frenchText = ""
        guard WCSession.isSupported() else {
            message = "WatchConnectivity indisponible."
            return
        }
        pendingStartMode = mode
        attemptPendingStart()
    }

    private func attemptPendingStart() {
        guard !isStarting, !isRecording, let mode = pendingStartMode else { return }
        guard WCSession.default.activationState == .activated, WCSession.default.isReachable else {
            message = "Connexion à Sarahia sur l’iPhone…"
            return
        }

        isStarting = true
        let audioSession = AVAudioSession.sharedInstance()
        let permissionResponse: (Bool) -> Void = { [weak self] granted in
            DispatchQueue.main.async {
                guard let self = self, self.pendingStartMode != nil else { return }
                guard granted else {
                    self.isStarting = false
                    self.pendingStartMode = nil
                    self.message = "Autorise le microphone dans Réglages pour Sarahia sur la montre."
                    return
                }
                self.startAudioCapture(mode: mode)
            }
        }
        switch audioSession.recordPermission {
        case .granted:
            permissionResponse(true)
        case .denied:
            permissionResponse(false)
        case .undetermined:
            audioSession.requestRecordPermission(permissionResponse)
        @unknown default:
            permissionResponse(false)
        }
    }

    private func startAudioCapture(mode: String) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default)
            try session.setActive(true)

            let input = audioEngine.inputNode
            let inputFormat = input.outputFormat(forBus: 0)
            guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0,
                  let outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16,
                                                  sampleRate: 16_000, channels: 1,
                                                  interleaved: false),
                  let converter = AVAudioConverter(from: inputFormat, to: outputFormat) else {
                throw NSError(domain: "SarahiaWatch", code: 1,
                              userInfo: [NSLocalizedDescriptionKey: "Format du micro indisponible"])
            }
            audioConverter = converter
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 2048, format: inputFormat) { [weak self] buffer, _ in
                self?.sendQueue.async { [weak self] in
                    self?.transmit(buffer, to: outputFormat)
                }
            }
            audioEngine.prepare()
            try audioEngine.start()
            pendingStartMode = nil
            isStarting = false
            isRecording = true
            message = "Micro Apple Watch actif"
            WCSession.default.sendMessage(["type": "watch.start", "mode": mode],
                                          replyHandler: nil,
                                          errorHandler: { [weak self] error in
                DispatchQueue.main.async { self?.message = error.localizedDescription }
            })
        } catch {
            pendingStartMode = nil
            isStarting = false
            if audioEngine.isRunning { audioEngine.stop() }
            audioEngine.inputNode.removeTap(onBus: 0)
            audioConverter = nil
            message = "Micro : \(error.localizedDescription)"
        }
    }

    private func transmit(_ source: AVAudioPCMBuffer, to format: AVAudioFormat) {
        guard audioEngine.isRunning, WCSession.default.isReachable,
              let converter = audioConverter else { return }
        let ratio = format.sampleRate / source.format.sampleRate
        let capacity = AVAudioFrameCount(ceil(Double(source.frameLength) * ratio) + 64)
        guard let dest = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: capacity) else { return }
        var used = false
        var convertError: NSError?
        let result = converter.convert(to: dest, error: &convertError) { _, inputStatus in
            if used { inputStatus.pointee = .noDataNow; return nil }
            used = true
            inputStatus.pointee = .haveData
            return source
        }
        guard convertError == nil, result != .error, dest.frameLength > 0,
              let samples = dest.int16ChannelData?[0] else { return }
        let sampleCount = Int(dest.frameLength)
        var sum: Double = 0
        for index in 0..<sampleCount {
            let sample = Double(samples[index]) / 32_768.0
            sum += sample * sample
        }
        let now = ProcessInfo.processInfo.systemUptime
        if now - lastLevelUpdate >= 0.15 {
            lastLevelUpdate = now
            let level = min(1, sqrt(sum / Double(sampleCount)) * 10)
            DispatchQueue.main.async { self.inputLevel = level }
        }
        var packet = Data([0x53, 0x41, 0x52, 0x31]) // "SAR1"
        packet.append(Data(bytes: samples, count: sampleCount * MemoryLayout<Int16>.size))
        WCSession.default.sendMessageData(packet, replyHandler: nil) { [weak self] error in
            DispatchQueue.main.async {
                self?.message = "Audio vers l’iPhone : \(error.localizedDescription)"
            }
        }
    }

    func stop() {
        pendingStartMode = nil
        isStarting = false
        if audioEngine.isRunning { audioEngine.stop() }
        audioEngine.inputNode.removeTap(onBus: 0)
        audioConverter = nil
        isRecording = false
        inputLevel = 0
        try? AVAudioSession.sharedInstance().setActive(false)
        if WCSession.isSupported(), WCSession.default.isReachable {
            WCSession.default.sendMessage(["type": "watch.stop", "mode": mode],
                                          replyHandler: nil, errorHandler: nil)
        }
        message = "Micro arrêté"
    }

    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState,
                 error: Error?) {
        DispatchQueue.main.async {
            self.reachable = state == .activated && session.isReachable
            if let error = error { self.message = "WatchConnectivity : \(error.localizedDescription)" }
            self.attemptPendingStart()
        }
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        DispatchQueue.main.async {
            self.reachable = session.isReachable
            if !session.isReachable, self.isRecording {
                self.stop()
                self.message = "Liaison iPhone interrompue"
            }
            self.attemptPendingStart()
        }
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        DispatchQueue.main.async {
            switch message["type"] as? String {
            case "iphone.start":
                self.start(mode: "translate")
            case "iphone.stop":
                self.stop()
            case "phone.transcript":
                self.recognizedText = message["he"] as? String ?? ""
                self.frenchText = message["fr"] as? String ?? ""
            case "phone.status":
                self.message = message["text"] as? String ?? ""
            default: break
            }
        }
    }
}
