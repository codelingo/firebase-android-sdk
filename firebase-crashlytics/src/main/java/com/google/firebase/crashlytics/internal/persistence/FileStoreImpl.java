// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.persistence;

import android.content.Context;
import android.os.Environment;
import com.google.firebase.crashlytics.internal.Logger;
import java.io.File;

public class FileStoreImpl implements FileStore {
  public static final String FILES_PATH = ".com.google.firebase.crashlytics";

  private final Context context;

  /** Relative sub-path to use for storing files. */
  private final String filePath;

  public FileStoreImpl(Context context) {
    this.context = context;
    this.filePath = FILES_PATH;
  }

  public FileStoreImpl(Context context, String filePath) {
    this.context = context;
    this.filePath = filePath;
  }

  /** @return Directory to store internal files. */
  @Override
  public File getFilesDir() {
    return prepare(new File(context.getFilesDir(), filePath));
  }

  File prepare(File file) {
    if (file != null) {
      if (file.exists() || file.mkdirs()) {
        return file;
      } else {
        Logger.getLogger().w(Logger.TAG, "Couldn't create file");
      }
    } else {
      Logger.getLogger().d(Logger.TAG, "Null File");
    }
    return null;
  }

  boolean isExternalStorageAvailable() {
    final String state = Environment.getExternalStorageState();
    if (!Environment.MEDIA_MOUNTED.equals(state)) {
      Logger.getLogger()
          .w(
              Logger.TAG,
              "External Storage is not mounted and/or writable\n"
                  + "Have you declared android.permission.WRITE_EXTERNAL_STORAGE "
                  + "in the manifest?");
      return false;
    }

    return true;
  }
}
