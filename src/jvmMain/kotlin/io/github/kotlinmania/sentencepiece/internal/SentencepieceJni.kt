package io.github.kotlinmania.sentencepiece.internal

internal object SentencepieceJni {
    init {
        System.loadLibrary("sentencepiece_jni")
    }

    @JvmStatic
    external fun getpid(): Int
}
