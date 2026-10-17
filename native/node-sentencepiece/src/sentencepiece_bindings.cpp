#include <napi.h>

#include <unistd.h>

static Napi::Value GetPid(const Napi::CallbackInfo& info) {
    return Napi::Number::New(info.Env(), static_cast<int>(::getpid()));
}

static Napi::Object Init(Napi::Env env, Napi::Object exports) {
    exports.Set("getpid", Napi::Function::New(env, GetPid));
    return exports;
}

NODE_API_MODULE(sentencepiece_native, Init)
