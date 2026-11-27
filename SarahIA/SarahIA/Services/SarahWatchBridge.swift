import Foundation
import AVFoundation
import Combine
import WatchConnectivity

/// Pont WatchConnectivity pour Sarahia Watch 1.0.0.
/// Le micro de l'iPhone n'est jamais ouvert quand la montre est la source.
final class SarahWatchBridge: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = SarahWatchBridge()

    @Published private(set) var isWatchInstalled = false
    @Published private(set) var isWatchReachable = false

    private override init() {
        super.init()
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
        updateStatus(session)
    }

    private func updateStatus(_ session: WCSession) {
        let installed = session.isPaired && session.isWatchAppInstalled
        DispatchQueue.main.async {
            self.isWatchInstalled = installed
            self.isWatchReachable = installed && session.isReachable
        }
    }

    var watchInstalledNow: Bool {
        WCSession.isSupported() && WCSession.default.isPaired && WCSession.default.isWatchAppInstalled
    }

    func requestStart() -> Bool {
        guard WCSession.isSupported(), WCSession.default.isReachable else { return false }
        WCSession.default.sendMessage(["type": "iphone.start"], replyHandler: nil) { error in
            DispatchQueue.main.async {
                LiveTranslationSession.shared.setWatchError("Liaison Apple Watch : \(error.localizedDescription)")
            }
        }
        return true
    }

    func requestStop() {
        guard WCSession.isSupported(), WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["type": "iphone.stop"], replyHandler: nil, errorHandler: nil)
    }

    func sendTranscript(hebrew: String, french: String) {
        guard WCSession.isSupported(), WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["type": "phone.transcript",
                                       "he": hebrew,
                                       "fr": french],
                                      replyHandler: nil, errorHandler: nil)
    }

    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState,
                 error: Error?) {
        updateStatus(session)
    }

    func sessionDidBecomeInactive(_ session: WCSession) {
        updateStatus(session)
    }

    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        updateStatus(session)
        if !session.isReachable {
            DispatchQueue.main.async {
                LiveTranslationSession.shared.watchDisconnected()
            }
        }
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        updateStatus(session)
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        handleMessage(message)
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any],
                 replyHandler: @escaping ([String: Any]) -> Void) {
        if message["type"] as? String == "watch.start" {
            replyHandler(["text": "iPhone connecté · traduction en préparation"])
        }
        handleMessage(message)
    }

    private func handleMessage(_ message: [String: Any]) {
        guard let type = message["type"] as? String else { return }
        DispatchQueue.main.async {
            switch type {
            case "watch.start":
                // Le mode vocal de la Watch a une UI dédiée mais son dialogue
                // avec le LLM n'est pas encore branché dans ce premier prototype.
                guard (message["mode"] as? String) == "translate" else { return }
                let live = LiveTranslationSession.shared
                live.setLanguages(LiveLanguagePair(sourceLanguage: "he",
                                                    targetLanguage: "fr",
                                                    isVoiceEnabled: true))
                live.startFromWatch()
                self.sendStatus("Micro Apple Watch connecté · parle en hébreu")
                NotificationCenter.default.post(name: .sarahPresentLiveTranslation,
                                                object: nil)
            case "watch.stop":
                if LiveTranslationSession.shared.microphoneSource == .watch {
                    LiveTranslationSession.shared.stop()
                }
            default: break
            }
        }
    }

    func sendStatus(_ text: String) {
        guard WCSession.isSupported(), WCSession.default.isReachable else { return }
        WCSession.default.sendMessage(["type": "phone.status", "text": text],
                                      replyHandler: nil, errorHandler: nil)
    }

    func session(_ session: WCSession, didReceiveMessageData messageData: Data) {
        // 'SAR1' = PCM signé 16-bit LE / 16 kHz / 1 canal.
        guard messageData.count > 4,
              messageData.prefix(4).elementsEqual([0x53, 0x41, 0x52, 0x31]),
              (messageData.count - 4) % 2 == 0 else { return }
        LiveTranslationSession.shared.ingestWatchPCM(messageData)
    }
}
