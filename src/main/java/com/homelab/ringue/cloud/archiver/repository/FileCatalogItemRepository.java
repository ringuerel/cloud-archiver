package com.homelab.ringue.cloud.archiver.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;

@Repository
public interface FileCatalogItemRepository extends MongoRepository<FileCatalogItem,String>{
    Optional<FileCatalogItem> findById(String absolutePath);
    List<FileCatalogItem> findByFileNameContains(String fileName);
    Page<FileCatalogItem> findByParentFolder(String parentFolder,Pageable pageable);
    Page<FileCatalogItem> findByParentFolderAndArchiveDateAfterOrParentFolderAndArchiveDateBefore(
            String rootFolder, Date since, String rootFolder2, Date olderThan, Pageable catalogPages);
}
