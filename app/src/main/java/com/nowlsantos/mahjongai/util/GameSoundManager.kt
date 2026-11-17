package com.nowlsantos.mahjongai.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.nowlsantos.mahjongai.R

class GameSoundManager(context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingSounds = mutableSetOf<Int>()

    val diceRollSoundId: Int = soundPool.load(context.applicationContext, R.raw.dice_roll, 1)
    val tileDiscardSoundId: Int = soundPool.load(context.applicationContext, R.raw.tile_discard, 1)
    val tileDrawSoundId: Int = soundPool.load(context.applicationContext, R.raw.tile_draw, 1)
    val tileBonusSoundId: Int = soundPool.load(context.applicationContext, R.raw.tile_bonus, 1)
    val tileChowSoundId: Int = soundPool.load(context.applicationContext, R.raw.tile_chow, 1)
    val tilePungSoundId: Int = soundPool.load(context.applicationContext, R.raw.tile_pung, 1)
    val tileKongSoundId: Int = soundPool.load(context.applicationContext, R.raw.tile_kong, 1)
    val tileClaimSoundId: Int = soundPool.load(context.applicationContext, R.raw.tile_claim, 1)

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds.add(sampleId)
                if (pendingSounds.remove(sampleId)) {
                    playSound(sampleId, 1.0f)
                }
            }
        }
    }

    fun playDiceRoll() {
        play(diceRollSoundId, 1.0f)
    }

    fun playTileDiscard() {
        play(tileDiscardSoundId, 1.0f)
    }

    fun playTileDraw() {
        play(tileDrawSoundId, 0.9f)
    }

    fun playTileBonus() {
        play(tileBonusSoundId, 1.0f)
    }

    fun playTileChow() {
        play(tileChowSoundId, 1.0f)
    }

    fun playTilePung() {
        play(tilePungSoundId, 1.0f)
    }

    fun playTileKong() {
        play(tileKongSoundId, 1.0f)
    }

    fun playTileClaim() {
        play(tileClaimSoundId, 1.0f)
    }

    private fun play(id: Int, volume: Float = 1.0f) {
        if (id in loadedSoundIds) {
            playSound(id, volume)
        } else {
            pendingSounds.add(id)
        }
    }

    private fun playSound(id: Int, volume: Float) {
        if (id != 0) {
            soundPool.play(id, volume, volume, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
