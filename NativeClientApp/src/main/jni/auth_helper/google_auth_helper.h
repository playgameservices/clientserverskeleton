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
#ifndef GOOGLE_AUTH_HELPER_H_
#define GOOGLE_AUTH_HELPER_H_

#include <jni.h>

namespace auth_helper {
class GoogleAuthHelper {
 public:
  typedef void (*AuthCallback)(void* callback_data, int rc,
                               const char* auth_code, const char* email,
                               const char* id_token);
  struct Configuration {
    /// true to use games signin, false for default signin.
    bool use_game_signin;
    /// web client id associated with this app.
    const char* web_client_id;
    /// true for getting an auth code when authenticating.
    bool request_auth_code;
    /// true to request to reset the refresh token.  Causes re-consent.
    bool force_token_refresh;
    /// request email address, requires consent.
    bool request_email;
    /// request id token, requires consent.
    bool request_id_token;
    /// used with games signin to show or hide the connecting popup UI.
    /// and to associate an invisible view for other popups.  This is
    /// recommended for VR applications.
    bool hide_ui_popups;
    /// account name to use when authenticating, null indicates use default.
    const char* account_name;
    /// additional scopes to request, requires consent.
    const char** additional_scopes;
    int additional_scope_count;
  };
  static GoogleAuthHelper* Create(JavaVM* vm);
  bool Configure(jobject activity, const Configuration& config);
  void Authenticate(AuthCallback callback, void* callback_data);
  void Signout();
  static void NativeOnAuthResult(JNIEnv* env, jobject obj, jlong callback,
                                 jlong callback_data, jint result,
                                 jstring auth_code, jstring email,
                                 jstring id_token);

 private:
  static void Initialize(JavaVM* vm);
  static JavaVM* vm_;
  static jclass helper_clazz_;
  static jmethodID config_method_;
  static jmethodID authenticate_method_;
  static jmethodID signout_method_;

  GoogleAuthHelper();
  static JNIEnv* GetJniEnv();

  jobject pending_result_obj_;
};
}  // namespace auth_helper

#endif  // GOOGLE_AUTH_HELPER_H_
