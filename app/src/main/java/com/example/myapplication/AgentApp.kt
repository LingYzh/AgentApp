package com.example.myapplication

import android.app.Application
import com.example.myapplication.agent.AgentEngine
import com.example.myapplication.agent.SubagentRunner
import com.example.myapplication.data.backup.BackupManager
import com.example.myapplication.data.store.FileStore
import com.example.myapplication.provider.ModelFetcher
import com.example.myapplication.provider.ProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application 类 + 手工 ServiceLocator（项目规模无需 DI 框架） */
class AgentApp : Application() {

    lateinit var store: FileStore
        private set
    lateinit var providerFactory: ProviderFactory
        private set
    lateinit var modelFetcher: ModelFetcher
        private set
    lateinit var backupManager: BackupManager
        private set

    /** 当前主题模式（system / light / dark），UI 即时响应 */
    private val _themeMode = MutableStateFlow("system")
    val themeMode = _themeMode.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = FileStore(filesDir)
        providerFactory = ProviderFactory()
        modelFetcher = ModelFetcher(providerFactory.client)
        backupManager = BackupManager(this, store)
        _themeMode.value = store.loadConfig().themeMode
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        store.saveConfig(store.loadConfig().copy(themeMode = mode))
    }

    /** 每次调用构造一个新的引擎 */
    fun newAgentEngine(onSubagentStatus: (String) -> Unit = {}): AgentEngine {
        val runner = SubagentRunner(store, providerFactory, onSubagentStatus)
        return AgentEngine(store, providerFactory, runner)
    }

    companion object {
        lateinit var instance: AgentApp
            private set
    }
}
