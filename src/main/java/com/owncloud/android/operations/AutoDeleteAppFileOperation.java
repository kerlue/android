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
import android.util.Log;

import com.nextcloud.client.account.User;
import com.nextcloud.client.account.UserAccountManager;
import com.owncloud.android.datamodel.ArbitraryDataProvider;
import com.owncloud.android.datamodel.UploadsStorageManager;
import com.owncloud.android.db.OCUpload;
import com.owncloud.android.lib.common.utils.Log_OC;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.util.Calendar;

/**
 * Unshare file/folder Save the data in Database
 */
//Issue: https://github.com/nextcloud/android/issues/9037
public class AutoDeleteAppFileOperation  {
    private static final String TAG = AutoDeleteAppFileOperation.class.getSimpleName();
    private static final String AUTO_DELETE_DIR = "app_folder_auto_delete";
    private final ArbitraryDataProvider db;
    private final Context context;
    private final UserAccountManager userAccountManager;
    private final User user;
    private JSONObject directoryJSON = new JSONObject();
    Calendar testDate = null;

    /**
     * Create an object for adding directories that files should be kept for a limited time.
     */

    public AutoDeleteAppFileOperation(UserAccountManager accountManager, Context targetContext) {
        context = targetContext;
        userAccountManager = accountManager;
        user = userAccountManager.getUser();
        db = new ArbitraryDataProvider(targetContext.getContentResolver());
        loadAutoDeleteDirectory();
    }

    /**
     * Add or update directory in which files will be deleted after offsetDays hsa past.
     * OffsetDays represents the number of days a file will be kept
     */
    public boolean addDirectory(String directory, int offsetDays) {
        try {
            if(offsetDays > 0 && directory != null  && !"".equals(directory)){
                directoryJSON.put(directory,offsetDays);
                return save();
            }
        } catch (JSONException e) {
            Log_OC.d(TAG,e.getMessage());
        }
        return false;
    }

    /**
     * Remove directory from auto delete list. Files will be kept indefinitely
     */
    public Boolean deleteDirectory(String directory) {
        return directoryJSON.remove(directory) != null && save();
    }

    /**
     * Check if a directory is setup for auto delete.
     */
    public boolean isDirectoryAdded(String directory) {
        return directoryJSON.has(directory);
    }

    /**
     * Get how long a file will be kept in days.
     */
    public int getDirectoryOffset(String directory) {
        int offset = 0;
        try {
            offset = directoryJSON.getInt(directory);
        } catch (JSONException e) {
            Log_OC.d(TAG,e.getMessage());
        }
        return offset;
    }

    /**
     * Persist changes to databse.
     */
    private boolean save() {
        if(directoryJSON != null){
            String jsonStr = directoryJSON.toString();
            jsonStr = jsonStr.replaceAll("\\\\", "");
            db.storeOrUpdateKeyValue(user.getAccountName(),
                                     AUTO_DELETE_DIR,
                                     jsonStr);
            return true;
        }
        return false;
    }

    /**
     * Delete files if daysoffset past
     */
    public int deleteFilesIfDatePast() {
        int deletedCount = 0;
        UploadsStorageManager uploadsStorageManager
            = new UploadsStorageManager(userAccountManager, context.getContentResolver());

        OCUpload[] uploads = uploadsStorageManager.getAllStoredUploads();
         for(OCUpload upload: uploads){
             String dir = getParentDirectory(upload);
             boolean hasKey = dir != null && directoryJSON.has(dir);
             if(hasKey && hasDaysToFilePast(upload, dir)){
                 File file = new File(upload.getLocalPath());
                 if(file.exists() && file.delete()){
                     deletedCount++;
                 }
             }
         }
         return deletedCount;
    }

    /**
     * Used ONLY for testing. Simulate today's date.
     */
    public void setTodayDate(Calendar date) {
        testDate = date;
    }

    private void loadAutoDeleteDirectory() {
        assert user != null;
        String res = db.getValue(user, AUTO_DELETE_DIR);
        try {
            directoryJSON = new JSONObject(res);
        } catch (JSONException e) {
            Log_OC.d(TAG,e.getMessage());
        }
    }

    private boolean hasDaysToFilePast(OCUpload upload, String dir) {
        long uploadTimestamp = upload.getUploadEndTimestamp();
        int offsetDays = 0;
        boolean res = false;
        try {
             offsetDays = directoryJSON.getInt(dir);
        } catch (JSONException e) {
            Log_OC.d(TAG,e.getMessage());
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

    private String getParentDirectory(OCUpload upload) {
        String localPath = upload.getLocalPath();
        String parentName = null;
        if(upload.getAccountName() != null){
            String accountName = sanitizeAccount(upload.getAccountName());
            if (localPath.contains(accountName)) {
                String path = localPath.split(accountName)[1];
                String parentDir = new File(path).getParent();
                if(parentDir != null){
                    parentName = "/".equals(parentDir) ? parentDir : parentDir + "/";
                }
            }
        }
        return parentName;
    }

    private String sanitizeAccount(String accountName) {
        String username = accountName.substring(0,accountName.lastIndexOf('@'));
        String url = accountName.substring(accountName.lastIndexOf('@') + 1);
        return username + "@" + URLEncoder.encode(url);
    }

}
