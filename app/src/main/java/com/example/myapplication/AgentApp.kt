package com.example.myapplication

import android.app.Application
import com.example.myapplication.agent.AgentEngine
import com.example.myapplication.agent.SubagentRunner
import com.example.myapplication.agent.SubagentRegistry
import com.example.myapplication.data.backup.BackupManager
import com.example.myapplication.data.backup.ConfigurationTransfer
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.data.store.AttachmentStore
import com.example.myapplication.provider.ModelFetcher
import com.example.myapplication.provider.ProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow

/** Application 类 + 手工 ServiceLocator（项目规模无需 DI 框架） */
class AgentApp : Application() {
    private val preferencesScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    val subagentRegistry = SubagentRegistry()
    val permissionCoordinator = com.example.myapplication.agent.PermissionCoordinator()
    val chatSessionPool = com.example.myapplication.ui.chat.ChatSessionPool()
    val deviceController by lazy { com.example.myapplication.device.DeviceController(this) }
    val deviceTools by lazy { com.example.myapplication.agent.AndroidDeviceTools(deviceController) }

    lateinit var store: FileStore
        private set
    lateinit var providerFactory: ProviderFactory
        private set
    lateinit var modelFetcher: ModelFetcher
        private set
    lateinit var attachmentStore: AttachmentStore
        private set
    lateinit var backupManager: BackupManager
        private set
    lateinit var configurationTransfer: ConfigurationTransfer
        private set

    /** 当前主题模式（system / light / dark），UI 即时响应 */
    private val _themeMode = MutableStateFlow("system")
    val themeMode = _themeMode.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.example.myapplication.diagnostics.RuntimeDiagnostics.initialize(java.io.File(filesDir, "logs"))
        com.example.myapplication.diagnostics.RuntimeDiagnostics.sharedStorageAccess = {
            com.example.myapplication.ui.settings.hasSharedStorageAccess(this)
        }
        com.example.myapplication.diagnostics.RuntimeDiagnostics.event("app_start",
            "sdk" to android.os.Build.VERSION.SDK_INT,
            "allFilesAccess" to com.example.myapplication.ui.settings.hasSharedStorageAccess(this))
        Thread.getDefaultUncaughtExceptionHandler()?.let { previous ->
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                com.example.myapplication.diagnostics.RuntimeDiagnostics.event("uncaught_exception",
                    "errorClass" to error.javaClass.name,
                    "frames" to error.stackTrace.take(12).joinToString(" | "))
                previous.uncaughtException(thread, error)
            }
        }
        store = FileStore(filesDir)
        store.recoverInterruptedSubagents()
        attachmentStore = AttachmentStore(store)
        providerFactory = ProviderFactory(client = ProviderFactory.defaultClient().newBuilder()
            .addInterceptor(com.example.myapplication.diagnostics.DiagnosticHttpInterceptor()).build(),
            attachmentStore = attachmentStore)
        modelFetcher = ModelFetcher(providerFactory.client)
        backupManager = BackupManager(this, store)
        configurationTransfer = ConfigurationTransfer(store)
        _themeMode.value = store.loadConfig().themeMode
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        store.saveConfig(store.loadConfig().copy(themeMode = mode))
    }

    fun rememberNewChatDefaults(defaults: com.example.myapplication.data.model.NewChatDefaults): kotlinx.coroutines.Deferred<Unit> {
        store.rememberNewChatDefaults(defaults)
        return preferencesScope.async { store.flushNewChatDefaults() }
    }

    /** 每次调用构造一个新的引擎 */
    fun newAgentEngine(onSubagentStatus: (String) -> Unit = {}, allowDeviceControl: Boolean = deviceController.enabled): AgentEngine {
        val tools = deviceTools.takeIf { allowDeviceControl }
        val runner = SubagentRunner(store, providerFactory, onSubagentStatus, subagentRegistry, tools)
        return AgentEngine(store, providerFactory, runner, tools)
    }

    companion object {
        lateinit var instance: AgentApp
            private set
    }
}
