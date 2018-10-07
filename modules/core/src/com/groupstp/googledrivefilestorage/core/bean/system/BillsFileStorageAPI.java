package com.groupstp.googledrivefilestorage.core.bean.system;

import com.haulmont.cuba.core.app.FileStorageAPI;
import com.haulmont.cuba.core.entity.FileDescriptor;
import com.haulmont.cuba.core.global.FileStorageException;

/**
 * @author adiatullin
 */
public interface BillsFileStorageAPI extends FileStorageAPI {

    String NAME = FileStorageAPI.NAME;

    /**
     * Get direct open url by provided file descriptor
     *
     * @param fd file descriptor
     * @return direct open url link
     */
    String getOpenUrl(FileDescriptor fd) throws FileStorageException;
}
