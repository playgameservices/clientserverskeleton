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
#include "google_auth_helper.h"

#define HELPER_CLASSNAME "com/google/sample/authhelper/AuthHelperFragment"

namespace auth_helper {

JavaVM* GoogleAuthHelper::vm_;

jclass GoogleAuthHelper::helper_clazz_;
jmethodID GoogleAuthHelper::config_method_;
jmethodID GoogleAuthHelper::authenticate_method_;
jmethodID GoogleAuthHelper::signout_method_;

// Methods called by the Java Activity.
static JNINativeMethod methods[] = {
    {"nativeOnAuthResult",
     "(JJILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
     reinterpret_cast<void*>(GoogleAuthHelper::NativeOnAuthResult)},
};

GoogleAuthHelper* GoogleAuthHelper::Create(JavaVM* vm) {
  Initialize(vm);
  return new GoogleAuthHelper();
}

void GoogleAuthHelper::Initialize(JavaVM* vm) {
  vm_ = vm;
  JNIEnv* env = GetJniEnv();

  // Find the java  helper class
  // initialize it.
  helper_clazz_ = env->FindClass(HELPER_CLASSNAME);
  if (helper_clazz_) {
    helper_clazz_ = (jclass)env->NewGlobalRef(helper_clazz_);
    env->RegisterNatives(helper_clazz_, methods,
                         sizeof(methods) / sizeof(methods[0]));
    config_method_ = env->GetStaticMethodID(
        helper_clazz_, "configure",
        "(Landroid/app/Activity;"
        "ZLjava/lang/String;"
        "ZZZZZLjava/lang/String;"
        "[Ljava/lang/String;)Z");
    authenticate_method_ =
        env->GetStaticMethodID(helper_clazz_, "authenticate", "(JJ)V");
    signout_method_ = env->GetStaticMethodID(helper_clazz_, "signOut", "()V");
  }
}

GoogleAuthHelper::GoogleAuthHelper() {}

bool GoogleAuthHelper::Configure(jobject activity,
                                 const Configuration& config) {
  JNIEnv* env = GetJniEnv();

  jstring j_web_client_id =
      config.web_client_id ? env->NewStringUTF(config.web_client_id) : nullptr;

  jstring j_account_name =
      config.account_name ? env->NewStringUTF(config.account_name) : nullptr;

  jobjectArray j_auth_scopes = nullptr;

  if (config.additional_scope_count > 0) {
    jclass string_clazz = env->FindClass("java/lang/String");
    j_auth_scopes = env->NewObjectArray(config.additional_scope_count,
                                        string_clazz, nullptr);

    for (int i = 0; i < config.additional_scope_count; i++) {
      env->SetObjectArrayElement(
          j_auth_scopes, i, env->NewStringUTF(config.additional_scopes[i]));
    }
    env->DeleteLocalRef(string_clazz);
  }

  bool retval = env->CallStaticBooleanMethod(
      helper_clazz_, config_method_, activity, config.use_game_signin,
      j_web_client_id, config.request_auth_code, config.force_token_refresh,
      config.request_email, config.request_id_token, config.hide_ui_popups,
      j_account_name, j_auth_scopes);

  if (j_web_client_id) {
    env->DeleteLocalRef(j_web_client_id);
  }

  if (j_account_name) {
    env->DeleteLocalRef(j_account_name);
  }

  if (j_auth_scopes) {
    env->DeleteLocalRef(j_auth_scopes);
  }

  return retval;
}
void GoogleAuthHelper::Authenticate(AuthCallback callback,
                                    void* callback_data) {
  JNIEnv* env = GetJniEnv();

  env->CallStaticVoidMethod(helper_clazz_, authenticate_method_,
                            reinterpret_cast<jlong>(callback),
                            reinterpret_cast<jlong>(callback_data));
}

void GoogleAuthHelper::Signout() {
  JNIEnv* env = GetJniEnv();

  env->CallStaticVoidMethod(helper_clazz_, signout_method_);
}

void GoogleAuthHelper::NativeOnAuthResult(JNIEnv* env, jobject obj,
                                          jlong callback, jlong callback_data,
                                          jint result, jstring auth_code,
                                          jstring email, jstring id_token) {
  AuthCallback cb = reinterpret_cast<AuthCallback>(callback);
  if (cb) {
    const char* c_auth_code =
        auth_code ? env->GetStringUTFChars(auth_code, nullptr) : nullptr;
    const char* c_email =
        email ? env->GetStringUTFChars(email, nullptr) : nullptr;
    const char* c_id_token =
        id_token ? env->GetStringUTFChars(id_token, nullptr) : nullptr;
    cb(reinterpret_cast<void*>(callback_data), result, c_auth_code, c_email,
       c_id_token);
    if (c_auth_code) {
      env->ReleaseStringUTFChars(auth_code, c_auth_code);
    }
    if (c_email) {
      env->ReleaseStringUTFChars(email, c_email);
    }
    if (c_id_token) {
      env->ReleaseStringUTFChars(id_token, c_id_token);
    }
  }
}

JNIEnv* GoogleAuthHelper::GetJniEnv() {
  JNIEnv* env;
  jint result = vm_->AttachCurrentThread(&env, nullptr);
  return result == JNI_OK ? env : nullptr;
}

}  // namespace auth_helper
