/*
 * Copyright (C) 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.sample.clientserverskeleton.client;

/**
 * Interface combining the Error and Response callbacks.
 *
 * @param <T> - the type of the responses object.
 */
public interface ClientResultListener<T> {

    /**
     * Called when the request was successful.
     *
     * @param result - the deserialized object.
     */
    void onSuccess(T result);

    /**
     * Called when there was an error in the request.
     *
     * @param error - the error code, usually an HTTP status code.
     * @param msg   - can be null if the error code is self explanatory.
     */
    void onFailure(int error, String msg);
}
