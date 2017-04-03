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
#ifndef CLIENTSERVERSKELETON_NATIVECLIENTACTIVITY_H
#define CLIENTSERVERSKELETON_NATIVECLIENTACTIVITY_H

#include <jni.h>

void NativeOnCreate(JNIEnv* e, jobject activity);
void NativeOnClick(JNIEnv* e, jobject activity, jint tag);

void OnAuthenticated(void* cb_data, int rc, const char* auth_code,
                     const char* email, const char* id_token);

void AppendLogMessage(const char* format, ...);

#endif //CLIENTSERVERSKELETON_NATIVECLIENTACTIVITY_H
