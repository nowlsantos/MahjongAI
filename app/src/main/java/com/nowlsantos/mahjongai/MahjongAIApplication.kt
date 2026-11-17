package com.nowlsantos.mahjongai

import android.app.Application
import com.nowlsantos.mahjongai.di.appModule
import com.nowlsantos.mahjongai.engine.rl.MahjongNeuralNet
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MahjongAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Pre-load trained Actor-Critic Neural Network weights from Android Assets
        MahjongNeuralNet.getSharedInstance {
            assets.open(MahjongNeuralNet.ASSET_FILE_NAME)
        }

        startKoin {
            androidLogger()
            androidContext(this@MahjongAIApplication)
            modules(appModule)
        }
    }
}
