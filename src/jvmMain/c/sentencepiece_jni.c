#include <jni.h>

#ifndef _WIN32
#include <unistd.h>
#else
#include <process.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_io_github_kotlinmania_sentencepiece_internal_SentencepieceJni_getpid(
    JNIEnv* env, jclass clazz) {
    (void)env; (void)clazz;
#ifndef _WIN32
    return (jint)getpid();
#else
    return (jint)_getpid();
#endif
}

#ifdef __cplusplus
}
#endif
