import SwiftUI
import VastuApp

/// The whole iPhone app is the shared Kotlin app, shown full screen. Compose draws inside the safe
/// area itself (every screen root pads for the notch, the Dynamic Island and the home bar), so the
/// Swift side ignores the safe area rather than adding a second margin.
@main
struct VastuFirstApp: App {
    var body: some Scene {
        WindowGroup {
            SharedAppView().ignoresSafeArea()
        }
    }
}

struct SharedAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosAppKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
