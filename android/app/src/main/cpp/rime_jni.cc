// 蝦拼 Android JNI 層：封裝 librime 的 C API（rime_api.h）。
// 不帶 Lua 插件；字根提示（boshiamy comment）在 Java 層做。
#include <jni.h>
#include <rime_api.h>
#include <string>
#include <android/log.h>
#include <unistd.h>

#define LOG_TAG "xiapin_rime"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static RimeApi *rime = nullptr;
static bool inited = false;
static RimeSessionId g_session = 0;

// 通知回呼：捕獲部署錯誤
static void rime_notify(void* ctx, RimeSessionId sid, const char* type, const char* val) {
    if (type && val) {
        ALOGE("RIME_NOTIFY type=%s val=%s", type, val);
    } else if (type) {
        ALOGE("RIME_NOTIFY type=%s (no val)", type);
    }
}

// 全域 JVM 指標（通知回呼用，此精簡版不註冊通知）
static JavaVM *g_jvm = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

// ---- helper：JNI string <-> std::string ----
static std::string jstring_to_std(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *utf = env->GetStringUTFChars(s, nullptr);
    std::string out(utf ? utf : "");
    if (utf) env->ReleaseStringUTFChars(s, utf);
    return out;
}

// ---- startup ----
extern "C" JNIEXPORT void JNICALL
Java_com_xiapin_ime_RimeJNI_startup(JNIEnv *env, jobject, jstring sharedDir, jstring userDir) {
    if (inited) return;
    rime = rime_get_api();
    if (!rime) { ALOGE("rime_get_api() failed"); return; }

    std::string shared = jstring_to_std(env, sharedDir);
    std::string user = jstring_to_std(env, userDir);

    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = shared.c_str();
    traits.user_data_dir = user.c_str();
    traits.log_dir = user.c_str();
    traits.app_name = "rime.xiapin";

    rime->setup(&traits);
    rime->initialize(&traits);
    // 註冊通知回呼以捕獲部署錯誤
    rime->set_notification_handler(rime_notify, nullptr);
    // 載入 deployer 模組（註冊 prebuild_all_schemas / schema_update 等任務）
    rime->deployer_initialize(&traits);

    ALOGE("shared_data_dir=[%s] user_data_dir=[%s]", shared.c_str(), user.c_str());

    // 等待 assets 部署完成：輪詢 xiapin.schema.yaml + default.yaml 都存在且大小穩定。
    std::string p_schema = shared + "/xiapin.schema.yaml";
    std::string p_default = shared + "/default.yaml";
    for (int i = 0; i < 40; i++) {
        bool ok_schema = (access(p_schema.c_str(), R_OK) == 0);
        bool ok_default = (access(p_default.c_str(), R_OK) == 0);
        if (ok_schema && ok_default) break;
        ALOGE("waiting assets (schema=%d default=%d) retry %d", ok_schema, ok_default, i);
        usleep(500000); // 0.5s
    }

    ALOGE("after initialize, maintenance=%d", rime->is_maintenance_mode());
    // 預編所有 schema（遍歷 shared_data_dir 下的 .schema.yaml）
    Bool pb = rime->prebuild();
    ALOGE("prebuild_all_schemas=%d", pb);

    // 備用：若 xiapin.extended.table.bin 未生成，用絕對路徑顯式部署 xiapin schema
    std::string ext_bin = user + "/build/xiapin.extended.table.bin";
    if (access(ext_bin.c_str(), R_OK) != 0) {
        std::string abs = shared + "/xiapin.schema.yaml";
        ALOGE("xiapin.extended not built, deploying xiapin schema via absolute path");
        Bool ds = rime->deploy_schema(abs.c_str());
        ALOGE("deploy_schema(xiapin absolute)=%d", ds);
    }
    ALOGE("after deploy, maintenance=%d", rime->is_maintenance_mode());
    g_session = rime->create_session();
    rime->select_schema(g_session, "xiapin");
    ALOGE("startup done: session=%llu", (unsigned long long)g_session);
    inited = true;
}

// ---- processKey ----
extern "C" JNIEXPORT jboolean JNICALL
Java_com_xiapin_ime_RimeJNI_processKey(JNIEnv *, jobject, jint keycode, jint mask) {
    if (!rime || !inited || !g_session) {
        ALOGE("processKey: not ready (rime=%p inited=%d session=%llu)", (void*)rime, inited, (unsigned long long)g_session);
        return JNI_FALSE;
    }
    bool handled = rime->process_key(g_session, keycode, mask);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "processKey keycode=%d mask=%d handled=%d", keycode, mask, handled);
    return handled ? JNI_TRUE : JNI_FALSE;
}

// ---- simulateKeySequence ----
extern "C" JNIEXPORT jboolean JNICALL
Java_com_xiapin_ime_RimeJNI_simulateKeySequence(JNIEnv *env, jobject, jstring seq) {
    if (!rime || !inited || !g_session) return JNI_FALSE;
    std::string s = jstring_to_std(env, seq);
    return rime->simulate_key_sequence(g_session, s.c_str()) ? JNI_TRUE : JNI_FALSE;
}

// ---- commitComposition ----
extern "C" JNIEXPORT jboolean JNICALL
Java_com_xiapin_ime_RimeJNI_commitComposition(JNIEnv *, jobject) {
    if (!rime || !inited || !g_session) return JNI_FALSE;
    return rime->commit_composition(g_session) ? JNI_TRUE : JNI_FALSE;
}

// ---- clearComposition ----
extern "C" JNIEXPORT void JNICALL
Java_com_xiapin_ime_RimeJNI_clearComposition(JNIEnv *, jobject) {
    if (!rime || !inited || !g_session) return;
    rime->clear_composition(g_session);
}

// ---- setOption ----
extern "C" JNIEXPORT void JNICALL
Java_com_xiapin_ime_RimeJNI_setOption(JNIEnv *env, jobject, jstring option, jboolean value) {
    if (!rime || !inited || !g_session) return;
    std::string opt = jstring_to_std(env, option);
    rime->set_option(g_session, opt.c_str(), value);
}

// ---- getOption ----
extern "C" JNIEXPORT jboolean JNICALL
Java_com_xiapin_ime_RimeJNI_getOption(JNIEnv *env, jobject, jstring option) {
    if (!rime || !inited || !g_session) return JNI_FALSE;
    std::string opt = jstring_to_std(env, option);
    return rime->get_option(g_session, opt.c_str()) ? JNI_TRUE : JNI_FALSE;
}

// ---- getCommitText ----
extern "C" JNIEXPORT jstring JNICALL
Java_com_xiapin_ime_RimeJNI_getCommitText(JNIEnv *env, jobject) {
    if (!rime || !inited || !g_session) return nullptr;
    RIME_STRUCT(RimeCommit, commit);
    jstring result = nullptr;
    if (rime->get_commit(g_session, &commit)) {
        if (commit.text && commit.text[0]) {
            result = env->NewStringUTF(commit.text);
        }
        rime->free_commit(&commit);
    }
    return result;
}

// ---- getContext ----
extern "C" JNIEXPORT jobject JNICALL
Java_com_xiapin_ime_RimeJNI_getContext(JNIEnv *env, jobject thiz) {
    // 用 local frame 包住，避免每次 refresh 累積 local ref 導致 table overflow
    if (env->PushLocalFrame(256) != 0) {
        return nullptr; // 極少發生
    }
    jclass ctxClass = env->FindClass("com/xiapin/ime/RimeJNI$Context");
    jclass candClass = env->FindClass("com/xiapin/ime/RimeJNI$Candidate");
    if (!ctxClass || !candClass) { env->PopLocalFrame(nullptr); return nullptr; }

    jobject ctx = env->AllocObject(ctxClass);
    if (!rime || !inited || !g_session) { env->PopLocalFrame(nullptr); return ctx; }

    RIME_STRUCT(RimeContext, c);
    if (!rime->get_context(g_session, &c)) {
        rime->free_context(&c);
        env->PopLocalFrame(nullptr);
        return ctx;
    }

    // preedit（組字區）：優先 composition.preedit，否則用 get_input()
    char preedit[1024] = {0};
    if (c.composition.preedit && c.composition.preedit[0]) {
        snprintf(preedit, sizeof(preedit), "%s", c.composition.preedit);
    } else {
        const char *input = rime->get_input(g_session);
        if (input) snprintf(preedit, sizeof(preedit), "%s", input);
    }
    jfieldID fidPreedit = env->GetFieldID(ctxClass, "preedit", "Ljava/lang/String;");
    env->SetObjectField(ctx, fidPreedit, env->NewStringUTF(preedit));

    jfieldID fidCaret = env->GetFieldID(ctxClass, "caretPos", "I");
    env->SetIntField(ctx, fidCaret, c.composition.cursor_pos);

    jfieldID fidComposing = env->GetFieldID(ctxClass, "composing", "Z");
    env->SetBooleanField(ctx, fidComposing,
        (c.composition.preedit && c.composition.preedit[0]) ? JNI_TRUE : JNI_FALSE);

    // candidates
    jfieldID fidCands = env->GetFieldID(ctxClass, "candidates", "Ljava/util/List;");
    jobject cands = env->GetObjectField(ctx, fidCands);
    if (cands == nullptr) {
        // AllocObject 不會執行 Java 初始化器，需手動建 List
        jclass arrayListClass = env->FindClass("java/util/ArrayList");
        cands = env->NewObject(arrayListClass,
            env->GetMethodID(arrayListClass, "<init>", "()V"));
        env->SetObjectField(ctx, fidCands, cands);
    }
    jmethodID add = env->GetMethodID(env->FindClass("java/util/List"), "add", "(Ljava/lang/Object;)Z");

    // 跨頁收集候選（手機橫滑，不限當頁 5～9 個）
    // 罐 在 guan 拼音裡約第 50+ 名，只取當頁會「搜不到」
    const int kMaxCandidates = 48;
    const int kMaxPages = 8;
    int pages_advanced = 0;
    int total = 0;
    bool first = true;
    RimeContext *pc = &c;
    RIME_STRUCT(RimeContext, c2);

    for (int page = 0; page < kMaxPages && total < kMaxCandidates; page++) {
        RimeContext *cur = first ? pc : &c2;
        if (!first) {
            // change_page(session, backward=false) → 下一頁
            if (!rime->change_page(g_session, False)) break;
            pages_advanced++;
            if (!rime->get_context(g_session, &c2)) break;
        }
        if (cur->menu.num_candidates <= 0) {
            if (!first) rime->free_context(cur);
            break;
        }
        for (int i = 0; i < cur->menu.num_candidates && total < kMaxCandidates; i++) {
            RimeCandidate *cc = &cur->menu.candidates[i];
            jstring text = env->NewStringUTF(cc->text ? cc->text : "");
            jstring comment = cc->comment ? env->NewStringUTF(cc->comment) : nullptr;
            jobject cand = env->NewObject(candClass,
                env->GetMethodID(candClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"),
                text, comment);
            env->CallBooleanMethod(cands, add, cand);
            total++;
        }
        bool last = cur->menu.is_last_page;
        if (!first) rime->free_context(cur);
        first = false;
        if (last) break;
    }
    rime->free_context(&c);

    // 翻回第一頁，讓 select_candidate 全域 index 行為穩定
    for (int i = 0; i < pages_advanced; i++) {
        rime->change_page(g_session, True); // backward
    }

    return env->PopLocalFrame(ctx);
}

// ---- selectCandidate ----
extern "C" JNIEXPORT jboolean JNICALL
Java_com_xiapin_ime_RimeJNI_selectCandidate(JNIEnv *, jobject, jint index) {
    if (!rime || !inited || !g_session) return JNI_FALSE;
    return rime->select_candidate(g_session, index) ? JNI_TRUE : JNI_FALSE;
}

// ---- pageCandidate ----
// forward: 1=下一頁, 0=上一頁。對應 librime change_page(session, backward)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_xiapin_ime_RimeJNI_pageCandidate(JNIEnv *, jobject, jboolean forward) {
    if (!rime || !inited || !g_session) return JNI_FALSE;
    bool backward = (forward == JNI_FALSE);
    return rime->change_page(g_session, backward) ? JNI_TRUE : JNI_FALSE;
}

// ---- selectSchema ----
extern "C" JNIEXPORT jboolean JNICALL
Java_com_xiapin_ime_RimeJNI_selectSchema(JNIEnv *env, jobject, jstring schemaId) {
    if (!rime || !inited || !g_session) return JNI_FALSE;
    const char *id = env->GetStringUTFChars(schemaId, nullptr);
    Bool ok = rime->select_schema(g_session, id);
    env->ReleaseStringUTFChars(schemaId, id);
    return ok ? JNI_TRUE : JNI_FALSE;
}
