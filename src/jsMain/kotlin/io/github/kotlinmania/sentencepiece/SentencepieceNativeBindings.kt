package io.github.kotlinmania.sentencepiece

@JsModule("@kotlinmania/sentencepiece-native-bindings")
public external interface SentencepieceNativeBindings {
    public fun getpid(): Int
}
