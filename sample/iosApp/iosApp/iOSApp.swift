import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            // Only the keyboard inset is ignored — Compose handles that one itself. Ignoring the
            // whole safe area puts the app's top bar under the status bar, where its controls are
            // drawn over by the system and receive no touches.
            ContentView().ignoresSafeArea(.keyboard)
        }
    }
}
