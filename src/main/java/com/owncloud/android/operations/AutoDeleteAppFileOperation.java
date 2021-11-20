/*
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.operations;

import android.content.Context;

import com.nextcloud.client.account.User;
import com.nextcloud.client.account.UserAccountManager;
import com.owncloud.android.datamodel.ArbitraryDataProvider;
import com.owncloud.android.datamodel.UploadsStorageManager;
import com.owncloud.android.db.OCUpload;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.Calendar;

/**
 * Unshare file/folder Save the data in Database
 */
public class AutoDeleteAppFileOperation  {

    private static final String AUTO_DELETE_DIR = "app_folder_auto_delete";
    private final ArbitraryDataProvider db;
    private final Context context;
    private final UserAccountManager userAccountManager;
    private final User user;
    private JSONObject directoryJSON = new JSONObject();
    Calendar testDate = null;

    public AutoDeleteAppFileOperation(UserAccountManager accountManager, Context targetContext) {
        context = targetContext;
        userAccountManager = accountManager;
        user = userAccountManager.getUser();
        db = new ArbitraryDataProvider(targetContext.getContentResolver());
        loadAutoDeleteDirectory();
    }

    private void loadAutoDeleteDirectory() {
        assert user != null;
        String res = db.getValue(user, AUTO_DELETE_DIR);
        try {
            directoryJSON = new JSONObject(res);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public AutoDeleteAppFileOperation addDirectory(String directory, int offsetDays) {
        try {
           directoryJSON.put(directory,offsetDays);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    public AutoDeleteAppFileOperation deleteDirectory(String directory) {
        directoryJSON.remove(directory);
        return this;
    }

    public boolean isDirectoryAdded(String directory) {
        return directoryJSON.has(directory);
    }

    public int getDirectoryOffset(String directory) {
        int offset = 0;
        try {
            offset = directoryJSON.getInt(directory);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return offset;
    }

    public void save() {
        if(directoryJSON != null){
            String jsonStr = directoryJSON.toString();
            jsonStr = jsonStr.replaceAll("\\\\", "");
            db.storeOrUpdateKeyValue(user.getAccountName(),
                                     AUTO_DELETE_DIR,
                                     jsonStr);
        }
    }

    public int deleteFilesIfDatePast() {
        int deletedCount = 0;
        UploadsStorageManager uploadsStorageManager
            = new UploadsStorageManager(userAccountManager, context.getContentResolver());

        OCUpload[] uploads = uploadsStorageManager.getAllStoredUploads();
         for(OCUpload upload: uploads){
             String dir = getParentDirectory(upload);
             if(dir != null && directoryJSON.has(dir)){
                if(hasDaysToFilePast(upload, dir)){
                    File file = new File(upload.getLocalPath());
                    if(file.exists() && file.delete()){
                        deletedCount++;
                    }
                }
             }
         }
         return deletedCount;
    }

    private boolean hasDaysToFilePast(OCUpload upload, String dir) {
        long uploadTimestamp = upload.getUploadEndTimestamp();
        String pathLocal = upload.getLocalPath();

        int offsetDays = 0;
        boolean res = false;
        try {
             offsetDays = directoryJSON.getInt(dir);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(uploadTimestamp > 0 && offsetDays > 0){
            Calendar offsetCal = Calendar.getInstance();
            offsetCal.setTimeInMillis(uploadTimestamp);
            offsetCal.add(Calendar.DAY_OF_WEEK, offsetDays);
            Calendar today = getTodayDate();
            res = today.after(offsetCal);
        }
        return res;
    }

    private Calendar getTodayDate() {
        return testDate != null ? testDate : Calendar.getInstance();
    }

    public Calendar setTodayDate(Calendar date) {
        return testDate = date;
    }

    private String getParentDirectory(OCUpload upload) {
        String localPath = upload.getLocalPath();
        String parentName = null;
        if(upload.getAccountName() != null){
            String accountName = sanitizeAccount(upload.getAccountName());
            if (localPath.contains(accountName)) {
                String path = localPath.split(accountName)[1];
                String parentDir = new File(path).getParent();
                if(parentDir != null){
                    parentName = parentDir.equals("/") ? parentDir : parentDir + "/";
                }
            }
        }
        return parentName;
    }

    private String sanitizeAccount(String accountName) {
        String username = accountName.substring(0,accountName.lastIndexOf("@"));
        String url = accountName.substring(accountName.lastIndexOf("@") + 1);
        return username + "@" + URLEncoder.encode(url);
    }

}
