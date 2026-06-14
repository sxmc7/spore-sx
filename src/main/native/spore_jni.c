#include <jni.h>
#include <pthread.h>
#include <string.h>
#include <stdlib.h>

static JavaVM* g_jvm = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

/* ===== 字段查找（遍历类层次） ===== */

static jfieldID findIntField(JNIEnv* env, jobject obj, const char* name) {
    jclass cls = (*env)->GetObjectClass(env, obj);
    while (cls != NULL) {
        jfieldID fid = (*env)->GetFieldID(env, cls, name, "I");
        if (fid != NULL) {
            (*env)->ExceptionClear(env);
            return fid;
        }
        (*env)->ExceptionClear(env);
        jclass super = (*env)->GetSuperclass(env, cls);
        (*env)->DeleteLocalRef(env, cls);
        cls = super;
    }
    return NULL;
}

static jfieldID findBoolField(JNIEnv* env, jobject obj, const char* name) {
    jclass cls = (*env)->GetObjectClass(env, obj);
    while (cls != NULL) {
        jfieldID fid = (*env)->GetFieldID(env, cls, name, "Z");
        if (fid != NULL) {
            (*env)->ExceptionClear(env);
            return fid;
        }
        (*env)->ExceptionClear(env);
        jclass super = (*env)->GetSuperclass(env, cls);
        (*env)->DeleteLocalRef(env, cls);
        cls = super;
    }
    return NULL;
}

/* ===== 方法查找（尝试多个名字） ===== */

static const char* HURT_NAMES[] = {"hurt", "m_6469_", NULL};
static const char* SETHEALTH_NAMES[] = {"setHealth", "m_21153_", NULL};

static jmethodID findMethod(JNIEnv* env, jobject obj, const char** names, const char* sig) {
    jclass cls = (*env)->GetObjectClass(env, obj);
    for (int i = 0; names[i] != NULL; i++) {
        jmethodID mid = (*env)->GetMethodID(env, cls, names[i], sig);
        if (mid != NULL) {
            (*env)->ExceptionClear(env);
            (*env)->DeleteLocalRef(env, cls);
            return mid;
        }
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, cls);
    return NULL;
}

/* ===== nSetInt ===== */

JNIEXPORT jboolean JNICALL
Java_com_Harbinger_Spore_util_SporeNativeBridge_nSetInt(
    JNIEnv* env, jclass cls, jobject target, jstring fieldName, jint value)
{
    const char* name = (*env)->GetStringUTFChars(env, fieldName, NULL);
    jfieldID fid = findIntField(env, target, name);
    (*env)->ReleaseStringUTFChars(env, fieldName, name);
    if (fid == NULL) return JNI_FALSE;
    (*env)->SetIntField(env, target, fid, value);
    return JNI_TRUE;
}

/* ===== nSetBool ===== */

JNIEXPORT jboolean JNICALL
Java_com_Harbinger_Spore_util_SporeNativeBridge_nSetBool(
    JNIEnv* env, jclass cls, jobject target, jstring fieldName, jboolean value)
{
    const char* name = (*env)->GetStringUTFChars(env, fieldName, NULL);
    jfieldID fid = findBoolField(env, target, name);
    (*env)->ReleaseStringUTFChars(env, fieldName, name);
    if (fid == NULL) return JNI_FALSE;
    (*env)->SetBooleanField(env, target, fid, value);
    return JNI_TRUE;
}

/* ===== nCallHurt ===== */

JNIEXPORT jboolean JNICALL
Java_com_Harbinger_Spore_util_SporeNativeBridge_nCallHurt(
    JNIEnv* env, jclass cls, jobject entity, jobject source, jfloat amount)
{
    const char* sig = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    jmethodID mid = findMethod(env, entity, HURT_NAMES, sig);
    if (mid == NULL) return JNI_FALSE;
    return (*env)->CallBooleanMethod(env, entity, mid, source, amount);
}

/* ===== nCallSetHealth ===== */

JNIEXPORT jboolean JNICALL
Java_com_Harbinger_Spore_util_SporeNativeBridge_nCallSetHealth(
    JNIEnv* env, jclass cls, jobject entity, jfloat health)
{
    jmethodID mid = findMethod(env, entity, SETHEALTH_NAMES, "(F)V");
    if (mid == NULL) return JNI_FALSE;
    (*env)->CallVoidMethod(env, entity, mid, health);
    return JNI_TRUE;
}

/* ===== 新线程方案（干净 Java 栈） ===== */

typedef struct {
    jobject entity;
    jobject source;
    jfloat amount;
    int is_hurt;       /* 1=hurt, 0=setHealth */
    pthread_mutex_t* mutex;
    pthread_cond_t* cond;
    int* done;
    jboolean result;
} CleanCallArgs;

static void* clean_thread_func(void* arg) {
    CleanCallArgs* args = (CleanCallArgs*)arg;
    JNIEnv* env = NULL;

    if ((*g_jvm)->AttachCurrentThread(g_jvm, (void**)&env, NULL) != JNI_OK) {
        args->result = JNI_FALSE;
        goto finish;
    }

    if (args->is_hurt) {
        const char* sig = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
        jmethodID mid = findMethod(env, args->entity, HURT_NAMES, sig);
        if (mid != NULL) {
            args->result = (*env)->CallBooleanMethod(env, args->entity, mid, args->source, args->amount);
        }
    } else {
        jmethodID mid = findMethod(env, args->entity, SETHEALTH_NAMES, "(F)V");
        if (mid != NULL) {
            (*env)->CallVoidMethod(env, args->entity, mid, args->amount);
            args->result = JNI_TRUE;
        }
    }

    if (args->source) (*env)->DeleteGlobalRef(env, args->source);
    (*env)->DeleteGlobalRef(env, args->entity);
    (*g_jvm)->DetachCurrentThread(g_jvm);

finish:
    pthread_mutex_lock(args->mutex);
    *(args->done) = 1;
    pthread_cond_signal(args->cond);
    pthread_mutex_unlock(args->mutex);
    return NULL;
}

static jboolean do_clean_call(JNIEnv* env, jobject entity, jobject source, jfloat amount, int is_hurt) {
    if (g_jvm == NULL) return JNI_FALSE;

    pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
    int done = 0;

    CleanCallArgs args;
    args.entity = (*env)->NewGlobalRef(env, entity);
    args.source = source ? (*env)->NewGlobalRef(env, source) : NULL;
    args.amount = amount;
    args.is_hurt = is_hurt;
    args.mutex = &mutex;
    args.cond = &cond;
    args.done = &done;
    args.result = JNI_FALSE;

    pthread_t thread;
    if (pthread_create(&thread, NULL, clean_thread_func, &args) != 0) {
        (*env)->DeleteGlobalRef(env, args.entity);
        if (args.source) (*env)->DeleteGlobalRef(env, args.source);
        return JNI_FALSE;
    }

    /* 等待完成 */
    pthread_mutex_lock(&mutex);
    while (!done) pthread_cond_wait(&cond, &mutex);
    pthread_mutex_unlock(&mutex);
    pthread_join(thread, NULL);

    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&cond);
    return args.result;
}

/* ===== nCleanHurt ===== */

JNIEXPORT jboolean JNICALL
Java_com_Harbinger_Spore_util_SporeNativeBridge_nCleanHurt(
    JNIEnv* env, jclass cls, jobject entity, jobject source, jfloat amount)
{
    return do_clean_call(env, entity, source, amount, 1);
}

/* ===== nCleanSetHealth ===== */

JNIEXPORT jboolean JNICALL
Java_com_Harbinger_Spore_util_SporeNativeBridge_nCleanSetHealth(
    JNIEnv* env, jclass cls, jobject entity, jfloat health)
{
    return do_clean_call(env, entity, NULL, health, 0);
}
