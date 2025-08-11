import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func makeCoordinator() -> ComposeCoordinator {
        return ComposeCoordinator()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

class ComposeCoordinator {

    init() {
         DependencyInjectionKt.doInitDependencyFramework(

         )
    }
}
struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
    }
}



