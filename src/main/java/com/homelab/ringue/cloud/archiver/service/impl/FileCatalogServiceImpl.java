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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

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

    @Autowired
    public FileCatalogServiceImpl(FileCatalogItemRepository fileCatalogItemRepository, FileCatalogItemMapper fileCatalogItemMapper,CloudProviderFactory cloudProviderFactory, ApplicationProperties applicationProperties,SyncSummaryRepository syncSummaryRepository){
        this.fileCatalogItemRepository = fileCatalogItemRepository;
        this.fileCatalogItemMapper = fileCatalogItemMapper;
        this.cloudProviderFactory = cloudProviderFactory;
        this.applicationProperties = applicationProperties;
        this.syncSummaryRepository = syncSummaryRepository;
    }

    @Override
    public List<FileCatalogItem> findByFileNameContains(String fileName){
        return fileCatalogItemRepository.findByFileNameContains(fileName);
    }

    @Override
    public void performLocationSync(ScanLocationConfig scanlocationconfig) throws CloudBackupException {
        startCloudBackup(scanlocationconfig);
        int updloadCount = catalogCount.get();
        long uploadSize = catalogSize.get();
        int deleteCount = 0;
        long deleteSize = 0;
        if(scanlocationconfig.isCleanRemovedFromCloud()){
            startCloudCleanup(scanlocationconfig);
            deleteCount = catalogCount.get();
            deleteSize = catalogSize.get();
        }
        addSummaryEntry(updloadCount,uploadSize,deleteCount,deleteSize);
        log.info("Location: {} uploads: {} with {} bytes, deletes: {} with {} bytes",scanlocationconfig.getScanFolder(),updloadCount,uploadSize,deleteCount,deleteSize);
    }

    private void startCloudBackup(ScanLocationConfig locationConfig) throws CloudBackupException {
        log.info(">>> Starts cloud backup for {} using CloudProvider: {}",locationConfig.getScanFolder(),applicationProperties.getCloudProviderConfig().getType());
        catalogCount.set(0);
        catalogSize.set(0);
        performFolderBackup(locationConfig);
        log.info("<<< Completed cloud backup with {} items {} bytes uploaded to the CloudProvider {}",catalogCount.get(),catalogSize.get(), applicationProperties.getCloudProviderConfig().getType());
    }

    private void addSummaryEntry(int uploadCount, long uploadSize, int deleteCount, long deleteSize) {
        if(uploadCount == 0 && deleteCount == 0){
            //No summary is persisted
            return;
        }
        // Format LocalDate as a string with the desired format
        String summaryId = Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
        SyncSummaryItem syncSummary = syncSummaryRepository.findById(summaryId).orElseGet(()-> new SyncSummaryItem(summaryId, 0, 0, 0, 0,Instant.now()));
        SyncSummaryItem savedSummary = syncSummaryRepository.save(new SyncSummaryItem(summaryId, syncSummary.uploadCount() + uploadCount, syncSummary.uploadSize() + uploadSize, syncSummary.deleteCount() + deleteCount, syncSummary.deleteSize() + deleteSize,Instant.now()));
        log.info("Sync summary {}",savedSummary);
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
        String rootFolder = fixLocationPath(locationConfig.getScanFolder());
        Page<FileCatalogItem> catalogEntriesByPages = null;
        if(Optional.ofNullable(locationConfig.getStandardDeleteDaysLimit()).isPresent()){
            Date since = Date.from(Instant.now().minus(Duration.ofDays(locationConfig.getStandardDeleteDaysLimit())));
            Date olderThan = Date.from(Instant.now().minus(Duration.ofDays(locationConfig.getArchiveDeleteDaysHold() + locationConfig.getStandardDeleteDaysLimit())));
            log.debug("{} will check for imports since {} or older than {}",locationConfig.getScanFolder(),since,olderThan);
            catalogEntriesByPages = fileCatalogItemRepository.findByParentFolderStartingWithAndArchiveDateAfterOrArchiveDateBefore(rootFolder,since,olderThan,catalogPages);
        }else{
            catalogEntriesByPages = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder,catalogPages);
        }
        log.trace("Total results: {} Processing batch {}/{}",catalogEntriesByPages.getTotalElements(),catalogEntriesByPages.getNumber(),catalogEntriesByPages.getTotalPages());
        processCatalogEntryForCleanup(catalogEntriesByPages.get().parallel());
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
        .filter(importingFileCatalogItem -> {
            boolean shouldPersist = !collectionIdsInMemoryCache.containsKey(importingFileCatalogItem.absolutePath());
            if(!shouldPersist){
                collectionIdsInMemoryCache.remove(importingFileCatalogItem.absolutePath());
            }
            return shouldPersist;
        })
        .forEach(this::performCloudBackup);
    }

    private void putCollectionIdsInMemoryCache(ScanLocationConfig locationConfig, Pageable pageRequest, Map<String, FileCatalogItem> catalogItemsKeys) {
        String rootFolder = fixLocationPath(locationConfig.getScanFolder());
        Page<FileCatalogItem> archivedCatalogItems = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder,pageRequest);
        log.trace("Got documents {} for collection",archivedCatalogItems.getTotalElements());
        archivedCatalogItems.get().parallel().forEach(fileCatalogItem -> catalogItemsKeys.put(fileCatalogItem.absolutePath(),fileCatalogItem));
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
            fileCatalogItem = fileCatalogItemMapper.mapFromFileCatalogItemAddArchiveDateAndCheckSum(fileCatalogItem, getCrC32C(fileCatalogItem.absolutePath()));
            cloudProviderFactory.getCloudProvider(applicationProperties.getCloudProviderConfig().getType()).upload(fileCatalogItem);
            fileCatalogItemRepository.save(fileCatalogItem);
            catalogCount.incrementAndGet();
            catalogSize.addAndGet(fileCatalogItem.fileSize());
        } catch (Exception e) {
            log.error("Failed to upload {} to the cloud provider",fileCatalogItem.absolutePath(), e);
        }
    }

    private String getCrC32C(String absolutePath) throws IOException {
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

    @Override
    @Async
    public void performReconcile(ScanLocationConfig locationConfig) {
        log.info(">> Starts reconcile process {} -",locationConfig.getScanFolder());
        String rootFolder = fixLocationPath(locationConfig.getScanFolder());
        PageRequest pageRequest = PageRequest.ofSize(locationConfig.getCollectionFetchSize());
        performPagedReconcile(locationConfig, rootFolder, pageRequest);
        log.info("<< Ends reconcile process {} -",locationConfig.getScanFolder());
    }

    private void performPagedReconcile(ScanLocationConfig locationConfig, String rootFolder,
            Pageable pageRequest) {
        Page<FileCatalogItem> archivedCatalogItems = fileCatalogItemRepository.findByParentFolderStartsWith(rootFolder,pageRequest);
        archivedCatalogItems.get().parallel().forEach(this::verifyFileCatalogItemIntegrity);
        if((archivedCatalogItems.hasNext())){
            performPagedReconcile(locationConfig, rootFolder, archivedCatalogItems.nextPageable());
        }
    }

    private void verifyFileCatalogItemIntegrity(FileCatalogItem fileCatalogItem) {
        try{
            String localCrc32c = getCrC32C(fileCatalogItem.absolutePath());
            String cloudProviderItemCrc32c = cloudProviderFactory.getCloudProvider(applicationProperties.getCloudProviderConfig().getType()).getCheckSum(fileCatalogItem);
            if( localCrc32c.equals(cloudProviderItemCrc32c) && cloudProviderItemCrc32c.equals(fileCatalogItem.crc32c())){
                return;
            }
            log.debug("{} local: {} provider: {}",fileCatalogItem.absolutePath(),localCrc32c,cloudProviderItemCrc32c);
            if(localCrc32c.equals(cloudProviderItemCrc32c)){
                updateIndexedCheckSumWhenEmpty(cloudProviderItemCrc32c,fileCatalogItem);
            }else{
                log.warn("{} crc32c difference [local] , cloud provider {}, local {}, will update indexed to cloud value so future operations of cloud archiver re-upload 'modified' local file",fileCatalogItem.absolutePath(),cloudProviderItemCrc32c,localCrc32c);
                setIndexedCrc32c(cloudProviderItemCrc32c,fileCatalogItem);
            }
        }catch(NoSuchFileException nsfe){
            log.warn("{} not found on disk",fileCatalogItem.absolutePath());
        }catch(Exception e){
            log.error("Unable to verify {}",fileCatalogItem.absolutePath(), e);
        }
    
    }

    private void updateIndexedCheckSumWhenEmpty(String cloudProviderItemCrc32c, FileCatalogItem fileCatalogItem) {
        if(ObjectUtils.isEmpty(fileCatalogItem.crc32c())){
            log.debug("{} indexed crc32c missing, setting cloud value {}",fileCatalogItem.absolutePath(),cloudProviderItemCrc32c);
            setIndexedCrc32c(cloudProviderItemCrc32c, fileCatalogItem);
        }else{
            if(!cloudProviderItemCrc32c.equals(fileCatalogItem.crc32c())){
                log.warn("{} crc32c difference [indexed], cloud provider and file {}, indexed {}",fileCatalogItem.absolutePath(),cloudProviderItemCrc32c,fileCatalogItem.crc32c());
            }
        }
    }

    private void setIndexedCrc32c(String cloudProviderItemCrc32c, FileCatalogItem fileCatalogItem) {
        fileCatalogItem = fileCatalogItemMapper.mapFromFileCatalogItemUpdateCheckSumOnly(fileCatalogItem, cloudProviderItemCrc32c);
        fileCatalogItemRepository.save(fileCatalogItem);
    }

}
