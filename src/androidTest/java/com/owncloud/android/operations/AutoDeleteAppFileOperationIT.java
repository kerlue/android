/*
 *
 * Nextcloud Android client application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky
 * Copyright (C) 2020 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.owncloud.android.operations;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentResolver;
import android.content.Context;

import com.nextcloud.client.account.CurrentAccountProvider;
import com.nextcloud.client.account.UserAccountManagerImpl;
import com.owncloud.android.AbstractOnServerIT;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.datamodel.UploadsStorageManager;
import com.owncloud.android.db.OCUpload;
import com.owncloud.android.files.services.FileUploader;
import com.owncloud.android.lib.common.accounts.AccountUtils;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.utils.FileStorageUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import androidx.test.core.app.ApplicationProvider;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

//Issue: https://github.com/nextcloud/android/issues/9037
public class AutoDeleteAppFileOperationIT extends AbstractOnServerIT {
    private static final String TAG = AutoDeleteAppFileOperationIT.class.getSimpleName();
    private static final String TEST_ROOT = "/test";
    private UploadsStorageManager uploadsStorageManager;
    private CurrentAccountProvider currentAccountProvider = () -> null;
    private UserAccountManagerImpl userAccountManager;

    @Before
    public void setUp() throws AuthenticatorException, OperationCanceledException, AccountUtils.AccountNotFoundException, IOException {
        Context instrumentationCtx = ApplicationProvider.getApplicationContext();
        ContentResolver contentResolver = instrumentationCtx.getContentResolver();
        uploadsStorageManager = new UploadsStorageManager(currentAccountProvider, contentResolver);
        userAccountManager = UserAccountManagerImpl.fromContext(targetContext);
        userAccountManager = UserAccountManagerImpl.fromContext(targetContext);
    }

    @After
    public void after() {
        try {
            deleteRootTestDirectory();
        } catch (IOException e) {
            Log_OC.d(TAG, e.getMessage());
        }
        UploadsStorageManager uploadsStorageManager
            = new UploadsStorageManager(userAccountManager, targetContext.getContentResolver());
        uploadsStorageManager.removeAllUploads();
    }

    @Test
    public void addDirectoryTest(){
        String directory = TEST_ROOT + "/local3/file/";
        int offsetDays = 3;

        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);

        autoDeleteOps.addDirectory(directory, offsetDays);
        assertTrue(autoDeleteOps.isDirectoryAdded(directory));

        autoDeleteOps.addDirectory("", offsetDays);
        assertFalse(autoDeleteOps.isDirectoryAdded(""));

        autoDeleteOps.addDirectory(null, offsetDays);
        assertFalse(autoDeleteOps.isDirectoryAdded(null));
    }

    @Test
    public void deleteDirectoryTest(){
        String directory = TEST_ROOT + "/local2/file/";

        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);

        assertTrue(autoDeleteOps.addDirectory(directory, 6));
        assertTrue(autoDeleteOps.isDirectoryAdded(directory));
        assertTrue(autoDeleteOps.deleteDirectory(directory));
        assertFalse(autoDeleteOps.deleteDirectory(null));
        assertFalse(autoDeleteOps.deleteDirectory(""));
        assertFalse(autoDeleteOps.deleteDirectory("/not/saved/"));
    }

    @Test
    public void setDaysToKeepFileTest(){
        String directory = TEST_ROOT + "/local/file/";
        int offsetDays = 3;

        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);

        autoDeleteOps.addDirectory(directory, offsetDays);
        int actual = autoDeleteOps.getDirectoryOffset(directory);
        assertEquals(offsetDays,actual);

        assertFalse(autoDeleteOps.addDirectory(directory, 0));
    }

    @Test
    public void updateDaysToKeepFileTest(){
        String directory = TEST_ROOT + "/local/file/";
        int offsetDays = 3;
        int updatedOffsetDays = 30;

        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);

        autoDeleteOps.addDirectory(directory, offsetDays);
        int actual = autoDeleteOps.getDirectoryOffset(directory);
        assertEquals(offsetDays,actual);

        autoDeleteOps.addDirectory(directory, updatedOffsetDays);
        actual = autoDeleteOps.getDirectoryOffset(directory);
        assertEquals(updatedOffsetDays,actual);

    }

    @Test
    public void daysToKeepFilePastTest() throws Exception {
        String path = TEST_ROOT + "/file/upload1/";
        String filename = "testfile.txt";
        int daysToKeepFiles = 7;
        int daysPast = 8;
        //Upload test file
        File uploadedFile = uploadTestFileHelper(path, filename);
        assertTrue(uploadedFile.exists());
        //Add directory and days to keep files.
        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);
        autoDeleteOps.addDirectory(path, daysToKeepFiles);
        assertTrue(autoDeleteOps.isDirectoryAdded(path));
        //Check that file exist in app directory.
        assertTrue(uploadedFile.exists());
        //Perform auto delete
        Calendar currentDate = Calendar.getInstance();
        currentDate.add(Calendar.DAY_OF_WEEK, daysPast);
        autoDeleteOps.setTodayDate(currentDate);
        autoDeleteOps.deleteFilesIfDatePast();
        //Check that file is deleted.
        assertFalse(uploadedFile.exists());
    }


    @Test
    public void daysToKeepFileNotPastTest() throws Exception {
        String path = TEST_ROOT + "/file/upload2/";
        String filename = "testfile1.txt";
        int daysToKeepFiles = 7;
        int daysPast = 2;
        //Upload test file
        File uploadedFile = uploadTestFileHelper(path, filename);
        assertTrue(uploadedFile.exists());
        //Add directory and days to keep files.
        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);
        autoDeleteOps.addDirectory(path, daysToKeepFiles);
        assertTrue(autoDeleteOps.isDirectoryAdded(path));
        //Check that file exist in app directory.
        assertTrue(uploadedFile.exists());
        //Perform auto delete
        Calendar currentDate = Calendar.getInstance();
        currentDate.add(Calendar.DAY_OF_WEEK, daysPast);
        autoDeleteOps.setTodayDate(currentDate);
        autoDeleteOps.deleteFilesIfDatePast();
        //Check that file is deleted.
        assertTrue(uploadedFile.exists());
    }

    @Test
    public void daysToKeepFileIsTodayTest() throws Exception {
        String path = TEST_ROOT + "/file/upload3/";
        String filename = "testup.txt";
        int daysToKeepFiles = 9;
        int daysPast = 9;
        //Upload test file
        File uploadedFile = uploadTestFileHelper(path, filename);
        assertTrue(uploadedFile.exists());
        //Add directory and days to keep files.
        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);
        autoDeleteOps.addDirectory(path, daysToKeepFiles);
        assertTrue(autoDeleteOps.isDirectoryAdded(path));
        //Check that file exist in app directory.
        assertTrue(uploadedFile.exists());
        //Perform auto delete
        Calendar currentDate = Calendar.getInstance();
        currentDate.add(Calendar.DAY_OF_WEEK, daysPast);
        autoDeleteOps.setTodayDate(currentDate);
        autoDeleteOps.deleteFilesIfDatePast();
        //Check that file is deleted.
        assertFalse(uploadedFile.exists());
    }

    @Test
    public void daysToKeepFilePastWithDirectoryRemovedTest() throws Exception {
        String path = TEST_ROOT + "/file/upload5/";
        String filename = "testfile3.txt";
        int daysToKeepFiles = 7;
        int daysPast = 18;
        //Upload test file
        File uploadedFile = uploadTestFileHelper(path, filename);
        assertTrue(uploadedFile.exists());
        //Add directory and days to keep files.
        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);
        autoDeleteOps.addDirectory(path, daysToKeepFiles);
        assertTrue(autoDeleteOps.isDirectoryAdded(path));
        //Check that file exist in app directory.
        assertTrue(uploadedFile.exists());
        //Perform auto delete
        Calendar currentDate = Calendar.getInstance();
        currentDate.add(Calendar.DAY_OF_WEEK, daysPast);
        autoDeleteOps.setTodayDate(currentDate);
        autoDeleteOps.deleteDirectory(path);
        autoDeleteOps.deleteFilesIfDatePast();
        //Check that was not deleted.
        assertTrue(uploadedFile.exists());
    }

    @Test
    public void daysToKeepFilePastForOneFileTest() throws Exception {
        String path = TEST_ROOT + "/file/upload6/";
        String filename1 = "testfile5.txt";
        String filename2 = "testfile2.txt";
        int daysToKeepFiles = 7;
        int daysPast = 18;
        int filename2CreatedDaysInFuture = 120;
        //Upload first file
        File uploadedFile1 = uploadTestFileHelper(path, filename1);
        assertTrue(uploadedFile1.exists());
        //Upload second file with created day in the future.
        File uploadedFile2 = uploadTestFileHelper(path, filename2,filename2CreatedDaysInFuture);
        assertTrue(uploadedFile2.exists());
        //Add directory and days to keep files.
        AutoDeleteAppFileOperation autoDeleteOps
            = new AutoDeleteAppFileOperation(userAccountManager, targetContext);
        autoDeleteOps.addDirectory(path, daysToKeepFiles);
        assertTrue(autoDeleteOps.isDirectoryAdded(path));
        //Perform auto delete
        Calendar currentDate = Calendar.getInstance();
        currentDate.add(Calendar.DAY_OF_WEEK, daysPast);
        autoDeleteOps.setTodayDate(currentDate);
        autoDeleteOps.deleteFilesIfDatePast();
        //Check that only one file was deleted.
        assertTrue(uploadedFile2.exists());
        assertFalse(uploadedFile1.exists());
    }

    private File uploadTestFileHelper(String path, String filename, int uploadedDaysInFuture) throws IOException {
        Calendar currentDate = Calendar.getInstance();
        currentDate.add(Calendar.DAY_OF_WEEK, uploadedDaysInFuture);
        return performUpload(path,filename,currentDate.getTimeInMillis());
    }

    private File uploadTestFileHelper(String path, String filename) throws IOException {
        return performUpload(path,filename,System.currentTimeMillis());
    }


    private File performUpload(String path, String filename, long uploadedTime) throws IOException {
        createFile(filename, 9);

        OCFile encFolder = createFolder(path);
        getStorageManager().saveFolder(encFolder, new ArrayList<>(), new ArrayList<>());

        String fileToUpload = FileStorageUtils.
            getInternalTemporalPath(account.name, targetContext) + "/"+filename;

        OCUpload ocUpload = new OCUpload(fileToUpload,
                                         encFolder.getRemotePath() + filename,
                                         account.name);
        long uploadId = uploadsStorageManager.storeUpload(ocUpload);
        uploadOCUpload(ocUpload, FileUploader.LOCAL_BEHAVIOUR_MOVE);
        OCFile uploadedFile = fileDataStorageManager
            .getFileByDecryptedRemotePath(encFolder.getRemotePath() + filename);
        File uploadFile = new File(uploadedFile.getStoragePath());

        ocUpload.setUploadId(uploadId);
        ocUpload.setLocalPath(uploadFile.getPath());
        ocUpload.setUploadEndTimestamp(uploadedTime);
        uploadsStorageManager.updateUpload(ocUpload);
        return new File(uploadedFile.getStoragePath());
    }

    public static File createFile(String name, int iteration) throws IOException {
        File file = new File(FileStorageUtils.getInternalTemporalPath(account.name, targetContext) + File.separator + name);
        if (!file.getParentFile().exists()) {
            Assert.assertTrue(file.getParentFile().mkdirs());
        }

        file.createNewFile();

        FileWriter writer = new FileWriter(file);

        for (int i = 0; i < iteration; i++) {
            writer.write("123123123123123123123123123\n");
        }
        writer.flush();
        writer.close();

        return file;
    }

    public OCFile createFolder(String remotePath) {
        new CreateFolderOperation(remotePath, user, targetContext, getStorageManager())
            .execute(client);

        return getStorageManager().getFileByDecryptedRemotePath(remotePath);
    }

    private void deleteRootTestDirectory() throws IOException {
        File file =
            new File(FileStorageUtils.getInternalTemporalPath(account.name, targetContext) + TEST_ROOT);

        if( file.listFiles() != null){
            for(File fileToDelete: file.listFiles()){
                fileToDelete.delete();
            }
        }
    }


}
