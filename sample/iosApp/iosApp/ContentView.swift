import HelloShared
import SwiftUI
import UIKit

/// Hosts the shared Compose hierarchy. `MainViewController()` is the Kotlin entry point in
/// `sample/hello-shared`; Kotlin/Native exports a file's top-level functions on a class named
/// after that file, hence `MainViewControllerKt`.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
