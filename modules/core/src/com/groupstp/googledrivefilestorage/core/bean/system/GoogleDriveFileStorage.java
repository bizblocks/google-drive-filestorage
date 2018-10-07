package com.groupstp.googledrivefilestorage.core.bean.system;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import com.groupstp.bills.core.config.GoogleDriveConfig;
import com.haulmont.bali.util.Preconditions;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileStorageException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.*;
import java.net.URLConnection;
import java.util.Collections;

import static com.haulmont.cuba.core.global.FileStorageException.Type.*;

/**
 * File storage implementation based on Google Drive
 *
 * @author adiatullin
 */
public class GoogleDriveFileStorage implements BillsFileStorageAPI {
    private static final Logger log = LoggerFactory.getLogger(GoogleDriveFileStorage.class);

    protected static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    @Inject
    protected GoogleDriveConfig config;

    @Override
    public long saveStream(FileDescriptor fd, InputStream inputStream) throws FileStorageException {
        Preconditions.checkNotNullArgument(fd.getSize());
        try {
            saveFile(fd, IOUtils.toByteArray(inputStream));
            return fd.getSize();
        } catch (Exception e) {
            if (e instanceof FileStorageException) {
                throw (FileStorageException) e;
            }
            throw new FileStorageException(IO_EXCEPTION, fd.getId().toString(), e);
        }
    }

    @Override
    public void saveFile(FileDescriptor fd, byte[] data) throws FileStorageException {
        Drive drive;
        try {
            drive = getDrive();
        } catch (Exception e) {
            throw new FileStorageException(STORAGE_INACCESSIBLE, fd.getId().toString(), e);
        }
        try {
            String type = URLConnection.guessContentTypeFromName(fd.getName());

            File googleFile = new File();
            googleFile.setName(fd.getId().toString() + "." + fd.getExtension());
            googleFile.setMimeType(type);
            String folderId = getFolderId(drive, fd);
            if (!StringUtils.isEmpty(folderId)) {
                googleFile.setParents(Collections.singletonList(folderId));
            }

            googleFile = drive.files().create(googleFile, new ByteArrayContent(type, data))
                    .setFields("id")
                    .execute();

            createReadPermission(drive, googleFile.getId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save file", e);
        }
    }

    @Override
    public void removeFile(FileDescriptor fd) throws FileStorageException {
        Drive drive;
        try {
            drive = getDrive();
        } catch (Exception e) {
            throw new FileStorageException(STORAGE_INACCESSIBLE, fd.getId().toString(), e);
        }
        try {
            drive.files().delete(getExternalId(drive, fd)).execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    @Override
    public InputStream openStream(FileDescriptor fileDescr) throws FileStorageException {
        return new ByteArrayInputStream(loadFile(fileDescr));
    }

    @Override
    public byte[] loadFile(FileDescriptor fd) throws FileStorageException {
        Drive drive;
        try {
            drive = getDrive();
        } catch (Exception e) {
            throw new FileStorageException(STORAGE_INACCESSIBLE, fd.getId().toString(), e);
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            drive.files().get(getExternalId(drive, fd))
                    .executeMediaAndDownloadTo(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file", e);
        }
    }

    @Override
    public boolean fileExists(FileDescriptor fd) throws FileStorageException {
        Drive drive;
        try {
            drive = getDrive();
        } catch (Exception e) {
            throw new FileStorageException(STORAGE_INACCESSIBLE, fd.getId().toString(), e);
        }
        try {
            File googleFile = drive.files().get(getExternalId(drive, fd)).execute();
            return googleFile != null && !Boolean.TRUE.equals(googleFile.getTrashed());
        } catch (Exception e) {
            log.error("Failed to check file status", e);
            return false;
        }
    }

    @Override
    public String getOpenUrl(FileDescriptor fd) throws FileStorageException {
        Preconditions.checkNotNullArgument(fd, "File descriptor is null");
        Drive drive;
        try {
            drive = getDrive();
        } catch (Exception e) {
            throw new FileStorageException(STORAGE_INACCESSIBLE, fd.getId().toString(), e);
        }
        try {
            return String.format(config.getViewUrlFormat(), getExternalId(drive, fd));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file direct url", e);
        }
    }

    /**
     * Retrieve file directory ID
     *
     * @param drive google drive link
     * @param fd    file description
     * @return ID of the folder for file
     * @throws Exception in case of any unexpected problems
     */
    protected String getFolderId(Drive drive, FileDescriptor fd) throws Exception {
        String folderId = null;
        String folderType = "application/vnd.google-apps.folder";
        String fileStorage = config.getFileStorageFolder();
        if (!StringUtils.isEmpty(fileStorage)) {
            String[] fileStoragePath = fileStorage.split("\\/");
            for (String pathPart : fileStoragePath) {
                if (!StringUtils.isBlank(pathPart)) {
                    FileList list = drive.files().list().setQ(
                            String.format("mimeType='%s' and name='%s' and trashed=false", folderType, pathPart)).execute();
                    if (list == null || CollectionUtils.isEmpty(list.getFiles())) {
                        File childFolder = new File().setName(pathPart).setMimeType(folderType);
                        if (!StringUtils.isEmpty(folderId)) {
                            childFolder.setParents(Collections.singletonList(folderId));
                        }
                        File createdFolder = drive.files()
                                .create(childFolder)
                                .setFields("id")
                                .execute();
                        folderId = createdFolder.getId();
                    } else {
                        folderId = list.getFiles().get(0).getId();
                    }
                }
            }
        }
        return folderId;
    }

    /**
     * Retrieve provided file external ID
     *
     * @param drive google drive link
     * @param fd    file descriptor
     * @return ID of the file
     * @throws Exception in case of any unexpected problems
     */
    protected String getExternalId(Drive drive, FileDescriptor fd) throws Exception {
        String name = fd.getId().toString();
        String nameWithExt = name + "." + fd.getExtension();

        FileList list = drive.files().list().setQ(
                String.format("(name='%s' or name='%s') and trashed=false", name, nameWithExt)).execute();
        if (list != null && !CollectionUtils.isEmpty(list.getFiles())) {
            return list.getFiles().get(0).getId();
        }
        throw new IllegalArgumentException(String.format("Failed to find external id for %s", fd.getName()));
    }

    /**
     * Create permission to read file by link
     *
     * @param drive  google drive link
     * @param fileId google file id
     * @return created permission object
     * @throws Exception in case of any unexpected problems
     */
    protected Permission createReadPermission(Drive drive, String fileId) throws Exception {
        Permission newPermission = new Permission();
        newPermission.setType("anyone");
        newPermission.setRole("reader");
        return drive.permissions().create(fileId, newPermission).execute();
    }

    /**
     * Get google drive link
     *
     * @return google drive link
     * @throws Exception in case of any unexpected problems
     */
    protected Drive getDrive() throws Exception {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(transport, JSON_FACTORY, null)
                .setHttpRequestInitializer(getCredentials(transport))
                .setApplicationName(config.getApplicationName())
                .build();
    }

    /**
     * Prepare and get google credentials
     *
     * @param transport communication transport object
     * @return google credentials
     * @throws Exception in case of any unexpected problems
     */
    protected Credential getCredentials(NetHttpTransport transport) throws Exception {
        Credential credential;
        /*
        //Using service account
        try (InputStream is = new ByteArrayInputStream(config.getClientSecret().getBytes(StandardCharsets.UTF_8))) {
            GoogleCredential c = GoogleCredential.fromStream(is);
            c = new GoogleCredential.Builder()
                    .setTransport(transport)
                    .setJsonFactory(JSON_FACTORY)
                    .setServiceAccountScopes(Collections.singleton(DriveScopes.DRIVE_FILE))
                    .setServiceAccountPrivateKeyId(c.getServiceAccountPrivateKeyId())
                    .setServiceAccountPrivateKey(c.getServiceAccountPrivateKey())
                    .setServiceAccountId(c.getServiceAccountId())
                    .setServiceAccountProjectId(c.getServiceAccountProjectId())
                    .setServiceAccountUser(c.getServiceAccountUser())
                    .build();
        }*/
        //Predefined access to specific google drive
        //guide (https://stackoverflow.com/questions/19766912/how-do-i-authorise-an-app-web-or-installed-without-user-intervention)
        try (StringReader reader = new StringReader(config.getClientOAuthSecretJson())) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
            credential = new GoogleCredential.Builder()
                    .setTransport(transport)
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(clientSecrets)
                    .build();
            credential.setRefreshToken(config.getRefreshToken());
        }
        /*
       // Using manual request to access to user google drive
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                transport, JSON_FACTORY, clientSecrets, Collections.singleton(DriveScopes.DRIVE_FILE))
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("GDrive")))
                .setAccessType("offline")
                .build();
        Credential c = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");*/
        return credential;
    }
}
