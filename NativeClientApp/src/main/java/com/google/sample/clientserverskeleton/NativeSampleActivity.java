/*
 * Copyright (C) Google Inc.
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
package com.google.sample.clientserverskeleton;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Activity to demonstrate how to get a server auth code from native C++ code.
 * This activity is used to provide a simple UI for the sample.  The actual
 * process of authenticating is handled in other code.
 */
public class NativeSampleActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "NativeSampleActivity";
    public static final int TAG_SIGNIN = 1;
    public static final int TAG_SIGNOUT = 2;

    private TextView status;

    protected native void nativeOnCreate();

    protected native void nativeOnClick(int tag);

    static {
        System.loadLibrary("NativeClientActivity");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.statustext);

        Button button = (Button) findViewById(R.id.signin);
        // Set a tag so the native code can identify the button.
        button.setTag(TAG_SIGNIN);
        button.setOnClickListener(this);

        button = (Button) findViewById(R.id.signout);
        // Set a tag so the native code can identify the button.
        button.setTag(TAG_SIGNOUT);
        button.setOnClickListener(this);

        nativeOnCreate();
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        if (v.getTag() != null) {
            nativeOnClick((int) v.getTag());
        } else {
            Log.e(TAG, "onClick for view: " + v + " has no tag!");
        }
    }

    /**
     * Appends a message to the status text view.
     *
     * @param msg - the message to append.
     */
    public void appendStatusText(String msg) {
        status.append(msg);
    }
}
