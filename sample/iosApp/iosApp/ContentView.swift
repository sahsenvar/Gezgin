import HelloShared
import SwiftUI
import UIKit

/// Hosts the shared Compose hierarchy. `MainViewController()` is the Kotlin entry point in
/// `sample/hello-shared`; Kotlin/Native exports a file's top-level functions on a class named
/// after that file, hence `MainViewControllerKt`.
///
/// Compose is wrapped in a `UINavigationController` because that is where iOS keeps the
/// interactive pop gesture. Hosted directly in SwiftUI the edge swipe produced no back event at
/// all — an always-enabled root `BackHandler` never fired, while the same gesture drove Apple's
/// own apps. The bar itself stays hidden: the application draws its own.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let navigation = UINavigationController(
            rootViewController: MainViewControllerKt.MainViewController()
        )
        navigation.setNavigationBarHidden(true, animated: false)
        // With a single view controller on the stack UIKit switches the pop gesture off; Compose
        // drives the transition itself, so it is kept on and left unclaimed by the delegate.
        navigation.interactivePopGestureRecognizer?.isEnabled = true
        navigation.interactivePopGestureRecognizer?.delegate = context.coordinator
        return navigation
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    /// Lets the edge-pan begin even though UIKit's own stack has nothing to pop.
    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool { true }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool { true }
    }
}
