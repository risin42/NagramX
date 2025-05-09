// Copyright 2014 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


// This file is autogenerated by
//     third_party/jni_zero/jni_generator.py
// For
//     org/webrtc/SessionDescription

#ifndef org_webrtc_SessionDescription_JNI
#define org_webrtc_SessionDescription_JNI

#include <jni.h>

#include "third_party/jni_zero/jni_export.h"
#include "webrtc/sdk/android/src/jni/jni_generator_helper.h"


// Step 1: Forward declarations.

JNI_ZERO_COMPONENT_BUILD_EXPORT extern const char kClassPath_org_webrtc_SessionDescription[];
const char kClassPath_org_webrtc_SessionDescription[] = "org/webrtc/SessionDescription";

JNI_ZERO_COMPONENT_BUILD_EXPORT extern const char
    kClassPath_org_webrtc_SessionDescription_00024Type[];
const char kClassPath_org_webrtc_SessionDescription_00024Type[] =
    "org/webrtc/SessionDescription$Type";
// Leaking this jclass as we cannot use LazyInstance from some threads.
JNI_ZERO_COMPONENT_BUILD_EXPORT std::atomic<jclass> g_org_webrtc_SessionDescription_clazz(nullptr);
#ifndef org_webrtc_SessionDescription_clazz_defined
#define org_webrtc_SessionDescription_clazz_defined
inline jclass org_webrtc_SessionDescription_clazz(JNIEnv* env) {
  return jni_zero::LazyGetClass(env, kClassPath_org_webrtc_SessionDescription,
      &g_org_webrtc_SessionDescription_clazz);
}
#endif
// Leaking this jclass as we cannot use LazyInstance from some threads.
JNI_ZERO_COMPONENT_BUILD_EXPORT std::atomic<jclass>
    g_org_webrtc_SessionDescription_00024Type_clazz(nullptr);
#ifndef org_webrtc_SessionDescription_00024Type_clazz_defined
#define org_webrtc_SessionDescription_00024Type_clazz_defined
inline jclass org_webrtc_SessionDescription_00024Type_clazz(JNIEnv* env) {
  return jni_zero::LazyGetClass(env, kClassPath_org_webrtc_SessionDescription_00024Type,
      &g_org_webrtc_SessionDescription_00024Type_clazz);
}
#endif


// Step 2: Constants (optional).


// Step 3: Method stubs.
namespace webrtc {
namespace jni {


static std::atomic<jmethodID> g_org_webrtc_SessionDescription_Constructor2(nullptr);
static jni_zero::ScopedJavaLocalRef<jobject> Java_SessionDescription_Constructor(JNIEnv* env, const
    jni_zero::JavaRef<jobject>& type,
    const jni_zero::JavaRef<jstring>& description) {
  jclass clazz = org_webrtc_SessionDescription_clazz(env);
  CHECK_CLAZZ(env, clazz,
      org_webrtc_SessionDescription_clazz(env), nullptr);

  jni_zero::JniJavaCallContextChecked call_context;
  call_context.Init<
      jni_zero::MethodID::TYPE_INSTANCE>(
          env,
          clazz,
          "<init>",
          "(Lorg/webrtc/SessionDescription$Type;Ljava/lang/String;)V",
          &g_org_webrtc_SessionDescription_Constructor2);

  jobject ret =
      env->NewObject(clazz,
          call_context.base.method_id, type.obj(), description.obj());
  return jni_zero::ScopedJavaLocalRef<jobject>(env, ret);
}

static std::atomic<jmethodID> g_org_webrtc_SessionDescription_getDescription0(nullptr);
static jni_zero::ScopedJavaLocalRef<jstring> Java_SessionDescription_getDescription(JNIEnv* env,
    const jni_zero::JavaRef<jobject>& obj) {
  jclass clazz = org_webrtc_SessionDescription_clazz(env);
  CHECK_CLAZZ(env, obj.obj(),
      org_webrtc_SessionDescription_clazz(env), nullptr);

  jni_zero::JniJavaCallContextChecked call_context;
  call_context.Init<
      jni_zero::MethodID::TYPE_INSTANCE>(
          env,
          clazz,
          "getDescription",
          "()Ljava/lang/String;",
          &g_org_webrtc_SessionDescription_getDescription0);

  jstring ret =
      static_cast<jstring>(env->CallObjectMethod(obj.obj(),
          call_context.base.method_id));
  return jni_zero::ScopedJavaLocalRef<jstring>(env, ret);
}

static std::atomic<jmethodID> g_org_webrtc_SessionDescription_getTypeInCanonicalForm0(nullptr);
static jni_zero::ScopedJavaLocalRef<jstring> Java_SessionDescription_getTypeInCanonicalForm(JNIEnv*
    env, const jni_zero::JavaRef<jobject>& obj) {
  jclass clazz = org_webrtc_SessionDescription_clazz(env);
  CHECK_CLAZZ(env, obj.obj(),
      org_webrtc_SessionDescription_clazz(env), nullptr);

  jni_zero::JniJavaCallContextChecked call_context;
  call_context.Init<
      jni_zero::MethodID::TYPE_INSTANCE>(
          env,
          clazz,
          "getTypeInCanonicalForm",
          "()Ljava/lang/String;",
          &g_org_webrtc_SessionDescription_getTypeInCanonicalForm0);

  jstring ret =
      static_cast<jstring>(env->CallObjectMethod(obj.obj(),
          call_context.base.method_id));
  return jni_zero::ScopedJavaLocalRef<jstring>(env, ret);
}

static std::atomic<jmethodID> g_org_webrtc_SessionDescription_00024Type_fromCanonicalForm1(nullptr);
static jni_zero::ScopedJavaLocalRef<jobject> Java_Type_fromCanonicalForm(JNIEnv* env, const
    jni_zero::JavaRef<jstring>& canonical) {
  jclass clazz = org_webrtc_SessionDescription_00024Type_clazz(env);
  CHECK_CLAZZ(env, clazz,
      org_webrtc_SessionDescription_00024Type_clazz(env), nullptr);

  jni_zero::JniJavaCallContextChecked call_context;
  call_context.Init<
      jni_zero::MethodID::TYPE_STATIC>(
          env,
          clazz,
          "fromCanonicalForm",
          "(Ljava/lang/String;)Lorg/webrtc/SessionDescription$Type;",
          &g_org_webrtc_SessionDescription_00024Type_fromCanonicalForm1);

  jobject ret =
      env->CallStaticObjectMethod(clazz,
          call_context.base.method_id, canonical.obj());
  return jni_zero::ScopedJavaLocalRef<jobject>(env, ret);
}

}  // namespace jni
}  // namespace webrtc

#endif  // org_webrtc_SessionDescription_JNI
