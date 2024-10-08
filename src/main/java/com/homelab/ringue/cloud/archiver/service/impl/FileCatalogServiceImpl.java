package com.homelab.ringue.cloud.archiver.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import com.homelab.ringue.cloud.archiver.cloudprovider.CloudProviderFactory;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties;
import com.homelab.ringue.cloud.archiver.config.ApplicationProperties.ScanLocationConfig;
import com.homelab.ringue.cloud.archiver.domain.FileCatalogItem;
import com.homelab.ringue.cloud.archiver.domain.SyncSummaryItem;
import com.homelab.ringue.cloud.archiver.exception.CloudBackupException;
import com.homelab.ringue.cloud.archiver.repository.FileCatalogItemRepository;
import com.homelab.ringue.cloud.archiver.repository.SyncSummaryRepository;
import com.homelab.ringue.cloud.archiver.service.FileCatalogItemMapper;
import com.homelab.ringue.cloud.archiver.service.FileCatalogService;
import com.homelab.ringue.cloud.archiver.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Scope("prototype")
public class FileCatalogServiceImpl implements FileCatalogService{
    
    private FileCatalogItemRepository fileCatalogItemRepository;
    private SyncSummaryRepository syncSummaryRepository;

    private FileCatalogItemMapper fileCatalogItemMapper;

    private AtomicInteger catalogCount = new AtomicInteger(0);
    private AtomicLong catalogSize = new AtomicLong(0);

    private CloudProviderFactory cloudProviderFactory;

    private ApplicationProperties applicationProperties;

    private NotificationService notificationService;

    @Autowired
    public FileCatalogServiceImpl(FileCatalogItemRepository fileCatalogItemRepository, FileCatalogItemMapper fileCatalogItemMapper,CloudProviderFactory cloudProviderFactory, ApplicationProperties applicationProperties,SyncSummaryRepository syncSummaryRepository, NotificationService notificationService){
        this.fileCatalogItemRepository = fileCatalogItemRepository;
        this.fileCatalogItemMapper = fileCatalogItemMapper;
        this.cloudProviderFactory = cloudProviderFactory;
        this.applicationProperties = applicationProperties;
        this.syncSummaryRepository = syncSummaryRepository;
        this.notificationService = notificationService;
    }

    @Override
    public List<FileCatalogItem> findByFileNameContains(String fileName){
        return fileCatalogItemRepository.findByFileNameContains(fileName);
    }

    @Override
    public void performLocationSync(ScanLocationConfig scanlocationconfig) throws CloudBackupException {
        notificationService.notifyInfoMessage("Started backup process", scanlocationconfig);
        startCloudBackup(scanlocationconfig);
        int updloadCount = catalogCount.get();
        long uploadSize = catalogSize.get();
        int deleteCount = 0;
        long deleteSize = 0;
        if(scanlocationconfig.isCleanRemovedFromCloud()){
            notificationService.notifyInfoMessage("Started cleanup process", scanlocationconfig);
            startCloudCleanup(scanlocationconfig);
            deleteCount = catalogCount.get();
            deleteSize = catalogSize.get();
        }
        addSummaryEntry(updloadCount,uploadSize,deleteCount,deleteSize,scanlocationconfig);
        log.info("Location: {} uploads: {} with {} bytes, deletes: {} with {} bytes",scanlocationconfig.getScanFolder(),updloadCount,uploadSize,deleteCount,deleteSize);
    }

    private void startCloudBackup(ScanLocationConfig locationConfig) throws CloudBackupException {
        log.info(">>> Starts cloud backup for {} using CloudProvider: {}",locationConfig.getScanFolder(),applicationProperties.getCloudProviderConfig().getType());
        catalogCount.set(0);
        catalogSize.set(0);
        performFolderBackup(locationConfig);
        log.info("<<< Completed cloud backup with {} items {} bytes uploaded to the CloudProvider {}",catalogCount.get(),catalogSize.get(), applicationProperties.getCloudProviderConfig().getType());
    }

    private void addSummaryEntry(int uploadCount, long uploadSize, int deleteCount, long deleteSize, ScanLocationConfig scanlocationconfig) {
        // Format LocalDate as a string with the desired format
        String summaryId = Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
        SyncSummaryItem currentSyncSummaryItem = new SyncSummaryItem(summaryId, uploadCount, uploadSize, deleteCount, deleteSize,Instant.now());
        try{
            if(uploadCount == 0 && deleteCount == 0){
                //No summary is persisted
                return;
            }    
            SyncSummaryItem existingSyncSummary = syncSummaryRepository.findById(summaryId).orElseGet(()-> new SyncSummaryItem(summaryId, 0, 0, 0, 0,Instant.now()));
            SyncSummaryItem savedSummary = syncSummaryRepository.save(new SyncSummaryItem(summaryId, existingSyncSummary.uploadCount() + currentSyncSummaryItem.uploadCount(), existingSyncSummary.uploadSize() + currentSyncSummaryItem.uploadSize(),
            existingSyncSummary.deleteCount() + currentSyncSummaryItem.deleteCount(), existingSyncSummary.deleteSize() + currentSyncSummaryItem.deleteSize(),Instant.now()));
            log.info("Daily Sync summary {} for {}",savedSummary,scanlocationconfig.getScanFolder());
        }finally{
            notificationService.notifySummary(currentSyncSummaryItem,scanlocationconfig);
        }
    }

    private void startCloudCleanup(ScanLocationConfig locationConfig) throws CloudBackupException{
        log.info(">>> Starts cloud cleanup for {} using CloudProvider: {}",locationConfig.getScanFolder(),applicationProperties.getCloudProviderConfig().getType());
        catalogCount.set(0);
        catalogSize.set(0);
        performBucketCleanup(PageRequest.ofSize(locationConfig.getCollectionFetchSize()),locationConfig);
        log.info("<<< Completed cloud cleanup with {} deleted items {} bytes from the CloudProvider {}",catalogCount.get(),catalogSize.get(),applicationProperties.getCloudProviderConfig().getType());
    }

    private void performBucketCleanup(Pageable catalogPages,ScanLocationConfig locationConfig) {
        //Fix for windows locations regex
        log.debug("Pageable: page {}, size {}, location: {}",catalogPages.getPageNumber(),catalogPages.getPageSize(),locationConfig.getScanFolder());
        String rootFolder = fixLocationPath(locationConfig.getScanFolder());
        Page<FileCatalogItem> catalogEntriesByPages = null;
        if(Optional.ofNullable(locationConfig.getStandardDeleteDaysLimit()).isPresent()){
            Date since = Date.from(Instant.now().minus(Duration.ofDays(locationConfig.getStandardDeleteDaysLimit())));
            Date olderThan = Date.from(Instant.now().minus(Duration.ofDays(locationConfig.getArchiveDeleteDaysHold() + locationConfig.getStandardDeleteDaysLimit())));
            log.debug("{} will check for imports since {} or older than {}",locationConfig.getScanFolder(),since,olderThan);
            catalogEntriesByPages = fileCatalogItemRepository.findByParentFolderStartsWithAndArchiveDateAfterOrParentFolderStartsWithAndArchiveDateBefore(rootFolder,since,rootFolder,olderThan,catalogPages);
        }else{
            catalogEntriesByPages = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder,catalogPages);
        }
        log.trace("Total results: {} Processing batch {}/{}",catalogEntriesByPages.getTotalElements(),catalogEntriesByPages.getNumber(),catalogEntriesByPages.getTotalPages());
        processCatalogEntryForCleanup(catalogEntriesByPages.getContent().parallelStream());
        if(catalogEntriesByPages.hasNext()){
            performBucketCleanup(catalogEntriesByPages.nextPageable(),locationConfig);
        }
    }

    private String fixLocationPath(String locationPath) {
        CharSequence charSquence = new SimpleMessage("\\\\");
        return locationPath.replace(charSquence, "\\");
    }

    private void processCatalogEntryForCleanup(Stream<FileCatalogItem> parallelStream) {
        parallelStream
        .filter(this::isFileNotExistOnDisk)
        .forEach(this::handleFileCatalogItemDelete);
    }

    private void performFolderBackup(ScanLocationConfig locationConfig) throws CloudBackupException {
        log.trace("Starting on backup:{}",locationConfig.getScanFolder());
        Path folder = Paths.get(locationConfig.getScanFolder());
        log.trace("Got path for folder:{}",folder.toString());
        Map<String,FileCatalogItem> collectionIdsInMemoryCache = new ConcurrentHashMap<>();
        log.trace("About to get documents from collection:{}",folder.toString());
        putCollectionIdsInMemoryCache(locationConfig,PageRequest.ofSize(locationConfig.getCollectionFetchSize()),collectionIdsInMemoryCache);
        log.trace("InMemory collection size:{}",collectionIdsInMemoryCache.size());
        try (Stream<Path> filesList = Files.walk(folder).parallel()) {
            processFileStreamForBackup(locationConfig, collectionIdsInMemoryCache, filesList);
        } catch (Exception e) {
            log.error("Failed processing {} for cloud backup",locationConfig, e);
            notificationService.notifyError("Error during cloud backup:"+e.getMessage(),locationConfig);
            throw new CloudBackupException(locationConfig.getScanFolder(),e);
        }
        log.debug("Finished with backup process: {}",locationConfig.getScanFolder());
    }

    void processFileStreamForBackup(ScanLocationConfig locationConfig, Map<String, FileCatalogItem> collectionIdsInMemoryCache,
            Stream<Path> filesStream) {
        locationConfig.initScanConfigLocation();
        filesStream
        .map(fileCatalogItemMapper::mapFromPath)
        .filter(Objects::nonNull)
        .filter(fileCatalogItem -> this.applyFilteringRules(locationConfig,fileCatalogItem))
        .filter(importingFileCatalogItem -> !importingFileCatalogItem.isDirectory())
        .map(fileOnDisk-> getFileToProcessIfAny(collectionIdsInMemoryCache, fileOnDisk))
        .filter(Objects::nonNull)//Filters out null again due to crc32c failures will return null
        .forEach(this::performCloudBackup);
        log.info("Updating metadata only for {} items on {}",collectionIdsInMemoryCache.size(),locationConfig.getScanFolder());
        collectionIdsInMemoryCache.values().parallelStream()
        .forEach(fileCatalogItemRepository::save);
    }

    FileCatalogItem getFileToProcessIfAny(Map<String, FileCatalogItem> collectionIdsInMemoryCache,
            FileCatalogItem fileOnDisk) {
        Optional<FileCatalogItem> fileCatalogItem = Optional.ofNullable(collectionIdsInMemoryCache.remove(fileOnDisk.absolutePath()));
        boolean isModifiedOrNewItemItem = true;
        if(fileCatalogItem.isPresent() && fileCatalogItem.get().lastModified() != null && fileCatalogItem.get().lastModified().toEpochMilli() == fileOnDisk.lastModified().toEpochMilli()){
            return null;//No need to check for CRC as it is a backed up item that does not seems to have changed
        }
        if(isModifiedOrNewItemItem){
            fileOnDisk = getCrC32CPopulatedItem(fileOnDisk);
            if(fileCatalogItem.isPresent() && fileOnDisk != null && fileOnDisk.crc32c().equals(fileCatalogItem.get().crc32c())){
                //Leaves updated lastModifiedDate version on the memory cache
                collectionIdsInMemoryCache.put(fileOnDisk.absolutePath(), fileCatalogItemMapper.mapFromFileCatalogItemUpdateLastModified(fileCatalogItem.get(), fileOnDisk.lastModified()));
                return null;//Nothing to backup to cloud
            }
        }
        return fileOnDisk;
    }

    private void putCollectionIdsInMemoryCache(ScanLocationConfig locationConfig, Pageable pageRequest, Map<String, FileCatalogItem> catalogItemsKeys) {
        String rootFolder = fixLocationPath(locationConfig.getScanFolder());
        Page<FileCatalogItem> archivedCatalogItems = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder,pageRequest);
        log.trace("Got documents {} for collection from rootFolder {}",archivedCatalogItems.getTotalElements(),rootFolder);
        archivedCatalogItems.getContent().parallelStream().forEach(fileCatalogItem -> catalogItemsKeys.put(fileCatalogItem.absolutePath(),fileCatalogItem));
        if((archivedCatalogItems.hasNext())){
            log.trace("Pageable {}/{}",pageRequest.getPageSize(),pageRequest.getPageNumber());
            putCollectionIdsInMemoryCache(locationConfig,archivedCatalogItems.nextPageable(),catalogItemsKeys);
        }
    }

    // TODO: Refactor filtering application
    protected boolean applyFilteringRules(ScanLocationConfig locationConfig, FileCatalogItem fileCatalogItem){
        boolean ignoreHiddenFile = locationConfig.isIgnoreHiddenFiles();
        if(ignoreHiddenFile){
            try {
                ignoreHiddenFile = Files.isHidden(Paths.get(fileCatalogItem.absolutePath()));
            } catch (IOException e) {
                log.warn("Failed to resolve file visibility, defaults to hidden {}",fileCatalogItem.absolutePath());
            }
        }
        if(ignoreHiddenFile){
            log.trace("Ignored by hidden file {}",fileCatalogItem.absolutePath());
            return false;
        }
        boolean shallIgnoreByPattern = locationConfig.getCompiledIgnorePatterns().parallelStream().anyMatch(pattern -> pattern.matcher(fileCatalogItem.fileName()).matches());
        if(shallIgnoreByPattern){
            log.trace("Ignored by configured pattern: {}",fileCatalogItem.absolutePath());
            return false;
        }
        // Allows processing
        return true;
    }

    void performCloudBackup(FileCatalogItem fileCatalogItem){
        try {
            log.debug("Performing cloud backup for: {} with a size of: {}",fileCatalogItem.absolutePath(), fileCatalogItem.fileSize());
            fileCatalogItem = fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDate(fileCatalogItem);
            cloudProviderFactory.getCloudProvider(applicationProperties.getCloudProviderConfig().getType()).upload(fileCatalogItem);
            fileCatalogItemRepository.save(fileCatalogItem);
            catalogCount.incrementAndGet();
            catalogSize.addAndGet(fileCatalogItem.fileSize());
        } catch (Exception e) {
            log.error("Failed to upload {} to the cloud provider",fileCatalogItem.absolutePath(), e);
            ScanLocationConfig fileLocationConfig = new ScanLocationConfig();
            fileLocationConfig.setScanFolder(fileCatalogItem.absolutePath());
            notificationService.notifyError("Failed to upload "+e.getMessage(),fileLocationConfig);
        }
    }

    protected String getCrC32C(String absolutePath) throws IOException {
        byte[] crc32CheckSum = new byte[0];
        File file = Paths.get(absolutePath).toFile();
        if(!file.exists()){
            throw new NoSuchFileException(absolutePath);
        }
        byte[] buffer = new byte[applicationProperties.getCrc32cBufferSize()];
        int limit = -1;
        HashFunction crc32cHashFunc = Hashing.crc32c();
        Hasher crc32cHasher = crc32cHashFunc.newHasher();
        //Buffering is needed for large files
        try (FileInputStream fis = new FileInputStream(file)) {
            while ((limit = fis.read(buffer)) > 0) {
                crc32cHasher.putBytes(buffer, 0, limit);
            }
            crc32CheckSum = Ints.toByteArray(crc32cHasher.hash().asInt());
        } catch (IOException e) {
            log.error("Unable to get crc32c for {}", absolutePath,e);
        }
        return BaseEncoding.base64().encode(crc32CheckSum);
    }

    private boolean isFileNotExistOnDisk(FileCatalogItem filecatalogitem) {
        return Files.notExists(Paths.get(filecatalogitem.absolutePath()));
    }

    private void handleFileCatalogItemDelete(FileCatalogItem filecatalogitem) {
        try{
            cloudProviderFactory.getCloudProvider(applicationProperties.getCloudProviderConfig().getType()).delete(filecatalogitem);
            fileCatalogItemRepository.delete(filecatalogitem);
            catalogCount.incrementAndGet();
            catalogSize.addAndGet(filecatalogitem.fileSize());
        }catch(Exception e){
            log.error("Unable to delete {} from the CloudProvider {}, item will be preserved in the catalog database for next iteration attempt", filecatalogitem.absolutePath(),applicationProperties.getCloudProviderConfig().getType(),e);
        }
    }

    protected FileCatalogItem getCrC32CPopulatedItem(FileCatalogItem filecatalogitem){
        try {
            return fileCatalogItemMapper.mapFromFileCatalogItemUpdateCheckSum(filecatalogitem,getCrC32C(filecatalogitem.absolutePath()));
        } catch (IOException e) {
            log.error("{} failed on crc32c generation",filecatalogitem.absolutePath(),e);
            return null;
        }
    }

}
