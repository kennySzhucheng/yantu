package com.monesy.kaoyan

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KaoyanApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 配置先于一切：UI、WorkManager Worker、Receiver 都依赖它
        ConfigLoader.load(this)
        Notify.createChannel(this)
        // 只补断链，不 REPLACE——避免把等待补发的过期任务取消掉
        appScope.launch { Notify.healChains(this@KaoyanApp) }
    }
}
