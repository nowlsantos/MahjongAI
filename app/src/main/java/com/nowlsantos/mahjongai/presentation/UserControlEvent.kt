package com.nowlsantos.mahjongai.presentation

sealed class UserControlEvent {
    object Draw : UserControlEvent()
    object Meld : UserControlEvent()
    object Chow : UserControlEvent()
    object Pung : UserControlEvent()
    object Kong : UserControlEvent()
    object Pass: UserControlEvent()
    object Win : UserControlEvent()
    object Discard : UserControlEvent()
    object SortSuit : UserControlEvent()
    object SortRank : UserControlEvent()
}