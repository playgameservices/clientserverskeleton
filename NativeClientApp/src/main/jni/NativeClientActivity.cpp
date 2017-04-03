//  Copyright (c) 2017 Google. All rights reserved.
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#include "NativeClientActivity.h"

#include <android/log.h>
#include <jni.h>
#include <stdarg.h>
#include <stdio.h>
#include <cstring>

#include "auth_helper/google_auth_helper.h"

#define TAG_SIGNIN 1
#define TAG_SIGNOUT 2
#define LOG_TAG "NativeClientActivity"

void CheckJNIException();

static JavaVM* g_vm;

static auth_helper::GoogleAuthHelper* g_instance;

static jobject g_activity;
static jclass g_activity_class;
static jmethodID g_append_text_method_id;
static jmethodID g_get_string_method;

static auth_helper::GoogleAuthHelper::Configuration AuthHelperConfig = {
    .use_game_signin = true,
    .web_client_id = nullptr,  // set to the value in onCreated.
    .request_auth_code = true,
    .force_token_refresh = false,
    .request_email = false,
    .request_id_token = false,
    .additional_scopes = nullptr,
    .additional_scope_count = 0,
    .hide_ui_popups = false};

// Methods called by the Java Activity.
static JNINativeMethod methods[] = {
    {"nativeOnCreate", "()V", reinterpret_cast<void*>(NativeOnCreate)},
    {"nativeOnClick", "(I)V", reinterpret_cast<void*>(NativeOnClick)},
};
extern "C" {
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    return -1;
  }

  g_vm = vm;

  // Get jclass with env->FindClass.
  // Register methods with env->RegisterNatives.

  g_activity_class =
      env->FindClass(
      "com/google/sample/clientserverskeleton/NativeSampleActivity");

  CheckJNIException();

  if (g_activity_class) {
    g_activity_class = (jclass)env->NewGlobalRef(g_activity_class);
    env->RegisterNatives(g_activity_class, methods,
                         sizeof(methods) / sizeof(methods[0]));
    g_append_text_method_id = env->GetMethodID(
        g_activity_class, "appendStatusText", "(Ljava/lang/String;)V");
    g_get_string_method = env->GetMethodID(g_activity_class, "getString",
                                           "(I)Ljava/lang/String;");
    CheckJNIException();
  }

  return JNI_VERSION_1_6;
}
}

// Get the JNI environment.
JNIEnv* GetJniEnv() {
  JavaVM* vm = g_vm;
  JNIEnv* env;
  jint result = vm->AttachCurrentThread(&env, nullptr);
  return result == JNI_OK ? env : nullptr;
}

// Checks if a JNI exception has happened, and if so, logs it to the console.
void CheckJNIException() {
  JNIEnv* env = GetJniEnv();
  if (env->ExceptionCheck()) {
    // Get the exception text.
    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();

    // Convert the exception to a string.
    jclass object_class = env->FindClass("java/lang/Object");
    jmethodID toString =
        env->GetMethodID(object_class, "toString", "()Ljava/lang/String;");
    jstring s = (jstring)env->CallObjectMethod(exception, toString);
    const char* exception_text = env->GetStringUTFChars(s, nullptr);

    // Log the exception text.
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "-------------------JNI exception:");
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", exception_text);
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "-------------------");

    // In the event we didn't assert fail, clean up.
    env->ReleaseStringUTFChars(s, exception_text);
    env->DeleteLocalRef(s);
    env->DeleteLocalRef(exception);
  }
}

void NativeOnCreate(JNIEnv* env, jobject activity) {
  jobjectRefType type = env->GetObjectRefType(activity);

  g_activity = env->NewGlobalRef(activity);

  __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Finding web client id");

  // Get the web client id
  jclass res_id_cls =
      env->FindClass("com/google/sample/clientserverskeleton/R$string");

  jfieldID fid = env->GetStaticFieldID(res_id_cls, "webclient_id", "I");

  jint j_id = env->GetStaticIntField(res_id_cls, fid);

  if (j_id) {
    jstring j_str =
        (jstring)env->CallObjectMethod(g_activity, g_get_string_method, j_id);
    int size = env->GetStringUTFLength(j_str);
    char* client_id = new char[size + 2];
    const char* value = env->GetStringUTFChars(j_str, nullptr);
    strcpy(client_id, value);
    AuthHelperConfig.web_client_id = client_id;
    env->ReleaseStringUTFChars(j_str, value);
    env->DeleteLocalRef(j_str);
    AppendLogMessage("Native Client Initialized");
  } else {
    AppendLogMessage("Could not load web client id");
  }
  env->DeleteLocalRef(res_id_cls);

  // Register the auth helper class.
  g_instance = auth_helper::GoogleAuthHelper::Create(g_vm);
}

void NativeOnClick(JNIEnv* env, jobject activity, jint tag) {
  if (tag == TAG_SIGNIN) {
    AppendLogMessage("Calling Authenticate!");
    g_instance->Configure(activity, AuthHelperConfig);
    g_instance->Authenticate(OnAuthenticated, nullptr);
  } else if (tag == TAG_SIGNOUT) {
    AppendLogMessage("Calling Signout");
    g_instance->Signout();
  } else {
    AppendLogMessage("Unknown tag %d in OnClick", tag);
  }
}

void OnAuthenticated(void* cb_data, int rc, const char* auth_code,
                     const char* email, const char* id_token) {
  AppendLogMessage("Callback returned %d\n", rc);
  AppendLogMessage("   Authcode: %s\n", auth_code ? auth_code : "<null>");
  AppendLogMessage("   email: %s\n", email ? email : "<null>");
  AppendLogMessage("   id_token: %s\n", id_token ? id_token : "<null>");

  // Call gpg StartAuthorizationUI() to complete Games initialization.
}

void AppendLogMessage(const char* format, ...) {
  static const int kLineBufferSize = 256;
  char buffer[kLineBufferSize + 2];
  va_list list;
  va_start(list, format);
  int string_len = vsnprintf(buffer, kLineBufferSize, format, list);
  string_len = string_len < kLineBufferSize ? string_len : kLineBufferSize;
  // append a linebreak to the buffer:
  buffer[string_len] = '\n';
  buffer[string_len + 1] = '\0';

  __android_log_vprint(ANDROID_LOG_INFO, LOG_TAG, format, list);

  JNIEnv* env = GetJniEnv();
  jstring text_string = env->NewStringUTF(buffer);
  env->CallVoidMethod(g_activity, g_append_text_method_id, text_string);
  env->DeleteLocalRef(text_string);

  CheckJNIException();
  va_end(list);
}
