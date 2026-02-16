package com.base.aihelperwearos

import android.app.Application
import android.util.Log
import com.base.aihelperwearos.data.rag.RagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class AIHelperApplication : Application() {

    companion object {
        private const val TAG = "AIHelperApplication"
        
        @Volatile
        private var ragRepository: RagRepository? = null
        
        /**
         * Provides the shared RAG repository instance when available.
         *
         * @return nullable `RagRepository` if initialized, otherwise `null`.
         */
        fun getRagRepository(): RagRepository? = ragRepository
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Initializes application-level state when the process starts.
     *
     * @return `Unit` after startup initialization kicks off.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AIHelperApplication onCreate")

        initializeCoil()
        initializeRagSystem()
    }

    /**
     * Starts background initialization of the RAG repository.
     *
     * @return `Unit` after launching the initialization coroutine.
     */
    private fun initializeCoil() {
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("latex_cache"))
                    .maxSizeBytes(50 * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()

        coil.Coil.setImageLoader(imageLoader)
        Log.d(TAG, "Coil ImageLoader initialized with SVG support")
    }

    private fun initializeRagSystem() {
        applicationScope.launch {
            try {
                Log.d(TAG, "Initializing RAG system...")
                val startTime = System.currentTimeMillis()
                
                ragRepository = RagRepository(this@AIHelperApplication).also { repo ->
                    repo.initialize()
                }
                
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "RAG system initialized in ${elapsed}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize RAG system", e)
                ragRepository = null
            }
        }
    }

    /**
     * Responds to system low-memory signals by clearing cached data.
     *
     * @return `Unit` after clearing RAG cache when present.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning - clearing RAG cache")
        ragRepository?.clearCache()
    }

    /**
     * Handles memory trim levels and clears cache for moderate or higher pressure.
     *
     * @param level trim level provided by the system.
     * @return `Unit` after optional cache clearing.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.w(TAG, "Trim memory level $level - clearing RAG cache")
            ragRepository?.clearCache()
        }
    }
}
