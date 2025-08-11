import di.appModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun doInitDependencyFramework(): KoinApplication {
    return startKoin {
        modules(appModule())
    }
}
