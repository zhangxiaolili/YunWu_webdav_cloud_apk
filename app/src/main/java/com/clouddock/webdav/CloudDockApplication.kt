package com.clouddock.webdav

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.clouddock.webdav.data.AccountRepository
import com.clouddock.webdav.data.SettingsRepository
import com.clouddock.webdav.data.local.CacheStore
import com.clouddock.webdav.data.remote.WebDavClientFactory
import com.clouddock.webdav.data.security.SecretCipher
import com.clouddock.webdav.data.sync.SyncBaselineStore
import com.clouddock.webdav.data.sync.SyncEngine
import com.clouddock.webdav.data.sync.SyncWorker
import java.io.File

class AppContainer(private val context: Application) {
    val secretCipher = SecretCipher()
    val accountRepository = AccountRepository(context, secretCipher)
    val settingsRepository = SettingsRepository(context)
    val clientFactory = WebDavClientFactory()
    val baselineStore = SyncBaselineStore(context)
    val syncEngine = SyncEngine(
        context = context,
        accountRepository = accountRepository,
        clientFactory = clientFactory,
        baselineStore = baselineStore
    )

    fun cacheStoreFor(account: com.clouddock.webdav.data.model.WebDavAccount): CacheStore =
        CacheStore(context, account)

    val tempDirectory: File = File(context.cacheDir, "clouddock").apply { mkdirs() }
}

class CloudDockApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.ensurePeriodicWork(this, container.accountRepository)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "clouddock/image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
